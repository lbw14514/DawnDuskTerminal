package com.dawnduskterminal.item;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.CarminiteBallEntity;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 砷铅铁钼反应权杖
 *
 * <p>近战按单手武器算（伤害加 7、攻速减 3.5，也就是每 2 秒 1 次），右键发射一枚
 * {@link CarminiteBallEntity}，每发消耗 1 个砷铅铁球
 *
 * <p>耐久只由近战与挖掘消耗，发射走弹药。源 mod 是每发掉 1 点耐久、没有弹药；弹药是当时
 * MCreator 做不出来的替代方案，现在补上
 *
 * <p>附魔全部照搬源 mod 的换算：快速装填降冷却、力量提初速，其余（火矢 冲击 多重 穿透）在弹体
 * 命中时读，见 {@link CarminiteBallEntity}。物品被加进 {@code enchantable/bow} 与
 * {@code enchantable/crossbow} 两个原版标签，否则这六种附魔根本附不上去
 */
public class CarminiteStaffItem extends Item {
    private static final double ATTACK_DAMAGE = 7.0D;
    private static final double ATTACK_SPEED = -3.5D;
    private static final int COOLDOWN_TICKS = 140;
    private static final int COOLDOWN_PER_QUICK_CHARGE = 25;
    /** 快速装填的原版上限，命令给超过 3 级时冷却会算成负数 */
    private static final int MAX_QUICK_CHARGE = 3;

    private static final float VELOCITY = 1.0F;
    private static final float VELOCITY_PER_POWER = 0.2F;
    private static final float INACCURACY = 1.0F;
    /** 附魔性，源 mod 是 2 */
    private static final int ENCHANTMENT_VALUE = 2;
    /** 近战与挖掘的耐久消耗，照抄源 mod */
    private static final int HURT_ENEMY_DAMAGE = 2;

    private static final int MINE_BLOCK_DAMAGE = 1;

    public CarminiteStaffItem(Properties properties) {
        super(properties);
    }

    /** 单手武器的属性表，1.21.1 通过 {@link Item.Properties#attributes} 挂到物品上 */
    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                DawnDuskTerminal.id("staff_attack_damage"),
                                ATTACK_DAMAGE,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                DawnDuskTerminal.id("staff_attack_speed"),
                                ATTACK_SPEED,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean infinite = player.hasInfiniteMaterials();
        if (!infinite && !hasAmmo(player)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            int power = CarminiteBallEntity.enchantmentLevel(stack, Enchantments.POWER);
            CarminiteBallEntity ball = new CarminiteBallEntity(level, player, stack);
            ball.shootFromRotation(
                    player,
                    player.getXRot(),
                    player.getYRot(),
                    0.0F,
                    VELOCITY + VELOCITY_PER_POWER * power,
                    INACCURACY);
            level.addFreshEntity(ball);
            level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    ModSounds.CARMINITE_STAFF_SHOOT.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
            if (!infinite) {
                consumeAmmo(player);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        int quickCharge =
                Math.min(MAX_QUICK_CHARGE, CarminiteBallEntity.enchantmentLevel(stack, Enchantments.QUICK_CHARGE));
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS - COOLDOWN_PER_QUICK_CHARGE * quickCharge);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** 源 mod 恒定附魔光效 */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return ENCHANTMENT_VALUE;
    }

    /**
     * 不接受无限：它在弓的物品标签里，会跟着力量 火矢 冲击一起被抽到，但权杖每次发射都要消耗弹药，
     * 无限在这里没有作用。附魔台（isPrimaryItemFor 会转调这里）与铁砧都走这个判断
     */
    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        if (enchantment.is(Enchantments.INFINITY)) {
            return false;
        }
        return super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(HURT_ENEMY_DAMAGE, attacker, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        stack.hurtAndBreak(MINE_BLOCK_DAMAGE, miner, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public void appendHoverText(
            ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.dawnduskterminal.carminite_molybdenum_reactor_staff.desc")
                .withStyle(ChatFormatting.GRAY));
    }

    /** 背包里有没有砷铅铁球。护甲槽也扫，和原版掏箭的做法一致 */
    private static boolean hasAmmo(Player player) {
        return player.getInventory().hasAnyMatching(stack -> stack.is(ModItems.CARMINITE_BALL.get()));
    }

    private static void consumeAmmo(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack ammo = player.getInventory().getItem(slot);
            if (ammo.is(ModItems.CARMINITE_BALL.get())) {
                // getItem 返回的是背包里的那一份，shrink 直接作用在上面
                ammo.shrink(1);
                return;
            }
        }
    }
}
