package com.dawnduskterminal.client;

import com.dawnduskterminal.entity.GhastEliteEntity;
import com.dawnduskterminal.entity.TowerGhastPhysics;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * 大恶魂的逐帧骨骼写入：两张脸 + 触手与腿的摆动
 *
 * <p>模型包给的两条片段都是 Yes Steve Model 的物理层：{@code parallel0} 用
 * {@code ysm.perlin_noise} 摆触手与腿根，{@code parallel1} 把各段触手与腿的旋转写成
 * {@code v.ribbonphy_*_rot} —— 后者是 YSM 宿主每帧算出来的「布条物理」变量，GeckoLib 既不会赋值，
 * 也不存在跨帧状态，直接播放只会把整条链摆成笔直。因此：
 *
 * <ul>
 *   <li>{@code parallel0} 照常播放（转换器已把它改写成 GeckoLib 能编译的形式），
 *       但其中的 perlin 通道被转换器归零了，所以这里按原式用
 *       {@link TowerGhastPhysics#perlinNoise} 逐帧补回去 —— 值与源表达式一一对应
 *   <li>{@code parallel1} 不播放；各段链的滞后改为「按根骨角度的比例弯曲」，
 *       观感上是一条跟着根摆动的软链
 * </ul>
 *
 * <p>两张脸是贴在脸前的薄片：{@code annoyed}（有索敌）与 {@code atack}（蓄力中，模型里就是这么拼的）。
 * 状态取实体自己同步过来的 {@link GhastEliteEntity#getFace()}，不直接读 {@code getTarget()}
 *
 * <p>必须挂在 {@code GeoModel.setCustomAnimations}：它是 {@code handleAnimations} 的最后一步，
 * 夹在「动画写入骨骼」与「骨骼矩阵被读走」之间，理由与 {@link TowerGhastBonePose} 相同
 */
final class GhastEliteBonePose {
    void apply(GhastEliteEntity entity, GeoModel<GhastEliteEntity> model, float partialTick) {
        GhastEliteEntity.Face face = entity.getFace();
        model.getBone("annoyed").ifPresent(bone -> bone.setHidden(face != GhastEliteEntity.Face.ANNOYED));
        model.getBone("atack").ifPresent(bone -> bone.setHidden(face != GhastEliteEntity.Face.ATTACK));

        double life = (entity.tickCount + partialTick) / 20.0D;
        this.poseTentacle(model, "lefttentacle", 0.0D,
            -10.0D - 20.0D * TowerGhastPhysics.perlinNoise(325, life, 0.0D, 0.0D),
            10.0D + 20.0D * TowerGhastPhysics.perlinNoise(325, 0.0D, life, 0.0D));
        this.poseTentacle(model, "righttentacle", 0.0D,
            10.0D - 20.0D * TowerGhastPhysics.perlinNoise(325, 0.0D, 0.0D, life),
            -10.0D + 20.0D * TowerGhastPhysics.perlinNoise(325, life, 325.0D, 0.0D));
        this.poseChain(model, "leg_M", new double[] {
            7.5D - 20.0D * TowerGhastPhysics.perlinNoise(1919810, life, 0.0D, 0.0D),
            0.0D,
            15.0D * TowerGhastPhysics.perlinNoise(1919810, 0.0D, life, 0.0D)});
        this.poseChain(model, "leg_F_L", new double[] {
            7.5D - 20.0D * TowerGhastPhysics.perlinNoise(59160153, life, 0.0D, 0.0D),
            0.0D,
            15.0D * TowerGhastPhysics.perlinNoise(59160153, 0.0D, life, 0.0D)});
        this.poseChain(model, "leg_F_R", new double[] {
            7.5D - 20.0D * TowerGhastPhysics.perlinNoise(114514, life, 0.0D, 0.0D),
            0.0D,
            15.0D * TowerGhastPhysics.perlinNoise(114514, 0.0D, life, 0.0D)});
        this.poseChain(model, "leg_B_L", new double[] {
            7.5D - 20.0D * TowerGhastPhysics.perlinNoise(59160153, 0.0D, 0.0D, life),
            0.0D,
            15.0D * TowerGhastPhysics.perlinNoise(1919810, 0.0D, 0.0D, life)});
        this.poseChain(model, "leg_B_R", new double[] {
            7.5D - 20.0D * TowerGhastPhysics.perlinNoise(114514, 0.0D, 0.0D, life),
            0.0D,
            15.0D * TowerGhastPhysics.perlinNoise(3601, 0.0D, life, 0.0D)});
    }

    /** 触手：根写死角度，两节后续段按比例递减，弯成一条跟着根摆的软链 */
    private void poseTentacle(GeoModel<GhastEliteEntity> model, String root, double x, double y, double z) {
        this.setDegrees(model, root, x, y, z);
        this.setDegrees(model, root + "2", x * 0.6D, y * 0.6D, z * 0.6D);
        this.setDegrees(model, root + "3", x * 0.35D, y * 0.35D, z * 0.35D);
    }

    /** 腿：根角度 + 两节递减 */
    private void poseChain(GeoModel<GhastEliteEntity> model, String root, double[] angles) {
        this.setDegrees(model, root, angles[0], angles[1], angles[2]);
        this.setDegrees(model, root + "_2", angles[0] * 0.6D, angles[1] * 0.6D, angles[2] * 0.6D);
        this.setDegrees(model, root + "_3", angles[0] * 0.35D, angles[1] * 0.35D, angles[2] * 0.35D);
    }

    private void setDegrees(GeoModel<GhastEliteEntity> model, String bone, double x, double y, double z) {
        model.getBone(bone).ifPresent(geoBone -> apply(geoBone, x, y, z));
    }

    private static void apply(GeoBone bone, double x, double y, double z) {
        bone.setRotX((float) Math.toRadians(x));
        bone.setRotY((float) Math.toRadians(y));
        bone.setRotZ((float) Math.toRadians(z));
    }
}
