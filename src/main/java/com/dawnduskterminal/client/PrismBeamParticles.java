package com.dawnduskterminal.client;

import com.dawnduskterminal.network.PrismBeamPayload;
import com.dawnduskterminal.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 棱镜权杖那一发的全部粒子：服务端只发十来个点（{@link PrismBeamPayload}），线段上的光点与终点的尘团
 * 都在这里本地生成
 *
 * <p>线段上的光点照暮色森林黄昏权杖的拖尾：那一支每 tick 沿弹体撒 5 颗，一颗的颜色是
 * 红 {@code (0.5..1) × 0.17}、绿 {@code × 0.80}、蓝 {@code × 0.69}，也就是偏青绿的一串。这里按
 * 每格 {@value #PER_BLOCK} 颗铺满整条线段，每颗再抖 {@value #JITTER} 格，位置与颜色都由客户端现算——
 * 同一发在所有客户端上端点一致，具体到每颗的抖动各不相同，正好是拖尾的毛边。初速给 0：
 * {@code SpellParticle} 自己会以 0.96 的摩擦慢慢飘、按 8~40 tick 淡出，给它别的初速反而会把线吹散
 *
 * <p>终点那团尘是「重锤粒子」：原版重锤砸地走 {@code levelEvent(2013)}，客户端
 * {@code ParticleUtils#spawnSmashAttackParticles} 撒的是带方块状态的 {@code ParticleTypes.DUST_PILLAR}，
 * 也就是被砸那块方块碎成的一团尘，这里照搬同一个粒子与同样「高斯抖动、无规律飞散」的撒法
 */
public final class PrismBeamParticles {
    /** 每格线上铺几颗 */
    public static final double PER_BLOCK = 4.0D;

    /** 一条射线的颗数上限：主射线 64 格 × 4 已经 256 颗，再多只是白烧 CPU */
    private static final int MAX_PARTICLES_PER_RAY = 128;

    /** 每颗相对线段的抖动半径 */
    public static final double JITTER = 0.15D;

    /** 终点那团尘的颗数：重锤砸地是 250 颗、范围 750 的一圈，这里打的是单点，40 颗就够 */
    public static final int IMPACT_PARTICLES = 40;

    /** 终点尘团的高斯抖动半径：原版是 {@code gaussian / 2}，这里同量级 */
    private static final double IMPACT_SPREAD = 0.2D;

    /** 颜色系数，与 {@code TwilightWandBolt#makeTrail} 逐字一致 */
    private static final float RED_SCALE = 0.17F;

    private static final float GREEN_SCALE = 0.80F;

    private static final float BLUE_SCALE = 0.69F;

    private PrismBeamParticles() {}

    /** 收到一发：主射线、主射线终点，然后每条分射线（分射线都从主射线终点出发）各一段 */
    public static void spawn(PrismBeamPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 origin = payload.origin();
        Vec3 mainEnd = payload.main().end();
        spawnRay(level, origin, mainEnd);
        spawnImpact(level, payload.main().blockStateId(), mainEnd);
        for (PrismBeamPayload.Ray ray : payload.splits()) {
            spawnRay(level, mainEnd, ray.end());
            spawnImpact(level, ray.blockStateId(), ray.end());
        }
    }

    private static void spawnRay(ClientLevel level, Vec3 start, Vec3 end) {
        double length = start.distanceTo(end);
        if (length < 1.0E-4D) {
            return;
        }
        int count = Mth.clamp((int) Math.ceil(length * PER_BLOCK), 1, MAX_PARTICLES_PER_RAY);
        Vec3 direction = end.subtract(start).scale(1.0D / length);
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            double along = length * (i + random.nextDouble()) / count;
            level.addParticle(
                    randomColor(random),
                    start.x + direction.x * along + jitter(random),
                    start.y + direction.y * along + jitter(random),
                    start.z + direction.z * along + jitter(random),
                    0.0D,
                    0.0D,
                    0.0D);
        }
    }

    /** 终点那团尘：方块状态 0（空气）说明服务端也没找到可用的贴图，那就不画 */
    private static void spawnImpact(ClientLevel level, int blockStateId, Vec3 pos) {
        BlockState state = Block.stateById(blockStateId);
        if (state.isAir()) {
            return;
        }
        BlockParticleOption option = new BlockParticleOption(ParticleTypes.DUST_PILLAR, state);
        RandomSource random = level.getRandom();
        for (int i = 0; i < IMPACT_PARTICLES; i++) {
            level.addParticle(
                    option,
                    pos.x + random.nextGaussian() * IMPACT_SPREAD,
                    pos.y + random.nextGaussian() * IMPACT_SPREAD,
                    pos.z + random.nextGaussian() * IMPACT_SPREAD,
                    random.nextGaussian() * 0.05D,
                    random.nextGaussian() * 0.05D,
                    random.nextGaussian() * 0.05D);
        }
    }

    private static double jitter(RandomSource random) {
        return (random.nextDouble() - random.nextDouble()) * JITTER;
    }

    private static ColorParticleOption randomColor(RandomSource random) {
        return ColorParticleOption.create(
                ModParticles.PRISM_TRACE.get(),
                ((random.nextFloat() * 0.5F) + 0.5F) * RED_SCALE,
                ((random.nextFloat() * 0.5F) + 0.5F) * GREEN_SCALE,
                ((random.nextFloat() * 0.5F) + 0.5F) * BLUE_SCALE);
    }
}
