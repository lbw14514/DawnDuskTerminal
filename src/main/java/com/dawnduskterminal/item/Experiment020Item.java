package com.dawnduskterminal.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.Level;

/**
 * 试验物品 020：20 点饥饿、20 点饱和度的食物，吃完随机给 0~3 个效果，每个 10~300 秒
 *
 * <p>效果是吃完那一刻现摇的，所以不能写进 {@code FoodProperties#effects()}（那是固定表，还会进
 * tooltip）；改在 {@link #finishUsingItem} 里摇，`super` 先照原版把那口饭吃掉、把物品栈换成剩下的，
 * 再往实体身上挂效果
 *
 * <p>摇的时候不放回：同一口里不会出现两个一样的效果；数量与时长都两头取满（0 个、3 个，10 秒、300 秒
 * 都能摇到）。等级固定 I 级
 *
 * <p>效果池的两条规矩见 {@link #effectPool()}：瞬发的不要、非原版里没有药水瓶的技术性效果也不要
 *
 * <p>只在服务端摇：{@link #finishUsingItem} 两端都会走一遍，客户端那边摇出来的结果没人同步，白摇
 */
public class Experiment020Item extends Item {
    /** 抽几个效果：0~3，两头都含（0 就是这一口白吃，什么都不给） */
    public static final int MIN_EFFECTS = 0;

    public static final int MAX_EFFECTS = 3;

    /** 每个效果持续多久：10~300 秒，两头都含 */
    public static final int MIN_SECONDS = 10;

    public static final int MAX_SECONDS = 300;

    private static final int TICKS_PER_SECOND = 20;

    private static final String VANILLA_NAMESPACE = ResourceLocation.DEFAULT_NAMESPACE;

    public Experiment020Item(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack leftover = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide) {
            for (MobEffectInstance effect : rollEffects(level.getRandom())) {
                entity.addEffect(effect);
            }
        }
        return leftover;
    }

    /**
     * 摇一口饭的效果：0~3 个互不相同、I 级、各 10~300 秒
     *
     * <p>随机源当参数传进来，这样测试可以塞固定种子，摇出来的结果可复现
     */
    public static List<MobEffectInstance> rollEffects(RandomSource random) {
        List<Holder<MobEffect>> pool = effectPool();
        int wanted = MIN_EFFECTS + random.nextInt(MAX_EFFECTS - MIN_EFFECTS + 1);
        int count = Math.min(wanted, pool.size());
        // 不放回抽样：把池子复制一份，每一步从「还没抽到的那段」里随机取一个换到前面，取前 count 个
        List<Holder<MobEffect>> shuffled = new ArrayList<>(pool);
        List<MobEffectInstance> rolled = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int pick = i + random.nextInt(shuffled.size() - i);
            Collections.swap(shuffled, i, pick);
            int seconds = MIN_SECONDS + random.nextInt(MAX_SECONDS - MIN_SECONDS + 1);
            rolled.add(new MobEffectInstance(shuffled.get(i), seconds * TICKS_PER_SECOND));
        }
        return rolled;
    }

    /**
     * 可抽的效果池，去掉两类
     *
     * <ol>
     *   <li>瞬发效果（{@code instant_health} / {@code instant_damage} / {@code saturation}）：它们根本不看持续时间，
     *       塞进「10~300 秒」这套规格里只会是个空字段
     *   <li><b>非原版</b>里没有药水瓶的技术性效果：模组常注册一批只给内部逻辑用的效果（阶段标记、诅咒、
     *       给生物挂的可见性开关之类），摇出来玩家只会看到一串莫名其妙的图标。判据是「有没有一瓶
     *       {@link Potion} 带这个效果」——药水瓶显示的效果本来就取自 {@code BuiltInRegistries.POTION} 这张表，
     *       表里查不到就等于世上没有这瓶药
     * </ol>
     *
     * <p>原版效果不套第二条：{@code bad_omen}、{@code hero_of_the_village}、{@code raid_omen}、{@code trial_omen}
     * 这些原版也没有对应药水，但玩家对它们的含义有共识，留着一并摇
     */
    public static List<Holder<MobEffect>> effectPool() {
        Set<Holder<MobEffect>> potionEffects = potionEffects();
        return BuiltInRegistries.MOB_EFFECT.holders()
            .<Holder<MobEffect>>map(holder -> holder)
            .filter(holder -> !holder.value().isInstantenous())
            .filter(holder -> isVanilla(holder) || potionEffects.contains(holder))
            .toList();
    }

    /** 药水瓶里出现过的效果：把 {@code BuiltInRegistries.POTION} 整张表扫一遍 */
    private static Set<Holder<MobEffect>> potionEffects() {
        Set<Holder<MobEffect>> effects = new HashSet<>();
        BuiltInRegistries.POTION.holders().forEach(potion -> {
            for (MobEffectInstance effect : potion.value().getEffects()) {
                effects.add(effect.getEffect());
            }
        });
        return effects;
    }

    /** 效果是不是原版的：看注册 ID 的命名空间 */
    private static boolean isVanilla(Holder<MobEffect> effect) {
        return effect.unwrapKey()
            .map(key -> key.location().getNamespace().equals(VANILLA_NAMESPACE))
            .orElse(false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.dawnduskterminal.experiment_020.desc")
            .withStyle(ChatFormatting.GRAY));
    }
}
