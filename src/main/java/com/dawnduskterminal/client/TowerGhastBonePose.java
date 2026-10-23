package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.TowerGhastEntity;
import com.dawnduskterminal.entity.TowerGhastPhysics;
import com.dawnduskterminal.mixin.GeckoLibResetHints;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 把 {@link TowerGhastBoneFilters} 的结果写回骨骼
 *
 * <h2>为什么必须是 {@code GeoModel.setCustomAnimations} 而不是渲染层</h2>
 *
 * GeckoLib 4.9.3 的管线是：
 *
 * <pre>
 * defaultRender
 *   preRender
 *   firePreRenderEvent
 *   preApplyRenderLayers      &lt;- BoneFilterGeoLayer 在这里
 *   actuallyRender
 *     handleAnimations        &lt;- tickAnimation 在这里把动画值写进骨骼
 *     renderRecursively       &lt;- 骨骼的矩阵在这里被读走
 * </pre>
 *
 * 也就是说 {@code BoneFilterGeoLayer} 跑在动画<strong>之前</strong>，它对骨骼的写入会被
 * {@code tickAnimation} 用「初始姿势 + 动画值」整条覆盖掉 —— 现象是层里的代码确实执行了
 * （日志能打出来）但屏幕上毫无变化
 *
 * <p>{@code setCustomAnimations} 是 {@code handleAnimations} 的最后一步，正好夹在
 * 「动画写完」与「矩阵读走」之间，官方注释也写着「Override to set custom animations (such as head
 * rotation, etc)」
 *
 * <h2>两类写入</h2>
 *
 * <ul>
 *   <li><b>替换式</b>（{@link #setRotation}）：主动画已经把滤波器的输入写在同一个通道上的那些，
 *       例如发丝与胸口的滤波器
 *   <li><b>叠加式</b>（{@link #addRotation}）：YSM 的并行动画是加法混合，而布条那几个通道在文件里是 0，
 *       主动画在 `run` `jump` `ladder_*` 里另有自己的关键帧，所以只能叠上去
 * </ul>
 *
 * <p>链条（{@link #LAGS}）逐 tick 在 {@link #advanceChains} 里积分，逐帧在 {@link #apply} 里插值写回
 *
 * <h2>单位与符号</h2>
 *
 * 源动画里的表达式都是<strong>度</strong>，GeckoLib 在求值那条路径上自己做了两件事
 * （{@code AnimationController.getAnimationPointAtTick}）：常数以外的值先 {@code toRadians}，
 * 再把 X 与 Y 取反；Z 不取反。这里照做，于是本层写出的值与动画文件里同一个通道的值处于同一套单位
 *
 * <p>旋转是「初始姿势加偏移」，位置与缩放是绝对值，与 {@code AnimationProcessor} 里的应用方式一致
 *
 * <h2>性能</h2>
 *
 * 骨骼引用、发丝下标、链条的父骨骼与祖先列表、二阶滤波系数都在第一次看到模型时建好
 * （{@link BoneLayout}）。之后每帧只做数组遍历，不再按名字查 {@code GeoModel.getBone}，
 * 也不再对每根骨骼重复算 12 支滤波器的插值
 */
public final class TowerGhastBonePose {

    private static final List<String> NAMED_BONES = List.of(
        "RightEyesBase", "LeftEyesBase",
        "Breast", "Carminite", "LeftGuard", "Bung",
        "FrontFragFlag", "BackFragFlag", "LeftFragFlag", "RightFragFlag",
        "AnteriorFragFlag", "AnteriorFragFlag2", "RearAnteriorFragFlag", "RearAnteriorFragFlag2",
        "FLongHair", "MLongHair", "ElytraLocator",
        "LeftHeadFrag", "RightHeadFrag");

    /** 发丝：{@code bone5} 到 {@code bone157} 这 38 根，父链都在 {@code Hair} 一系下面 */
    private record Strand(String bone, int axis, double perlinSign, double yawSign) {}

    /**
     * 发丝的通道表，从源动画的 {@code parallel0} 里抽出来的
     *
     * <p>它们的式子统一是 {@code ±10*ysm.perlin_noise(1492, query.life_time) ± ysm.second_order('头发角速度', …)}，
     * 只有落在哪个轴、两个符号是正是负不同。seed 全是 1492
     */
    private static final List<Strand> STRANDS = List.of(
        new Strand("bone10", 2, 1, 1),
        new Strand("bone11", 2, 1, 1),
        new Strand("bone127", 2, 1, 1),
        new Strand("bone128", 2, 1, 1),
        new Strand("bone13", 2, 1, 1),
        new Strand("bone131", 0, -1, -1),
        new Strand("bone15", 2, 1, 1),
        new Strand("bone151", 0, -1, -1),
        new Strand("bone154", 2, 1, 1),
        new Strand("bone155", 2, 1, 1),
        new Strand("bone157", 2, 1, 1),
        new Strand("bone17", 2, 1, 1),
        new Strand("bone18", 2, 1, 1),
        new Strand("bone19", 2, 1, 1),
        new Strand("bone20", 0, -1, -1),
        new Strand("bone21", 2, 1, 1),
        new Strand("bone22", 2, 1, 1),
        new Strand("bone23", 2, 1, 1),
        new Strand("bone24", 2, 1, 1),
        new Strand("bone25", 2, 1, 1),
        new Strand("bone34", 2, 1, 1),
        new Strand("bone37", 2, 1, 1),
        new Strand("bone38", 2, 1, 1),
        new Strand("bone39", 2, 1, 1),
        new Strand("bone40", 2, 1, 1),
        new Strand("bone5", 0, 1, 1),
        new Strand("bone6", 0, 1, 1),
        new Strand("bone7", 0, 1, 1),
        new Strand("bone76", 2, 1, 1),
        new Strand("bone8", 2, 1, 1),
        new Strand("bone82", 2, 1, 1),
        new Strand("bone86", 2, 1, 1),
        new Strand("bone87", 2, 1, 1),
        new Strand("bone88", 2, 1, 1),
        new Strand("bone9", 2, 1, 1),
        new Strand("bone91", 2, 1, 1),
        new Strand("bone92", 2, 1, 1),
        new Strand("bone93", 2, 1, 1));

    /**
     * 链条：源动画用 {@code ysm.bone_rot('父骨骼')} 做「父带子」递次滞后的一根骨骼的一个轴
     *
     * <p>值是 {@code sign * F(name, 父骨骼 parentAxis 轴上的度数) + parentGain * 父骨骼 subtractAxis 轴上的度数}
     * （{@code F} 是二阶滤波，稳定增益 1）。源式子只有三种形状，都落在这一条通式里：
     * {@code F(x)-x}（sign=1，parentGain=-1）、{@code -F(x)+0.8x}（sign=-1，parentGain=0.8）、{@code F(x)}（parentGain=0）
     *
     * <p>表里唯一一处 {@code parentAxis != subtractAxis} 的是 {@code FragFlag16.rotation[0]}：
     * 源动画写的是 {@code F(bone_rot('FragFlag15').z) - 0.6*bone_rot('FragFlag15').x}，
     * 看着像作者的笔误，但照抄
     *
     * <p><strong>顺序必须是父在前</strong>：逐 tick 那一步读的是骨骼上一帧的旋转，父先算完子才能接着
     */
    private record Lag(String bone, int axis, String name, double frequency, double coefficient,
                       String parent, int parentAxis, int subtractAxis, double sign, double parentGain) {}

    /** 源动画 parallel0 与 pre_parallel0 里全部 64 条 {@code ysm.bone_rot} 通道 */
    private static final List<Lag> LAGS = List.of(
        new Lag("FragFlag2", 0, "前2x", 2.0, 0.4, "FrontFragFlag", 0, 0, 1.0, -1.0),
        new Lag("FragFlag2", 2, "前2", 2.0, 0.4, "FrontFragFlag", 2, 2, 1.0, -1.0),
        new Lag("FragFlag3", 0, "前3x", 2.0, 0.4, "FragFlag2", 0, 0, 1.0, -1.0),
        new Lag("FragFlag3", 2, "前3", 2.0, 0.4, "FragFlag2", 2, 2, 1.0, 0.0),
        new Lag("FragFlag4", 0, "前4x", 2.0, 0.5, "FragFlag3", 0, 0, 1.0, -1.0),
        new Lag("FragFlag4", 2, "前4", 2.0, 0.5, "FragFlag3", 2, 2, 1.0, 0.0),
        new Lag("FragFlag14", 0, "后2x", 2.0, 0.4, "BackFragFlag", 0, 0, 1.0, -1.0),
        new Lag("FragFlag14", 2, "后2", 2.0, 0.4, "BackFragFlag", 2, 2, 1.0, -1.0),
        new Lag("FragFlag15", 0, "后3x", 2.0, 0.5, "FragFlag14", 0, 0, -1.0, 0.8),
        new Lag("FragFlag15", 2, "后3", 2.0, 0.5, "FragFlag14", 2, 2, -1.0, 0.8),
        new Lag("FragFlag16", 0, "后4x", 2.0, 0.4, "FragFlag15", 2, 0, 1.0, -0.6),
        new Lag("FragFlag16", 2, "后4", 2.0, 0.4, "FragFlag15", 2, 2, 1.0, -0.6),
        new Lag("FragFlag17", 0, "后5x", 2.0, 0.5, "FragFlag16", 0, 0, -1.0, 0.4),
        new Lag("FragFlag17", 2, "后5", 2.0, 0.5, "FragFlag16", 2, 2, -1.0, 0.4),
        new Lag("LeftFragFlag2", 2, "左2", 2.0, 0.4, "LeftFragFlag", 1, 1, 1.0, -1.0),
        new Lag("LeftFragFlag3", 0, "左3x", 2.0, 0.4, "LeftFragFlag2", 0, 0, 1.0, 0.0),
        new Lag("LeftFragFlag3", 2, "左3", 2.0, 0.4, "LeftFragFlag2", 2, 2, 1.0, 0.0),
        new Lag("LeftFragFlag4", 0, "左4x", 2.0, 0.5, "LeftFragFlag3", 0, 0, 1.0, 0.0),
        new Lag("LeftFragFlag4", 2, "左4", 2.0, 0.5, "LeftFragFlag3", 2, 2, 1.0, 0.0),
        new Lag("FragFlag6", 0, "左前2x", 2.0, 0.4, "AnteriorFragFlag", 0, 0, 1.0, -1.0),
        new Lag("FragFlag6", 2, "左前2", 2.0, 0.4, "AnteriorFragFlag", 2, 2, 1.0, -1.0),
        new Lag("FragFlag7", 0, "左前3x", 2.0, 0.5, "FragFlag6", 0, 0, -1.0, 0.6),
        new Lag("FragFlag7", 2, "左前3", 2.0, 0.5, "FragFlag6", 2, 2, -1.0, 0.6),
        new Lag("FragFlag8", 0, "左前4x", 2.0, 0.4, "FragFlag7", 0, 0, 1.0, -0.3),
        new Lag("FragFlag8", 2, "左前4", 2.0, 0.4, "FragFlag7", 2, 2, 1.0, -0.3),
        new Lag("FragFlag5", 0, "左前5x", 2.0, 0.4, "FragFlag8", 0, 0, 1.0, 0.0),
        new Lag("FragFlag5", 2, "左前5", 2.0, 0.4, "FragFlag8", 2, 2, 1.0, 0.0),
        new Lag("FragFlag9", 0, "左后2x", 2.0, 0.4, "RearAnteriorFragFlag", 0, 0, 1.0, -1.0),
        new Lag("FragFlag9", 2, "左后2", 2.0, 0.4, "RearAnteriorFragFlag", 2, 2, 1.0, -1.0),
        new Lag("FragFlag11", 0, "左后x", 2.0, 0.5, "FragFlag9", 0, 0, -1.0, 0.8),
        new Lag("FragFlag11", 2, "左后3", 2.0, 0.5, "FragFlag9", 2, 2, -1.0, 0.8),
        new Lag("FragFlag12", 0, "左后4x", 2.0, 0.4, "FragFlag11", 0, 0, 1.0, -0.6),
        new Lag("FragFlag12", 2, "左后4", 2.0, 0.4, "FragFlag11", 2, 2, 1.0, -0.6),
        new Lag("FragFlag13", 0, "左后5x", 2.0, 0.5, "FragFlag12", 0, 0, -1.0, 0.4),
        new Lag("FragFlag13", 2, "左后5", 2.0, 0.5, "FragFlag12", 2, 2, -1.0, 0.4),
        new Lag("FragFlag22", 0, "右后2x", 2.0, 0.4, "RearAnteriorFragFlag2", 0, 0, 1.0, -1.0),
        new Lag("FragFlag22", 2, "右后2", 2.0, 0.4, "RearAnteriorFragFlag2", 2, 2, 1.0, -1.0),
        new Lag("FragFlag24", 0, "右后3x", 2.0, 0.5, "FragFlag22", 0, 0, -1.0, 0.8),
        new Lag("FragFlag24", 2, "右后3", 2.0, 0.5, "FragFlag22", 2, 2, -1.0, 0.8),
        new Lag("FragFlag25", 0, "右后4x", 2.0, 0.4, "FragFlag24", 0, 0, 1.0, -0.6),
        new Lag("FragFlag25", 2, "右后4", 2.0, 0.4, "FragFlag24", 2, 2, 1.0, -0.6),
        new Lag("FragFlag26", 0, "右后5x", 2.0, 0.5, "FragFlag25", 0, 0, -1.0, 0.4),
        new Lag("FragFlag26", 2, "右后5", 2.0, 0.5, "FragFlag25", 2, 2, -1.0, 0.4),
        new Lag("FragFlag18", 0, "右前2x", 2.0, 0.4, "AnteriorFragFlag2", 0, 0, 1.0, -1.0),
        new Lag("FragFlag18", 2, "右前2", 2.0, 0.4, "AnteriorFragFlag2", 2, 2, 1.0, -1.0),
        new Lag("FragFlag19", 0, "右前3x", 2.0, 0.5, "FragFlag18", 0, 0, -1.0, 0.6),
        new Lag("FragFlag19", 2, "右前3", 2.0, 0.5, "FragFlag18", 2, 2, -1.0, 0.6),
        new Lag("FragFlag20", 0, "右前4x", 2.0, 0.4, "FragFlag19", 0, 0, 1.0, -0.3),
        new Lag("FragFlag20", 2, "右前4", 2.0, 0.4, "FragFlag19", 2, 2, 1.0, -0.3),
        new Lag("FragFlag21", 0, "右前5x", 2.0, 0.4, "FragFlag20", 0, 0, 1.0, 0.0),
        new Lag("FragFlag21", 2, "右前5", 2.0, 0.4, "FragFlag20", 2, 2, 1.0, 0.0),
        new Lag("RightFragFlag2", 2, "右2", 2.0, 0.4, "RightFragFlag", 1, 1, 1.0, -1.0),
        new Lag("RightFragFlag3", 0, "右3x", 2.0, 0.4, "RightFragFlag2", 0, 0, 1.0, 0.0),
        new Lag("RightFragFlag3", 2, "右3", 2.0, 0.4, "RightFragFlag2", 2, 2, 1.0, 0.0),
        new Lag("RightFragFlag4", 0, "右4x", 2.0, 0.5, "RightFragFlag3", 0, 0, 1.0, 0.0),
        new Lag("RightFragFlag4", 2, "右4", 2.0, 0.5, "RightFragFlag3", 2, 2, 1.0, 0.0),
        new Lag("LeftHeadFrag2", 0, "头2x", 2.0, 0.4, "LeftHeadFrag", 0, 0, 1.0, -1.0),
        new Lag("LeftHeadFrag2", 2, "头2", 2.0, 0.4, "LeftHeadFrag", 2, 2, 1.0, -1.0),
        new Lag("LeftHeadFrag3", 0, "头3x", 2.0, 0.4, "LeftHeadFrag2", 0, 0, 1.0, 0.0),
        new Lag("LeftHeadFrag3", 2, "头3", 2.0, 0.4, "LeftHeadFrag2", 2, 2, 1.0, 0.0),
        new Lag("RightHeadFrag2", 0, "头22x", 2.0, 0.4, "RightHeadFrag", 0, 0, 1.0, -1.0),
        new Lag("RightHeadFrag2", 2, "头22", 2.0, 0.4, "RightHeadFrag", 2, 2, 1.0, -1.0),
        new Lag("RightHeadFrag3", 0, "头23x", 2.0, 0.4, "RightHeadFrag2", 0, 0, 1.0, 0.0),
        new Lag("RightHeadFrag3", 2, "头23", 2.0, 0.4, "RightHeadFrag2", 2, 2, 1.0, 0.0));

    private static final List<String> BONES;
    static {
        List<String> all = new ArrayList<>(NAMED_BONES);
        STRANDS.forEach(strand -> all.add(strand.bone()));
        LAGS.forEach(lag -> all.add(lag.bone()));
        BONES = all.stream().distinct().toList();
    }

    /** 源动画里发丝用的 perlin seed，38 根全是它 */
    private static final int PERLIN_SEED = 1492;
    /** 源式子是 {@code 10*ysm.perlin_noise(...)}，10 度是振幅 */
    private static final double PERLIN_AMPLITUDE = 10.0;

    /**
     * 头部跟踪写在哪根骨骼上
     *
     * <p>选 {@code AllHead} 而不是 {@code Head} 或 {@code MHead}：脖子（{@code Neck}）是它的子骨骼，
     * 而 {@code pre_parallel2} 给脖子写的是 {@code -0.5*相对头部偏航} + {@code -0.5*俯仰} ——
     * 那是「脖子只跟着转一半」的写法，只有把跟踪加在脖子的父级以上才成立
     */
    private static final String HEAD_BONE = "AllHead";

    static {
        // 这些骨骼由 setCustomAnimations 直接写，没有对应的 BoneSnapshot
        // 不能让 GeckoLib 的重置剪枝把它们当静态骨骼剔掉
        GeckoLibResetHints.markAlwaysReset(BONES);
        GeckoLibResetHints.markAlwaysReset(List.of(HEAD_BONE));
    }

    private static final int KIND_IGNORE = 0;
    private static final int KIND_EYES = 1;
    private static final int KIND_BREAST = 2;
    private static final int KIND_CARMINITE = 3;
    private static final int KIND_LEFT_GUARD = 4;
    private static final int KIND_BUNG = 5;
    private static final int KIND_FRONT_FRAG = 6;
    private static final int KIND_BACK_FRAG = 7;
    private static final int KIND_SIDE_FRAG = 8;
    private static final int KIND_FLONG_HAIR = 9;
    private static final int KIND_MLONG_HAIR = 10;
    private static final int KIND_ELYTRA = 11;
    private static final int KIND_LEFT_HEAD_FRAG = 12;
    private static final int KIND_RIGHT_HEAD_FRAG = 13;
    private static final int KIND_STRAND = 14;

    /** 每个实体一份滤波器状态。弱键，实体卸载后自己消失 */
    private final Map<TowerGhastEntity, TowerGhastBoneFilters> states = new WeakHashMap<>();

    /** 缺骨骼只报一次，别每帧刷屏 */
    private final List<String> reportedMissing = new ArrayList<>();

    /** 每个模型只建一次骨骼布局；资源重载后哨兵骨骼的 identity 会变，届时重建 */
    private GeoModel<?> cachedModel;
    private BoneLayout cachedLayout;

    /** 12 支滤波器的本帧插值结果，避免在 90 根骨骼上各算一遍 */
    private final double[] filterValues = new double[TowerGhastBoneFilters.COUNT];

    /**
     * 每渲染帧调一次，紧跟在动画写入骨骼之后
     *
     * @param partialTick 用于两刻之间插值，滤波器本身每 tick 只走一步
     */
    public void apply(TowerGhastEntity entity, GeoModel<TowerGhastEntity> model, float partialTick) {
        BoneLayout layout = layout(model);
        TowerGhastBoneFilters filters = this.states.computeIfAbsent(entity,
            unused -> new TowerGhastBoneFilters(layout.lags.length, layout.boneCount));

        // 发丝的 perlin 要按<strong>帧</strong>取：噪声乘 10 度加在发丝上，而 GeckoLib 每帧都求值动画，
        // 按 20 Hz 的 tickCount 取值在 60 fps 下就是一跳一跳的（YSM 那边 query.life_time 也是连续的）
        double perlin = TowerGhastPhysics.perlinNoise(PERLIN_SEED, (entity.tickCount + partialTick) / 20.0);
        boolean newTick = filters.advanceIfNewTick(entity, perlin);
        if (layout.head != null) {
            applyHeadTracking(entity, layout.head, partialTick);
        }

        double[] f = this.filterValues;
        for (int i = 0; i < TowerGhastBoneFilters.COUNT; i++) {
            f[i] = filters.interpolate(i, partialTick);
        }
        double chestSum = f[TowerGhastBoneFilters.CHEST_VERTICAL] + f[TowerGhastBoneFilters.CHEST_HORIZONTAL];
        double turnLag = f[TowerGhastBoneFilters.BODY_TURN] - f[TowerGhastBoneFilters.BODY_ANGLE_FIX];

        for (BoneSlot slot : layout.applySlots) {
            applyBone(slot, f, chestSum, turnLag, perlin);
        }

        // 链条要读的是「基础姿势已经写完」的骨骼，所以排在 BONES 循环之后，见 advanceChains
        if (newTick) {
            advanceChains(layout, filters);
        }
        // 链条的骨骼不在 NAMED_BONES 里，单独写回
        for (LagSlot lag : layout.lags) {
            addRotation(lag.bone, lag.axis, filters.interpolateChain(lag.chainIndex, partialTick));
        }
    }

    private BoneLayout layout(GeoModel<TowerGhastEntity> model) {
        BoneLayout layout = this.cachedLayout;
        if (layout != null && this.cachedModel == model && layout.isCurrent(model)) {
            return layout;
        }
        layout = BoneLayout.build(model);
        this.cachedModel = model;
        this.cachedLayout = layout;
        reportMissing(layout);
        return layout;
    }

    private void reportMissing(BoneLayout layout) {
        List<String> missing = null;
        for (String name : layout.missing) {
            if (!this.reportedMissing.contains(name)) {
                if (missing == null) {
                    missing = new ArrayList<>();
                }
                missing.add(name);
            }
        }
        if (missing != null) {
            this.reportedMissing.addAll(missing);
            DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal 高塔恶魂的次级运动有 {} 根骨骼不在模型里，对应通道不会生效：{}",
                missing.size(), missing);
        }
    }

    /**
     * 逐 tick 走一遍链条：每根子骨骼的二阶滤波输入取父骨骼的<strong>合成</strong>角度
     *
     * <p>写回不在这一步，而是在 {@link #apply} 里按 partialTick 插值写
     *
     * <p><strong>必须在 {@link #applyBone} 循环之后调用</strong>：布条根部那点运动（{@code 0.5*turnLag}、
     * 头部跟踪、发丝滤波器）都是本层每帧写上去的，而转换后的动画文件里那些通道是 0。
     * 排在前面的读到的就是 0，链条整条安静，表现是布条作为一块硬片跟着根骨转
     *
     * <p>父骨骼上一 tick 的链条量要靠 {@link TowerGhastBoneFilters#chainPose(int, int)} 补回来：
     * GeckoLib 每帧 {@code tickAnimation} 把骨骼重置回关键帧，本层的 {@code addRotation} 活不过一帧，
     * 直接读骨骼只能读到动画叠加的那部分
     *
     * <p>链条是一棵严格的父→子树，没有自引用，{@code F(x)-x} 这种形状反而会自己把自己拉回 0
     */
    private static void advanceChains(BoneLayout layout, TowerGhastBoneFilters filters) {
        for (LagSlot lag : layout.lags) {
            if (lag.parent == null) {
                continue;
            }
            double input = composedDegrees(lag.parent, lag.parentAxis)
                + chainPoseSum(filters, lag.ancestors, lag.parentAxis);
            // 源式子的第二项读的是父骨骼另一个轴上的值（64 条里只有 FragFlag16.rotation[0] 与第一项不同轴）
            double subtracted = lag.parentAxis == lag.subtractAxis
                ? input
                : composedDegrees(lag.parent, lag.subtractAxis)
                    + chainPoseSum(filters, lag.ancestors, lag.subtractAxis);
            double value = filters.stepChain(lag.chainIndex, input, subtracted,
                lag.sign, lag.parentGain, lag.k1, lag.k2, lag.k3, lag.cycles, lag.step);
            filters.setChainPose(lag.boneIndex, lag.axis, value);
        }
    }

    private static double chainPoseSum(TowerGhastBoneFilters filters, int[] ancestors, int axis) {
        double total = 0.0;
        for (int index : ancestors) {
            total += filters.chainPose(index, axis);
        }
        return total;
    }

    /**
     * 骨骼在某个轴上的<strong>合成</strong>角度：它自己的偏移加上所有祖先的偏移
     *
     * <p>{@code ysm.bone_rot('x')} 给的是骨骼在模型里的旋转，不是它相对父骨骼的那一小段
     * 链条每一级都读合成角度时滞后才会逐级累加：上一级被带走的量势必要传给下一级，
     * 看上去就是鞭子（布条、发帘都是这个味道）
     *
     * <p>只读局部角度会把整条链拉直 —— 纯滞后的那几级（{@code F(x)} 形状）稳态下会收敛到
     * 与父骨相同的角度，于是从第二段开始全是一个姿势
     *
     * <p>欧拉角逐级相加不是严格的矩阵合成，但本模型每根布条基本只在单轴上弯，误差远小于
     * 真去算四元数再回拆的开销
     */
    private static double composedDegrees(GeoBone bone, int axis) {
        double total = 0.0;
        for (GeoBone current = bone; current != null; current = current.getParent()) {
            total += offsetDegrees(current, axis);
        }
        return total;
    }

    /**
     * 把实体的头部俯仰与偏航写到 {@link #HEAD_BONE} 上
     *
     * <p>GeckoLib 自己那份头部跟踪在 {@code DefaultedEntityGeoModel.setCustomAnimations} 里，
     * 而本模组覆写了那个方法（要写回滤波器与链条），于是头根本不会转 ——
     * 脖子的 {@code -0.5*相对偏航} 就失去了参照，看上去就是「脖子反着转」
     *
     * <p>公式照抄 GeckoLib：{@code GeoEntityRenderer} 把 {@code -netHeadYaw} 与 {@code -headPitch}
     * 装进 {@code EntityModelData}，跟踪再乘弧度，本层的 {@link #setRotation} 就是那套符号约定
     *
     * <p>代价是 {@code death} 与 {@code attacked} 里 {@code AllHead} 自己的关键帧被跟踪覆盖
     * （YSM 的跟踪也在同一个位置，覆盖面相同）
     */
    private static void applyHeadTracking(TowerGhastEntity entity, GeoBone head, float partialTick) {
        // 与 query.head_x_rotation（getViewXRot）以及相对身体偏航同源，见类注释的「单位与符号」
        double pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        double yaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot)
            - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        setRotation(head, 0, pitch);
        setRotation(head, 1, yaw);
    }

    private static void applyBone(BoneSlot slot, double[] f, double chestSum, double turnLag, double perlin) {
        GeoBone bone = slot.bone;
        switch (slot.kind) {
            case KIND_EYES -> {
                bone.setPosX((float) f[TowerGhastBoneFilters.EYE_YAW]);
                bone.setPosY((float) f[TowerGhastBoneFilters.EYE_PITCH]);
            }
            case KIND_BREAST -> {
                setRotation(bone, 0, chestSum);
                setRotation(bone, 1, f[TowerGhastBoneFilters.CHEST_YAW_SPEED]);
                setScale(bone, 0, 1.0 - 0.002 * chestSum);
                setScale(bone, 1, 1.0 + 0.01 * chestSum);
                setScale(bone, 2, 1.0 - 0.008 * chestSum);
            }
            case KIND_CARMINITE -> {
                setRotation(bone, 0, chestSum);
                setRotation(bone, 2, 3.0 * f[TowerGhastBoneFilters.CHEST_YAW_SPEED]);
            }
            case KIND_LEFT_GUARD -> {
                addRotation(bone, 0,
                    3.0 * f[TowerGhastBoneFilters.CHEST_HORIZONTAL]
                        - 3.0 * f[TowerGhastBoneFilters.CHEST_YAW_SPEED]);
                addRotation(bone, 2, 3.0 * f[TowerGhastBoneFilters.CHEST_VERTICAL]);
            }
            case KIND_BUNG -> {
                setRotation(bone, 0, f[TowerGhastBoneFilters.CHEST_VERTICAL]);
                setRotation(bone, 1, f[TowerGhastBoneFilters.CHEST_YAW_SPEED]);
            }
            case KIND_FRONT_FRAG -> addRotation(bone, 2, 0.5 * turnLag);
            case KIND_BACK_FRAG -> addRotation(bone, 2, -0.5 * turnLag);
            case KIND_SIDE_FRAG -> addRotation(bone, 1, -0.3 * turnLag);
            case KIND_FLONG_HAIR -> setRotation(bone, 0, f[TowerGhastBoneFilters.HAIR_PITCH_CLAMPED]);
            case KIND_MLONG_HAIR -> {
                setRotation(bone, 0,
                    f[TowerGhastBoneFilters.HAIR_VERTICAL] + f[TowerGhastBoneFilters.HAIR_HORIZONTAL]);
                setRotation(bone, 2, f[TowerGhastBoneFilters.HAIR_YAW_SPEED]);
            }
            case KIND_ELYTRA -> {
                setRotation(bone, 0,
                    0.6 * f[TowerGhastBoneFilters.HAIR_VERTICAL]
                        + 0.6 * f[TowerGhastBoneFilters.HAIR_HORIZONTAL]);
                setRotation(bone, 2, f[TowerGhastBoneFilters.HAIR_YAW_SPEED]);
            }
            case KIND_LEFT_HEAD_FRAG -> {
                setRotation(bone, 0, -1.5 * f[TowerGhastBoneFilters.HAIR_VERTICAL]);
                setRotation(bone, 2,
                    -0.9 * f[TowerGhastBoneFilters.HAIR_PITCH]
                        + 1.5 * f[TowerGhastBoneFilters.HAIR_YAW_SPEED]
                        - 3.0 * f[TowerGhastBoneFilters.HAIR_HORIZONTAL]);
            }
            case KIND_RIGHT_HEAD_FRAG -> {
                setRotation(bone, 0, -1.5 * f[TowerGhastBoneFilters.HAIR_VERTICAL]);
                setRotation(bone, 2,
                    0.9 * f[TowerGhastBoneFilters.HAIR_PITCH]
                        + 1.5 * f[TowerGhastBoneFilters.HAIR_YAW_SPEED]
                        + 3.0 * f[TowerGhastBoneFilters.HAIR_HORIZONTAL]);
            }
            case KIND_STRAND -> {
                Strand strand = STRANDS.get(slot.strandIndex);
                setRotation(bone, strand.axis(),
                    strand.perlinSign() * PERLIN_AMPLITUDE * perlin
                        + strand.yawSign() * f[TowerGhastBoneFilters.HAIR_YAW_SPEED]);
            }
            default -> {
            }
        }
    }

    /** 度，按 GeckoLib 对表达式的处理转成弧度：X 与 Y 取反，Z 不取反 */
    private static void setRotation(GeoBone bone, int axis, double degrees) {
        BoneSnapshot initial = bone.getInitialSnapshot();
        float radians = (float) Math.toRadians(degrees);
        switch (axis) {
            case 0 -> bone.setRotX(initial.getRotX() - radians);
            case 1 -> bone.setRotY(initial.getRotY() - radians);
            default -> bone.setRotZ(initial.getRotZ() + radians);
        }
    }

    /**
     * 叠加式写入，符号与 {@link #setRotation} 一致
     *
     * <p>YSM 的并行动画是<strong>加法混合</strong>，所以次级运动必须叠在主动画之上：
     * 布条那几根骨骼在 {@code run} {@code jump} {@code ladder_*} 里都有自己的关键帧，
     * 替换写法会把它们整段抹掉（表现是跑动时布条僵在原地）
     *
     * <p>只有主动画写 0 或根本不碰那些通道的骨骼用得到它（{@link #setRotation} 仍用于主动画已经
     * 把同一个值写进去的那些，例如发丝与胸口的滤波器输入）
     */
    private static void addRotation(GeoBone bone, int axis, double degrees) {
        float radians = (float) Math.toRadians(degrees);
        switch (axis) {
            case 0 -> bone.setRotX(bone.getRotX() - radians);
            case 1 -> bone.setRotY(bone.getRotY() - radians);
            default -> bone.setRotZ(bone.getRotZ() + radians);
        }
    }

    /** 骨骼当前角度相对初始姿势的偏移，换成动画文件里的度数（与 {@link #addRotation} 互逆） */
    private static double offsetDegrees(GeoBone bone, int axis) {
        BoneSnapshot initial = bone.getInitialSnapshot();
        return switch (axis) {
            case 0 -> -Math.toDegrees(bone.getRotX() - initial.getRotX());
            case 1 -> -Math.toDegrees(bone.getRotY() - initial.getRotY());
            default -> Math.toDegrees(bone.getRotZ() - initial.getRotZ());
        };
    }

    private static void setScale(GeoBone bone, int axis, double value) {
        switch (axis) {
            case 0 -> bone.setScaleX((float) value);
            case 1 -> bone.setScaleY((float) value);
            default -> bone.setScaleZ((float) value);
        }
    }

    private static int kindForNamed(String name) {
        return switch (name) {
            case "RightEyesBase", "LeftEyesBase" -> KIND_EYES;
            case "Breast" -> KIND_BREAST;
            case "Carminite" -> KIND_CARMINITE;
            case "LeftGuard" -> KIND_LEFT_GUARD;
            case "Bung" -> KIND_BUNG;
            case "FrontFragFlag", "AnteriorFragFlag", "AnteriorFragFlag2" -> KIND_FRONT_FRAG;
            case "BackFragFlag", "RearAnteriorFragFlag", "RearAnteriorFragFlag2" -> KIND_BACK_FRAG;
            case "LeftFragFlag", "RightFragFlag" -> KIND_SIDE_FRAG;
            case "FLongHair" -> KIND_FLONG_HAIR;
            case "MLongHair" -> KIND_MLONG_HAIR;
            case "ElytraLocator" -> KIND_ELYTRA;
            case "LeftHeadFrag" -> KIND_LEFT_HEAD_FRAG;
            case "RightHeadFrag" -> KIND_RIGHT_HEAD_FRAG;
            default -> KIND_IGNORE;
        };
    }

    /** 模型加载时一次性建好的骨骼索引，之后每帧只读 */
    private static final class BoneLayout {
        private final BoneSlot[] applySlots;
        private final LagSlot[] lags;
        private final GeoBone head;
        private final String sentinelName;
        private final GeoBone sentinel;
        private final List<String> missing;
        private final int boneCount;

        private BoneLayout(BoneSlot[] applySlots, LagSlot[] lags, GeoBone head,
                           String sentinelName, GeoBone sentinel, List<String> missing, int boneCount) {
            this.applySlots = applySlots;
            this.lags = lags;
            this.head = head;
            this.sentinelName = sentinelName;
            this.sentinel = sentinel;
            this.missing = missing;
            this.boneCount = boneCount;
        }

        private static BoneLayout build(GeoModel<?> model) {
            var processor = model.getAnimationProcessor();
            Map<String, Integer> boneIndex = new HashMap<>();
            for (int i = 0; i < BONES.size(); i++) {
                boneIndex.put(BONES.get(i), i);
            }

            List<String> missing = new ArrayList<>();
            for (String name : BONES) {
                if (processor.getBone(name) == null) {
                    missing.add(name);
                }
            }

            boolean[] lagBone = new boolean[BONES.size()];
            for (Lag lag : LAGS) {
                Integer index = boneIndex.get(lag.bone());
                if (index != null) {
                    lagBone[index] = true;
                }
            }

            List<BoneSlot> slots = new ArrayList<>(NAMED_BONES.size() + STRANDS.size());
            for (String name : NAMED_BONES) {
                GeoBone bone = processor.getBone(name);
                int kind = kindForNamed(name);
                if (bone != null && kind != KIND_IGNORE) {
                    slots.add(new BoneSlot(bone, kind, -1));
                }
            }
            for (int i = 0; i < STRANDS.size(); i++) {
                GeoBone bone = processor.getBone(STRANDS.get(i).bone());
                if (bone != null) {
                    slots.add(new BoneSlot(bone, KIND_STRAND, i));
                }
            }

            List<LagSlot> lags = new ArrayList<>(LAGS.size());
            int chainIndex = 0;
            for (Lag lag : LAGS) {
                GeoBone bone = processor.getBone(lag.bone());
                GeoBone parent = processor.getBone(lag.parent());
                Integer boneId = boneIndex.get(lag.bone());
                if (bone == null || boneId == null) {
                    continue;
                }
                TowerGhastPhysics.SecondOrderCoefficients coefficients =
                    TowerGhastPhysics.secondOrderCoefficients(lag.frequency(), lag.coefficient(), 0.0);
                lags.add(new LagSlot(
                    bone, parent, chainIndex++, boneId, lag.axis(), lag.parentAxis(), lag.subtractAxis(),
                    lag.sign(), lag.parentGain(), coefficients,
                    lagAncestors(parent, boneIndex, lagBone)));
            }

            GeoBone sentinel = processor.getRegisteredBones().stream().findFirst().orElse(null);
            return new BoneLayout(
                slots.toArray(BoneSlot[]::new),
                lags.toArray(LagSlot[]::new),
                processor.getBone(HEAD_BONE),
                sentinel == null ? null : sentinel.getName(),
                sentinel,
                missing,
                BONES.size());
        }

        private boolean isCurrent(GeoModel<?> model) {
            if (this.sentinelName == null) {
                return model.getAnimationProcessor().getRegisteredBones().isEmpty();
            }
            return model.getAnimationProcessor().getBone(this.sentinelName) == this.sentinel;
        }

        /**
         * 收集父骨骼到根之间真正参与链条的骨骼下标
         *
         * <p>只有 {@code LAGS} 里的骨骼会被 {@code setChainPose} 写值，其余祖先的贡献恒为 0
         * 所以不必把它们也放进求和列表
         */
        private static int[] lagAncestors(GeoBone parent, Map<String, Integer> boneIndex, boolean[] lagBone) {
            List<Integer> ancestors = new ArrayList<>();
            for (GeoBone current = parent; current != null; current = current.getParent()) {
                Integer index = boneIndex.get(current.getName());
                if (index != null && lagBone[index]) {
                    ancestors.add(index);
                }
            }
            int[] result = new int[ancestors.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = ancestors.get(i);
            }
            return result;
        }
    }

    private static final class BoneSlot {
        private final GeoBone bone;
        private final int kind;
        private final int strandIndex;

        private BoneSlot(GeoBone bone, int kind, int strandIndex) {
            this.bone = bone;
            this.kind = kind;
            this.strandIndex = strandIndex;
        }
    }

    private static final class LagSlot {
        private final GeoBone bone;
        private final GeoBone parent;
        private final int chainIndex;
        private final int boneIndex;
        private final int axis;
        private final int parentAxis;
        private final int subtractAxis;
        private final double sign;
        private final double parentGain;
        private final double k1;
        private final double k2;
        private final double k3;
        private final double step;
        private final int cycles;
        private final int[] ancestors;

        private LagSlot(GeoBone bone, GeoBone parent, int chainIndex, int boneIndex,
                        int axis, int parentAxis, int subtractAxis, double sign, double parentGain,
                        TowerGhastPhysics.SecondOrderCoefficients coefficients, int[] ancestors) {
            this.bone = bone;
            this.parent = parent;
            this.chainIndex = chainIndex;
            this.boneIndex = boneIndex;
            this.axis = axis;
            this.parentAxis = parentAxis;
            this.subtractAxis = subtractAxis;
            this.sign = sign;
            this.parentGain = parentGain;
            this.k1 = coefficients.k1();
            this.k2 = coefficients.k2();
            this.k3 = coefficients.k3();
            this.step = coefficients.step();
            this.cycles = coefficients.cycles();
            this.ancestors = ancestors;
        }
    }
}
