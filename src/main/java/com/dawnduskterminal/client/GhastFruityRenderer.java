package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.block.Experiment052Block;
import com.dawnduskterminal.entity.GhastFruityEntity;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 「恶果」的客户端接线：GeckoLib 渲染器 + 三张脸的切换 + 持块变体手里那块方块
 *
 * <p>三条资源路径在这里写死，不依赖 {@code DefaultedEntityGeoModel} 的命名约定，
 * 理由与 {@link TowerGhastRenderer} 相同：那个约定会在实体 id 之上再拼一层 {@code entity/}，
 * 路径写死就没有「模型能加载但找错目录」这一类问题
 *
 * <p>几何有两套：普通恶果用 {@code ghastfruity.geo.json}，持块变体用 {@code ghastfruity_hasblock.geo.json}。
 * 后者里那个 16³ 的 {@code theblock} 占位 cube 已经删掉，手里那块方块在渲染器里按真正的方块模型画，
 * 这样拿什么方块就显示什么
 */
public final class GhastFruityRenderer {
    private GhastFruityRenderer() {}

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GHASTFRUITY.get(), GhastFruityEntityRenderer::new);
    }

    private static final class GhastFruityEntityRenderer extends GeoEntityRenderer<GhastFruityEntity> {
        /**
         * 持块变体的方块位置：几何里那块占位 cube 的中心是 (0, -4, -20) 单位，
         * 即体中心前方 1.25 格、脚上方 -0.25 格；顶面朝前，所以整块绕 X 转 -90 度
         */
        private static final double HELD_FORWARD = 1.25D;
        private static final double HELD_HEIGHT = -0.25D;

        private final Map<GhastFruityEntity.HeldBlock, BlockState> heldStates =
            new EnumMap<>(GhastFruityEntity.HeldBlock.class);

        private GhastFruityEntityRenderer(EntityRendererProvider.Context context) {
            super(context, new GhastFruityModel());
            // 模型按几何原尺寸绘制（24 单位 = 1.5 格），不做缩放；碰撞箱也是这 1.5 格，见 ModEntities
        }

        @SuppressWarnings("deprecation")
        @Override
        public void render(GhastFruityEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                           MultiBufferSource bufferSource, int packedLight) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            if (!entity.isBlockVariant()) {
                return;
            }
            // 与 GeckoLib 对模型本体用的是同一条隐身规则：隐身时方块一起消失
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

    private static final class GhastFruityModel extends GeoModel<GhastFruityEntity> {
        /**
         * 几何里那三张脸是「一张底图 + 两片贴在脸前的薄片」
         *
         * <p>立方体的北面用的是贴图 (16,16) 那一张 —— 平静的脸，它永远画；
         * {@code annoyed} 与 {@code attack} 是两片 24x24x1.5 的薄片，分别用 (32,16) 与 (48,0) 那两张，
         * 摆在底面前方 0.05 单位处，谁可见谁就盖住平静的脸。所以「三张脸」不需要换贴图，
         * 只是把这两片薄片的可见性按状态开关：
         *
         * <ul>
         *   <li>蓄力中（{@code isCharging()}，即射击目标的最后 10 tick）→ 只显示 {@code attack}
         *   <li>有索敌但没在蓄力 → 只显示 {@code annoyed}
         *   <li>没有索敌 → 两张都藏起来，露出平静的脸
         * </ul>
         *
         * <p>状态取的是实体自己同步过来的 {@code GhastFruityEntity#getFace()}，不直接读
         * {@code getTarget()}：目标只在服务端有值（原版没有任何字段把它同步给客户端），
         * 客户端直接读会永远读到 {@code null}，不悦脸就永远不出现
         *
         * <p>两片薄片在同一深度上，绝不能同时可见（会闪烁），因此这两个条件是互斥的
         *
         * <p>必须挂在 {@code setCustomAnimations}：它是 {@code handleAnimations} 的最后一步，
         * 位于「动画写入骨骼」之后、「骨骼矩阵被读走」之前，与本模组高塔恶魂的叠加写入同一个位置
         */
        @Override
        public void setCustomAnimations(GhastFruityEntity entity, long instanceId,
                                        AnimationState<GhastFruityEntity> animationState) {
            GhastFruityEntity.Face face = entity.getFace();
            this.getBone("annoyed").ifPresent(bone -> bone.setHidden(face != GhastFruityEntity.Face.ANNOYED));
            this.getBone("attack").ifPresent(bone -> bone.setHidden(face != GhastFruityEntity.Face.ATTACK));
        }

        private static final ResourceLocation MODEL =
            DawnDuskTerminal.id("geo/ghastfruity.geo.json");
        private static final ResourceLocation MODEL_HELD =
            DawnDuskTerminal.id("geo/ghastfruity_hasblock.geo.json");
        private static final ResourceLocation TEXTURE =
            DawnDuskTerminal.id("textures/entity/ghastfruity.png");
        /** 动画与语音一样是「恶魂通用」素材，文件名保持模型包里的 {@code ghast_universal} */
        private static final ResourceLocation ANIMATIONS =
            DawnDuskTerminal.id("animations/ghast_universal.animation.json");

        @SuppressWarnings("deprecation")
        @Override
        public ResourceLocation getModelResource(GhastFruityEntity animatable) {
            return animatable.isBlockVariant() ? MODEL_HELD : MODEL;
        }

        @SuppressWarnings("deprecation")
        @Override
        public ResourceLocation getTextureResource(GhastFruityEntity animatable) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(GhastFruityEntity animatable) {
            return ANIMATIONS;
        }
    }
}
