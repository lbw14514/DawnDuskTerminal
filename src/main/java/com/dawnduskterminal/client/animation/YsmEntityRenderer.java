package com.dawnduskterminal.client.animation;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.NativeGeoMesh;
import com.dawnduskterminal.client.NativeMesh;
import com.dawnduskterminal.client.NativeMeshBatch;
import com.dawnduskterminal.geckolib3.core.util.Color;
import com.dawnduskterminal.geckolib3.geo.GeoReplacedEntityRenderer;
import com.dawnduskterminal.geckolib3.geo.animated.AnimatedGeoBone;
import com.dawnduskterminal.geckolib3.core.processor.ILocationBone;
import com.dawnduskterminal.geckolib3.geo.animated.AnimatedGeoModel;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoMesh;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoModel;
import com.dawnduskterminal.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
    private final ItemInHandRenderer itemInHandRenderer;

    private final NativeMeshBatch nativeBatch = new NativeMeshBatch();
    private NativeGeoMesh nativeMesh;
    /** 已经确认没有可渲染立方体的模型：不要再每帧重试 build */
    private GeoModel emptyNativeModel;

    protected YsmEntityRenderer(EntityRendererProvider.Context context, Function<T, A> factory) {
        super(context);
        this.factory = factory;
        this.itemInHandRenderer = context.getItemInHandRenderer();
    }

    /**
     * 每只实体一份动画实例
     *
     * <p>模型包可以换（{@code /ddt pack}），换了就得重建实例：模型、动画文件与物理状态都挂在实例上。
     * 判断交给 {@link #isCurrent}。
     */
    @Override
    public A getAnimatableEntity(T entity) {
        A current = this.animatables.get(entity);
        if (current != null && this.isCurrent(current, entity)) {
            return current;
        }
        A created = this.factory.apply(entity);
        this.animatables.put(entity, created);
        return created;
    }

    /**
     * 这只实体此刻想要的模型包；返回 null 表示不支持换包，始终用默认包
     *
     * <p>子类实现它即可获得「`/ddt pack` 换包后自动重建动画实例」的行为。
     */
    @Nullable
    protected ResourceLocation requestedPack(T entity) {
        return null;
    }

    /** 现有的动画实例是否仍然适用于这只实体：模型包变了就得重建 */
    protected boolean isCurrent(A animatable, T entity) {
        ResourceLocation wanted = this.requestedPack(entity);
        return wanted == null || wanted.equals(animatable.packId());
    }

    @Override
    public void render(T entity, A animatable, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (!this.renderWithNative(entity, animatable, entityYaw, partialTick, poseStack, bufferSource, packedLight)) {
            super.render(entity, animatable, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }
        this.renderHeldItems(entity, animatable, poseStack, bufferSource, packedLight);
    }

    /**
     * 把手上的物品画到模型的手部 Locator 上
     *
     * <p>YSM 的模型包自带 {@code RightHandLocator} / {@code LeftHandLocator} 骨骼，就是给这件事用的
     * （高塔恶魂的包里还有 {@code BladeLocator}、{@code RifleLocator} 等，供拔刀与枪械模组使用）。
     * 做法与 TouhouLittleMaid 的 {@code GeckoLayerMaidHeld} 相同：逐级下行到手部骨骼，
     * 再按「向上 1/16 格、绕 X 转 -90 度」摆正物品，最后交给原版 {@link ItemInHandRenderer}。
     *
     * <p>骨骼缩放到 0（模型包用它隐藏部件）时不画，否则物品会留在原地。
     */
    private void renderHeldItems(T entity, A animatable, PoseStack poseStack, MultiBufferSource bufferSource,
                                 int packedLight) {
        ItemStack mainHand = entity.getMainHandItem();
        ItemStack offHand = entity.getOffhandItem();
        if (mainHand.isEmpty() && offHand.isEmpty()) {
            return;
        }
        AnimatedGeoModel model = animatable.getCurrentModel();
        if (model == null) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player != null && entity.isInvisibleTo(player)) {
            return;
        }
        if (!mainHand.isEmpty()) {
            this.renderInHand(entity, mainHand, model.rightHandBones(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                false, poseStack, bufferSource, packedLight);
        }
        if (!offHand.isEmpty()) {
            this.renderInHand(entity, offHand, model.leftHandBones(), ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                true, poseStack, bufferSource, packedLight);
        }
    }

    private void renderInHand(T entity, ItemStack stack, List<? extends ILocationBone> hand,
                              ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack,
                              MultiBufferSource bufferSource, int packedLight) {
        if (hand.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        boolean hidden = RenderUtils.prepMatrixForLocator(poseStack, hand);
        if (!hidden) {
            poseStack.translate(0.0D, -0.0625D, -0.1D);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            this.itemInHandRenderer.renderItem(entity, stack, displayContext, leftHand, poseStack, bufferSource,
                packedLight);
        }
        poseStack.popPose();
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
