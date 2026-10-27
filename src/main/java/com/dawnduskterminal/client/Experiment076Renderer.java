package com.dawnduskterminal.client;

import com.dawnduskterminal.block.entity.Experiment076BlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 试验物品 076 在世界里的外观：每一 tick 给六个面各抽一张贴图，候选是地狱岩、黑曜石、基岩、传送门
 *
 * <p>几何与抽法在 {@link Experiment076Cube}，这里的活儿只有三件：算这一 tick 的种子、按邻居剔掉看不见的面、
 * 把外壳比方块本体往外撑一点点。方块自己的 cube_all 留着当远距离的兜底（方块实体只在 64 格内画）
 * 与遮挡层，见 {@code Experiment076Block}
 */
public final class Experiment076Renderer implements BlockEntityRenderer<Experiment076BlockEntity> {
    /**
     * 外壳比方块本体往外撑半个万分之一格
     *
     * <p>两层正好贴在一起时谁盖谁就成了深度比较上的平局。撑出这么一点点，外壳的每个面都严格在块模型
     * 那个面的前面；半个万分之一格在 16 像素的面上是百分之一像素，看不见
     */
    private static final float EXPAND = 0.0005F;

    public Experiment076Renderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(Experiment076BlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        BlockState state = blockEntity.getBlockState();
        // 只有「tick 数 + 坐标」进种子：同一刻所有客户端算出同一个样子，方块实体不用存东西
        long seed = Experiment076Cube.mix(level.getGameTime() * 0x9E3779B97F4A7C15L ^ pos.asLong());

        poseStack.pushPose();
        poseStack.translate(-EXPAND, -EXPAND, -EXPAND);
        poseStack.scale(1.0F + 2.0F * EXPAND, 1.0F + 2.0F * EXPAND, 1.0F + 2.0F * EXPAND);
        VertexConsumer buffer = bufferSource.getBuffer(Experiment076Cube.LAYER);
        for (Direction face : Direction.values()) {
            // 邻居把这面挡住了就不画：问的是区块网格问的同一个问题（Block#shouldRenderFace），
            // 所以一摞 076、或者砌进墙里的那一块，六面大部分都不用白画
            if (!Block.shouldRenderFace(state, level, pos, face, pos.relative(face))) {
                continue;
            }
            Experiment076Cube.drawFace(buffer, poseStack.last().pose(), face, seed, packedOverlay);
        }
        poseStack.popPose();
    }
}
