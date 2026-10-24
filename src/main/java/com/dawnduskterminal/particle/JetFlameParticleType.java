package com.dawnduskterminal.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 大火焰粒子的类型：带数据的 {@code ParticleType<JetFlameParticleOptions>}
 *
 * <p>与 {@code SimpleParticleType} 的区别就是那份数据，编解码器见 {@link JetFlameParticleOptions}；
 * 贴图表在 {@code assets/dawnduskterminal/particles/particle_jet_flame.json}（直接借原版
 * {@code minecraft:flame}），客户端 provider 在 {@code client/JetFlameParticle}
 */

public final class JetFlameParticleType extends ParticleType<JetFlameParticleOptions> {
    public JetFlameParticleType() {
        super(false);
    }

    public MapCodec<JetFlameParticleOptions> codec() {
        return JetFlameParticleOptions.CODEC;
    }

    public StreamCodec<? super RegistryFriendlyByteBuf, JetFlameParticleOptions> streamCodec() {
        return JetFlameParticleOptions.STREAM_CODEC;
    }
}
