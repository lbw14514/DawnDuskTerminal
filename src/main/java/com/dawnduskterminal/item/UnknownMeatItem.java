package com.dawnduskterminal.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 不明肉块：试验物品 061、070、074 共用的那一份规格
 *
 * <p>数值取原版生肉那一档：2 点饥饿、0.3 的饱和度系数（点数 = 系数 × 饥饿 × 2 = 1.2），与生鸡肉、生羊肉
 * 逐项相同。吃下时两条独立的掷骰，都写在 {@link #FOOD} 的效果表里：
 *
 * <ol>
 *   <li>三成概率饥饿 30 秒，与生鸡肉那一条一致（{@code Foods#CHICKEN} 是 600 tick、概率 0.3）
 *   <li>三成概率中毒 5 秒，时长照毒马铃薯那一条（{@code Foods#POISONOUS_POTATO} 是 100 tick），概率改成
 *       与上面那条一样的三成
 * </ol>
 *
 * <p>两条都不用自己掷：原版 {@code LivingEntity#addEatEffect} 会遍历 {@code FoodProperties#effects()}，
 * 逐个以 {@code random.nextFloat() < probability} 判定，并且整段只在服务端执行，客户端那边不会重复掷骰。
 * 所以这里没有 {@code finishUsingItem}，也没有另写一个物品类去摇效果——{@link Experiment020Item} 那样的
 * 现摇逻辑是因为效果表本身要随机，肉块这张表是固定的
 *
 * <p>效果用 NeoForge 的 {@code Supplier} 重载登记：原版的 {@code effect(MobEffectInstance, float)} 在
 * NeoForge 上已标为废弃，{@code MobEffectInstance} 要到吃下去那一刻才需要存在
 *
 * <p>三块肉共用同一个 {@link FoodProperties} 实例（record 不可变），物品本身不带耐久、不带「随时可吃」，
 * 堆叠上限是食物的默认值 64
 */
public class UnknownMeatItem extends Item {
    /** 饥饿点数，与生鸡肉、生羊肉相同 */
    public static final int NUTRITION = 2;

    /** 饱和度系数，与生鸡肉、生羊肉相同：点数 = 0.3 × 2 × 2 = 1.2 */
    public static final float SATURATION_MODIFIER = 0.3F;

    /** 饥饿给多少 tick：30 秒，照原版生鸡肉 */
    public static final int HUNGER_TICKS = 600;

    /** 吃出饥饿的概率，照原版生鸡肉 */
    public static final float HUNGER_CHANCE = 0.3F;

    /** 中毒给多少 tick：5 秒，照原版毒马铃薯 */
    public static final int POISON_TICKS = 100;

    /** 吃出中毒的概率：与饥饿那条一样的三成 */
    public static final float POISON_CHANCE = 0.3F;

    /** 三块肉共用这一份食物属性 */
    public static final FoodProperties FOOD = new FoodProperties.Builder()
        .nutrition(NUTRITION)
        .saturationModifier(SATURATION_MODIFIER)
        .effect(() -> new MobEffectInstance(MobEffects.HUNGER, HUNGER_TICKS, 0), HUNGER_CHANCE)
        .effect(() -> new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0), POISON_CHANCE)
        .build();

    public UnknownMeatItem(Properties properties) {
        super(properties);
    }

    /**
     * 描述行是「不明肉块」
     *
     * <p>键由物品自己的描述 ID 加 {@code .desc} 拼出来，三个序号各有一条（{@code experiment_061.desc} 等），
     * 与本模组其它试验物品的描述行同一套写法；物品名字仍是「试验物品NNN」
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
