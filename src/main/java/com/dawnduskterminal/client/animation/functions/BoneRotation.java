package com.dawnduskterminal.client.animation.functions;

import com.dawnduskterminal.client.animation.struct.Vec3fStruct;
import com.dawnduskterminal.geckolib3.core.molang.context.IContext;
import com.dawnduskterminal.geckolib3.core.processor.AnimationProcessor;
import com.dawnduskterminal.geckolib3.geo.animated.AnimatedGeoBone;
import com.dawnduskterminal.geckolib3.core.molang.function.entity.EntityFunction;
import com.dawnduskterminal.molang.runtime.ExecutionContext;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * {@code ysm.bone_rot(骨骼名)}：骨骼的旋转，度数，X 与 Y 取反
 *
 * <p>取的是<strong>骨骼自己的</strong>旋转（初始姿势加动画与并行层的写入），与 YSM 上游一致
 * （{@code ContextReads#boneRotation}）。曾经试过改成「累积到根骨骼」的旋转，想让布条在直走时也有摆动，
 * 结果是逐级相加把四级链条卷成一团：每一级写的是 {@code second_order(父) - 父} 的滞后量，
 * 而累积输入让这个滞后量里也含进了整条身体链的旋转，末端叠加后远超模型能接受的角度。
 *
 * <p>旧实现（{@code client/TowerGhastBonePose}）之所以能读累积角，是因为它给每条通道单独配了增益，
 * 而不是照搬模型包的表达式。
 */
public final class BoneRotation extends EntityFunction {
    @Override
    protected Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        String name = arguments.getAsString(context, 0);
        if (name == null || name.isEmpty()) {
            return null;
        }
        AnimationProcessor<?> processor = context.entity().geoInstance().getAnimationProcessor();
        if (!(processor.getBone(name) instanceof AnimatedGeoBone bone)) {
            return null;
        }
        return new RotationStruct(-Math.toDegrees(bone.getRotationX()),
            -Math.toDegrees(bone.getRotationY()), Math.toDegrees(bone.getRotationZ()));
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }

    private static final class RotationStruct extends Vec3fStruct {
        private final double x;
        private final double y;
        private final double z;

        private RotationStruct(double x, double y, double z) {
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
