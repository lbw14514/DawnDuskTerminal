package com.dawnduskterminal.client.animation.functions;

import com.dawnduskterminal.client.animation.struct.Vec3fStruct;
import com.dawnduskterminal.geckolib3.core.molang.context.IContext;
import com.dawnduskterminal.geckolib3.core.molang.function.entity.EntityFunction;
import com.dawnduskterminal.geckolib3.core.processor.AnimationProcessor;
import com.dawnduskterminal.geckolib3.core.processor.IBone;
import com.dawnduskterminal.geckolib3.geo.animated.AnimatedGeoBone;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoBone;
import com.dawnduskterminal.molang.runtime.ExecutionContext;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code ysm.bone_pivot_abs(骨骼名)}：骨骼当前的绝对 pivot，Bedrock 坐标
 *
 * <p>这不是静态值。第 2、3 段的 pivot 依赖上一段自己的旋转，而模型正是靠这条反馈做布条与触手
 * 的链式回复力（源表达式每帧读它当作「质点的当前位置」）。若返回静止 pivot，整条链会退化成
 * 一块刚性平面；若返回 0（官方 3.0-dev 与 OpenYSM 的 `bone_pivot_abs` 都是占位实现），
 * 辅助骨骼的基矩阵会变成反射，触手翻到 90~150 度。
 *
 * <p>x 不取负：YSM 内部骨骼空间是镜像的，Molang 访问器负责换回作者空间（`ContextReads#boneRotation`
 * 对 X、Y 取负即可见），`bone_pivot_abs` 属于同一族。源表达式自己写了 `-ysm.bone_pivot_abs(...).x`。
 *
 * <p>累积方式：从根骨骼的静止 pivot 出发，逐级加上「父骨骼当前旋转作用在本段偏移上的结果」，
 * 旋转按 {@code Rz·Ry·Rx} 合成，角度取骨骼当前值（X、Y 取反换回 Bedrock 空间）。这与 GeckoLib
 * 合成骨骼旋转的顺序一致。
 *
 * <p>每次调用都重算，不做缓存：骨骼深度不超过 4 层，而这些通道每帧只有几百次。
 */
public class BonePivotAbs extends EntityFunction {
    @Override
    protected Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        String name = arguments.getAsString(context, 0);
        if (name == null || name.isEmpty()) {
            return null;
        }
        AnimationProcessor<?> processor = context.entity().geoInstance().getAnimationProcessor();
        if (!(processor.getBone(name) instanceof AnimatedGeoBone animated)) {
            return null;
        }

        List<GeoBone> chain = new ArrayList<>();
        for (GeoBone bone = animated.geoBone(); bone != null; bone = bone.parent()) {
            chain.add(bone);
        }

        GeoBone root = chain.get(chain.size() - 1);
        double[] pivot = restPivot(root);
        double[] rotation = new double[3];
        for (int index = chain.size() - 2; index >= 0; index--) {
            GeoBone parent = chain.get(index + 1);
            GeoBone child = chain.get(index);
            add(rotation, bedrockDegrees(processor.getBone(parent.name())));
            double[] offset = restPivot(child);
            // 偏移 = 本骨骼静止 pivot 减父骨骼静止 pivot，再绕父骨骼的累积旋转转过去
            double[] base = restPivot(parent);
            offset[0] -= base[0];
            offset[1] -= base[1];
            offset[2] -= base[2];
            double[] rotated = rotate(rotation, offset);
            pivot[0] += rotated[0];
            pivot[1] += rotated[1];
            pivot[2] += rotated[2];
        }
        return new PivotStruct(pivot[0], pivot[1], pivot[2]);
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }

    /** 静止 pivot，Bedrock 坐标（x 不取负） */
    private static double[] restPivot(GeoBone bone) {
        Vector3f pivot = bone.pivot();
        return new double[] {-pivot.x, pivot.y, pivot.z};
    }

    /** 骨骼当前旋转，换成 Bedrock 空间的度数：X、Y 取反，Z 不变 */
    private static double[] bedrockDegrees(IBone bone) {
        if (bone == null) {
            return new double[3];
        }
        return new double[] {
            -Math.toDegrees(bone.getRotationX()),
            -Math.toDegrees(bone.getRotationY()),
            Math.toDegrees(bone.getRotationZ())};
    }

    private static void add(double[] target, double[] value) {
        target[0] += value[0];
        target[1] += value[1];
        target[2] += value[2];
    }

    /** {@code Rz·Ry·Rx} 作用在列向量上 */
    private static double[] rotate(double[] degrees, double[] vector) {
        double x = Math.toRadians(degrees[0]);
        double y = Math.toRadians(degrees[1]);
        double z = Math.toRadians(degrees[2]);
        double cx = Math.cos(x), sx = Math.sin(x);
        double cy = Math.cos(y), sy = Math.sin(y);
        double cz = Math.cos(z), sz = Math.sin(z);
        return new double[] {
            (cz * cy) * vector[0] + (cz * sy * sx - sz * cx) * vector[1] + (cz * sy * cx + sz * sx) * vector[2],
            (sz * cy) * vector[0] + (sz * sy * sx + cz * cx) * vector[1] + (sz * sy * cx - cz * sx) * vector[2],
            (-sy) * vector[0] + (cy * sx) * vector[1] + (cy * cx) * vector[2]};
    }

    private static final class PivotStruct extends Vec3fStruct {
        private final double x;
        private final double y;
        private final double z;

        private PivotStruct(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        protected float getX() {
            return (float) this.x;
        }

        @Override
        protected float getY() {
            return (float) this.y;
        }

        @Override
        protected float getZ() {
            return (float) this.z;
        }
    }
}
