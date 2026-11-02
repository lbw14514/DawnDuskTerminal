package com.dawnduskterminal.mixin;

import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.world.ChronoLine;
import com.dawnduskterminal.world.ChronoLineState;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把原版的太阳轨道转到晨昏线的法向上
 *
 * <p>原版太阳永远从同一个方位升起（{@code mulPose(Axis.YP.rotationDegrees(-90))} 之后轨道落在 X 轴上），
 * 与这个维度的晨昏线没有关系。{@link ClientLevelTimeOfDayMixin} 只管太阳的高度，方位得在这里补
 *
 * <p>两处插入点都在 {@code PoseStack.pushPose()} 之后，垫一层绕世界 Y 轴的旋转：
 * 第一次在日落光晕的 pose 里（那个圆环朝太阳方向鼓起来，不转就会指错边），第二次在日月的 pose 里。
 * 星空共用第二次的 pose，星图是均匀球面，转了看不出来
 *
 * <p>yaw 的取值让原版 {@code t=0.25}（太阳落在地平线上）时的方位 {@code (-1, 0)} 转到法向
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyAlignMixin {
    private static final String RENDER_SKY =
            "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V";

    @Inject(
            method = RENDER_SKY,
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                            ordinal = 0,
                            shift = At.Shift.AFTER))
    private void ddt$alignSunsetGlow(CallbackInfo ci, @Local PoseStack poseStack) {
        ddt$yawToLineNormal(poseStack);
    }

    @Inject(
            method = RENDER_SKY,
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                            ordinal = 1,
                            shift = At.Shift.AFTER))
    private void ddt$alignSunAndMoon(CallbackInfo ci, @Local PoseStack poseStack) {
        ddt$yawToLineNormal(poseStack);
    }

    private static void ddt$yawToLineNormal(PoseStack poseStack) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(ModDimensions.CHRONO)) {
            return;
        }
        ChronoLine line = ChronoLineState.clientLine();
        double length = Math.hypot(line.normalX(), line.normalZ());
        if (length < 1.0E-6D) {
            return;
        }
        double yaw = Math.toDegrees(Math.atan2(line.normalZ() / length, -line.normalX() / length));
        poseStack.mulPose(Axis.YP.rotationDegrees((float) yaw));
    }
}
