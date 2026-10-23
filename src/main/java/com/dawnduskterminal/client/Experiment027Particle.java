package com.dawnduskterminal.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 试验物品 027 的红石点：静止、原版红石粒子的一半大小、每个存在 60 秒
 *
 * <p>为什么不用原版的 {@code ParticleTypes.DUST}：原版红石粒子的寿命写死在
 * {@code DustParticleBase} 里（约 8~40 tick），尺寸还会随年龄收缩，而这里要的是 1200 tick 一动不动、大小不变的标记。
 * 贴图仍用原版红石粒子那组 {@code generic_*}（原版就是拿它们乘上颜色），颜色按原版
 * {@code DustParticle} 的算法乘成红石那个红，所以材质与观感就是原版红石粒子，只是活得久
 *
 * <p>大小：原版红石粒子的 quad 是默认随机尺寸 0.1..0.2 乘 0.75，再乘颜色选项里的 scale；
 * 这里在同一个基准上再乘 0.5，所以正好是原版的一半
 *
 * <p>静止：初速为 0、重力为 0、{@code hasPhysics} 为 false，父类的 tick 只会加年龄
 */
public final class Experiment027Particle extends TextureSheetParticle {
    /** 每个粒子 60 秒 = 1200 tick */
    public static final int LIFETIME = 20 * 60;

    /** 原版红石粒子的 0.75 基准，再乘 0.5 的一半大小 */
    public static final float SIZE_MULTIPLIER = 0.75F * 0.5F;

    private Experiment027Particle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        // 父类构造已经给了 0.1..0.2 的随机 quadSize，原版红石粒子就是在这个值上乘 0.75
        this.quadSize *= SIZE_MULTIPLIER;
        this.lifetime = LIFETIME;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        // 照原版 DustParticle 的着色：红色通道随机压暗一点，绿蓝为零
        float brightness = this.random.nextFloat() * 0.4F + 0.6F;
        this.rCol = (this.random.nextFloat() * 0.2F + 0.8F) * brightness;
        this.gCol = 0.0F;
        this.bCol = 0.0F;
        // 贴图表见 assets/dawnduskterminal/particles/particle_experiment_027.json
        this.setSprite(sprites.get(this.random));
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, level, x, y, z, vx, vy, vz) -> new Experiment027Particle(level, x, y, z, sprites);
    }

    /** 与原版红石粒子同一层：不透明面片，暗处也看得见 */
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }
}
