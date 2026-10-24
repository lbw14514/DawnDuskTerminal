package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.particle.JetFlameParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 砷铅铁反应权杖用到的三种粒子、试验物品 027 的红石点，以及试验物品 043 的大火焰
 *
 * <p>贴图表与帧序在 {@code assets/dawnduskterminal/particles/particle_carminite_*.json}，
 * 客户端 provider 在 {@link com.dawnduskterminal.client.CarminiteParticle}；
 * 027 的贴图表在 {@code particles/particle_experiment_027.json}，实现在
 * {@link com.dawnduskterminal.client.Experiment027Particle}；043 的贴图表在
 * {@code particles/particle_jet_flame.json}（直接借原版 {@code minecraft:flame}），实现在
 * {@link com.dawnduskterminal.client.JetFlameParticle}
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
        DeferredRegister.create(Registries.PARTICLE_TYPE, DawnDuskTerminal.MOD_ID);

    /** 飞行中的弹体本体 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CARMINITE_BALL =
        register("particle_carminite_ball");

    /** 弹体拖尾 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CARMINITE_TRACE =
        register("particle_carminite_trace");

    /** 命中后炸开的反应场 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CARMINITE_EFFECT =
        register("particle_carminite_effect");

    /**
     * 爆炸专用：与 {@link #CARMINITE_EFFECT} 同一套贴图（{@code particle_carminite_effect_1..8}），
     * 换一份帧序，帧走得更慢
     *
     * <p>另开一种而不是改 {@code effect} 的帧间隔：反应场那圈粒子是照着源 mod 的节奏来的，
     * 爆炸想要更慢的帧不该顺带把反应场也改掉
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CARMINITE_BLAST =
        register("particle_carminite_blast");

    /** 试验物品 027 画在方块面上的红石点：静止、半尺寸、60 秒 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EXPERIMENT_027 =
        register("particle_experiment_027");

    /**
     * 砷铅铁余烬：红石粒子那组贴图（{@code minecraft:generic_0..7}）另开的一种粒子，
     * 比原版红石粒子大一半、恒满亮、像烟一样往上散
     *
     * <p>用于 {@code carminite_fire} 向上飘的余烬与试验物品 052 碎片拖的那条红线，
     * 实现见 {@link com.dawnduskterminal.client.CarminiteEmberParticle}
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CARMINITE_EMBER =
        register("particle_carminite_ember");

    /**
     * 试验物品 043 的大火焰：初速随粒子选项一起发，不走原版那种给客户端现摇随机速度的路子
     *
     * <p>类型见 {@link JetFlameParticleType}，数据见 {@link com.dawnduskterminal.particle.JetFlameParticleOptions}
     */
    public static final DeferredHolder<ParticleType<?>, JetFlameParticleType> JET_FLAME =
        PARTICLE_TYPES.register("particle_jet_flame", JetFlameParticleType::new);

    private ModParticles() {}

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        // overrideLimiter 为 false，粒子照常受视距限制
        return PARTICLE_TYPES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus bus) {
        PARTICLE_TYPES.register(bus);
    }
}
