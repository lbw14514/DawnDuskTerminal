package com.dawnduskterminal.item;

import com.dawnduskterminal.entity.ExpowheelEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 博览轮舞：试验物品 057 的加强版，右键打出一片会自己找目标的飞轮
 *
 * <p>它只剩「发射」这一件事。057 上的近战部分整个摘掉：没有 {@code ATTACK_DAMAGE} / {@code ATTACK_SPEED}
 * 两个修饰符，因此拿在手上就是空手的面板；也不进 {@code #minecraft:swords}，附魔值取默认的 0，
 * 于是附魔台里一个近战附魔都不会出来。物品提示因此只有名字，没有描述行。
 *
 * <p>右键发射一片 {@link ExpowheelEntity}，一次扣 1 点耐久、冷却 {@value #COOLDOWN_TICKS} tick（1.5 秒）。
 * 与 057 一样不 {@code startUsingItem}：按一下就走完，不需要按住。
 *
 * <p>耐久 {@value #DURABILITY} 点沿用 057：需求没有改这一条，发射一次的代价也和 057 一样是 1 点。
 */
public class ExpowheelFleshbookItem extends Item {
    public static final int DURABILITY = 64;

    /** 两次发射之间隔 1.5 秒 */
    public static final int COOLDOWN_TICKS = 30;

    /** 每发扣多少耐久 */
    private static final int DURABILITY_COST = 1;

    public ExpowheelFleshbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (level instanceof ServerLevel server) {
            ExpowheelEntity.launch(server, player);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            stack.hurtAndBreak(DURABILITY_COST, player, LivingEntity.getSlotForHand(hand));
            player.awardStat(Stats.ITEM_USED.get(this));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
