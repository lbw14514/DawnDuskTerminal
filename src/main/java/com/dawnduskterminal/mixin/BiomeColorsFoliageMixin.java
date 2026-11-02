package com.dawnduskterminal.mixin;

import com.dawnduskterminal.client.ChronoFoliageColors;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.ColorResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 包住 {@code BiomeColors.FOLIAGE_COLOR_RESOLVER}，让晨昏线维度移植来的暮色森林群系拿到暮色森林的
 * 树叶色（jungle 对应魔法森林，dark_forest 对应黑暗森林中心）
 *
 * <p>包装保留原取色器不动，只对这两个晨昏线群系 ID 改结果，所以暮色森林自己的 ASM 补丁与其他所有
 * 群系都不受影响
 */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsFoliageMixin {
    @Shadow
    @Final
    @Mutable
    public static ColorResolver FOLIAGE_COLOR_RESOLVER;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void ddt$wrapFoliageResolver(CallbackInfo ci) {
        ColorResolver original = FOLIAGE_COLOR_RESOLVER;
        FOLIAGE_COLOR_RESOLVER =
                (biome, x, z) -> ChronoFoliageColors.resolve(original.getColor(biome, x, z), biome, x, z);
    }
}
