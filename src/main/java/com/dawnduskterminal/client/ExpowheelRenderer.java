package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.animation.YsmAnimatable;
import com.dawnduskterminal.entity.ExpowheelEntity;
import com.dawnduskterminal.geckolib3.core.event.predicate.AnimationEvent;
import com.dawnduskterminal.geckolib3.core.molang.context.AnimationContext;
import com.dawnduskterminal.geckolib3.core.util.Color;
import com.dawnduskterminal.geckolib3.geo.IGeoRenderer;
import com.dawnduskterminal.geckolib3.geo.animated.AnimatedGeoModel;
import com.dawnduskterminal.geckolib3.model.provider.data.EntityModelData;
import com.dawnduskterminal.geckolib3.util.EModelRenderCycle;
import com.dawnduskterminal.geckolib3.util.IRenderCycle;
import com.dawnduskterminal.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 血肉之书那片飞轮的客户端接线
 *
 * <p>几何与动画是源文件逐字节复制：{@code geo/expowheel.geo.json}（骨骼 {@code RightMagic} 上挂着
 * {@code ysmGlowIn} / {@code ysmGlowOut} 与 P/Q 两组十六根发光环节）与
 * {@code animations/expowheel.animation.json}（片段 {@code parallel0}，全部由 Molang 驱动，
 * 没有写死的 {@code animation_length}，因此按循环处理）。
 *
 * <p>贴图不另画一份，直接用「测试恶魂591」（{@code tower_ghast} 模型包）的整张 256×256 贴图：
 * {@code ysm/tower_ghast/textures/ghast.png}。几何的 {@code texture_width/height} 也是 256，
 * UV 坐标因此一一对得上。
 *
 * <p>飞轮是投掷物而不是生物，所以不复用 {@code YsmEntityRenderer}（那个基类建立在
 * {@code LivingEntityRenderer} 之上）：这里直接实现 {@link IGeoRenderer}，自己驱动一帧动画再画模型，
 * 动画实例按实体存在 {@link WeakHashMap} 里。
 *
 * <p><b>自发光</b>：渲染时把光照打包值换成 {@link LightTexture#FULL_BRIGHT}，贴图不再被天光与方块光压暗。
 * 渲染类型仍是 {@code entityCutoutNoCull}，透明像素照旧裁掉，不会出现半透明层挡住别的实体。
 */
public final class ExpowheelRenderer {
    private static final ResourceLocation MODEL = DawnDuskTerminal.id("geo/expowheel.geo.json");
    private static final ResourceLocation TEXTURE = DawnDuskTerminal.id("ysm/tower_ghast/textures/ghast.png");
    private static final ResourceLocation ANIMATIONS = DawnDuskTerminal.id("animations/expowheel.animation.json");

    private ExpowheelRenderer() {}

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.EXPOWHEEL.get(), WheelRenderer::new);
    }

    private static final class WheelRenderer extends EntityRenderer<ExpowheelEntity>
            implements IGeoRenderer<ExpowheelEntity> {
        private final Map<ExpowheelEntity, Animatable> animatables = new WeakHashMap<>();

        @Nullable
        private MultiBufferSource renderBuffer;

        private IRenderCycle renderCycle = EModelRenderCycle.INITIAL;

        private WheelRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.15F;
        }

        @Override
        public void render(
                ExpowheelEntity entity,
                float entityYaw,
                float partialTick,
                PoseStack poseStack,
                MultiBufferSource bufferSource,
                int packedLight) {
            Animatable animatable = this.animatables.computeIfAbsent(entity, Animatable::new);
            EntityModelData data = new EntityModelData();
            AnimationEvent<Animatable> event =
                    new AnimationEvent<>(animatable, 0.0F, 0.0F, partialTick, false, List.of(data));
            AnimationContext<?> context = new AnimationContext<>(entity, animatable, event, data);
            animatable.setCustomAnimations(context, event);
            AnimatedGeoModel model = animatable.getCurrentModel();
            if (model == null) {
                return;
            }

            // 与投掷物一致：先转向飞行方向，再按俯仰抬头
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
            this.setCurrentModelRenderCycle(EModelRenderCycle.INITIAL);
            this.render(
                    model,
                    entity,
                    partialTick,
                    this.getRenderType(entity, partialTick, poseStack, bufferSource, null, packedLight, TEXTURE),
                    poseStack,
                    bufferSource,
                    null,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F);
            poseStack.popPose();
        }

        @Override
        public ResourceLocation getTextureLocation(ExpowheelEntity entity) {
            return TEXTURE;
        }

        @Override
        public MultiBufferSource getCurrentRTB() {
            return this.renderBuffer;
        }

        @Override
        public void setCurrentRTB(MultiBufferSource bufferSource) {
            this.renderBuffer = bufferSource;
        }

        @Override
        public IRenderCycle getCurrentModelRenderCycle() {
            return this.renderCycle;
        }

        @Override
        public void setCurrentModelRenderCycle(IRenderCycle cycle) {
            this.renderCycle = cycle;
        }

        @Override
        public Color getRenderColor(
                ExpowheelEntity animatable,
                float partialTick,
                PoseStack poseStack,
                @Nullable MultiBufferSource bufferSource,
                @Nullable com.mojang.blaze3d.vertex.VertexConsumer buffer,
                int packedLight) {
            return Color.WHITE;
        }
    }

    private static final class Animatable extends YsmAnimatable<ExpowheelEntity> {
        private Animatable(ExpowheelEntity entity) {
            super(entity, MODEL, ANIMATIONS, TEXTURE);
        }

        /** 片段 {@code parallel0} 由并行层控制器循环播放，没有主动画 */
        @Override
        protected String mainClip(AnimationEvent<YsmAnimatable<ExpowheelEntity>> event) {
            return null;
        }
    }
}
