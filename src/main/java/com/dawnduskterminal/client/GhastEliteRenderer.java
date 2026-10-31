package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.GhastEliteEntity;
import com.dawnduskterminal.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 「大恶魂」的客户端接线：GeckoLib 渲染器与三条资源路径
 *
 * <p>三条资源路径直接写出来，不依赖 GeckoLib 的命名约定，理由与 {@link TowerGhastRenderer} 相同
 *
 * <p>两张脸与触手、腿的逐帧写入不在这一层，见 {@link GhastEliteBonePose}：它们必须挂在
 * {@code GeoModel.setCustomAnimations} 上，而不是渲染层
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
        /** 两张脸与触手、腿的逐帧写入，见 {@link GhastEliteBonePose} */
        private final GhastEliteBonePose bonePose = new GhastEliteBonePose();

        @Override
        public void setCustomAnimations(GhastEliteEntity entity, long instanceId,
                                        AnimationState<GhastEliteEntity> animationState) {
            this.bonePose.apply(entity, this, animationState.getPartialTick());
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
