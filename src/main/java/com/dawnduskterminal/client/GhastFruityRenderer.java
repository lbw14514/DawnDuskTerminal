package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.block.Experiment052Block;
import com.dawnduskterminal.client.animation.YsmAnimatable;
import com.dawnduskterminal.client.animation.YsmEntityRenderer;
import com.dawnduskterminal.entity.GhastFruityEntity;
import com.dawnduskterminal.geckolib3.core.event.predicate.AnimationEvent;
import com.dawnduskterminal.geckolib3.core.molang.context.AnimationContext;
import com.dawnduskterminal.geckolib3.core.processor.IBone;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.EnumMap;
import java.util.Map;

/**
 * 「恶果」的客户端接线：模型两套几何、三张脸的切换、持块变体手里那块方块
 *
 * <p>几何有两套：普通恶果用 {@code ghastfruity.geo.json}，持块变体用 {@code ghastfruity_hasblock.geo.json}。
 * 后者里那个 16³ 的 {@code theblock} 占位 cube 已经删掉，手里那块方块在渲染器里按真正的方块模型画，
 * 这样拿什么方块就显示什么。
 *
 * <p>动画走 {@code animations/ghast_universal.animation.json} 里的 {@code parallel0}，
 * 与语音一样是「恶魂通用」素材，由并行动画控制器循环播放。
 */
public final class GhastFruityRenderer {
    private GhastFruityRenderer() {
    }

    private static final ResourceLocation MODEL = DawnDuskTerminal.id("geo/ghastfruity.geo.json");
    private static final ResourceLocation MODEL_HELD = DawnDuskTerminal.id("geo/ghastfruity_hasblock.geo.json");
    private static final ResourceLocation TEXTURE = DawnDuskTerminal.id("textures/entity/ghastfruity.png");
    private static final ResourceLocation ANIMATIONS = DawnDuskTerminal.id("animations/ghast_universal.animation.json");

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GHASTFRUITY.get(), Renderer::new);
    }

    private static final class Renderer extends YsmEntityRenderer<GhastFruityEntity, Animatable> {
        /**
         * 持块变体的方块位置：几何里那块占位 cube 的中心是 (0, -4, -20) 单位，
         * 即体中心前方 1.25 格、脚上方 -0.25 格；顶面朝前，所以整块绕 X 转 -90 度
         */
        private static final double HELD_FORWARD = 1.25D;
        private static final double HELD_HEIGHT = -0.25D;

        private final Map<GhastFruityEntity.HeldBlock, BlockState> heldStates =
            new EnumMap<>(GhastFruityEntity.HeldBlock.class);

        private Renderer(EntityRendererProvider.Context context) {
            super(context, Animatable::new);
        }

        @Override
        public void render(GhastFruityEntity entity, Animatable animatable, float entityYaw, float partialTick,
                           PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
            super.render(entity, animatable, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            if (!entity.isBlockVariant()) {
                return;
            }
            // 与模型本体一样的隐身规则：隐身时方块一起消失
            if (entity.isInvisible() && entity.isInvisibleTo(Minecraft.getInstance().player)) {
                return;
            }
            BlockState state = this.heldState(entity.getHeldBlock());
            if (state.isAir()) {
                return;
            }
            if (state.is(ModBlocks.EXPERIMENT_052.get())) {
                // 点着之后手里那颗方雷换成发光的那一套，与方块点亮时的样子一致
                state = state.setValue(Experiment052Block.ACTIVE, entity.isIgnited());
            }

            poseStack.pushPose();
            poseStack.translate(0.0D, HELD_HEIGHT, 0.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
            poseStack.translate(0.0D, 0.0D, -HELD_FORWARD);
            // 方块的顶面朝实体前方
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.translate(-0.5D, -0.5D, -0.5D);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        private BlockState heldState(GhastFruityEntity.HeldBlock block) {
            return this.heldStates.computeIfAbsent(block, key -> {
                if (key == GhastFruityEntity.HeldBlock.NONE) {
                    return Blocks.AIR.defaultBlockState();
                }
                Block resolved = key.block();
                return resolved == null ? Blocks.AIR.defaultBlockState() : resolved.defaultBlockState();
            });
        }
    }

    private static final class Animatable extends YsmAnimatable<GhastFruityEntity> {
        private Animatable(GhastFruityEntity entity) {
            super(entity, MODEL, ANIMATIONS, TEXTURE);
        }

        @Override
        public ResourceLocation getModelLocation() {
            return this.entity.isBlockVariant() ? MODEL_HELD : MODEL;
        }

        /** 模型包没有主动画：{@code parallel0} 由并行动画控制器循环播放 */
        @Override
        protected String mainClip(AnimationEvent<YsmAnimatable<GhastFruityEntity>> event) {
            return null;
        }

        /**
         * 三张脸：几何里是「一张底图 + 两片贴在脸前的薄片」
         *
         * <p>立方体的北面用的是贴图 (16,16) 那一张——平静的脸，它永远画；{@code annoyed} 与
         * {@code attack} 是两片薄片，谁可见谁就盖住平静的脸。所以「三张脸」不需要换贴图，
         * 只是把这两片薄片的可见性按状态开关；两片在同一深度上，绝不能同时可见。
         *
         * <p>状态取的是实体自己同步过来的 {@link GhastFruityEntity#getFace()}，不直接读
         * {@code getTarget()}：目标只在服务端有值，客户端读会永远读到 {@code null}。
         *
         * <p>必须在动画写入骨骼之后执行，因此挂在 {@code setCustomAnimations} 的末尾。
         */
        @Override
        public boolean setCustomAnimations(AnimationContext<?> context, AnimationEvent<?> event) {
            boolean updated = super.setCustomAnimations(context, event);
            GhastFruityEntity.Face face = this.entity.getFace();
            hideBone("annoyed", face != GhastFruityEntity.Face.ANNOYED);
            hideBone("attack", face != GhastFruityEntity.Face.ATTACK);
            return updated;
        }

        private void hideBone(String name, boolean hidden) {
            IBone bone = this.getAnimationProcessor().getBone(name);
            if (bone != null) {
                bone.setHidden(hidden);
            } else {
                DawnDuskTerminal.LOGGER.debug("恶果的模型里没有骨骼 {}", name);
            }
        }
    }
}
