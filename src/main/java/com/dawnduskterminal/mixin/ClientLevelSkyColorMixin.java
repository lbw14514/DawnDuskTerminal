package com.dawnduskterminal.mixin;

import com.dawnduskterminal.registry.ModDimensions;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 把这个维度的天穹压暗，否则原版的太阳看不见
 *
 * <p>原版日月是加色混合（{@code blendFuncSeparate(SRC_ALPHA, ONE, ONE, ZERO)}），只能往天空色上
 * 加。极昼侧的自定义群系把 {@code sky_color} 写成了近白色（{@code white_land} 是 {@code FFF9F0}），
 * 加到纯白上还是纯白，太阳和天空糊成一片
 *
 * <p>天穹色同时决定雾色（{@code FogRenderer.setupColor} 拿 {@code getSkyColor} 的结果去
 * {@code getBrightnessDependentFogColor}），所以压暗之后整片过曝的雾也一起降下来
 *
 * <p>乘的是常数，不是只挑白地群系：{@code white_land} 以外的亮色天空同样吃不住加色的太阳
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSkyColorMixin {
    private static final double SKY_BRIGHTNESS = 0.75D;

    @ModifyReturnValue(
            method = "getSkyColor(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"))
    private Vec3 ddt$dimChronoSky(Vec3 original) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (!level.dimension().equals(ModDimensions.CHRONO)) {
            return original;
        }
        return original.scale(SKY_BRIGHTNESS);
    }
}
