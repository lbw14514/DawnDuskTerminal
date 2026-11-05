package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.Experiment011PlateEntity;
import com.dawnduskterminal.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * 试验物品 011 那只盘子的客户端：一张 14x1 的薄片，加上盘子上摆的那一件
 *
 * <p><b>盘子本体</b>不走模型烘焙，六个面每帧现画（{@link #drawPlate}）：盘子只有 14x1 像素厚，
 * 用一个方块模型去套得先建一个 14x1 的盒子再贴图，比直接照坐标画六个面麻烦得多。
 * 贴图是 {@code textures/block/experment_011.png}（文件名按源文件原样拼，比 experiment 少一个 i）：
 * 把那张 16x16 的图中间 14x14 截下来当盘面（{@link #TOP_U0}..{@link #TOP_U1}），
 * 四个侧面只截一行像素当边，所以盘面的纹路与边是同一张图上的
 *
 * <p><b>盘子上那件</b>：物品能不能换成方块由 {@link #blockStateOf} 判——{@link BlockItem} 就摆成
 * 12 像素大小的小方块，别的物品就摊成一片 12 像素见方的面片。面片取的是这物品模型的
 * {@code particle} 贴图（{@code item/generated} 那张就是图标本身），与
 * {@code Experiment076Cube} 用的是同一套「从方块图集里取 sprite、自己在实体层画」的做法
 *
 * <p>面片只画朝上的那一面：盘子的东西是给人从上往下看的，底面贴着盘子，本来就看不见
 */
public final class Experiment011PlateRenderer {
    /** 盘子六个面用的贴图：本模组自己的那张 16x16，写全 {@code textures/...png} 路径 */
    private static final ResourceLocation TEXTURE = DawnDuskTerminal.id("textures/block/experment_011.png");

    /** 盘子的六个面画在实体裁切层：带背面剔除、写深度，透明像素照旧裁掉 */
    private static final RenderType PLATE_LAYER = RenderType.entityCutoutNoCull(TEXTURE);

    /** 面片画的层：方块图集 + 裁切，与 {@link #PLATE_LAYER} 同一种写法，只是图集不同 */
    private static final RenderType ITEM_LAYER = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);

    /** 盘子半宽：14 像素宽 → 0.4375 格 */
    private static final float HALF = 7.0F / 16.0F;

    /** 盘子厚度：1 像素 → 0.0625 格 */
    private static final float THICK = 1.0F / 16.0F;

    /** 盘子上摆的东西占 12 像素 → 0.75 格 */
    private static final float DISPLAY = 12.0F / 16.0F;

    /** 盘面在 16x16 的图里占中间那 14x14：两边各让出半个像素的边 */
    private static final float TOP_U0 = 1.0F / 16.0F;

    private static final float TOP_U1 = 15.0F / 16.0F;

    /** 侧面只取一行像素，16x16 的图里就是第 0 行 */
    private static final float RIM_V0 = 0.0F;

    private static final float RIM_V1 = 1.0F / 16.0F;

    /** 面片抬起来的那一点点：贴着盘面画会与盘面打架，抬 1/512 格看不出来又足够分开 */
    private static final float FACE_LIFT = 1.0F / 512.0F;

    private Experiment011PlateRenderer() {}

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.EXPERIMENT_011_PLATE.get(), PlateRenderer::new);
    }

    /**
     * 这件物品有没有对应的方块
     *
     * <p>是 {@link BlockItem} 就摆它自己的默认状态，其余返回 {@code null} 交给面片那条路；
     * 默认状态是空气的（理论上没有）也当普通物品处理，免得摆出一个空
     */
    private static BlockState blockStateOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        return state.isAir() ? null : state;
    }

    private static final class PlateRenderer extends EntityRenderer<Experiment011PlateEntity> {
        private final BlockRenderDispatcher blockRenderer;

        private PlateRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.blockRenderer = context.getBlockRenderDispatcher();
            // 1 像素厚的盘子底下那一圈影子：取小一号的半径，免得影子比盘子还宽
            this.shadowRadius = 0.4F;
            this.shadowStrength = 0.4F;
        }

        @Override
        public void render(
                Experiment011PlateEntity entity,
                float entityYaw,
                float partialTick,
                PoseStack poseStack,
                MultiBufferSource bufferSource,
                int packedLight) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            drawPlate(poseStack, bufferSource, packedLight);
            ItemStack displayed = entity.displayed();
            if (displayed.isEmpty()) {
                return;
            }
            poseStack.pushPose();
            poseStack.translate(0.0F, THICK, 0.0F);
            this.drawDisplayed(displayed, entity, poseStack, bufferSource, packedLight);
            poseStack.popPose();
        }

        /**
         * 盘子的六个面
         *
         * <p>四个角按「从外面看过去 左下 右下 右上 左上」排，顺序与 {@code Experiment076Cube} 一致：
         * {@code entityCutoutNoCull} 虽说不剔背面，绕序反了贴图仍会上下颠倒
         */
        private static void drawPlate(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
            VertexConsumer buffer = bufferSource.getBuffer(PLATE_LAYER);
            Matrix4f matrix = poseStack.last().pose();
            float l = -HALF;
            float h = HALF;
            float b = 0.0F;
            float t = THICK;
            // 盘面与盘底：中间 14x14
            quad(
                    buffer,
                    matrix,
                    Direction.UP,
                    packedLight,
                    new float[][] {{l, t, h}, {h, t, h}, {h, t, l}, {l, t, l}},
                    TOP_U0,
                    TOP_U1,
                    TOP_U0,
                    TOP_U1);
            quad(
                    buffer,
                    matrix,
                    Direction.DOWN,
                    packedLight,
                    new float[][] {{l, b, l}, {h, b, l}, {h, b, h}, {l, b, h}},
                    TOP_U0,
                    TOP_U1,
                    TOP_U0,
                    TOP_U1);
            // 四条边：一行像素
            quad(
                    buffer,
                    matrix,
                    Direction.NORTH,
                    packedLight,
                    new float[][] {{h, b, l}, {l, b, l}, {l, t, l}, {h, t, l}},
                    TOP_U0,
                    TOP_U1,
                    RIM_V0,
                    RIM_V1);
            quad(
                    buffer,
                    matrix,
                    Direction.SOUTH,
                    packedLight,
                    new float[][] {{l, b, h}, {h, b, h}, {h, t, h}, {l, t, h}},
                    TOP_U0,
                    TOP_U1,
                    RIM_V0,
                    RIM_V1);
            quad(
                    buffer,
                    matrix,
                    Direction.WEST,
                    packedLight,
                    new float[][] {{l, b, l}, {l, b, h}, {l, t, h}, {l, t, l}},
                    TOP_U0,
                    TOP_U1,
                    RIM_V0,
                    RIM_V1);
            quad(
                    buffer,
                    matrix,
                    Direction.EAST,
                    packedLight,
                    new float[][] {{h, b, h}, {h, b, l}, {h, t, l}, {h, t, h}},
                    TOP_U0,
                    TOP_U1,
                    RIM_V0,
                    RIM_V1);
        }

        /** 盘子上摆的那一件：方块摆成小方块，其余摊成一片朝上的面片 */
        private void drawDisplayed(
                ItemStack stack,
                Experiment011PlateEntity entity,
                PoseStack poseStack,
                MultiBufferSource bufferSource,
                int packedLight) {
            BlockState state = blockStateOf(stack);
            poseStack.pushPose();
            if (state != null) {
                // 方块模型占 0..1，先把原点挪到方块中心，缩放之后再挪回来，于是 12 像素的小方块正好坐在盘子中间
                poseStack.scale(DISPLAY, DISPLAY, DISPLAY);
                poseStack.translate(-0.5F, 0.0F, -0.5F);
                this.blockRenderer.renderSingleBlock(
                        state, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
            } else {
                TextureAtlasSprite sprite = particleSprite(stack, entity);
                Matrix4f matrix = poseStack.last().pose();
                float r = DISPLAY / 2.0F;
                float y = FACE_LIFT;
                quad(
                        bufferSource.getBuffer(ITEM_LAYER),
                        matrix,
                        Direction.UP,
                        packedLight,
                        new float[][] {{-r, y, r}, {r, y, r}, {r, y, -r}, {-r, y, -r}},
                        sprite.getU0(),
                        sprite.getU1(),
                        sprite.getV0(),
                        sprite.getV1());
            }
            poseStack.popPose();
        }

        /**
         * 物品图标的 sprite
         *
         * <p>{@code BakedModel#getParticleIcon} 给的就是这件物品模型里写的 {@code particle} 贴图；
         * {@code item/generated} 那类模型的 particle 与 {@code layer0} 是同一张，所以拿到的就是图标本身。
         * 每帧现取，换资源包之后不用清任何缓存
         */
        private static TextureAtlasSprite particleSprite(ItemStack stack, Experiment011PlateEntity entity) {
            BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), null, 0);
            return model.getParticleIcon(ModelData.EMPTY);
        }

        /**
         * 一个面：{@code corners} 是四个顶点，{@code u0..v1} 是这张面上截取的贴图范围
         *
         * <p>属性顺序照 {@code Experiment076Cube} 那一份：位置 贴图 光图 叠加层 法线 颜色
         */
        private static void quad(
                VertexConsumer buffer,
                Matrix4f matrix,
                Direction face,
                int packedLight,
                float[][] corners,
                float u0,
                float u1,
                float v0,
                float v1) {
            float[] us = {u0, u1, u1, u0};
            float[] vs = {v1, v1, v0, v0};
            for (int corner = 0; corner < 4; corner++) {
                float[] xyz = corners[corner];
                buffer.addVertex(matrix, xyz[0], xyz[1], xyz[2])
                        .setUv(us[corner], vs[corner])
                        .setLight(packedLight)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setNormal(face.getStepX(), face.getStepY(), face.getStepZ())
                        .setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        @Override
        public ResourceLocation getTextureLocation(Experiment011PlateEntity entity) {
            return TEXTURE;
        }
    }
}
