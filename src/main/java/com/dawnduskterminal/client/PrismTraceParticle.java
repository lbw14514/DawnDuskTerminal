package com.dawnduskterminal.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpellParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.ColorParticleOption;

/**
 * 棱镜射线上的那一颗光点
 *
 * <p>直接派生自原版 {@link SpellParticle}：贴图、飘动、摩擦与寿命都照旧，只改两处——颜色由
 * {@code ColorParticleOption} 一颗粒子一个色，亮度恒满（{@link LightTexture#FULL_BRIGHT}）。
 * 这两处合起来就是暮色森林黄昏权杖拖尾的那个观感：{@code TwilightWandBolt#makeTrail} 每 tick
 * 用 {@code TFParticleType.MAGIC_EFFECT} 撒五颗，而那个类型（{@code MagicEffectParticle}）正是
 * 同一个 {@code SpellParticle} 派生出来的、同样恒满亮，贴图也同样是 {@code effect_7..0}
 *
 * <p>原版 {@code ParticleTypes.ENTITY_EFFECT} 其实已经能带颜色，只是它不覆盖
 * {@code getLightColor}：夜里发出去的那条线会随环境光一起暗下去，不像一束光
 */
public class PrismTraceParticle extends SpellParticle {

    protected PrismTraceParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    /** 贴图表在 {@code assets/dawnduskterminal/particles/particle_prism_trace.json} */
    public static class Provider implements ParticleProvider<ColorParticleOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                ColorParticleOption option,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            PrismTraceParticle particle = new PrismTraceParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
            particle.setColor(option.getRed(), option.getGreen(), option.getBlue());
            particle.setAlpha(option.getAlpha());
            return particle;
        }
    }
}
