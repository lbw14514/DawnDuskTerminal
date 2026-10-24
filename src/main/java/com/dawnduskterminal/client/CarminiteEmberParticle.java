package com.dawnduskterminal.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 砷铅铁余烬：{@code carminite_fire} 向上飘的那点火星，也是试验物品 052 碎片拖着的红线
 *
 * <p>贴图用原版红石粒子那组 {@code minecraft:generic_0..7}（见
 * {@code particles/particle_carminite_ember.json}），颜色按原版 {@code DustParticle} 的算法乘成红石红。
 *
 * <p>与 027 那个红石点、以及原版红石粒子的差别只有两条：
 * <ul>
 *   <li>尺寸是原版红石粒子的 1.5 倍（同一个 {@code 0.75} 基准上乘 1.5），全程不变
 *   <li>恒满亮（{@code getLightColor} 写死满值 + {@link ParticleRenderType#PARTICLE_SHEET_LIT}），暗处也看得见
 * </ul>
 *
 * <p>序列帧：八张贴图按年龄依次播完，用 {@code SpriteSet#get(age, lifetime)} 在整条寿命上均匀铺开，
 * 与 {@code client/CarminiteParticle} 换帧是同一个思路
 *
 * <p>初速不能走 {@code Particle(level,x,y,z,vx,vy,vz)} 那个构造：它会再加一个 ±0.4 的随机速度并把总速度
 * 归一化到 0.4 上下（见 {@code Particle} 的那个构造），横向一下就飞出去了。这里走不带宽度的构造，
 * 三个分量由调用方给什么就是什么，再加一个负重力让它自己往上飘
 */
public final class CarminiteEmberParticle extends TextureSheetParticle {
    /** 原版红石粒子的 0.75 基准，再乘 1.5 = 比原版大一半 */
    public static final float SIZE_MULTIPLIER = 0.75F * 1.5F;

    /** 寿命 12~20 tick，够把八帧播一遍 */
    private static final int MIN_LIFETIME = 12;
    private static final int MAX_LIFETIME = 20;

    /** 负重力：每 tick 往上抬 */
    private static final float RISE = -0.004F;

    /** 横向阻尼，让「散开」是慢慢散而不是横着飞 */
    private static final float HORIZONTAL_FRICTION = 0.94F;

    /** 贴图表里的帧数 */
    private static final int FRAMES = 8;

    private final SpriteSet sprites;

    private CarminiteEmberParticle(ClientLevel level, double x, double y, double z,
                                   double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        // 父类构造已经给了 0.1..0.2 的随机 quadSize，原版红石粒子就是在这个值上乘 0.75
        this.quadSize *= SIZE_MULTIPLIER;
        this.lifetime = MIN_LIFETIME + this.random.nextInt(MAX_LIFETIME - MIN_LIFETIME + 1);
        // 不带宽度的构造不会塞随机速度，三个分量照调用方给的来
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = RISE;
        this.friction = HORIZONTAL_FRICTION;
        this.hasPhysics = false;
        // 照原版 DustParticle 的着色：红色通道随机压暗一点，绿蓝为零
        float brightness = this.random.nextFloat() * 0.4F + 0.6F;
        this.rCol = (this.random.nextFloat() * 0.2F + 0.8F) * brightness;
        this.gCol = 0.0F;
        this.bCol = 0.0F;
        // 八帧序列：at age 0 从第一帧开始，走完整条寿命正好播完
        this.setSprite(this.sprites.get(0, FRAMES));
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, level, x, y, z, vx, vy, vz) ->
            new CarminiteEmberParticle(level, x, y, z, vx, vy, vz, sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSprite(this.sprites.get(this.age, this.lifetime));
        }
    }

    /** 恒满亮 */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
    }
}
