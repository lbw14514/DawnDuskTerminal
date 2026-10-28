package com.dawnduskterminal.item;

import com.dawnduskterminal.block.SusCakeBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * 可疑蛋糕（{@code sus_<口味>_cake}）：既能吃又能摆的方块物品，行为逐条照暮色森林的试验物品 115
 *
 * <p><b>一个物品就是一片</b>：吃这一个物品、或者在摆着的那块上吃一片，都是 {@link SusCakeBlock#NUTRITION} 点饥饿、
 * {@link SusCakeBlock#SATURATION_MODIFIER} 系数（6 点饥饿、4 点饱和度）；115 是一片 4 与 0.3（2.4 点），
 * 口径相同、数值不同。摆下来是只剩 1 片的方块（{@code omnomnom=7}，与 115 一样），一块满的蛋糕是 8 片，
 * 所以摆满一块要 8 个物品
 *
 * <p>{@link #useOn} 逐行照 115：点在自己那一块上不动手（把交互让给方块，方块自己补片或取片），不是潜行就摆，
 * 摆不下才转成吃
 */
public class SusCakeItem extends BlockItem {
    public SusCakeItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().getBlockState(context.getClickedPos()).is(this.getBlock())) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (player != null && !player.isShiftKeyDown()) {
            InteractionResult result = this.place(new BlockPlaceContext(context));
            return !result.consumesAction() && context.getItemInHand().get(DataComponents.FOOD) != null
                ? this.use(context.getLevel(), player, context.getHand()).getResult()
                : result;
        }
        return InteractionResult.PASS;
    }
}
