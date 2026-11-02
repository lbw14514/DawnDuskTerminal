package com.dawnduskterminal.client.animation;

import org.lwjgl.stb.STBPerlin;

/**
 * {@code ysm.perlin_noise} 的实现
 *
 * <p>YSM 一侧的调用是 {@code STBPerlin.stb_perlin_noise3_seed(x, y, z, 0, 0, 0, seed)}（见 OpenYSM 的
 * {@code BonePivotAbs} 同族实现与它的算法包）。直接借用 lwjgl 的 {@code org.lwjgl.stb}，因此这不是
 * 「仿一个噪声」，而是同一个算法，发丝、触手与弦的波形因而能对上。
 *
 * <p>三个坐标都要透传：源动画把 {@code query.life_time} 分别放在 x、y、z 上取噪声，
 * 只用单轴复现不出这种错位。
 */
public final class YsmNoise {
    private YsmNoise() {}

    public static double perlin(int seed, double x, double y, double z) {
        return STBPerlin.stb_perlin_noise3_seed((float) x, (float) y, (float) z, 0, 0, 0, seed);
    }
}
