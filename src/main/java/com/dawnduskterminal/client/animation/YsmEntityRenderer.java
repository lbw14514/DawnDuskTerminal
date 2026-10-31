package com.dawnduskterminal.client.animation;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.NativeGeoMesh;
import com.dawnduskterminal.client.NativeMesh;
import com.dawnduskterminal.client.NativeMeshBatch;
import com.dawnduskterminal.geckolib3.core.util.Color;
import com.dawnduskterminal.geckolib3.geo.GeoReplacedEntityRenderer;
import com.dawnduskterminal.geckolib3.geo.animated.AnimatedGeoBone;
import com.dawnduskterminal.geckolib3.geo.animated.AnimatedGeoModel;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoMesh;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * 本模组所有 Bedrock 模型实体的渲染器基类：每只实体一份动画实例 + native 网格捷径
 *
 * <p>动画实例按实体存：它带着跨帧的滤波状态、变量槽位与播放进度，多个实体共用会互相拉扯。
 * 用 {@link WeakHashMap} 是为了不把实体钉在渲染器里——渲染器活得比实体久。
 *
 * <p>native 路径与 Java 路径的关系：{@code renderCubesOfBone} 是「把某根骨骼的立方体写进
 * {@link VertexConsumer}」的唯一出口，native 可用时这里改为把立方体编号与骨骼姿势攒进
 * {@link NativeMeshBatch}，一次 JNI 算完再写出去；native 不可用、建表失败或 flush 失败时整帧退回
 * {@link GeoReplacedEntityRenderer} 的 Java 实现。两条路的顶点顺序、UV、法线与法线取反标志都相同，
 * 见 {@link NativeGeoMesh}。
 */
public abstract class YsmEntityRenderer<T extends Mob, A extends YsmAnimatable<T>>
    extends GeoReplacedEntityRenderer<T, A> {

    private final Map<T, A> animatables = new WeakHashMap<>();
    private final Function<T, A> factory;

    private final NativeMeshBatch nativeBatch = new NativeMeshBatch();
    private NativeGeoMesh nativeMesh;
    /** 已经确认没有可渲染立方体的模型：不要再每帧重试 build */
    private GeoModel emptyNativeModel;

    protected YsmEntityRenderer(EntityRendererProvider.Context context, Function<T, A> factory) {
        super(context);
        this.factory = factory;
    }

    @Override
    public A getAnimatableEntity(T entity) {
        return this.animatables.computeIfAbsent(entity, this.factory);
    }

    @Override
    public void render(T entity, A animatable, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (this.renderWithNative(entity, animatable, entityYaw, partialTick, poseStack, bufferSource, packedLight)) {
            return;
        }
        super.render(entity, animatable, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private boolean renderWithNative(T entity, A animatable, float entityYaw, float partialTick,
                                     PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (!NativeMesh.isAvailable() || !animatable.updateModel()) {
            return false;
        }
        NativeGeoMesh mesh = this.ensureNativeMesh(animatable.getCurrentModel().geoModel());
        if (mesh == null) {
            return false;
        }
        try {
            this.nativeBatch.begin(mesh);
        } catch (NativeMesh.NativeMeshException failure) {
            this.dropNativeMesh();
            return false;
        }
        try {
            try {
                super.render(entity, animatable, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            } finally {
                // 中途抛异常时也要收尾，否则批次会一直挂在 active 上，把后面几帧的立方体全攒进来
                this.nativeBatch.end();
            }
            RenderType type = this.getRenderType(entity, partialTick, poseStack, bufferSource, null, packedLight,
                this.getTextureLocation(entity));
            this.nativeBatch.flush(bufferSource.getBuffer(type));
            return true;
        } catch (NativeMesh.NativeMeshException failure) {
            // native 已熔断，批次在 finally 里清空；这一帧改走 Java 路径
            this.dropNativeMesh();
            return false;
        }
    }

    @Override
    public void renderCubesOfBone(AnimatedGeoBone bone, PoseStack poseStack, VertexConsumer buffer,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        if (!this.nativeBatch.isActive()) {
            super.renderCubesOfBone(bone, poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            return;
        }
        GeoMesh mesh = bone.geoBone().cubes();
        if (mesh == null) {
            return;
        }
        int colour = Color.ofRGBA(red, green, blue, alpha).getColor();
        for (int index = 0; index < mesh.getCubeCount(); index++) {
            this.nativeBatch.add(mesh, index, poseStack, buffer, packedLight, packedOverlay, colour);
        }
    }

    /**
     * @return 可用的 native 网格；加载或建表失败时返回 null，调用方改走 Java 路径
     */
    private NativeGeoMesh ensureNativeMesh(GeoModel model) {
        if (this.nativeMesh != null && this.nativeMesh.model() == model) {
            return this.nativeMesh;
        }
        if (this.emptyNativeModel == model) {
            return null;
        }
        this.dropNativeMesh();
        if (!NativeMesh.isAvailable()) {
            return null;
        }
        try {
            this.nativeMesh = NativeGeoMesh.build(model);
        } catch (NativeMesh.NativeMeshException failure) {
            this.nativeMesh = null;
        } catch (IllegalStateException emptyModel) {
            // 空模型是模型资源本身的问题，native 没坏：不能让它熔断，也不能每帧重试
            this.emptyNativeModel = model;
            DawnDuskTerminal.LOGGER.warn("{} 的模型没有可渲染的立方体，回退 Java 路径",
                this.getClass().getSimpleName());
        }
        return this.nativeMesh;
    }

    private void dropNativeMesh() {
        if (this.nativeMesh != null) {
            this.nativeMesh.close();
            this.nativeMesh = null;
        }
    }

    @Override
    public float getWidthScale(T entity) {
        return this.scaleOf(entity);
    }

    @Override
    public float getHeightScale(T entity) {
        return this.scaleOf(entity);
    }

    /** 模型在屏幕上的缩放；默认 1，由子类按 {@code ysm.json} 覆写 */
    protected float scaleOf(T entity) {
        return 1.0F;
    }

    /**
     * 是否关掉原版那套「死亡时绕 Z 扳倒」的旋转
     *
     * <p>1.21.1 把这个角度直接写进了 {@code LivingEntityRenderer.setupRotations}，没有可以覆写的常量，
     * 所以这里在调用父类期间把 {@code deathTime} 临时置零再还原。模型包自带 {@code death} 片段时
     * 必须打开：两者同时生效就是「先被扳倒、再按自己的演出躺下」，看着像抽搐。
     */
    protected boolean suppressDeathRotation(T entity) {
        return false;
    }

    @Override
    protected void setupRotations(T entity, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTicks, float scale) {
        int deathTime = entity.deathTime;
        if (deathTime <= 0 || !this.suppressDeathRotation(entity)) {
            super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks, scale);
            return;
        }
        entity.deathTime = 0;
        try {
            super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks, scale);
        } finally {
            entity.deathTime = deathTime;
        }
    }
}
