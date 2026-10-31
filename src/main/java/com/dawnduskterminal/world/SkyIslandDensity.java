package com.dawnduskterminal.world;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

/**
 * 空岛的唯一判据：这个格点的实心来自 {@code sky_island_density} 而不是地形噪声
 *
 * <p>{@code sloped_cheese} 是 {@code max(base_3d_noise, sky_island_density)}。两项都只由坐标决定：
 * {@code base_3d_noise} 就是 {@code minecraft:old_blended_noise}，{@code sky_island_density} 是两段
 * {@code y_clamped_gradient} 加一个同款噪声。两者都是 {@code DensityFunction.SimpleFunction}，不需要
 * {@code NoiseChunk} 插值，所以在 {@code BIOMES} 阶段（地形还没生成）也能求值
 *
 * <p>于是群系 云 水洼 河道共用这一条判据：{@code sky > 0 && sky > base}。山体在 140~196 与空岛带重叠
 * 的那一段是靠 {@code base > sky} 排除的，不需要额外的间隙扫描
 *
 * <p>函数实例从 {@code RandomState} 已经换过种子的函数树里抓，见 {@code SkyIslandDensityMixin}。抓的是
 * {@link SkyIslandMarker} 包着的那两项：{@code RandomState} 会把 {@code HolderHolder} 换成直接持有者，
 * 引用键随之消失，只能认类型
 */
public final class SkyIslandDensity {
    public static final ResourceLocation SKY_ISLAND = DawnDuskTerminal.id("sky_island_density");
    public static final ResourceLocation BASE_3D_NOISE = DawnDuskTerminal.id("overworld/base_3d_noise");

    private static final ThreadLocal<Cursor> CURSOR = ThreadLocal.withInitial(Cursor::new);

    private static volatile DensityFunction sky;
    private static volatile DensityFunction base;
    private static volatile RandomState resolvedFor;

    private SkyIslandDensity() {}

    /**
     * 从换过种子的函数树里把两项摘出来，按 {@code RandomState} 幂等
     *
     * <p>{@code mapAll} 会重建整棵树，所以每个维度的 {@code RandomState} 只做一次
     */
    public static void remember(RandomState randomState, NoiseGeneratorSettings settings) {
        if (resolvedFor == randomState) {
            return;
        }
        resolvedFor = randomState;
        DensityFunction[] found = new DensityFunction[2];
        randomState.router().finalDensity().mapAll(node -> {
            if (node instanceof SkyIslandMarker marker) {
                if (marker.wrapped() instanceof BlendedNoise) {
                    found[1] = marker;
                } else {
                    found[0] = marker;
                }
            }
            return node;
        });
        if (found[0] == null || found[1] == null) {
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal could not find both island density markers, sky islands will not be recognised");
            return;
        }
        base = found[1];
        sky = found[0];
    }

    /** 已经抓到函数实例；抓不到时所有判据都返回 false，退化成"没有空岛" */
    public static boolean ready() {
        return sky != null && base != null;
    }

    public static boolean isIsland(int x, int y, int z) {
        DensityFunction skyFunction = sky;
        DensityFunction baseFunction = base;
        if (skyFunction == null || baseFunction == null) {
            return false;
        }
        Cursor cursor = CURSOR.get();
        cursor.set(x, y, z);
        double skyValue = skyFunction.compute(cursor);
        return skyValue > 0.0D && skyValue > baseFunction.compute(cursor);
    }

    /** 只给调参用：{@code sky_island_density} 的原始值 */
    public static double skyValue(int x, int y, int z) {
        DensityFunction function = sky;
        if (function == null) {
            return Double.NaN;
        }
        Cursor cursor = CURSOR.get();
        cursor.set(x, y, z);
        return function.compute(cursor);
    }

    /**
     * 只给 GameTest 用：换掉静态槽位，让判据不依赖真实维度
     *
     * @return 上一次的实例，调用方负责还原
     */
    public static Snapshot swap(DensityFunction skyFunction, DensityFunction baseFunction) {
        Snapshot previous = new Snapshot(sky, base, resolvedFor);
        sky = skyFunction;
        base = baseFunction;
        resolvedFor = null;
        return previous;
    }

    public static void restore(Snapshot snapshot) {
        sky = snapshot.sky();
        base = snapshot.base();
        resolvedFor = snapshot.resolvedFor();
    }

    public record Snapshot(DensityFunction sky, DensityFunction base, RandomState resolvedFor) {}

    /** 避免每个格点都 new 一个 {@code SinglePointContext} */
    private static final class Cursor implements DensityFunction.FunctionContext {
        private int x;
        private int y;
        private int z;

        void set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int blockX() {
            return this.x;
        }

        @Override
        public int blockY() {
            return this.y;
        }

        @Override
        public int blockZ() {
            return this.z;
        }
    }
}
