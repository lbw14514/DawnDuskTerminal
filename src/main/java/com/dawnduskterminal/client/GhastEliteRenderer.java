package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.GhastEliteEntity;
import com.dawnduskterminal.entity.TowerGhastPhysics;
import com.dawnduskterminal.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 「大恶魂」的客户端接线：GeckoLib 渲染器 + 两张脸 + 触手与腿的摆动
 *
 * <p>模型包给的两条片段都是 Yes Steve Model 的物理层：{@code parallel0} 用
 * {@code ysm.perlin_noise} 摆触手与腿根，{@code parallel1} 把各段触手与腿的旋转写成
 * {@code v.ribbonphy_*_rot} —— 后者是 YSM 宿主每帧算出来的「布条物理」变量，GeckoLib 既不会赋值，
 * 也不存在跨帧状态，直接播放只会把整条链摆成笔直。因此：
 *
 * <ul>
 *   <li>{@code parallel0} 照常播放（转换器已把它改写成 GeckoLib 能编译的形式），
 *       但其中的 perlin 通道被转换器归零了，所以下面按原式用 {@link TowerGhastPhysics#perlinNoise}
 *       逐帧补回去 —— 值与源表达式一一对应
 *   <li>{@code parallel1} 不播放；各段链的滞后改为「按根骨角度的比例弯曲」，
 *       观感上是一条跟着根摆动的软链
 * </ul>
 *
 * <p>两张脸是贴在脸前的薄片：{@code annoyed}（有索敌）与 {@code atack}（蓄力中，模型里就是这么拼的）。
 * 状态取实体自己同步过来的 {@code GhastEliteEntity#getFace()}，不直接读 {@code getTarget()}
 */
public final class GhastEliteRenderer {
    private GhastEliteRenderer() {}

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GHAST_ELITE.get(), GhastEliteEntityRenderer::new);
        // 火球的渲染器漏了会让 EntityRenderDispatcher 拿到 null，客户端直接 NPE 崩掉
        event.registerEntityRenderer(ModEntities.GHAST_ELITE_FIREBALL.get(),
            context -> new ThrownItemRenderer<>(context, 3.0F, true));
    }

    private static final class GhastEliteEntityRenderer extends GeoEntityRenderer<GhastEliteEntity> {
        private GhastEliteEntityRenderer(EntityRendererProvider.Context context) {
            super(context, new GhastEliteModel());
            // 碰撞箱 8 格见方，影子半径取一半
            this.shadowRadius = 4.0F;
        }

        /** 模型包没有死亡片段，GeckoLib 默认的「绕 Z 扳倒 90 度」就是唯一的死亡演出，保留 */
        @Override
        protected float getDeathMaxRotation(GhastEliteEntity animatable) {
            return 90.0F;
        }
    }

    private static final class GhastEliteModel extends GeoModel<GhastEliteEntity> {
        @Override
        public void setCustomAnimations(GhastEliteEntity entity, long instanceId,
                                        AnimationState<GhastEliteEntity> animationState) {
            GhastEliteEntity.Face face = entity.getFace();
            this.getBone("annoyed").ifPresent(bone -> bone.setHidden(face != GhastEliteEntity.Face.ANNOYED));
            this.getBone("atack").ifPresent(bone -> bone.setHidden(face != GhastEliteEntity.Face.ATTACK));

            double life = (entity.tickCount + animationState.getPartialTick()) / 20.0D;
            this.poseTentacle("lefttentacle", 0.0D,
                -10.0D - 20.0D * TowerGhastPhysics.perlinNoise(325, life, 0.0D, 0.0D),
                10.0D + 20.0D * TowerGhastPhysics.perlinNoise(325, 0.0D, life, 0.0D));
            this.poseTentacle("righttentacle", 0.0D,
                10.0D - 20.0D * TowerGhastPhysics.perlinNoise(325, 0.0D, 0.0D, life),
                -10.0D + 20.0D * TowerGhastPhysics.perlinNoise(325, life, 325.0D, 0.0D));
            this.poseChain("leg_M", new double[] {
                7.5D - 20.0D * TowerGhastPhysics.perlinNoise(1919810, life, 0.0D, 0.0D),
                0.0D,
                15.0D * TowerGhastPhysics.perlinNoise(1919810, 0.0D, life, 0.0D)});
            this.poseChain("leg_F_L", new double[] {
                7.5D - 20.0D * TowerGhastPhysics.perlinNoise(59160153, life, 0.0D, 0.0D),
                0.0D,
                15.0D * TowerGhastPhysics.perlinNoise(59160153, 0.0D, life, 0.0D)});
            this.poseChain("leg_F_R", new double[] {
                7.5D - 20.0D * TowerGhastPhysics.perlinNoise(114514, life, 0.0D, 0.0D),
                0.0D,
                15.0D * TowerGhastPhysics.perlinNoise(114514, 0.0D, life, 0.0D)});
            this.poseChain("leg_B_L", new double[] {
                7.5D - 20.0D * TowerGhastPhysics.perlinNoise(59160153, 0.0D, 0.0D, life),
                0.0D,
                15.0D * TowerGhastPhysics.perlinNoise(1919810, 0.0D, 0.0D, life)});
            this.poseChain("leg_B_R", new double[] {
                7.5D - 20.0D * TowerGhastPhysics.perlinNoise(114514, 0.0D, 0.0D, life),
                0.0D,
                15.0D * TowerGhastPhysics.perlinNoise(3601, 0.0D, life, 0.0D)});
        }

        /** 触手：根写死角度，两节后续段按比例递减，弯成一条跟着根摆的软链 */
        private void poseTentacle(String root, double x, double y, double z) {
            this.setDegrees(root, x, y, z);
            this.setDegrees(root + "2", x * 0.6D, y * 0.6D, z * 0.6D);
            this.setDegrees(root + "3", x * 0.35D, y * 0.35D, z * 0.35D);
        }

        /** 腿：根角度 + 两节递减 */
        private void poseChain(String root, double[] angles) {
            this.setDegrees(root, angles[0], angles[1], angles[2]);
            this.setDegrees(root + "_2", angles[0] * 0.6D, angles[1] * 0.6D, angles[2] * 0.6D);
            this.setDegrees(root + "_3", angles[0] * 0.35D, angles[1] * 0.35D, angles[2] * 0.35D);
        }

        private void setDegrees(String bone, double x, double y, double z) {
            this.getBone(bone).ifPresent(geoBone -> this.apply(geoBone, x, y, z));
        }

        private void apply(GeoBone bone, double x, double y, double z) {
            bone.setRotX((float) Math.toRadians(x));
            bone.setRotY((float) Math.toRadians(y));
            bone.setRotZ((float) Math.toRadians(z));
        }

        private static final ResourceLocation MODEL =
            DawnDuskTerminal.id("geo/ghast_elite.geo.json");
        private static final ResourceLocation TEXTURE =
            DawnDuskTerminal.id("textures/entity/ghast_elite.png");
        private static final ResourceLocation ANIMATIONS =
            DawnDuskTerminal.id("animations/ghast_elite.animation.json");

        @SuppressWarnings("deprecation")
        @Override
        public ResourceLocation getModelResource(GhastEliteEntity animatable) {
            return MODEL;
        }

        @SuppressWarnings("deprecation")
        @Override
        public ResourceLocation getTextureResource(GhastEliteEntity animatable) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(GhastEliteEntity animatable) {
            return ANIMATIONS;
        }
    }
}
