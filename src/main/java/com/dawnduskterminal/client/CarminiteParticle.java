package com.dawnduskterminal.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 砷铅铁三种粒子共用的实现
 *
 * <p>数值逐项照抄源 mod 的三个 provider：它们只差尺寸 寿命 重力 换帧节奏 自转，所以共用一支。
 * 帧序由 {@code particles/particle_carminite_*.json} 的贴图顺序决定，换帧节奏写在下面每个
 * provider 里
 *
 * <p>满亮坐标加 {@link ParticleRenderType#PARTICLE_SHEET_LIT}，和源 mod 一致。原版粒子着色器
 * 会丢弃全透明像素，所以贴图边缘不会画成黑框
 *
 * <p>尺寸照抄源 mod 的写法：{@code setSize(1,1)} 设的是碰撞箱（这里 hasPhysics 为 false，用不上），
 * 真正的 quadSize 是默认随机值 0.1..0.2 再乘倍率，所以弹体实际是 0.9..1.8 格，不是 9 格
 */
public final class CarminiteParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final int frames;
    private final int ticksPerFrame;
    private final float spinSpeed;

    private CarminiteParticle(ClientLevel level, double x, double y, double z,
                              double vx, double vy, double vz, SpriteSet sprites,
                              float sizeMultiplier, int lifetime, float gravity,
                              int frames, int ticksPerFrame, float spinSpeed) {
        super(level, x, y, z);
        this.sprites = sprites;
        // 父类的字段初始化已经给了 0.1..0.2 的随机值，源 mod 就是在这个值上乘倍率
        this.quadSize *= sizeMultiplier;
        this.lifetime = lifetime;
        this.gravity = gravity;
        this.hasPhysics = false;
        this.frames = frames;
        this.ticksPerFrame = ticksPerFrame;
        this.spinSpeed = spinSpeed;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.setSprite(this.sprites.get(this.random));
    }

    /** 飞行中的弹体，本身不动，只在原地转 */
    public static ParticleProvider<SimpleParticleType> ballProvider(SpriteSet sprites) {
        return (type, level, x, y, z, vx, vy, vz) ->
            new CarminiteParticle(level, x, y, z, 0.0D, 0.0D, 0.0D, sprites,
                9.0F, 16, 0.0F, 8, 2, 0.3F);
    }

    /** 拖尾，负重力让它慢慢上飘，4 帧循环 79 tick */
    public static ParticleProvider<SimpleParticleType> traceProvider(SpriteSet sprites) {
        return (type, level, x, y, z, vx, vy, vz) ->
            new CarminiteParticle(level, x, y, z, vx, vy, vz, sprites,
                3.0F, 79, -0.005F, 4, 20, 0.0F);
    }

    /** 反应场，继承传入的向外速度，15 tick 的 8 帧淡出（帧间隔 2，照源 mod 的节奏，别动） */
    public static ParticleProvider<SimpleParticleType> effectProvider(SpriteSet sprites) {
        return (type, level, x, y, z, vx, vy, vz) ->
            new CarminiteParticle(level, x, y, z, vx, vy, vz, sprites,
                6.0F, 15, -0.05F, 8, 2, 0.3F);
    }

    /**
     * 爆炸专用：同一套 8 帧贴图，帧间隔 3 tick（比 {@link #effectProvider} 慢一拍），寿命也拉长到 20 tick
     *
     * <p>爆炸那一圈粒子铺在球形范围里往外散，帧走得慢一点、活得久一点才看得清形状；
     * 反应场那圈保持原样，两边各用各的类型
     */
    public static ParticleProvider<SimpleParticleType> blastProvider(SpriteSet sprites) {
        return (type, level, x, y, z, vx, vy, vz) ->
            new CarminiteParticle(level, x, y, z, vx, vy, vz, sprites,
                6.0F, 20, -0.03F, 8, 3, 0.3F);
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += this.spinSpeed;
        if (!this.removed) {
            // +1 是照抄源 mod：SpriteSet.get(index, max) 会把 index 按比例映射到贴图表上
            this.setSprite(this.sprites.get(this.age / this.ticksPerFrame % this.frames + 1, this.frames));
        }
    }

    /** 恒满亮，暗处也看得见 */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
    }
}
