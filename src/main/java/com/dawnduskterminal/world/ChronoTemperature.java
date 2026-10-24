package com.dawnduskterminal.world;

import com.dawnduskterminal.config.DdtConfig;
import net.minecraft.util.Mth;

/**
 * 把温度改写成晨昏线 {@code param} 的分段函数，让群系按极昼 昼区 热带 温带 寒带 夜区 极夜七段分布
 *
 * <p>偏移点在 {@code PaletteBiomeSource#getNoiseBiome}：先照常算原版温度，再用 {@link #shift} 换成
 * {@code param} 的分段映射。放在群系源而不是密度函数图里，是因为区块落盘 结构判定 {@code locate biome}
 * 刷怪 雕刻最终都走同一个 {@code getNoiseBiome}，而 {@code RandomState} 与 {@code NoiseChunk} 完全不用改。
 * 曾经把包装塞进 {@code RandomState} 的 {@code router}，{@code NoiseChunk} 会为包装器再建插值层，
 * {@code locate structure} 直接慢到触发看门狗，这条路不要再走
 *
 * <p>{@code param} 是 {@link ChronoLine#param(double, double)}：方块坐标投影到线的法向上，除以
 * {@code maxDistance} 后钳到 {@code [-1, 1]}。六个断点 {@link #BREAKPOINTS} 把 {@code param} 切成七段，
 * 每段线性映射到原版气候表的一段温度（{@link #BAND_TEMPERATURE}），段与段的端点温度接得上，所以整体连续。
 * 于是每一带只对应原版的一档温度，热带的温度上界够不到针叶林那一档，寒带也够不到雪原那一档
 *
 * <p>叠在映射之上的是原版温度噪声乘 {@link #SPREAD}：它的作用只是让分带边界弯折，而不是决定分带。
 * 带内温度已经被映射钉死在一档里，噪声再大也只在档内晃，只有边界两侧各有约 {@code SPREAD} 宽的过渡
 *
 * <p>那条噪声太粗糙，撑不起交界：它来自 {@code noise/temperature.json} 的 {@code firstOctave -10}，主导
 * 分量波长四千格，边界是在几公里的尺度上慢慢弯，站在交界上看到的仍是一条直线。所以断点两侧再叠一层
 * {@link #seamDither}：三个八度的值噪声，波长是 {@link #SEAM_WIDTH} 的 1 / 0.3 / 0.1，幅度按到最近断点的
 * 距离加权（断点上满幅，离开 {@link #SEAM_WIDTH} 归零）。交界于是长成两侧群系互相咬进去的楔形与飞地，
 * 带心还是原来那一档
 *
 * <p>带宽与抖动波长都写成 {@code param} 的比例，跟七带的宽度一样，于是两者都跟着 {@code maxDistance}
 * 一起缩放：把世界缩成一半，交界也一起缩一半，不会相对变粗
 *
 * <p>极带边界（{@code ±0.8}）同样吃这层抖动，只是不加 {@code SPREAD} 那条：光滑的低频噪声一压就会把
 * 温度推离 {@code extra} 的 {@code ±1 ~ ±2} 跨度，而抖动的幅度本身够小，推不出那一行
 *
 * <p>{@code |param| >= 0.8} 的两个极带继续往外推：{@code param} 走到 {@code ±1} 时温度到
 * {@link #POLAR_TEMPERATURE}。多出来的这一段正好是原版气候表没覆盖的地盘，{@code PaletteBiomeSource} 的
 * {@code extra} 在那儿放了极昼极夜的群系，两边的断点必须对齐，否则极带群系永远选不到
 */
public final class ChronoTemperature {
    /** 极夜侧边界，再往外温度开始越过原版值域 */
    public static final double POLAR_NIGHT = -0.8D;
    /** 夜区与寒带的边界 */
    public static final double NIGHT_ZONE = -0.6D;
    /** 寒带与温带的边界 */
    public static final double COLD_ZONE = -0.3D;
    /** 温带与热带的边界 */
    public static final double TROPIC_ZONE = 0.3D;
    /** 热带与昼区的边界 */
    public static final double DAY_ZONE = 0.6D;
    /** 极昼侧边界，再往外温度开始越过原版值域 */
    public static final double POLAR_DAY = 0.8D;

    /**
     * 极带到线端的温度，{@code param} 被 {@link ChronoLine} 钳在 {@code ±1}，所以温度的上界就是这里
     *
     * <p>气候表的条款只收到 {@code ±1}（{@code Climate.Parameter.CODEC} 也只认 {@code -2 ~ 2}），
     * 超出去这一段正是自建极带群系的地盘，写成一整段而不再是原来的 {@code ±1} 常量
     */
    public static final double POLAR_TEMPERATURE = 2.0D;

    /** 分带断点，与 {@link #BAND_TEMPERATURE} 一一对应 */
    private static final double[] BREAKPOINTS = {
        POLAR_NIGHT, NIGHT_ZONE, COLD_ZONE, TROPIC_ZONE, DAY_ZONE, POLAR_DAY
    };

    /**
     * 断点处的温度，取自原版气候表的温度档边界
     *
     * <p>{@code -1.00 ~ -0.45} 冻结档（雪原 冰川），{@code -0.45 ~ -0.15} 寒带，{@code -0.15 ~ 0.20} 温带，
     * {@code 0.20 ~ 0.55} 热带，{@code 0.55 ~ 1.00} 热档（昼区）
     */
    private static final double[] BAND_TEMPERATURE = {
        -1.0D, -0.45D, -0.15D, 0.20D, 0.55D, 1.0D
    };

    /** 噪声在映射之后叠加的比例：只负责让分带边界弯折，不改变每一带对应的温度档 */
    public static final double SPREAD = 0.08D;

    /**
     * 交界抖动的半宽，单位是 {@code param}：断点处满幅，离开这么远就衰减到 0
     *
     * <p>与七带的宽度同一个尺度：夜区 昼区 只要 `0.2` 宽，这里取 `0.08`，两侧各吃掉这么多还剩带心。
     * `maxDistance` 默认 5000 时约合 400 格
     */
    public static final double SEAM_WIDTH = 0.08D;

    /** 交界抖动的幅度（温度）。越大楔子越深，但连同 {@link #SPREAD} 一起不能把带推过相邻那一档 */
    public static final double SEAM_DITHER = 0.09D;

    /**
     * 抖动三个八度的波长，单位是 {@code param}，也就是 {@code SEAM_WIDTH} 的 1 / 0.3 / 0.1：宽约的负责
     * 整体凹凸，窄的负责小飞地与毛边
     *
     * <p>写成比例而不是格数，是为了跟七带的宽度一起缩放：`maxDistance` 改成 2500 时，带宽减半，
     * 波长也减半（默认 5000 下是 400 / 120 / 40 格）
     */
    private static final double[] SEAM_WAVELENGTHS = {0.08D, 0.024D, 0.008D};

    /** 三个八度的权重，归一化用 {@link #SEAM_WEIGHT_SUM}，保证 {@link #seamDither} 不超出幅度 */
    private static final double[] SEAM_WEIGHTS = {1.0D, 0.5D, 0.25D};

    private static final double SEAM_WEIGHT_SUM = 1.75D;

    private ChronoTemperature() {}

    /**
     * 把原版温度换成晨昏线的分段温度
     *
     * <p>这是整个偏移唯一的落点：所有采样器给出的原版温度都从这里过一遍，结构判定与区块落盘因此不可能
     * 再判出不同群系。纯函数，只依赖坐标 线 配置与传入的原版温度
     *
     * @param vanillaTemperature 采样器算出的原版温度
     * @param blockX 方块坐标 X（与 {@code Climate.Sampler#sample} 一致，不是 quart 坐标）
     * @param blockZ 方块坐标 Z
     */
    public static double shift(double vanillaTemperature, int blockX, int blockZ) {
        double param = ChronoLineState.serverLine().param(blockX, blockZ);
        double band = bandTemperature(param);
        // 极带不叠温度噪声：噪声一压就可能掉回原版那一行，极端侧还会推出 extra 的 ±1 ~ ±2 跨度，
        // 极带边界会变成一条毛边。交界抖动另算，它幅度小，推不出那一行
        double jitter = band <= -1.0D || band >= 1.0D ? 0.0D : vanillaTemperature * SPREAD;
        return band + jitter + seamDither(param, blockX, blockZ);
    }

    /**
     * {@code param} 到温度的纯函数，不带噪声
     *
     * <p>抽出来是为了能在游戏测试里直接断言断点：温度档一旦错位，某个带里就会冒出别的带的群系
     */
    public static double bandTemperature(double param) {
        // 极带继续按同样的斜率往外推，跨过 ±1 就进了 extra 那几条的地盘
        if (param >= POLAR_DAY) {
            double ratio = Mth.clamp((param - POLAR_DAY) / (1.0D - POLAR_DAY), 0.0D, 1.0D);
            return Mth.lerp(ratio, BAND_TEMPERATURE[BAND_TEMPERATURE.length - 1], POLAR_TEMPERATURE);
        }
        if (param <= POLAR_NIGHT) {
            double ratio = Mth.clamp((POLAR_NIGHT - param) / (POLAR_NIGHT + 1.0D), 0.0D, 1.0D);
            return Mth.lerp(ratio, BAND_TEMPERATURE[0], -POLAR_TEMPERATURE);
        }
        // 中间五段用比较直接落段，算式与逐段循环完全一致（断点取下一段，ratio 为 0）
        if (param < NIGHT_ZONE) {
            return lerpSegment(0, param);
        }
        if (param < COLD_ZONE) {
            return lerpSegment(1, param);
        }
        if (param < TROPIC_ZONE) {
            return lerpSegment(2, param);
        }
        if (param < DAY_ZONE) {
            return lerpSegment(3, param);
        }
        return lerpSegment(4, param);
    }

    private static double lerpSegment(int index, double param) {
        double ratio = (param - BREAKPOINTS[index]) / (BREAKPOINTS[index + 1] - BREAKPOINTS[index]);
        return Mth.lerp(ratio, BAND_TEMPERATURE[index], BAND_TEMPERATURE[index + 1]);
    }

    /**
     * 交界抖动在 {@code param} 上的权重：断点处 1，离开 {@link #SEAM_WIDTH} 后 0，中间用 smoothstep 过渡
     *
     * <p>权重让带心保持干净：只有断点两侧这么宽的一条吃抖动，带中间的温度分布与只叠 {@link #SPREAD}
     * 时一模一样
     */
    public static double seamWeight(double param) {
        double t = nearestBreakpointDistance(param) / SEAM_WIDTH;
        if (t >= 1.0D) {
            return 0.0D;
        }
        return 1.0D - t * t * (3.0D - 2.0D * t);
    }

    /**
     * 到最近断点的距离，单位是 {@code param}
     *
     * <p>落在哪一段就只看那一段的两个端点，和遍历全部六个断点取最小值的结果一样，少四次绝对值
     */
    private static double nearestBreakpointDistance(double param) {
        if (param <= POLAR_NIGHT) {
            return POLAR_NIGHT - param;
        }
        if (param >= POLAR_DAY) {
            return param - POLAR_DAY;
        }
        if (param < NIGHT_ZONE) {
            return Math.min(param - POLAR_NIGHT, NIGHT_ZONE - param);
        }
        if (param < COLD_ZONE) {
            return Math.min(param - NIGHT_ZONE, COLD_ZONE - param);
        }
        if (param < TROPIC_ZONE) {
            return Math.min(param - COLD_ZONE, TROPIC_ZONE - param);
        }
        if (param < DAY_ZONE) {
            return Math.min(param - TROPIC_ZONE, DAY_ZONE - param);
        }
        return Math.min(param - DAY_ZONE, POLAR_DAY - param);
    }

    /**
     * 交界处的温度抖动，落在 {@code [-SEAM_DITHER, SEAM_DITHER]}，带心（{@link #seamWeight} 为 0）为 0
     *
     * <p>纯函数：只依赖方块坐标，不读注册表也不读配置，所以跨区块、跨线程、重开存档都是同一张图。
     * 值噪声在十倍于波长以上的尺度也能保持连续，因此丢到气候采样里不会在林子里切出格子状断层
     */
    public static double seamDither(double param, double blockX, double blockZ) {
        return seamDither(param, blockX, blockZ, Math.max(1.0D, DdtConfig.maxDistance()));
    }

    private static double seamDither(double param, double blockX, double blockZ, double scale) {
        double weight = seamWeight(param);
        if (weight == 0.0D) {
            return 0.0D;
        }
        // 波长是 param 的比例，所以先把坐标除回 param 的尺度。除数与 ChronoLine.param 用的那个一致
        double nx = blockX / scale;
        double nz = blockZ / scale;
        double noise = 0.0D;
        for (int i = 0; i < SEAM_WAVELENGTHS.length; i++) {
            noise += SEAM_WEIGHTS[i] * valueNoise(nx, nz, SEAM_WAVELENGTHS[i], i);
        }
        return weight * noise / SEAM_WEIGHT_SUM * SEAM_DITHER;
    }

    /** 平滑插值的值噪声，值域 {@code [-1, 1)} */
    private static double valueNoise(double x, double z, double wavelength, long salt) {
        double fx = x / wavelength;
        double fz = z / wavelength;
        long x0 = (long) Math.floor(fx);
        long z0 = (long) Math.floor(fz);
        double tx = fx - x0;
        double tz = fz - z0;
        // smoothstep，而不是线性插值：线性插值的格线会在交界上留下能看出来的十字
        double sx = tx * tx * (3.0D - 2.0D * tx);
        double sz = tz * tz * (3.0D - 2.0D * tz);
        return Mth.lerp(sz,
            Mth.lerp(sx, lattice(x0, z0, salt), lattice(x0 + 1L, z0, salt)),
            Mth.lerp(sx, lattice(x0, z0 + 1L, salt), lattice(x0 + 1L, z0 + 1L, salt)));
    }

    /** 格点上的伪随机值，落在 {@code [-1, 1)}；变异来自 SplitMix64 的收尾三步 */
    private static double lattice(long x, long z, long salt) {
        long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL + salt;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53 * 2.0D - 1.0D;
    }
}
