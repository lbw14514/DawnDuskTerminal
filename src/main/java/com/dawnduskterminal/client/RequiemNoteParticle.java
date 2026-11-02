package com.dawnduskterminal.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 试验物品 095（镇魂曲）那一圈音符粒子
 *
 * <p>物理照抄原版音符粒子（{@code NoteParticle}）：摩擦 {@value #FRICTION}，{@code speedUpWhenYMotionIsBlocked}
 * 打开，初速之外再给 {@value #LIFT} 的上飘，寿命 {@value #LIFETIME} tick，尺寸乘 1.5，随寿命从 0 涨到满
 * ——于是它一边往外冲一边被阻力拉住、同时往上飘，就是原版音符被弹出来那副样子
 *
 * <p>与原版音符唯一的区别是初速：原版 {@code NoteParticle} 的构造函数把速度整个乘了 0.01，
 * 横向永远是 0，只能原地往上飘；这里直接把服务端给的初速装上，才能甩出一整圈。
 * 贴图仍是原版那张 {@code minecraft:note}（见 {@code particles/particle_requiem_note.json}）
 *
 * <p>颜色不进粒子包：色相在客户端现摇（每颗各自一个），三个通道照抄原版音符那条
 * 「正弦取色」的公式，所以看上去就是原版音符那一套饱和度，只是颜色随机
 *
 * <p>上飘分两段：{@link #LIFT} 是出生时那一下就有的向上初速（{@link #FRICTION} 会慢慢把它磨平），
 * 之后靠 {@link #RISE_PER_TICK} 每 tick 再往上托一点——只有前一段的话，音符冲到最高处就停住不动，
 * 看着像「弹上去」而不是「飘」
 */
public final class RequiemNoteParticle extends TextureSheetParticle {

    private static final float FRICTION = 0.9F;

    private static final float LIFT = 0.1F;

    private static final int LIFETIME = 40;
    /** 原版音符的尺寸倍率 */
    private static final float SIZE_MULTIPLIER = 2F;
    /** 每 tick 再往上托一点：稳态约 0.1 格每 tick，音符到最高处之后仍然在慢慢上升 */
    private static final float RISE_PER_TICK = 0.006F;
    /** 上托量本身也带随机，同一圈里的音符不会一同匀速上升 */
    private static final float RISE_JITTER = 0.010F;
    /** 寿命在 {@link #LIFETIME} 上下浮动这么多 tick，免得每一圈同时出生、同时消失 */
    private static final int LIFETIME_JITTER = 4;

    private RequiemNoteParticle(ClientLevel level, double x, double y, double z,
                                double velocityX, double velocityY, double velocityZ, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = velocityX;
        this.yd = velocityY + LIFT;
        this.zd = velocityZ;
        this.friction = FRICTION;
        this.speedUpWhenYMotionIsBlocked = true;
        this.quadSize *= SIZE_MULTIPLIER;
        this.lifetime = LIFETIME + this.random.nextInt(LIFETIME_JITTER * 2 + 1) - LIFETIME_JITTER;
        setHue(this.random.nextFloat());
        this.pickSprite(sprites);
    }

    /** 推进照旧交给 {@code Particle#tick}（移、{@code speedUpWhenYMotionIsBlocked}、摩擦），只是每 tick 先托一下 */
    @Override
    public void tick() {
        this.yd += RISE_PER_TICK + this.random.nextFloat() * RISE_JITTER;
        super.tick();
    }

    /** 原版音符的取色公式：把相位摊到三个通道上，取整圈正弦，再压到 0.35~1.0 */
    private void setHue(float hue) {
        this.rCol = Math.max(0.0F, Mth.sin(hue * Mth.TWO_PI) * 0.65F + 0.35F);
        this.gCol = Math.max(0.0F, Mth.sin((hue + 0.33333334F) * Mth.TWO_PI) * 0.65F + 0.35F);
        this.bCol = Math.max(0.0F, Mth.sin((hue + 0.6666667F) * Mth.TWO_PI) * 0.65F + 0.35F);
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, level, x, y, z, velocityX, velocityY, velocityZ) ->
            new RequiemNoteParticle(level, x, y, z, velocityX, velocityY, velocityZ, sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    /** 与原版音符一样从 0 涨到满，只是基数换成了这里的尺寸 */
    @Override
    public float getQuadSize(float partialTick) {
        return this.quadSize * Mth.clamp((this.age + partialTick) / (float)this.lifetime * 32.0F, 0.0F, 1.0F);
    }
}
