package com.dawnduskterminal.particle;

import com.dawnduskterminal.registry.ModParticles;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * 大火焰粒子的选项：把初速一起发给客户端
 *
 * <p>原版粒子包只带位置与速度，速度还得客户端现摇，想要「同一拍、同一条线」的喷火就必须自带方向。
 * 这个 record 只带一个 {@code Vec3} 速度，编解码器与类型一起挂在
 * {@link com.dawnduskterminal.registry.ModParticles#JET_FLAME}（{@link JetFlameParticleType}）上，
 * 客户端 provider 在 {@code client/JetFlameParticle}
 */

public record JetFlameParticleOptions(Vec3 velocity) implements ParticleOptions {
    public static final MapCodec<JetFlameParticleOptions> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Vec3.CODEC.fieldOf("velocity").forGetter(JetFlameParticleOptions::velocity)).apply(instance, JetFlameParticleOptions::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, JetFlameParticleOptions> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT,
        options -> (float)options.velocity().x,
        ByteBufCodecs.FLOAT,
        options -> (float)options.velocity().y,
        ByteBufCodecs.FLOAT,
        options -> (float)options.velocity().z,
        (x, y, z) -> new JetFlameParticleOptions(new Vec3((double)x.floatValue(), (double)y.floatValue(), (double)z.floatValue()))
    );

    @Override
    public ParticleType<?> getType() {
        return (ParticleType<?>)ModParticles.JET_FLAME.get();
    }
}
