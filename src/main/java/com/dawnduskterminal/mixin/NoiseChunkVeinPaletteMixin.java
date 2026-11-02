package com.dawnduskterminal.mixin;

import com.dawnduskterminal.world.ChronoDimensionProbe;
import com.dawnduskterminal.world.ChronoVeinPalette;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 包住区块最终拼好的方块规则，让大矿脉不再铺原版花岗岩与凝灰岩
 *
 * <p>原版在 {@code NoiseChunk} 构造函数里拼出这条规则，并把 {@code OreVeinifier.create(...)} 折进去；
 * {@code OreVeinifier.VeinType} 把 {@code GRANITE} 与 {@code TUFF} 写死成矿脉填充物，数据包够不到。
 * 包住拼好的规则正好覆盖它们，又不碰数据包已经处理的部分
 *
 * <p>识别维度的方式见 {@link ChronoDimensionProbe}
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkVeinPaletteMixin {
    @Shadow
    @Final
    @Mutable
    private NoiseChunk.BlockStateFiller blockStateRule;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ddt$paletteVeinFillers(
            int cellCountXZ,
            RandomState randomState,
            int blockX,
            int blockZ,
            NoiseSettings noiseSettings,
            DensityFunctions.BeardifierOrMarker beardifier,
            NoiseGeneratorSettings settings,
            Aquifer.FluidPicker fluidPicker,
            Blender blender,
            CallbackInfo ci) {
        if (!ChronoDimensionProbe.isChrono(settings)) {
            return;
        }
        this.blockStateRule = ChronoVeinPalette.wrap(this.blockStateRule);
    }
}
