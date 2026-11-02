package com.dawnduskterminal.item;

import com.dawnduskterminal.entity.Experiment057BladeEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * 试验物品 057：那把 8 点伤害、攻速 1.6，右键打出一片制导飞刃的刀
 *
 * <p>数值只有三条，都按**面板值**写死：攻击力 {@value #ATTACK_DAMAGE}、攻速 {@value #ATTACK_SPEED}、
 * 耐久 {@value #DURABILITY}。面板值与修饰符之间差的是玩家底数（攻击力 {@value #PLAYER_ATTACK_DAMAGE}、
 * 攻速 {@value #PLAYER_ATTACK_SPEED}），{@link #createAttributes()} 里就减这一步，
 * 于是代码里不会出现「-2.4」这种与需求对不上的数字
 *
 * <p>它不挂 tool tier：不挖方块，只在近战与发射上消耗耐久，因此也不进那五个工具标签
 *
 * <p>「可接受近战附魔」靠两件事：附魔值 {@value #ENCHANTMENT_VALUE}（1.21.1 的附魔台按
 * {@code Item#getEnchantmentValue(ItemStack)} 判定，返回 0 的物品在附魔台里一个选项都没有），
 * 以及把自己挂进 {@code #minecraft:swords}——原版那几个近战附魔的 {@code supported_items} 全部由
 * {@code #minecraft:enchantable/sharp_weapon} 与 {@code #minecraft:enchantable/sword} 派生，
 * 这两条又都指向 {@code #minecraft:swords}，于是锋利、亡灵杀手、节肢杀手、击退、火焰附加、抢夺、
 * 横扫之刃、耐久与经验修补都能上去。附魔加成会进 {@code ATTACK_DAMAGE} 属性，飞刃每次命中都现读这个
 * 属性（见 {@link Experiment057BladeEntity}），所以锋利多少级就打多少伤害，不是写死的 8
 *
 * <p>右键发射一片 {@link Experiment057BladeEntity}，一次扣 1 点耐久、冷却 {@value #COOLDOWN_TICKS} tick
 * （2 秒）。与喷火器、恶魂陷阱书一样不 {@code startUsingItem}：按一下就走完，不需要按住
 */
public class Experiment057Item extends Item {
    public static final int DURABILITY = 64;

    /** 面板近战伤害 */
    public static final double ATTACK_DAMAGE = 8.0D;

    /** 面板攻速：每秒 1.6 次 */
    public static final double ATTACK_SPEED = 1.6D;

    /** 两次发射之间隔 2 秒 */
    public static final int COOLDOWN_TICKS = 40;

    /** 玩家自身的主手底数，原版写死在 {@code Player} 的初始属性里；修饰符是「面板值 − 底数」 */
    private static final double PLAYER_ATTACK_DAMAGE = 1.0D;
    private static final double PLAYER_ATTACK_SPEED = 4.0D;

    /** 每发扣多少耐久 */
    private static final int DURABILITY_COST = 1;

    /** 附魔值，照钻石剑那一档的 10 */
    private static final int ENCHANTMENT_VALUE = 10;

    public Experiment057Item(Properties properties) {
        super(properties);
    }

    /**
     * 主手属性表：面板值减掉玩家底数，得到两个修饰符的量
     *
     * <p>面板 8 对应 {@code ATTACK_DAMAGE +7}，面板 1.6 对应 {@code ATTACK_SPEED -2.4}——
     * 减法在这里做，取值只在 {@value #ATTACK_DAMAGE} 与 {@value #ATTACK_SPEED} 两处
     *
     * <p>两个修饰符的 ID 必须是原版的 {@link Item#BASE_ATTACK_DAMAGE_ID} 与
     * {@link Item#BASE_ATTACK_SPEED_ID}：物品提示里只有认出这两个 ID 才会把「底数 + 修饰符」按最终值
     * 显示成「8 攻击伤害 / 1.6 攻击速度」，换成一个自定义 ID 就会退化成「+7 / -2.4」（原版剑的显示
     * 正是靠这条判据，见 {@code ItemStack#addModifierTooltip}）
     */
    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ATTACK_DAMAGE_ID, ATTACK_DAMAGE - PLAYER_ATTACK_DAMAGE,
                    AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,
                new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED - PLAYER_ATTACK_SPEED,
                    AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (level instanceof ServerLevel server) {
            Experiment057BladeEntity.launch(server, player);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            stack.hurtAndBreak(DURABILITY_COST, player, LivingEntity.getSlotForHand(hand));
            player.awardStat(Stats.ITEM_USED.get(this));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return ENCHANTMENT_VALUE;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.dawnduskterminal.experiment_057.desc")
            .withStyle(ChatFormatting.GRAY));
    }
}
