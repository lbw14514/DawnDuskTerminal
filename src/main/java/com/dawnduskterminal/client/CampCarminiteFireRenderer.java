package com.dawnduskterminal.client;

import com.dawnduskterminal.block.CampCarminiteFireBlock;
import com.dawnduskterminal.block.entity.CampCarminiteFireBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 红篝火火堆上那四格食材：与原版 {@code CampfireRenderer} 同一个摆法
 *
 * <p>四方各一份，随方块的 {@code FACING} 转，位置抬到火舌下面（y = 0.44921875，就是原版的 7.1875/16），
 * 摆成平放的小方块（{@code ItemDisplayContext.FIXED}、旋转 90°、缩放 0.375）
 *
 * <p>不能直接复用原版那个渲染器：它的泛型写死成 {@code CampfireBlockEntity}，本模组的方块实体是
 * 自己那一份（原版的类型认不了本方块），所以这里照它的渲染循环抄了一遍
 */
public class CampCarminiteFireRenderer implements BlockEntityRenderer<CampCarminiteFireBlockEntity> {
    /** 食材的缩放，与原版一致 */
    private static final float SIZE = 0.375F;

    /** 食材在方格里横向的偏移，与原版一致（0.5 - 0.3125 = 0.1875） */
    private static final float OFFSET = 0.3125F;

    /** 食材的高度：原版篝火的 0.44921875 */
    private static final float HEIGHT = 0.44921875F;

    private final ItemRenderer itemRenderer;

    public CampCarminiteFireRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            CampCarminiteFireBlockEntity campfire,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        Direction facing = campfire.getBlockState().getValue(CampCarminiteFireBlock.FACING);
        NonNullList<ItemStack> items = campfire.getItems();
        int seed = (int) campfire.getBlockPos().asLong();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            pose.pushPose();
            pose.translate(0.5F, HEIGHT, 0.5F);
            Direction direction = Direction.from2DDataValue((slot + facing.get2DDataValue()) % 4);
            pose.mulPose(Axis.YP.rotationDegrees(-direction.toYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            pose.translate(-OFFSET, -OFFSET, 0.0F);
            pose.scale(SIZE, SIZE, SIZE);
            this.itemRenderer.renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    pose,
                    buffers,
                    campfire.getLevel(),
                    seed + slot);
            pose.popPose();
        }
    }
}
