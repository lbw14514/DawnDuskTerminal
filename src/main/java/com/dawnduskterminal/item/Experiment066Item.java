package com.dawnduskterminal.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 试验物品 066：那本手持式的恶魂陷阱书
 *
 * <p>单击右键沿视线开一条 6 秒的走廊，里面所有活物（施放者自己除外）被往轴上拽，并且每 tick 挨一次
 * 7 点——原版无敌帧会把连续的施压排成每 0.5 秒一下，6 秒正好 12 下；整套在 {@link GhastTrapProcess} 里推进。
 * 与暮色森林的恶魂陷阱方块一样会吸东西、会打伤害，但没有「先杀恶魂充能」这一段：不需要充能，翻开就压
 *
 * <p>场不挑对象：怪物、动物、玩家都会中招（不绕无敌帧、也不绕玩家），所以这本书也能装到敌怪身上去打玩家；
 * 要区分敌我只是施放者这边的事。冷却 {@value #COOLDOWN_TICKS} tick = 整段
 * {@link GhastTrapProcess#TOTAL_TICKS} tick 再多 3 秒；翻一次扣 1 点耐久，64 点。与喷火器一样不用
 * {@code startUsingItem}：按一下就走完，不需要按住
 */
public class Experiment066Item extends Item {
    public static final int DURABILITY = 64;
    /** 冷却：整段 120 tick 再加 3 秒 */
    public static final int COOLDOWN_TICKS = GhastTrapProcess.TOTAL_TICKS + 60;

    public Experiment066Item(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (level instanceof ServerLevel server) {
            GhastTrapProcess.start(server, player);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.dawnduskterminal.experiment_066.desc").withStyle(ChatFormatting.GRAY));
    }
}
