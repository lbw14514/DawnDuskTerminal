package com.dawnduskterminal.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * 试验物品 012 的物品形态：那只杯子
 *
 * <p>加杯子的判定全在方块上（{@code Experiment012Block} 的 {@code canBeReplaced} 与
 * {@code getStateForPlacement}），这里只多一条描述行：一格能摆三只这件事，光看物品图标看不出来
 */
public class Experiment012Item extends BlockItem {
    public Experiment012Item(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.dawnduskterminal.experiment_012.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
