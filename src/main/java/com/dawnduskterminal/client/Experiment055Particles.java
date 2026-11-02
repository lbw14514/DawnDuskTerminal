package com.dawnduskterminal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 试验物品 055 那圈外泄的 enchant 粒子：服务端每 tick 只发一条射线的**参数**，粒子在这里本地生成
 *
 * <p>为什么这么切：一颗一包地用 {@code sendParticles} 打放射线，每 tick、每个施放者要发几十上百个包；
 * 而射线本身只有「方向 + 长度」两个量，服务端每 tick 发一个
 * {@code Experiment055RayPayload} 就够，粒子由各客户端自己沿这条线铺开。包量从上百降到 1，
 * 而且所有客户端看到的是同一条射线（方向是服务端摇的）
 *
 * <p>密度按长度算：每格 {@value #PER_BLOCK} 颗，封顶 {@value #MAX_PARTICLES_PER_RAY} 颗。
 * 影响半径是从 0 涨到 24 的变量，固定颗数在半径一大时就稀得看不见
 *
 * <p>速度一律给 0：原版 {@code ParticleTypes.ENCHANT}（{@code FlyTowardsPositionParticle}）自己就会在
 * 30~40 tick 里一边淡出、一边按 {@code -f1 × 1.2} 慢慢往下飘，位置给在射线哪一点它就在哪里飘下去。
 * 千万不要把「射线上那点相对使用者的位移」当速度塞进去——那正好是它的「飞向目标点」用法，
 * 粒子会全体朝使用者汇拢，看起来像被吸回去，不是这里要的东西
 */
public final class Experiment055Particles {
    /** 每格射线上铺几颗：一格长度至少要有好几颗，否则半径一大就只剩一根虚线 */
    public static final double PER_BLOCK = 4.0D;
    /** 一条射线的颗数上限：满充能时 36 格 × 4 = 144 颗，再多就只是白烧 CPU */
    private static final int MAX_PARTICLES_PER_RAY = 144;

    private Experiment055Particles() {}

    /** 收到服务端那条射线（方向是单位向量，长度已按 1.5 × 影响半径算好） */
    public static void spawn(int casterId, double directionX, double directionY, double directionZ, double length) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Entity caster = level.getEntity(casterId);
        if (caster == null || length <= 0.0D) {
            return;
        }
        double norm = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
        if (norm < 1.0E-4D) {
            return;
        }
        Vec3 direction = new Vec3(directionX / norm, directionY / norm, directionZ / norm);
        Vec3 centre = caster.position().add(0.0D, caster.getBbHeight() * 0.5D, 0.0D);
        int count = Mth.clamp((int) Math.ceil(length * PER_BLOCK), 1, MAX_PARTICLES_PER_RAY);
        for (int i = 0; i < count; i++) {
            // 沿射线均匀铺开，每颗再抖半格间距，免得排成一条等距的点线；速度 0，剩下的交给原版粒子自己飘
            double along = length * (i + level.getRandom().nextDouble()) / count;
            level.addParticle(
                    ParticleTypes.ENCHANT,
                    centre.x + direction.x * along,
                    centre.y + direction.y * along,
                    centre.z + direction.z * along,
                    0.0D,
                    0.0D,
                    0.0D);
        }
    }
}
