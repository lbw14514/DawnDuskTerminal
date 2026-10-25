package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.StringEntity;
import com.dawnduskterminal.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 「弦」的客户端接线
 *
 * <p>小型与大型是两个不同的 geo 模型，贴图直接指向流体方块的两张图：
 * {@code textures/block/portal_fluid_still.png} 与 {@code textures/block/portal_fluid_flow.png}。
 * 这两张是 16x512 与 32x1024 的动画条，GeckoLib 的 {@code AnimatableTexture} 会读同目录的
 * {@code .mcmeta} 并按帧上传，所以实体贴图和方块流体一样会动；geo 里的 texture_width/height
 * 正好是单帧尺寸 16x16 与 32x32。
 *
 * <p>渲染类型用半透明实体层：流体贴图的 alpha 是 230/255，默认的 cutout 会把它当成不透明。
 *
 * <p>死亡不播倒地动画：{@code getDeathMaxRotation} 覆写为 0。模型本身也没有死亡片段，
 * 姿态层会继续按液面游走的动作摆动，只是不再位移。
 */
public final class StringRenderer {
    private StringRenderer() {}

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.STRING.get(), StringEntityRenderer::new);
    }

    private static final class StringEntityRenderer extends GeoEntityRenderer<StringEntity> {
        private StringEntityRenderer(EntityRendererProvider.Context context) {
            super(context, new StringGeoModel());
        }

        @Override
        protected float getDeathMaxRotation(StringEntity animatable) {
            return 0.0F;
        }

        @Override
        public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
                                        StringEntity animatable, BakedGeoModel model, boolean isReRender,
                                        float partialTick, int packedLight, int packedOverlay) {
            float scale = animatable.getRenderScale();
            super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable,
                model, isReRender, partialTick, packedLight, packedOverlay);
        }

        @Override
        public RenderType getRenderType(StringEntity animatable, ResourceLocation texture,
                                        @Nullable MultiBufferSource bufferSource, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }
    }

    private static final class StringGeoModel extends GeoModel<StringEntity> {
        private static final ResourceLocation SMALL_GEO = DawnDuskTerminal.id("geo/string.geo.json");
        private static final ResourceLocation LARGE_GEO = DawnDuskTerminal.id("geo/string_large.geo.json");
        private static final ResourceLocation SMALL_TEXTURE =
            DawnDuskTerminal.id("textures/block/portal_fluid_still.png");
        private static final ResourceLocation LARGE_TEXTURE =
            DawnDuskTerminal.id("textures/block/portal_fluid_flow.png");
        /**
         * 两个源片段全部是 YSM 状态查询，转换后只剩 0/0，所以文件里不放片段，
         * 姿态完全由 {@link StringPose} 在 setCustomAnimations 里算
         */
        private static final ResourceLocation ANIMATIONS =
            DawnDuskTerminal.id("animations/string.animation.json");

        private final StringPose pose = new StringPose();

        @Override
        public void setCustomAnimations(StringEntity entity, long instanceId,
                                        AnimationState<StringEntity> animationState) {
            this.pose.apply(entity, this, animationState.getPartialTick());
        }

        @SuppressWarnings("deprecation")
        @Override
        public ResourceLocation getModelResource(StringEntity animatable) {
            return animatable.isLargeVariant() ? LARGE_GEO : SMALL_GEO;
        }

        @SuppressWarnings("deprecation")
        @Override
        public ResourceLocation getTextureResource(StringEntity animatable) {
            return animatable.isLargeVariant() ? LARGE_TEXTURE : SMALL_TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(StringEntity animatable) {
            return ANIMATIONS;
        }
    }
}
