package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.StringEntity;
import com.dawnduskterminal.client.animation.YsmNoise;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.dawnduskterminal.geckolib3.core.processor.IBone;
import com.dawnduskterminal.geckolib3.geo.animated.AnimatedGeoBone;
import com.dawnduskterminal.geckolib3.geo.animated.AnimatedGeoModel;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoMesh;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 「弦」的两个源动画片段全部由 YSM 的状态查询构成，转换器只能把它们压成 0，于是这里按原式重算
 *
 * <h2>源动画在做什么</h2>
 *
 * <ul>
 *   <li>{@code parallel1}：每个 {@code spokeN} 用 {@code ysm.perlin_noise} 加 {@code math.random}
 *       做小幅摆动，{@code root} 用柏林噪声歪两轴并按 {@code 60*life_time} 度持续自转
 *   <li>{@code parallel0}：每根 {@code segimentN} 连接相邻两个 {@code segimentlocatorN}，
 *       位置取后者的绝对枢轴，朝向取两点连线，{@code segiment_YN} 的 Y 缩放取连线长度比
 * </ul>
 *
 * <p>第二段就是一条闭合的八边形或十二边形绳环：弦长与模型里立方体的高度
 * （12.2459 / 20.7055）逐位相等，所以只要方向算对，缩放就是 1 附近的值。
 *
 * <h2>为什么在 {@code setCustomAnimations} 里写</h2>
 *
 * <p>GeckoLib 的渲染顺序是 {@code tickAnimation} 先按动画写骨骼、{@code setCustomAnimations}
 * 再执行、之后才读骨骼矩阵。写在这里是动画结果与矩阵之间唯一的位置，和高塔恶魂的次级运动同一套接法。
 * 这套实体没有注册任何控制器，动画文件是空壳，骨骼每帧完全由这里决定。
 *
 * <h2>方向解算</h2>
 *
 * <p>GeckoLib 的骨骼旋转是 {@code Quaternionf.rotationZYX(z, y, x)}，即矩阵
 * {@code Rz * Ry * Rx}。令 {@code v = Rz(z) * Rx(x) * (0,1,0)}，有
 *
 * <pre>
 * v = (-sin(z)cos(x), cos(z)cos(x), sin(x))
 * </pre>
 *
 * <p>反过来解连线 {@code D} 就是 {@code x = asin(D.z/|D|)}、
 * {@code z = atan2(-D.x, D.y)}。用这个公式算出来的弦长与模型常数完全吻合，
 * 不是凑出来的经验值。
 *
 * <h2>单位</h2>
 *
 * <p>源表达式是度，并且 GeckoLib 在动画求值时会先取弧度再把 X 与 Y 取反。本类写在 Java 侧，
 * 需要自己照做：{@code gx = -toRadians(xDeg)}、{@code gy = -toRadians(yDeg)}、{@code gz = toRadians(zDeg)}。
 * 骨骼位置与缩放是模型单位（枢轴 16 或 40 即 1 格或 2.5 格半径），位置渲染时 X 会取反，
 * 所以 {@code posX = -locator.x}。
 */
public final class StringPose {
    private static final int ROOT_SEED = 514;
    private static final int[] SPOKE_SEEDS = {37, 114, 53, 71, 89, 131, 173, 211, 251, 293, 337, 383};
    /** 小型与大型的立方体高度，模型里没有这两个骨骼的立方体时才用这个兜底 */
    private static final float SMALL_SEGMENT_LENGTH = 12.2459F;
    private static final float LARGE_SEGMENT_LENGTH = 20.7055F;

    /** 每个实体一份逐 tick 的随机量，渲染帧之间用 partialTick 插值 */
    private final Map<StringEntity, PoseState> states = new WeakHashMap<>();

    private Layout cachedLayout;

    /** 本帧复用的中间对象，渲染线程私有 */
    private final Quaternionf rootRotation = new Quaternionf();
    private final Quaternionf spokeRotation = new Quaternionf();
    private final Vector3f locator = new Vector3f();

    public void apply(StringEntity entity, AnimatedGeoModel model, float partialTick) {
        Layout layout = layout(model);
        if (layout == null || layout.count == 0) {
            return;
        }
        PoseState state = this.states.computeIfAbsent(entity, unused -> new PoseState());

        if (state.lastTick != entity.tickCount) {
            state.advance(entity, layout);
            state.lastTick = entity.tickCount;
        }

        double life = (entity.tickCount + partialTick) / 20.0D;

        // root：两轴柏林噪声 + 绕 Z 的匀速自转，源式子是 60*life_time 度
        double rootXDeg = -30.0D * YsmNoise.perlin(ROOT_SEED, 1.0D, life, 2.0D);
        double rootYDeg = -30.0D * YsmNoise.perlin(ROOT_SEED, life, 1.0D, 2.0D);
        double rootZDeg = 60.0D * life;
        GeoBoneRotation.set(layout.root, rootXDeg, rootYDeg, rootZDeg);
        this.rootRotation.rotationZYX(layout.root.getRotationZ(), layout.root.getRotationY(), layout.root.getRotationX());

        for (int i = 0; i < layout.count; i++) {
            IBone spoke = layout.spokes[i];
            int offset = i * PoseState.STRIDE;
            double randX = Mth.lerp(partialTick, state.prev[offset], state.curr[offset]);
            double randZ = Mth.lerp(partialTick, state.prev[offset + 1], state.curr[offset + 1]);
            double randScale = Mth.lerp(partialTick, state.prev[offset + 2], state.curr[offset + 2]);

            int seed = SPOKE_SEEDS[i];
            double pX = YsmNoise.perlin(seed, 1.0D, life, 2.0D);
            double pZ = YsmNoise.perlin(seed, 1.0D, 2.0D, life);
            double pScale = YsmNoise.perlin(seed, life, 1.0D, 2.0D);

            double spokeXDeg = -40.0D * pX - randX;
            double spokeZDeg = layout.zAmplitude * pZ + randZ;
            double scale = 0.3D * pScale + randScale;

            GeoBoneRotation.set(spoke, spokeXDeg, 0.0D, spokeZDeg);
            spoke.setScaleX((float) scale);
            spoke.setScaleY((float) scale);
            spoke.setScaleZ((float) scale);

            this.spokeRotation.rotationZYX(spoke.getRotationZ(), spoke.getRotationY(), spoke.getRotationX());
            this.locator.set(0.0F, layout.locatorPivotY, 0.0F)
                .mul((float) scale)
                .rotate(this.spokeRotation)
                .rotate(this.rootRotation);
            layout.locatorX[i] = this.locator.x;
            layout.locatorY[i] = this.locator.y;
            layout.locatorZ[i] = this.locator.z;
        }

        for (int i = 0; i < layout.count; i++) {
            int next = (i + 1) % layout.count;
            double dx = layout.locatorX[next] - layout.locatorX[i];
            double dy = layout.locatorY[next] - layout.locatorY[i];
            double dz = layout.locatorZ[next] - layout.locatorZ[i];
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

            IBone segment = layout.segments[i];
            segment.setPositionX(-layout.locatorX[i]);
            segment.setPositionY(layout.locatorY[i]);
            segment.setPositionZ(layout.locatorZ[i]);

            if (length < 1.0E-6D) {
                setRotation(segment, 0.0F, 0.0F, 0.0F);
            } else {
                float x = (float) Math.asin(Mth.clamp(dz / length, -1.0D, 1.0D));
                float z = (float) Math.atan2(-dx, dy);
                setRotation(segment, x, 0.0F, z);
            }

            IBone body = layout.bodies[i];
            float yScale = (float) (length / layout.nominalLength);
            body.setScaleX(1.0F);
            body.setScaleY(yScale);
            body.setScaleZ(1.0F);
        }
    }

    private Layout layout(AnimatedGeoModel model) {
        Layout layout = this.cachedLayout;
        if (layout != null && layout.isCurrent(model)) {
            return layout;
        }
        layout = Layout.build(model);
        this.cachedLayout = layout;
        return layout;
    }

    /**
     * 只接已经算好的弧度：弦段的朝向由 {@code asin} / {@code atan2} 直接给出、没有对应的角度表达，
     * 取反已在调用处完成。角度那条路径走 {@link GeoBoneRotation}
     */
    private static void setRotation(IBone bone, float x, float y, float z) {
        var initial = bone.getInitialSnapshot();
        bone.setRotationX(initial.rotationValueX + x);
        bone.setRotationY(initial.rotationValueY + y);
        bone.setRotationZ(initial.rotationValueZ + z);
    }

    /** 逐 tick 的随机项，两个 tick 之间插值，避免 20 Hz 的跳变 */
    private static final class PoseState {
        private static final int STRIDE = 3;
        private static final int LENGTH = SPOKE_SEEDS.length * STRIDE;

        private final double[] prev = new double[LENGTH];
        private final double[] curr = new double[LENGTH];
        private long lastTick = Long.MIN_VALUE;

        private void advance(StringEntity entity, Layout layout) {
            System.arraycopy(this.curr, 0, this.prev, 0, LENGTH);
            var random = entity.getRandom();
            int count = layout.count;
            double scaleMin = layout.large ? 0.8D : 0.85D;
            double scaleMax = layout.large ? 1.2D : 1.15D;
            for (int i = 0; i < count; i++) {
                int offset = i * STRIDE;
                this.curr[offset] = random.nextDouble() * 30.0D - 15.0D;
                this.curr[offset + 1] = random.nextDouble() * 15.0D - 7.5D;
                this.curr[offset + 2] = scaleMin + random.nextDouble() * (scaleMax - scaleMin);
            }
            if (this.lastTick == Long.MIN_VALUE) {
                System.arraycopy(this.curr, 0, this.prev, 0, LENGTH);
            }
        }
    }

    /** 模型里能查到的骨骼引用与常量，第一次换模型时建一次 */
    private static final class Layout {
        private final IBone sentinel;
        private final IBone root;
        private final IBone[] spokes;
        private final IBone[] segments;
        private final IBone[] bodies;
        private final int count;
        private final boolean large;
        private final float locatorPivotY;
        private final float nominalLength;
        private final double zAmplitude;
        private final float[] locatorX;
        private final float[] locatorY;
        private final float[] locatorZ;

        private Layout(IBone sentinel, IBone root, IBone[] spokes, IBone[] segments,
                       IBone[] bodies, int count, boolean large, float locatorPivotY,
                       float nominalLength, double zAmplitude) {
            this.sentinel = sentinel;
            this.root = root;
            this.spokes = spokes;
            this.segments = segments;
            this.bodies = bodies;
            this.count = count;
            this.large = large;
            this.locatorPivotY = locatorPivotY;
            this.nominalLength = nominalLength;
            this.zAmplitude = zAmplitude;
            this.locatorX = new float[Math.max(count, 1)];
            this.locatorY = new float[Math.max(count, 1)];
            this.locatorZ = new float[Math.max(count, 1)];
        }

        private boolean isCurrent(AnimatedGeoModel model) {
            return model.bones().get("root") == this.sentinel;
        }

        private static Layout build(AnimatedGeoModel model) {
            var processor = model.bones();
            IBone sentinel = processor.get("root");
            boolean large = processor.get("spoke12") != null;
            int count = large ? 12 : 8;

            IBone root = processor.get("root");
            IBone[] spokes = new IBone[count];
            IBone[] segments = new IBone[count];
            IBone[] bodies = new IBone[count];
            boolean complete = root != null;
            for (int i = 0; i < count; i++) {
                spokes[i] = processor.get("spoke" + (i + 1));
                segments[i] = processor.get("segiment_" + (i + 1));
                bodies[i] = processor.get("segiment_Y_" + (i + 1));
                complete &= spokes[i] != null && segments[i] != null && bodies[i] != null;
            }
            if (!complete) {
                DawnDuskTerminal.LOGGER.warn(
                    "DawnDuskTerminal 弦的模型缺少 root/spoke/segiment 骨骼，姿态层不会生效");
                return new Layout(sentinel, root, new IBone[0], new IBone[0], new IBone[0], 0,
                    false, 16.0F, SMALL_SEGMENT_LENGTH, 15.0D);
            }

            IBone locator = processor.get("segimentlocator1");
            float pivotY = locator instanceof AnimatedGeoBone animated ? animated.geoBone().pivot().y : (large ? 40.0F : 16.0F);
            float nominalLength = large ? LARGE_SEGMENT_LENGTH : SMALL_SEGMENT_LENGTH;
            IBone firstBody = bodies[0];
            if (firstBody instanceof AnimatedGeoBone animatedBody) {
                GeoMesh mesh = animatedBody.geoBone().cubes();
                if (mesh != null && mesh.getCubeCount() > 0 && mesh.dy(0).length() > 0.0F) {
                    nominalLength = mesh.dy(0).length();
                }
            }
            // 大型的 Z 轴摆动幅度是 10 度，小型是 15 度，见源文件两个 parallel1
            double zAmplitude = large ? 10.0D : 15.0D;
            return new Layout(sentinel, root, spokes, segments, bodies, count, large, pivotY,
                nominalLength, zAmplitude);
        }
    }
}
