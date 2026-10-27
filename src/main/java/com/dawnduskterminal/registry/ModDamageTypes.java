package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * 伤害类型键。条目本身由数据包驱动（{@code data/dawnduskterminal/damage_type/*.json}），这里只放代码里
 * 构建 {@link net.minecraft.world.damagesource.DamageSource} 用到的键
 */
public final class ModDamageTypes {
    /** 「热情的暮光」(enthu_fluid) 的接触伤害，每次 6 点，自定义死亡信息 */
    public static final ResourceKey<DamageType> ENTHU_FLUID =
        ResourceKey.create(Registries.DAMAGE_TYPE, DawnDuskTerminal.id("enthu_fluid"));

    /** 「稳定」(stability) 的接触伤害，待在传送门流体里每 2 秒 2 点 */
    public static final ResourceKey<DamageType> STABILITY =
        ResourceKey.create(Registries.DAMAGE_TYPE, DawnDuskTerminal.id("stability"));

    /** 反应权杖弹体的范围伤害，死在里面的玩家有专属死亡信息 */
    public static final ResourceKey<DamageType> CARMINITE_FIELD =
        ResourceKey.create(Registries.DAMAGE_TYPE, DawnDuskTerminal.id("carminite_field"));

    /**
     * 试验物品 043 的喷火伤害：火焰伤害、不击退，标签在 {@code data/minecraft/tags/damage_type/} 下的
     * {@code is_fire}、{@code no_knockback}、{@code ignites_armor_stands} 三条里，与暮色森林那台喷火器的
     * {@code twilightforest:fire_jet} 同一套
     */
    public static final ResourceKey<DamageType> EXPERIMENT_043 =
        ResourceKey.create(Registries.DAMAGE_TYPE, DawnDuskTerminal.id("experiment_043"));

    /**
     * 试验物品 066 那本恶魂陷阱书的持续伤害，每一下 7 点
     *
     * <p>只进 {@code data/minecraft/tags/damage_type/} 下的 {@code no_knockback}：书自己每 tick 把场里的
     * 活物往视线轴上拽，伤害要是再推一把，两边会互相打架
     *
     * <p><b>不进 {@code bypasses_cooldown}</b>：场每 tick 都试一次 {@code hurt}，原版无敌帧会把连续的施压
     * 自动排成每 0.5 秒一下——无敌帧就是这个场的节拍，绕过去反而是错的。也不进 {@code is_fire}，它不是火
     */
    public static final ResourceKey<DamageType> EXPERIMENT_066 =
        ResourceKey.create(Registries.DAMAGE_TYPE, DawnDuskTerminal.id("experiment_066"));

    /**
     * 砷铅铁火焰的接触伤害，每次 2 点，自定义死亡信息
     *
     * <p>只进 {@code data/minecraft/tags/damage_type/} 下的 {@code no_knockback}（不产生击退），
     * <b>不进 {@code is_fire}</b>：进了那条标签就会被 {@code Entity#isInvulnerableTo} 里的
     * 「火焰免疫」挡住，烈焰人那一类就烧不着了。砷铅铁火焰要能烧穿火焰免疫，所以按独立的伤害类型算
     */
    public static final ResourceKey<DamageType> CARMINITE_FIRE =
        ResourceKey.create(Registries.DAMAGE_TYPE, DawnDuskTerminal.id("carminite_fire"));

    private ModDamageTypes() {}
}
