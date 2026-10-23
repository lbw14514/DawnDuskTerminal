package com.dawnduskterminal.entity;

import org.lwjgl.stb.STBPerlin;

/**
 * 高塔恶魂的两支有状态滤波器
 *
 * <h2>为什么它们不能由 GeckoLib 求值</h2>
 *
 * YSM 的动画片段里写的 {@code ysm.first_order(name, input, response)} 与
 * {@code ysm.second_order(name, input, frequency, coefficient, response)} 是<strong>按 name 记忆的递归滤波器</strong>，
 * 每 tick 积分一次；{@code ysm.bone_rot(name)} 读的是<strong>上一帧</strong>的骨骼变换。
 * GeckoLib 的表达式每条都无状态求值，存不下这个状态
 *
 * <h2>为什么也不能离线烘焙</h2>
 *
 * 滤波器的输入是活的运行时量：{@code ysm.head_yaw}、{@code q.ground_speed}、{@code q.vertical_speed}、
 * {@code q.yaw_speed}，以及上一帧的骨骼旋转。{@code parallel0} 与 {@code pre_parallel0} 这两层更是
 * <strong>连 {@code animation_length} 都没有</strong>，也就是不存在可以烘焙的关键帧轴 ——
 * 它们是逐帧函数，不是时间轴
 *
 * 所以唯一忠实的做法是把状态放在实体的 tick 里自己积分，再把结果写回骨骼
 *
 * <h2>数值来自哪里</h2>
 *
 * 公式取自 OpenYSM（MIT，Yes Steve Model 的开放重实现）的参考实现，与
 * {@code tools/ysm/physics_reference.py} 逐字对应。那份 Python 是量度近似误差用的，
 * 这里是真正跑在游戏里的那一份，{@code TowerGhastPhysicsTests} 把两者的阶跃响应钉成同一个表
 */
public final class TowerGhastPhysics {
    /** 20 fps，与 YSM 每 tick 积分一次一致 */
    public static final double DT = 1.0 / 20.0;

    private TowerGhastPhysics() {}

    /**
     * 一阶滤波：{@code last = (1 - dt/response) * last + (dt/response) * input}
     *
     * @param response 响应时间，单位秒；越大越迟钝
     */
    public static double firstOrderStep(double input, double response, double previous) {
        return (1.0 - DT / response) * previous + (DT / response) * input;
    }

    /** 二阶滤波的跨帧状态，除当前值外还要记住速度与上一帧输入 */
    public record SecondOrderState(double value, double velocity, double previousInput) {
        public static final SecondOrderState ZERO = new SecondOrderState(0.0, 0.0, 0.0);
    }

    /**
     * 二阶滤波的参数只由频率 阻尼 响应时间决定
     *
     * <p>这三支系数和分子步长在 {@code name} 不变时是常量，提前算好可以避免每 tick
     * 重复做 {@code sqrt} {@code ceil} 和除法；{@link #secondOrderStep} 仍按原来的顺序计算，
     * 数值与逐次重算完全一致
     */
    public record SecondOrderCoefficients(double k1, double k2, double k3, int cycles, double step) {}

    /** 按 YSM 的夹取规则预计算二阶滤波系数 */
    public static SecondOrderCoefficients secondOrderCoefficients(double frequency, double coefficient,
                                                                  double response) {
        double f = clamp(frequency, 0.0, 5.0);
        double c = clamp(coefficient, 0.0, 1.0);
        double k1 = c / Math.PI / f;
        double k2 = 1.0 / (2.0 * Math.PI * f) / (2.0 * Math.PI * f);
        double k3 = response * c / 2.0 / Math.PI / f;

        double maxStep = Math.sqrt(4.0 * k2 + k1 * k1) - k1;
        int cycles = maxStep > 0.0 ? Math.max(1, (int) Math.ceil(DT / maxStep)) : 1;
        return new SecondOrderCoefficients(k1, k2, k3, cycles, DT / cycles);
    }

    /**
     * 二阶滤波走一 tick
     *
     * <p>公式与 {@code physics_reference.second_order_state} 一致：先夹取参数，再由三支系数按分子步积分，
     * 步长取 {@code sqrt(4*k2 + k1^2) - k1}，保证积分离散后不震荡
     *
     * @param frequency  固有频率，夹到 0 到 5
     * @param coefficient 阻尼系数，夹到 0 到 1
     * @param response   响应时间，单位秒
     */
    public static SecondOrderState secondOrderStep(double input, double frequency, double coefficient,
                                                   double response, SecondOrderState state) {
        SecondOrderCoefficients coefficients = secondOrderCoefficients(frequency, coefficient, response);
        double value = state.value();
        double velocity = state.velocity();
        double inputDot = (input - state.previousInput()) / DT;

        for (int i = 0; i < coefficients.cycles(); i++) {
            value += coefficients.step() * velocity;
            velocity += coefficients.step()
                * (coefficients.k3() * inputDot + input - value - coefficients.k1() * velocity)
                / coefficients.k2();
        }
        return new SecondOrderState(value, velocity, input);
    }

    /**
     * 用预计算系数推进一份内联状态
     *
     * <p>{@code state[offset]} 是当前值 {@code state[offset + 1]} 是速度
     * {@code state[offset + 2]} 是上一帧输入；返回推进后的当前值。运算顺序与
     * {@link #secondOrderStep} 相同，只是不再构造 record
     */
    public static double secondOrderStepPrecomputed(double input, double k1, double k2, double k3,
                                                    int cycles, double step, double[] state, int offset) {
        double inputDot = (input - state[offset + 2]) / DT;
        double value = state[offset];
        double velocity = state[offset + 1];

        for (int i = 0; i < cycles; i++) {
            value += step * velocity;
            velocity += step * (k3 * inputDot + input - value - k1 * velocity) / k2;
        }
        state[offset] = value;
        state[offset + 1] = velocity;
        state[offset + 2] = input;
        return value;
    }

    /**
     * YSM 的 {@code ysm.perlin_noise(seed, x, y, z)}
     *
     * <p>实现取的是 OpenYSM（MIT，YSM 的开放重实现）里的同一句话：
     * {@code STBPerlin.stb_perlin_noise3_seed(x, y, z, 0, 0, 0, seed)}。所以这不是「仿一个噪声」，
     * 是与 YSM 逐字相同的算法，头发那条 {@code 10*perlin} 的波形因而能对上
     *
     * <p>只由客户端渲染层调用（{@code org.lwjgl.stb} 的本地库在专门服务端不一定加载）
     */
    public static double perlinNoise(int seed, double x) {
        return STBPerlin.stb_perlin_noise3_seed((float) x, 0.0F, 0.0F, 0, 0, 0, seed);
    }

    /** 与 YSM 的 {@code math.clamp} 同义 */
    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
