package com.dawnduskterminal.client;

import com.dawnduskterminal.particle.JetFlameParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.phys.Vec3;

/**
 * 试验物品 043 的大火焰粒子
 *
 * <p>尺寸是原版火焰粒子的五倍（{@code SIZE_MULTIPLIER}），随寿命从满尺寸收缩到一半，
 * 寿命按「8 秒 ÷ (0.2~1.0) + 4」现摇（大约 12~44 tick），速度每 tick 衰减 0.96，看着是一团喷出去再收住的火。
 * 初速来自 {@link com.dawnduskterminal.particle.JetFlameParticleOptions}：原版粒子包只能给客户端现摇随机速度，
 * 想要「同一拍、同一条线」就必须把方向塞进粒子选项里一起发，这是 043 另开一套粒子类型的原因
 */

public final class JetFlameParticle extends TextureSheetParticle {
    private static final float SIZE_MULTIPLIER = 5.0F;
    private final float flameScale;

    private JetFlameParticle(ClientLevel level, double x, double y, double z, Vec3 velocity, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = this.xd * 0.01 + velocity.x;
        this.yd = this.yd * 0.01 + velocity.y;
        this.zd = this.zd * 0.01 + velocity.z;
        this.quadSize *= 5.0F;
        this.flameScale = this.quadSize;
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.lifetime = (int)(8.0 / (this.random.nextDouble() * 0.8 + 0.2)) + 4;
        this.hasPhysics = false;
        this.setSprite(sprites.get(this.random));
    }

    public static ParticleProvider<JetFlameParticleOptions> provider(SpriteSet sprites) {
        return (options, level, x, y, z, vx, vy, vz) -> new JetFlameParticle(level, x, y, z, options.velocity(), sprites);
    }

    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public float getQuadSize(float partialTick) {
        float t = ((float)this.age + partialTick) / (float)this.lifetime;
        return this.flameScale * (1.0F - t * t * 0.5F);
    }

    public int getLightColor(float partialTick) {
        float t = ((float)this.age + partialTick) / (float)this.lifetime;
        if (t < 0.0F) {
            t = 0.0F;
        }

        if (t > 1.0F) {
            t = 1.0F;
        }

        int packed = super.getLightColor(partialTick);
        int block = packed & 0xFF;
        int sky = packed >> 16 & 0xFF;
        block += (int)(t * 15.0F * 16.0F);
        if (block > 240) {
            block = 240;
        }

        return block | sky << 16;
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (++this.age >= this.lifetime) {
            this.remove();
        }

        this.yd += 0.004;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.96;
        this.yd *= 0.96;
        this.zd *= 0.96;
        if (this.onGround) {
            this.xd *= 0.7;
            this.zd *= 0.7;
        }
    }
}
