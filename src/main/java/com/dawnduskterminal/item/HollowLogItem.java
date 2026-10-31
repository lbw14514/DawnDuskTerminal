package com.dawnduskterminal.item;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 空心原木的物品：一个物品对应三块方块（横、竖、攀爬版）
 *
 * <p>照暮色森林的 {@code twilightforest.item.HollowLogItem} 移植：
 * 点方块的顶面或底面时放竖向那块，点侧面时放横向那块（轴向跟着点击面走）。
 * 三块方块都映射回同一件物品，挖下来不会变成三种东西，所以这里要覆盖
 * {@link #registerBlocks}——默认实现只认主方块那一块。
 */
public class HollowLogItem extends BlockItem {
    private final Block horizontalLog;
    private final Block verticalLog;
    private final Block climbableLog;

    public HollowLogItem(Block horizontalLog, Block verticalLog, Block climbableLog, Properties properties) {
        super(verticalLog, properties);
        this.horizontalLog = horizontalLog;
        this.verticalLog = verticalLog;
        this.climbableLog = climbableLog;
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        return context.getClickedFace().getAxis().isVertical()
            ? this.verticalLog.getStateForPlacement(context)
            : this.horizontalLog.getStateForPlacement(context);
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
        super.registerBlocks(blockToItemMap, item);
        blockToItemMap.put(this.horizontalLog, item);
        blockToItemMap.put(this.verticalLog, item);
        blockToItemMap.put(this.climbableLog, item);
    }

    @Override
    public int getBurnTime(ItemStack itemStack, @Nullable RecipeType<?> recipeType) {
        return 300;
    }
}
