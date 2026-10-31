package com.dawnduskterminal.client;

import com.dawnduskterminal.geckolib3.core.processor.IBone;
import com.dawnduskterminal.geckolib3.core.snapshot.BoneSnapshot;

/**
 * 把动画文件里的旋转角度写到骨骼上：符号与单位只在这里定义一次
 *
 * <p>动画文件里写的是「度、相对初始姿势」，而骨骼内部的 rotX / rotY 与它镜像。运行时读关键帧时
 * 先把常量换成弧度再把 X 与 Y 取反（{@code AnimationController}），任何逐帧写回骨骼的代码都必须
 * 照抄这条映射，否则该骨骼朝反侧摆——触手的偏航与腿的俯仰正属于这一类。
 *
 * <p>{@link #set} 会加上初始姿势，与 {@code AnimationProcessor} 的
 * {@code initialSnapshot.rotationValueX + 动画值} 一致；{@link #add} 是叠加式写入。
 */
public final class GeoBoneRotation {
    private GeoBoneRotation() {
    }

    /** 三轴一起写，入参为动画文件里的度数 */
    public static void set(IBone bone, double xDegrees, double yDegrees, double zDegrees) {
        BoneSnapshot initial = bone.getInitialSnapshot();
        bone.setRotationX(initial.rotationValueX - radians(xDegrees));
        bone.setRotationY(initial.rotationValueY - radians(yDegrees));
        bone.setRotationZ(initial.rotationValueZ + radians(zDegrees));
    }

    /** 只写一根轴，{@code axis} 为 0 / 1 / 2，入参为动画文件里的度数 */
    public static void setAxis(IBone bone, int axis, double degrees) {
        BoneSnapshot initial = bone.getInitialSnapshot();
        switch (axis) {
            case 0 -> bone.setRotationX(initial.rotationValueX - radians(degrees));
            case 1 -> bone.setRotationY(initial.rotationValueY - radians(degrees));
            default -> bone.setRotationZ(initial.rotationValueZ + radians(degrees));
        }
    }

    /** 在当前姿势上叠加，符号与 {@link #setAxis} 一致 */
    public static void add(IBone bone, int axis, double degrees) {
        switch (axis) {
            case 0 -> bone.setRotationX(bone.getRotationX() - radians(degrees));
            case 1 -> bone.setRotationY(bone.getRotationY() - radians(degrees));
            default -> bone.setRotationZ(bone.getRotationZ() + radians(degrees));
        }
    }

    private static float radians(double degrees) {
        return (float) Math.toRadians(degrees);
    }
}
