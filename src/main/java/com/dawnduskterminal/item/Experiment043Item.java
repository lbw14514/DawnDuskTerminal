package com.dawnduskterminal.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

/**
 * 试验物品 043：那台手持喷火器
 *
 * <p>单击右键沿视线喷一次，整套「预热 → 火焰 → 冷却」在 {@link FireJetProcess} 里推进，
 * 过程挂在服务端 tick 上，所以松手、换物品都不打断。喷一次扣 1 点耐久，64 点
 *
 * <p>不用 {@code startUsingItem}：与长矛那种按住才有效的东西不同，这是「按一下就走完」的一次性动作
 */

public class Experiment043Item extends Item {
    public static final int DURABILITY = 64;

    public Experiment043Item(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        } else {
            if (level instanceof ServerLevel server) {
                FireJetProcess.start(server, player, hand);
                player.getCooldowns().addCooldown(this, 201);
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
    }
}
