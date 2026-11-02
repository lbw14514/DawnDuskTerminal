package com.dawnduskterminal.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 生砷铅恶魂肉
 *
 * <p>照腐肉那一套做：吃下有概率给饥饿，已经饥饿的时候还会加重并附带中毒。熟肉是普通食物，走
 * {@link Item} 的默认实现，不吃这个类
 *
 * <p>源 mod 把这些写成了一段独立的「食用后执行」脚本，挂在物品的事件里。1.21.1 的
 * {@link Item#finishUsingItem} 就是官方给的位置，不需要外用事件
 */
public class CarminiteGhastMeatItem extends Item {
    /** 吃下就给饥饿的概率，与原版腐肉一致 */
    private static final float HUNGER_CHANCE = 0.8F;
    /** 饥饿持续时间，与原版腐肉一致：30 秒 */
    private static final int HUNGER_TICKS = 600;
    /** 已经饥饿时继续吃，有概率延长饥饿并中毒 */
    private static final float ESCALATE_CHANCE = 0.4F;

    private static final int POISON_TICKS = 100;
    /** 效果等级上限，防止反复吃把等级堆到不可收拾 */
    private static final int MAX_AMPLIFIER = 9;

    public CarminiteGhastMeatItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player) {
            MobEffectInstance hunger = player.getEffect(MobEffects.HUNGER);
            if (player.getRandom().nextFloat() < HUNGER_CHANCE) {
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, HUNGER_TICKS, 0));
            }
            // 加重与第一次发饥饿是两次独立的掷骰。写进 else if 会让这次只在第一次没中时才发生，
            // 概率从 40% 掉到 8%，与腐肉那套行为对不上
            if (hunger != null && player.getRandom().nextFloat() < ESCALATE_CHANCE) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.HUNGER,
                        hunger.getDuration() + HUNGER_TICKS / 2,
                        Math.min(hunger.getAmplifier() + 1, MAX_AMPLIFIER)));
                player.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0));
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
