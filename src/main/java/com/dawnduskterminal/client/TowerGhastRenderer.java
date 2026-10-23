package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.TowerGhastEntity;
import com.dawnduskterminal.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * 高塔恶魂的客户端渲染接线
 *
 * <p>三条资源路径在这里写死，没有沿用
 * {@link software.bernie.geckolib.model.DefaultedEntityGeoModel} 的命名约定 那个约定把每条路径都在
 * 实体 id 之上再拼一层 {@code subtype()}，而该类的 {@code subtype()} 返回字面量 {@code "entity"}，
 * 于是它实际会去找 {@code geo/entity/tower_ghast.geo.json}
 * {@code animations/entity/tower_ghast.animation.json} {@code textures/entity/tower_ghast.png}
 * 直接把路径写出来，代码里的路径就永远是 jar 里的那个文件，
 * 少掉一整类「模型能加载但不动」的排查
 *
 * <p>在 {@link DdtClient#init} 里显式注册，不用 {@code @EventBusSubscriber}，
 * 与本模组其它客户端监听器的接法一致 该监听器跑在 mod 事件总线上
 */
public final class TowerGhastRenderer {
    private TowerGhastRenderer() {}

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TOWER_GHAST.get(), TowerGhastEntityRenderer::new);
    }

    /**
     * 多出来的唯一一件事是把 GeckoLib 自带的倒地关掉
     *
     * <p>{@code GeoEntityRenderer.applyRotations} 在 {@code deathTime > 0} 时会把整个姿势绕 Z 转到
     * {@link #getDeathMaxRotation} 度，默认 90。这套模型包自带 {@code death} 片段（1 秒，停在最后一帧），
     * 两者同时生效就是「先被扳倒、再按自己的演出躺下」，看着像抽搐
     */
    private static final class TowerGhastEntityRenderer extends GeoEntityRenderer<TowerGhastEntity> {
        /**
         * GeckoLib 的 renderCube/createVerticesOfQuad 默认实现每个 cube 新建 Matrix4f 与三个
         * Quaternionf 每个 quad 新建 Vector3f 每个顶点新建 Vector4f
         *
         * <p>这个模型有 712 个 cube 约 1.7 万个顶点 每帧每实体两万多个临时对象
         * 这里全部复用 运算顺序与原实现一致 只把中间对象换成渲染线程私有的 scratch
         */
        private final Matrix4f poseScratch = new Matrix4f();
        private final Matrix4f savedPose = new Matrix4f();
        private final Matrix3f savedNormal = new Matrix3f();
        private final Vector3f normalScratch = new Vector3f();
        private final Vector4f positionScratch = new Vector4f();
        private final Quaternionf rotationScratch = new Quaternionf();
        private final float[] matrixScratch = new float[16];
        private final float[] normalMatrixScratch = new float[9];

        private final long nativePool;
        private NativeTowerGhastMesh nativeMesh;
        private boolean nativeFailed;

        private boolean batching;
        private int batchCount;
        private float[] batchMatrices = new float[1024 * 16];
        private float[] batchNormals = new float[1024 * 9];
        private int[] batchCubeIds = new int[1024];
        private int[] batchLights = new int[1024];
        private int[] batchOverlays = new int[1024];
        private int[] batchColours = new int[1024];
        private VertexConsumer[] batchBuffers = new VertexConsumer[1024];
        private ByteBuffer nativeOutput;
        private FloatBuffer nativeOutputFloats;

        private TowerGhastEntityRenderer(EntityRendererProvider.Context context) {
            super(context, new TowerGhastModel());
            // 缩放取自 TowerGhastEntity：碰撞箱用同一个常数，两者不能各写一份
            withScale(TowerGhastEntity.MODEL_SCALE);
            long pool = 0L;
            if (NativeVertex.isAvailable()) {
                try {
                    pool = NativeVertex.createPool(
                        Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors())));
                } catch (Throwable throwable) {
                    DawnDuskTerminal.LOGGER.warn(
                        "DawnDuskTerminal native vertex pool unavailable, using Java renderer: {}",
                        throwable.toString());
                }
            }
            this.nativePool = pool;
        }

        @Override
        protected float getDeathMaxRotation(TowerGhastEntity animatable) {
            return 0.0F;
        }

        @Override
        public void actuallyRender(PoseStack poseStack, TowerGhastEntity animatable, BakedGeoModel model,
                                   RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                   boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                   int colour) {
            if (!isReRender && buffer != null && model != null && this.nativePool != 0L && !this.nativeFailed) {
                ensureNativeMesh(model);
                if (this.nativeMesh != null) {
                    this.batching = true;
                    this.batchCount = 0;
                    try {
                        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer,
                            false, partialTick, packedLight, packedOverlay, colour);
                    } finally {
                        this.batching = false;
                    }
                    flushNative(buffer);
                    return;
                }
            }
            super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, colour);
        }

        @Override
        public void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer,
                               int packedLight, int packedOverlay, int colour) {
            if (this.batching && this.nativeMesh != null) {
                int cubeId = this.nativeMesh.id(cube);
                if (cubeId >= 0) {
                    applyCubeTransform(poseStack, cube);
                    ensureBatchCapacity(this.batchCount + 1);
                    int matrixOffset = this.batchCount * 16;
                    int normalOffset = this.batchCount * 9;
                    poseStack.last().pose().get(this.matrixScratch);
                    System.arraycopy(this.matrixScratch, 0, this.batchMatrices, matrixOffset, 16);
                    poseStack.last().normal().get(this.normalMatrixScratch);
                    System.arraycopy(this.normalMatrixScratch, 0, this.batchNormals, normalOffset, 9);
                    int index = this.batchCount++;
                    this.batchCubeIds[index] = cubeId;
                    this.batchLights[index] = packedLight;
                    this.batchOverlays[index] = packedOverlay;
                    this.batchColours[index] = colour;
                    this.batchBuffers[index] = buffer;
                    return;
                }
            }

            applyCubeTransform(poseStack, cube);
            Matrix3f normalisedPoseState = poseStack.last().normal();
            this.poseScratch.set(poseStack.last().pose());
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) {
                    continue;
                }
                this.normalScratch.set(quad.normal());
                normalisedPoseState.transform(this.normalScratch);
                RenderUtil.fixInvertedFlatCube(cube, this.normalScratch);
                createVerticesOfQuad(quad, this.poseScratch, this.normalScratch, buffer,
                    packedLight, packedOverlay, colour);
            }
        }

        private void applyCubeTransform(PoseStack poseStack, GeoCube cube) {
            RenderUtil.translateToPivotPoint(poseStack, cube);
            Vec3 rotation = cube.rotation();
            this.rotationScratch.rotationXYZ(0.0F, 0.0F, (float) rotation.z());
            poseStack.mulPose(this.rotationScratch);
            this.rotationScratch.rotationXYZ(0.0F, (float) rotation.y(), 0.0F);
            poseStack.mulPose(this.rotationScratch);
            this.rotationScratch.rotationXYZ((float) rotation.x(), 0.0F, 0.0F);
            poseStack.mulPose(this.rotationScratch);
            RenderUtil.translateAwayFromPivotPoint(poseStack, cube);
        }

        @Override
        public void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal,
                                         VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
            for (GeoVertex vertex : quad.vertices()) {
                Vector3f position = vertex.position();
                this.positionScratch.set(position.x(), position.y(), position.z(), 1.0F);
                poseState.transform(this.positionScratch);
                buffer.addVertex(this.positionScratch.x(), this.positionScratch.y(), this.positionScratch.z(),
                    colour, vertex.texU(), vertex.texV(), packedOverlay, packedLight,
                    normal.x(), normal.y(), normal.z());
            }
        }

        /**
         * 默认实现每个 cube 都 {@code pushPose/popPose}，会为 712 个 cube 各分配一个 Pose
         *
         * <p>这里改成保存/恢复最后一层矩阵，矩阵内容与 push/pop 完全等价，但不再复制 Pose 对象
         */
        @Override
        public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer,
                                      int packedLight, int packedOverlay, int colour) {
            if (bone.isHidden()) {
                return;
            }
            Matrix4f pose = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            this.savedPose.set(pose);
            this.savedNormal.set(normal);
            for (GeoCube cube : bone.getCubes()) {
                renderCube(poseStack, cube, buffer, packedLight, packedOverlay, colour);
                pose.set(this.savedPose);
                normal.set(this.savedNormal);
            }
        }

        private void ensureNativeMesh(BakedGeoModel model) {
            if (this.nativeMesh != null && this.nativeMesh.model() == model) {
                return;
            }
            if (this.nativeMesh != null) {
                this.nativeMesh.close();
                this.nativeMesh = null;
            }
            try {
                this.nativeMesh = NativeTowerGhastMesh.build(model);
            } catch (Throwable throwable) {
                this.nativeFailed = true;
                DawnDuskTerminal.LOGGER.warn(
                    "DawnDuskTerminal native mesh build failed, using Java renderer: {}",
                    throwable.toString());
            }
        }

        private void ensureBatchCapacity(int needed) {
            if (needed <= this.batchCubeIds.length) {
                return;
            }
            int size = Math.max(needed, this.batchCubeIds.length * 2);
            this.batchMatrices = Arrays.copyOf(this.batchMatrices, size * 16);
            this.batchNormals = Arrays.copyOf(this.batchNormals, size * 9);
            this.batchCubeIds = Arrays.copyOf(this.batchCubeIds, size);
            this.batchLights = Arrays.copyOf(this.batchLights, size);
            this.batchOverlays = Arrays.copyOf(this.batchOverlays, size);
            this.batchColours = Arrays.copyOf(this.batchColours, size);
            this.batchBuffers = Arrays.copyOf(this.batchBuffers, size);
        }

        private void ensureNativeOutput() {
            int floats = this.nativeMesh.vertexCount() * 8;
            if (this.nativeOutput == null || this.nativeOutput.capacity() < floats * Float.BYTES) {
                this.nativeOutput = ByteBuffer.allocateDirect(floats * Float.BYTES)
                    .order(ByteOrder.nativeOrder());
                this.nativeOutputFloats = this.nativeOutput.asFloatBuffer();
            }
        }

        private void flushNative(VertexConsumer fallbackBuffer) {
            if (this.batchCount == 0) {
                return;
            }
            if (this.nativeMesh == null) {
                resetBatch();
                return;
            }
            if (this.nativePool == 0L) {
                writeJavaBatch(fallbackBuffer);
                resetBatch();
                return;
            }

            ensureNativeOutput();
            int result;
            try {
                result = NativeVertex.transform(
                    this.nativePool, this.nativeMesh.handle(),
                    this.batchMatrices, this.batchNormals, this.batchCubeIds, this.batchCount,
                    this.nativeOutput, this.nativeOutput.capacity() / Float.BYTES);
            } catch (Throwable throwable) {
                this.nativeFailed = true;
                DawnDuskTerminal.LOGGER.warn(
                    "DawnDuskTerminal native vertex transform failed, using Java renderer: {}",
                    throwable.toString());
                result = -1;
            }

            if (result < 0) {
                writeJavaBatch(fallbackBuffer);
            } else {
                FloatBuffer output = this.nativeOutputFloats;
                output.position(0);
                for (int i = 0; i < this.batchCount; i++) {
                    int cubeId = this.batchCubeIds[i];
                    int offset = this.nativeMesh.offset(cubeId);
                    int count = this.nativeMesh.count(cubeId);
                    VertexConsumer buffer = this.batchBuffers[i] != null ? this.batchBuffers[i] : fallbackBuffer;
                    if (buffer == null) {
                        continue;
                    }
                    int colour = this.batchColours[i];
                    int overlay = this.batchOverlays[i];
                    int light = this.batchLights[i];
                    for (int v = 0; v < count; v++) {
                        float x = output.get();
                        float y = output.get();
                        float z = output.get();
                        float nx = output.get();
                        float ny = output.get();
                        float nz = output.get();
                        float u = output.get();
                        float texV = output.get();
                        buffer.addVertex(x, y, z, colour, u, texV, overlay, light, nx, ny, nz);
                    }
                }
            }
            resetBatch();
        }

        private void writeJavaBatch(VertexConsumer fallbackBuffer) {
            float[] vertices = this.nativeMesh.vertices();
            for (int i = 0; i < this.batchCount; i++) {
                int cubeId = this.batchCubeIds[i];
                int offset = this.nativeMesh.offset(cubeId);
                int count = this.nativeMesh.count(cubeId);
                float sizeX = this.nativeMesh.size(cubeId, 0);
                float sizeY = this.nativeMesh.size(cubeId, 1);
                float sizeZ = this.nativeMesh.size(cubeId, 2);
                VertexConsumer buffer = this.batchBuffers[i] != null ? this.batchBuffers[i] : fallbackBuffer;
                if (buffer == null) {
                    continue;
                }
                int matrixOffset = i * 16;
                int normalOffset = i * 9;
                int colour = this.batchColours[i];
                int overlay = this.batchOverlays[i];
                int light = this.batchLights[i];
                for (int v = 0; v < count; v++) {
                    int source = (offset + v) * 8;
                    float x = vertices[source];
                    float y = vertices[source + 1];
                    float z = vertices[source + 2];
                    float vertexX = this.batchMatrices[matrixOffset] * x
                        + this.batchMatrices[matrixOffset + 4] * y
                        + this.batchMatrices[matrixOffset + 8] * z
                        + this.batchMatrices[matrixOffset + 12];
                    float vertexY = this.batchMatrices[matrixOffset + 1] * x
                        + this.batchMatrices[matrixOffset + 5] * y
                        + this.batchMatrices[matrixOffset + 9] * z
                        + this.batchMatrices[matrixOffset + 13];
                    float vertexZ = this.batchMatrices[matrixOffset + 2] * x
                        + this.batchMatrices[matrixOffset + 6] * y
                        + this.batchMatrices[matrixOffset + 10] * z
                        + this.batchMatrices[matrixOffset + 14];
                    float nx = this.batchNormals[normalOffset] * vertices[source + 3]
                        + this.batchNormals[normalOffset + 3] * vertices[source + 4]
                        + this.batchNormals[normalOffset + 6] * vertices[source + 5];
                    float ny = this.batchNormals[normalOffset + 1] * vertices[source + 3]
                        + this.batchNormals[normalOffset + 4] * vertices[source + 4]
                        + this.batchNormals[normalOffset + 7] * vertices[source + 5];
                    float nz = this.batchNormals[normalOffset + 2] * vertices[source + 3]
                        + this.batchNormals[normalOffset + 5] * vertices[source + 4]
                        + this.batchNormals[normalOffset + 8] * vertices[source + 5];
                    if (nx < 0.0F && (sizeY == 0.0F || sizeZ == 0.0F)) {
                        nx = -nx;
                    }
                    if (ny < 0.0F && (sizeX == 0.0F || sizeZ == 0.0F)) {
                        ny = -ny;
                    }
                    if (nz < 0.0F && (sizeX == 0.0F || sizeY == 0.0F)) {
                        nz = -nz;
                    }
                    buffer.addVertex(vertexX, vertexY, vertexZ, colour,
                        vertices[source + 6], vertices[source + 7], overlay, light, nx, ny, nz);
                }
            }
        }

        private void resetBatch() {
            int count = this.batchCount;
            this.batchCount = 0;
            Arrays.fill(this.batchBuffers, 0, Math.min(this.batchBuffers.length, count), null);
        }
    }

    /**
     * 只用本模组发布的那几条路径的 Geo 模型，不依赖 GeckoLib 的命名约定
     *
     * <p>GeckoLib 把单参数的 {@code getModelResource(T)} / {@code getTextureResource(T)} 标为过时，
     * 推荐改用同时接收渲染器的双参数版本 但双参数版本的默认实现本身就是转调单参数版本，
     * 而单参数版本仍是抽象方法，绕不过去 所以这里只实现三个单参数方法，
     * 三条路径各自只有一处定义，双参数调用最终也会走到它们
     */
    private static final class TowerGhastModel extends GeoModel<TowerGhastEntity> {
        /**
         * 有状态的那两层（parallel0 与 pre_parallel0）在这里写回
         *
         * <p>必须挂在这里而不是渲染层：{@code setCustomAnimations} 是 {@code handleAnimations} 的最后
         * 一步，夹在「动画写入骨骼」与「骨骼矩阵被读走」之间，见 {@link TowerGhastBonePose} 的类注释
         */
        @Override
        public void setCustomAnimations(TowerGhastEntity entity, long instanceId,
                                        AnimationState<TowerGhastEntity> animationState) {
            this.bonePose.apply(entity, this, animationState.getPartialTick());
        }

        private final TowerGhastBonePose bonePose = new TowerGhastBonePose();

        private static final ResourceLocation MODEL =
            DawnDuskTerminal.id("geo/tower_ghast.geo.json");
        private static final ResourceLocation TEXTURE =
            DawnDuskTerminal.id("textures/entity/tower_ghast.png");
        private static final ResourceLocation ANIMATIONS =
            DawnDuskTerminal.id("animations/tower_ghast.animation.json");

        @SuppressWarnings("deprecation")
        @Override
        public ResourceLocation getModelResource(TowerGhastEntity animatable) {
            return MODEL;
        }

        @SuppressWarnings("deprecation")
        @Override
        public ResourceLocation getTextureResource(TowerGhastEntity animatable) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(TowerGhastEntity animatable) {
            return ANIMATIONS;
        }
    }
}
