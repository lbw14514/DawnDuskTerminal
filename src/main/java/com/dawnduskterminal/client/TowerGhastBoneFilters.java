package com.dawnduskterminal.client;

import com.dawnduskterminal.entity.TowerGhastEntity;
import com.dawnduskterminal.entity.TowerGhastPhysics;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 高塔恶魂那两支有状态滤波器的运行时缓存，逐 tick 推进一次
 *
 * <p>YSM 的 {@code second_order(name, ...)} 按名字记忆状态。同名第一次之外的调用在 YSM 里也不会再积分
 * 一次（滤波器的 dt 是「距上次积分的 tick 差」，同帧内为 0），所以这里每个名字每 tick 只算一次，
 * 与源动画里同一个名字出现在多条通道上的写法一致
 *
 * <h2>三处与 GeckoLib 内置 query 不一样的地方</h2>
 *
 * 这几处如果直接照 GeckoLib 的 {@code MolangQueries} 抄，数值会差一个量级，实测症状是
 * <strong>眼睛偏离头部很远、头发看不到动</strong>：
 *
 * <ul>
 *   <li><b>头部偏航要相对身体</b>。Bedrock（也就是 YSM 的 {@code ysm.head_yaw}）给的是<em>相对身体</em>的
 *       头部转角，而 GeckoLib 的 {@code query.head_y_rotation} 是 {@code getViewYRot()}，
 *       也就是<em>世界坐标</em>下的偏航。眼睛里那条 {@code head_yaw/180} 在相对值上最大约 0.25 格，
 *       在绝对值上能到 2 格 —— 眼睛直接被推出脸外
 *   <li><b>速度类 query 是每秒，不是每 tick</b>。源动画把 {@code q.ground_speed} 夹在 {@code 0..20}、
 *       把 {@code q.vertical_speed} 乘 {@code -3} 后夹在 {@code -10..120}、把 {@code q.yaw_speed}
 *       乘 {@code -0.02} 后夹在 {@code -60..60}，这些夹位都只有在秒为单位的量级上才够得着；
 *       GeckoLib 的 {@code query.ground_speed} 是 {@code horizontalDistance()}，每 tick 只有 0.1 上下，
 *       于是乘完系数只剩零点几度，看上去就是「不动」。这里统一乘 20 换成每秒
 *   <li><b>取当前值而不是上一刻的插值</b>。{@code getViewXRot(0.0F)} 在 partialTick 为 0 时返回的是
 *       {@code yHeadRotO} 一类的上一刻值，滤波器本来就每 tick 只走一步，这里直接用当前值
 *   <li><b>YSM 的 head query 命名与 GeckoLib 相反</b>。维基写的是 {@code ysm.head_yaw} 与
 *       {@code query.head_x_rotation} 相同、{@code ysm.head_pitch} 与 {@code query.head_y_rotation} 相同，
 *       所以源动画里的 {@code query.head_x_rotation} 是<strong>偏航</strong>。
 *       {@code 转体} 那条链的输入就是它（不是俯仰），见 {@link #advanceIfNewTick}
 * </ul>
 *
 * <p>除了这 12 支按实体状态驱动的滤波器，这个类还持有 <strong>64 条链条滤波器的状态</strong>
 * （{@code ysm.bone_rot} 的父带子递次滞后，见 {@link #stepChain(int, double, double, double, double, double, double, double, int, double)}）
 *
 * <p>源模型里 {@code v.bv}、{@code v.bg}、{@code v.hv}、{@code v.hg} 这些变量
 * 由外部自定义函数脚本喂值，而那套脚本不在这个模型包里，YSM 对未初始化变量取 0，所以这里也当 0
 * （{@code v.sleevefix} 与 {@code v.roaming.shoe} 不属此类：它们由片段自己的 timeline 赋值，
 * 转换时已搬进 {@code molang} 通道，见 {@code tools/ysm/README.md}）
 */
public final class TowerGhastBoneFilters {
    // 每个滤波器的结果在数组里的下标
    public static final int EYE_YAW = 0;
    public static final int EYE_PITCH = 1;
    public static final int CHEST_VERTICAL = 2;
    public static final int CHEST_HORIZONTAL = 3;
    public static final int CHEST_YAW_SPEED = 4;
    public static final int HAIR_VERTICAL = 5;
    public static final int HAIR_HORIZONTAL = 6;
    public static final int HAIR_YAW_SPEED = 7;
    public static final int HAIR_PITCH = 8;
    public static final int HAIR_PITCH_CLAMPED = 9;
    public static final int BODY_ANGLE_FIX = 10;
    public static final int BODY_TURN = 11;
    /** 滤波器个数，也是结果数组的长度 */
    public static final int COUNT = 12;

    /** 模型没有写进包里的那几个变量，YSM 取 0 */
    private static final double UNASSIGNED_VARIABLE = 0.0;
    /** 每秒 = 每 tick 乘 20 */
    private static final double PER_SECOND = 20.0;

    /**
     * 二阶滤波的系数只由频率 阻尼 响应时间决定，提前算好
     *
     * <p>这样每 tick 不再为 12 支主滤波器重复做 sqrt ceil 和除法；
     * {@link TowerGhastPhysics#secondOrderStepPrecomputed} 的运算顺序与
     * {@link TowerGhastPhysics#secondOrderStep} 相同
     */
    private static final double[] SECOND_K1 = new double[COUNT];
    private static final double[] SECOND_K2 = new double[COUNT];
    private static final double[] SECOND_K3 = new double[COUNT];
    private static final double[] SECOND_STEP = new double[COUNT];
    private static final int[] SECOND_CYCLES = new int[COUNT];
    /** 一阶滤波的 alpha = dt / response */
    private static final double[] FIRST_ALPHA = new double[COUNT];

    static {
        initSecond(EYE_YAW, 1.0, 0.8, 0.0);
        initSecond(EYE_PITCH, 1.0, 0.8, 0.0);
        initSecond(CHEST_VERTICAL, 3.0, 0.3, 0.0);
        initSecond(CHEST_HORIZONTAL, 2.0, 0.5, 1.0);
        initSecond(CHEST_YAW_SPEED, 3.0, 0.3, 0.0);
        initSecond(HAIR_VERTICAL, 2.0, 0.6, 0.0);
        initSecond(HAIR_HORIZONTAL, 2.0, 0.6, 0.0);
        initSecond(HAIR_YAW_SPEED, 2.0, 0.6, 0.0);
        initSecond(HAIR_PITCH, 2.0, 0.6, 0.0);
        initSecond(HAIR_PITCH_CLAMPED, 2.0, 0.6, 0.0);
        initSecond(BODY_TURN, 2.5, 0.3, 0.0);
        initFirst(BODY_ANGLE_FIX, 0.3);
    }

    private static void initSecond(int index, double frequency, double coefficient, double response) {
        TowerGhastPhysics.SecondOrderCoefficients coefficients =
            TowerGhastPhysics.secondOrderCoefficients(frequency, coefficient, response);
        SECOND_K1[index] = coefficients.k1();
        SECOND_K2[index] = coefficients.k2();
        SECOND_K3[index] = coefficients.k3();
        SECOND_CYCLES[index] = coefficients.cycles();
        SECOND_STEP[index] = coefficients.step();
    }

    private static void initFirst(int index, double response) {
        FIRST_ALPHA[index] = TowerGhastPhysics.DT / response;
    }

    /** 第一刻要把累积的世界朝向对到实体的实际朝向上 */
    private boolean started;

    /** 12 支主滤波器的状态，value/velocity/previousInput 三个一组 */
    private final double[] secondState = new double[COUNT * 3];
    private final double[] firstState = new double[COUNT];

    /** 链条（{@code ysm.bone_rot} 的递次滞后）的滤波状态，每根三个 double */
    private double[] chainState = new double[0];
    /** 链条结果的两刻缓存，交给渲染帧插值 */
    private double[] chainPrevious = new double[0];
    private double[] chainCurrent = new double[0];
    /** 上一步写到某根骨骼某个轴上的链条量（度），按骨骼下标记忆 */
    private double[] chainPose = new double[0];

    /**
     * 仅给旧的名字式调用与测试使用
     *
     * <p>生产路径由 {@code TowerGhastBonePose} 使用 int 下标，不会碰这两个 Map
     */
    private final Map<String, Integer> chainIndices = new HashMap<>();
    private final Map<String, Integer> poseIndices = new HashMap<>();

    private final double[] current = new double[COUNT];
    private final double[] previous = new double[COUNT];
    private int lastTick = Integer.MIN_VALUE;

    /** 累积的世界朝向（度），用来代替 GeckoLib 没有的 {@code query.eye_target_y_rotation} */
    private double worldYaw;

    /** 本刻的 {@code ysm.perlin_noise(1492, query.life_time)}，由渲染层喂 */
    private double perlin;

    public TowerGhastBoneFilters() {
        this(0, 0);
    }

    /**
     * @param chainCapacity 链条数量，生产路径用 {@code TowerGhastBonePose} 的布局大小
     * @param boneCapacity  参与链条姿态的骨骼数量
     */
    public TowerGhastBoneFilters(int chainCapacity, int boneCapacity) {
        ensureChainCapacity(chainCapacity);
        ensureBoneCapacity(boneCapacity);
    }

    /**
     * 每个游戏刻推进一次；同一刻重复调用直接返回 false
     *
     * <p>{@code perlinNoise} 由客户端渲染层算好传进来（见 {@code TowerGhastBonePose}），
     * 这样这个类的单元测试不需要碰 LWJGL 的本地库
     *
     * <p>{@code partialTick} 不参与滤波器的积分：YSM 也是每 tick 积分一次，介于两刻之间的渲染帧靠
     * {@link #interpolate} 插值
     *
     * @return 这一回真的推进了一刻（调用方要在这一刻接着走链条，见 {@link #stepChain(int, double, double, double, double, double, double, double, int, double)}）
     */
    public boolean advanceIfNewTick(TowerGhastEntity entity, double perlinNoise) {
        if (entity.tickCount == this.lastTick) {
            return false;
        }
        this.lastTick = entity.tickCount;
        this.perlin = perlinNoise;
        if (!this.started) {
            this.started = true;
            // 世界朝向从<strong>上一刻</strong>的朝向起步，再加上这一步的增量就等于当前的 yRot：
            // 从 getYRot() 起步会把第一帧的增量算两次
            this.worldYaw = entity.yRotO;
        }
        // wrapDegrees(yRot - yRotO) 是「这一步转了多少」，累加得到<strong>连续</strong>的世界朝向：
        // 直接读 yRot 会在 ±180 处跳 360 度，而滤波器的滞后会把那一跳放大成布条抽一下
        this.worldYaw += Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
        System.arraycopy(this.current, 0, this.previous, 0, COUNT);

        // 相对身体的头部转角，见类注释
        double headYaw = Mth.wrapDegrees(entity.yHeadRot - entity.yBodyRot);
        double headPitch = entity.getXRot();
        // 每 tick 的速度乘 20 换成每秒，见类注释
        double verticalSpeed = entity.getDeltaMovement().y * PER_SECOND;
        double groundSpeed = entity.getDeltaMovement().horizontalDistance() * PER_SECOND;
        double yawSpeed = Mth.wrapDegrees(entity.getYRot() - entity.yRotO) * PER_SECOND;

        this.current[EYE_YAW] = second(EYE_YAW, headYaw / 180.0);
        this.current[EYE_PITCH] = second(EYE_PITCH, headPitch / 360.0);
        this.current[CHEST_VERTICAL] = second(CHEST_VERTICAL,
            clamp(verticalSpeed + UNASSIGNED_VARIABLE, -30.0, 30.0));
        this.current[CHEST_HORIZONTAL] = second(CHEST_HORIZONTAL,
            clamp(groundSpeed + UNASSIGNED_VARIABLE, 0.0, 20.0));
        this.current[CHEST_YAW_SPEED] = second(CHEST_YAW_SPEED,
            clamp(-0.01 * yawSpeed, -30.0, 30.0));
        this.current[HAIR_VERTICAL] = second(HAIR_VERTICAL,
            clamp(-3.0 * verticalSpeed + UNASSIGNED_VARIABLE, -10.0, 120.0));
        this.current[HAIR_HORIZONTAL] = second(HAIR_HORIZONTAL,
            clamp(groundSpeed + UNASSIGNED_VARIABLE, 0.0, 20.0));
        this.current[HAIR_YAW_SPEED] = second(HAIR_YAW_SPEED,
            clamp(-0.02 * yawSpeed, -60.0, 60.0));
        this.current[HAIR_PITCH] = second(HAIR_PITCH, headPitch);
        this.current[HAIR_PITCH_CLAMPED] = second(HAIR_PITCH_CLAMPED,
            Math.max(headPitch, -40.0));
        // 源式子是 second_order('转体', first_order('身体角度修正', eye_target_y_rotation + query.head_x_rotation, 0.3), ...)
        // 而 YSM 的 query.head_x_rotation 是<strong>偏航</strong>（YSM 的 head query 与 GeckoLib 命名相反，
        // 见 tools/ysm/README.md），用俯仰当输入等于让整条转体链死掉：
        // 恶魂待机时靠 LookAtPlayer 一直在改头部偏航，俯仰几乎不变
        // eye_target_y_rotation（视角朝向）GeckoLib 没有，用累积的世界朝向加相对头部偏航代替，
        // 两者之和就是「头的世界偏航」，也就是源式子在源侧算出来的量
        this.current[BODY_ANGLE_FIX] = first(BODY_ANGLE_FIX, this.worldYaw + headYaw);
        this.current[BODY_TURN] = second(BODY_TURN, this.current[BODY_ANGLE_FIX]);
        return true;
    }

    /**
     * 链条走一 tick：{@code 值 = sign * F(name, 输入) + parentGain * 输入}
     *
     * <p>{@code parallel0} 与 {@code pre_parallel0} 里用 {@code ysm.bone_rot('父骨骼')} 做
     * 「父带子」递次滞后的 64 条通道就是它（源式子有 {@code F(x)-x}、{@code -F(x)+0.8x}、{@code F(x)}
     * 三种形状，都落在这一条通式里）
     *
     * <p>生产路径使用这个 int 下标版本：布局在 {@code TowerGhastBonePose} 里预先建好，
     * 这里不再做任何字符串查找，也不构造 {@code SecondOrderState}
     *
     * @return 这一步写出去的量（度），调用方要把它连同骨骼下标记进 {@link #setChainPose(int, int, double)}
     */
    public double stepChain(int index, double filterInput, double subtractInput, double sign, double parentGain,
                            double k1, double k2, double k3, int cycles, double step) {
        ensureChainCapacity(index + 1);
        double filtered = TowerGhastPhysics.secondOrderStepPrecomputed(
            filterInput, k1, k2, k3, cycles, step, this.chainState, index * 3);
        double value = sign * filtered + parentGain * subtractInput;
        this.chainPrevious[index] = this.chainCurrent[index];
        this.chainCurrent[index] = value;
        return value;
    }

    /**
     * 名字式入口，保留给测试与旧的调用点
     *
     * <p>生产路径不要用这个重载，它包含一次 Map 查找
     */
    public double stepChain(String name, double filterInput, double subtractInput, double frequency,
                            double coefficient, double sign, double parentGain) {
        int index = this.chainIndices.computeIfAbsent(name, key -> this.chainIndices.size());
        TowerGhastPhysics.SecondOrderCoefficients coefficients =
            TowerGhastPhysics.secondOrderCoefficients(frequency, coefficient, 0.0);
        return stepChain(index, filterInput, subtractInput, sign, parentGain,
            coefficients.k1(), coefficients.k2(), coefficients.k3(),
            coefficients.cycles(), coefficients.step());
    }

    /**
     * 记下链条这一步写到某根骨骼某个轴上的量
     *
     * <p>链条的下一级读父骨骼的<em>合成</em>角度，而 GeckoLib 每帧 {@code tickAnimation} 都会把
     * 那些骨骼重置回「关键帧的值 + 初始姿势」，上一帧 {@code addRotation} 加的量活不过一帧，
     * 所以只能由这里替它记着（{@code TowerGhastBonePose.advanceChains}）
     */
    public void setChainPose(int boneIndex, int axis, double degrees) {
        if (boneIndex < 0) {
            return;
        }
        ensureBoneCapacity(boneIndex + 1);
        this.chainPose[boneIndex * 3 + axis] = degrees;
    }

    /** 上一步链条写在这根骨骼这个轴上的量，没写过就是 0 */
    public double chainPose(int boneIndex, int axis) {
        if (boneIndex < 0) {
            return 0.0;
        }
        int offset = boneIndex * 3 + axis;
        return offset < this.chainPose.length ? this.chainPose[offset] : 0.0;
    }

    /** 链条结果在两刻之间插值，没走过就直接是 0 */
    public double interpolateChain(int index, float partialTick) {
        if (index < 0 || index >= this.chainCurrent.length) {
            return 0.0;
        }
        double previousValue = this.chainPrevious[index];
        return previousValue + (this.chainCurrent[index] - previousValue) * partialTick;
    }

    /** 两刻之间插值，避免 20 Hz 的滤波器在更高帧率下抖动 */
    public double interpolate(int index, float partialTick) {
        return this.previous[index] + (this.current[index] - this.previous[index]) * partialTick;
    }

    /** 名字式链条入口，保留给测试 */
    public void setChainPose(String bone, int axis, double degrees) {
        setChainPose(this.poseIndices.computeIfAbsent(bone, key -> this.poseIndices.size()), axis, degrees);
    }

    /** 名字式链条入口，保留给测试 */
    public double chainPose(String bone, int axis) {
        Integer index = this.poseIndices.get(bone);
        return index == null ? 0.0 : chainPose(index, axis);
    }

    /** 名字式链条入口，保留给测试 */
    public double interpolateChain(String name, float partialTick) {
        Integer index = this.chainIndices.get(name);
        return index == null ? 0.0 : interpolateChain(index, partialTick);
    }

    private double second(int index, double input) {
        return TowerGhastPhysics.secondOrderStepPrecomputed(
            input, SECOND_K1[index], SECOND_K2[index], SECOND_K3[index],
            SECOND_CYCLES[index], SECOND_STEP[index], this.secondState, index * 3);
    }

    private double first(int index, double input) {
        double alpha = FIRST_ALPHA[index];
        double value = (1.0 - alpha) * this.firstState[index] + alpha * input;
        this.firstState[index] = value;
        return value;
    }

    private void ensureChainCapacity(int count) {
        if (count <= this.chainCurrent.length) {
            return;
        }
        int newCount = Math.max(count, Math.max(8, this.chainCurrent.length * 2));
        this.chainState = Arrays.copyOf(this.chainState, newCount * 3);
        this.chainPrevious = Arrays.copyOf(this.chainPrevious, newCount);
        this.chainCurrent = Arrays.copyOf(this.chainCurrent, newCount);
    }

    private void ensureBoneCapacity(int count) {
        if (count <= this.chainPose.length / 3) {
            return;
        }
        int newCount = Math.max(count, Math.max(8, this.chainPose.length / 3 * 2));
        this.chainPose = Arrays.copyOf(this.chainPose, newCount * 3);
    }

    private static double clamp(double value, double min, double max) {
        return TowerGhastPhysics.clamp(value, min, max);
    }
}
