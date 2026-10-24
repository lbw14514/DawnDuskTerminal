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
