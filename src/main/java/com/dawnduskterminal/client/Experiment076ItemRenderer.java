package com.dawnduskterminal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 试验物品 076 的物品形态：物品栏图标、手持、丢在地上都在这里画，和世界里的方块一样每 tick 换一次贴图
 *
 * <p>物品模型挂 {@code minecraft:builtin/entity}（见 {@code models/item/experiment_076.json}），
 * 于是 {@code ItemRenderer} 不走烘焙模型的静态几何，而是从这里取渲染器现画——静态图标做不到「一直切换」，
 * 换贴图只能是每帧现画
 *
 * <p>坐标系：原版在调 {@code renderByItem} 之前已经按模型里的 {@code display} 摆好姿势、再
 * {@code translate(-0.5, -0.5, -0.5)}，所以这里照常画 0 到 1 的单位立方体就是标准方块物品的样子，
 * 不用自己再摆姿势
 *
 * <p>种子用世界时间（没有世界时是 0）：同一刻所有客户端、物品栏与手上的那一份都是同一个样子，
 * 与世界里那个方块各抽各的（方块那边额外拌了坐标）
 */
public final class Experiment076ItemRenderer extends BlockEntityWithoutLevelRenderer {
    public Experiment076ItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int light, int overlay) {
        Level level = Minecraft.getInstance().level;
        long tick = level == null ? 0L : level.getGameTime();
        VertexConsumer buffer = bufferSource.getBuffer(Experiment076Cube.LAYER);
        Experiment076Cube.drawCube(buffer, poseStack.last().pose(),
            Experiment076Cube.mix(tick * 0x9E3779B97F4A7C15L), overlay);
    }
}
