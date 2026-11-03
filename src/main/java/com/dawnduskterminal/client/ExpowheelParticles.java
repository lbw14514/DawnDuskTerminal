package com.dawnduskterminal.client;

import com.dawnduskterminal.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * 博览轮舞那片飞轮寿命到点那一圈散开的砷铅铁反应场粒子（{@code particle_carminite_effect}）
 *
 * <p>与 {@code CarminiteFieldEntity#spawnRing} 同一条写法：一圈上均匀摆 {@value #RING_POINTS} 颗，
 * 每颗带一股向外的速度；再把弹体**当时的速度**整份加上去，于是整个环顺着飞行方向甩出去，
 * 而不是原地炸成一朵花。
 *
 * <p>触发方是服务端（{@code network/ExpowheelBurstPayload}）：弹体一秒多就飞出客户端跟踪范围，
 * 客户端根本活不到它 6 秒的寿命到点，所以那件事只能由服务端说。
 */
public final class ExpowheelParticles {
    private static final int RING_POINTS = 12;

    /** 环的半径，与弹体 2 格宽的判定箱同尺寸 */
    private static final double RING_RADIUS = 1.0D;

    /** 每颗向外的分速度：环主要靠自己往外撑开 */
    private static final double RING_SPREAD = 0.6D;

    private static final double CARRIED_FACTOR = 1D;

    private ExpowheelParticles() {}

    /** 在 {@code (x, y, z)} 铺一圈，速度 = 弹体速度的 {@value #CARRIED_FACTOR} 倍 + 向外的分速度 */
    public static void spawnBurst(double x, double y, double z, double vx, double vy, double vz) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        double carriedX = vx * CARRIED_FACTOR;
        double carriedY = vy * CARRIED_FACTOR;
        double carriedZ = vz * CARRIED_FACTOR;
        for (int index = 0; index < RING_POINTS; index++) {
            double angle = Mth.TWO_PI * index / RING_POINTS;
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);
            level.addParticle(
                    ModParticles.CARMINITE_RING.get(),
                    x + dirX * RING_RADIUS,
                    y,
                    z + dirZ * RING_RADIUS,
                    carriedX + dirX * RING_SPREAD,
                    carriedY,
                    carriedZ + dirZ * RING_SPREAD);
        }
    }
}
