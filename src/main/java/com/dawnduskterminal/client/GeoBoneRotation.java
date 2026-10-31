package com.dawnduskterminal.client;

import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * 把动画文件里的旋转角度写到 {@link GeoBone} 上：符号与单位只在这里定义一次
 *
 * <p>动画文件里写的是「度、相对初始姿势」，而 {@link GeoBone} 的 rotX / rotY 与它镜像。GeckoLib
 * 自己读关键帧时先把常量换成弧度再把 X 与 Y 取反（{@code BakedAnimationsAdapter} 的 205-207 行），
 * 表达式那条路径在 {@code AnimationController.getAnimationPointAtTick} 的 745-758 行做同样处理，
 * 两者都不取反 Z。任何逐帧写回骨骼的代码都必须照抄这条映射，否则该骨骼朝反侧摆——触手的偏航与
 * 腿的俯仰正属于这一类。
 *
 * <p>{@link #set} 与 {@link #setAxis} 会加上初始姿势，与 {@code AnimationProcessor} 的
 * {@code initialSnapshot.getRotX() + 动画值} 一致。{@link #add} 是叠加式写入，用于 YSM 并行动画
 * 那种加法混合（{@link TowerGhastBonePose} 的次级运动需要它）。
 */
public final class GeoBoneRotation {
    private GeoBoneRotation() {}

    /** 三轴一起写，入参为动画文件里的度数 */
    public static void set(GeoBone bone, double xDegrees, double yDegrees, double zDegrees) {
        BoneSnapshot initial = bone.getInitialSnapshot();
        bone.setRotX(initial.getRotX() - radians(xDegrees));
        bone.setRotY(initial.getRotY() - radians(yDegrees));
        bone.setRotZ(initial.getRotZ() + radians(zDegrees));
    }

    /** 只写一根轴，{@code axis} 为 0 / 1 / 2，入参为动画文件里的度数 */
    public static void setAxis(GeoBone bone, int axis, double degrees) {
        BoneSnapshot initial = bone.getInitialSnapshot();
        switch (axis) {
            case 0 -> bone.setRotX(initial.getRotX() - radians(degrees));
            case 1 -> bone.setRotY(initial.getRotY() - radians(degrees));
            default -> bone.setRotZ(initial.getRotZ() + radians(degrees));
        }
    }

    /** 在当前姿势上叠加，符号与 {@link #setAxis} 一致 */
    public static void add(GeoBone bone, int axis, double degrees) {
        switch (axis) {
            case 0 -> bone.setRotX(bone.getRotX() - radians(degrees));
            case 1 -> bone.setRotY(bone.getRotY() - radians(degrees));
            default -> bone.setRotZ(bone.getRotZ() + radians(degrees));
        }
    }

    private static float radians(double degrees) {
        return (float) Math.toRadians(degrees);
    }
}
