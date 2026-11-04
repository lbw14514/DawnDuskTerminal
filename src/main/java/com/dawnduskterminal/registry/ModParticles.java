package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.particle.JetFlameParticleType;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 砷铅铁反应权杖用到的三种粒子、试验物品 027 的红石点、试验物品 043 的大火焰，以及试验物品 095 的音符
 *
 * <p>贴图表与帧序在 {@code assets/dawnduskterminal/particles/particle_carminite_*.json}，
 * 客户端 provider 在 {@link com.dawnduskterminal.client.CarminiteParticle}；
 * 027 的贴图表在 {@code particles/particle_experiment_027.json}，实现在
 * {@link com.dawnduskterminal.client.Experiment027Particle}；043 的贴图表在
 * {@code particles/particle_jet_flame.json}（直接借原版 {@code minecraft:flame}），实现在
 * {@link com.dawnduskterminal.client.JetFlameParticle}；095 的音符在
 * {@code particles/particle_requiem_note.json}（借原版 {@code minecraft:note}），实现在
 * {@link com.dawnduskterminal.client.RequiemNoteParticle}
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

    /**
     * 博览轮舞那片飞轮寿命到点那一圈：贴图与 {@link #CARMINITE_EFFECT} 同一套，
     * 只把摩擦加大到 0.8，让整环自己慢下来
     *
     * <p>粒子类型是按摩擦分的——{@code addParticle} 只能给位置与初速，改不了阻尼，
     * 所以想让这一圈「自己减速」就得另开一种
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CARMINITE_RING =
            register("particle_carminite_ring");

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

    /**
     * 试验物品 095 那一圈音符：贴图直接借原版 {@code minecraft:note}，颜色在客户端现摇，
     * 速度走粒子包自己的初速参数
     *
     * <p>不能用原版 {@code ParticleTypes.NOTE}：{@code NoteParticle} 的构造函数把速度整个乘了 0.01，
     * 横向初速永远是 0，粒子只会原地往上飘，做不出「向四周甩出一圈」。所以另开一种：摩擦 0.9（横向冲出去
     * 之后还要能扩到 32 格，所以不能像原版音符那样用 0.66）、出生时给一份向上初速、之后每 tick 再往上托一点、
     * 寿命 40 ± 4 tick、尺寸乘 2。见 {@link com.dawnduskterminal.client.RequiemNoteParticle}
     *
     * <p>颜色不进粒子选项：它是纯装饰，每个客户端各自随机一个色相，谁也看不出两边的差别，
     * 就不用为了一个 {@code float} 再造一套 {@code ParticleType}
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> REQUIEM_NOTE =
            register("particle_requiem_note");

    /**
     * 棱镜权杖射线上的一颗光点：带颜色的 {@code SpellParticle}（贴图借原版 {@code effect_7..0}，
     * 恒满亮），观感照暮色森林黄昏权杖拖尾的 {@code magic_effect}，实现在
     * {@link com.dawnduskterminal.client.PrismTraceParticle}
     *
     * <p>带颜色就不能用 {@link SimpleParticleType}：颜色随每颗粒子现摇，走
     * {@link ColorParticleOption} 一起发；这个类型因此要自带 codec 与 stream codec
     */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> PRISM_TRACE =
            PARTICLE_TYPES.register("particle_prism_trace", () -> new ParticleType<ColorParticleOption>(false) {
                @Override
                public MapCodec<ColorParticleOption> codec() {
                    return ColorParticleOption.codec(this);
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                    return ColorParticleOption.streamCodec(this);
                }
            });

    private ModParticles() {}

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        // overrideLimiter 为 false，粒子照常受视距限制
        return PARTICLE_TYPES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus bus) {
        PARTICLE_TYPES.register(bus);
    }
}
