package com.dawnduskterminal.mixin;

import com.dawnduskterminal.world.ChronoDimensionProbe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让晨昏线的石柱洞穴（dawnduskterminal:overworld/caves/pillars &lt; 0）保持干燥
 *
 * <p>噪声设置已经把洞穴内的 {@code fluid_level_floodedness} 压到 -1，普通含水层因此被抑制。但这个
 * 信号管不到海洋与河流灌水：原版 {@code Aquifer.NoiseBasedAquifer.computeFluid} 在表面采样循环里就
 * 提前返回了海洋/河流流体，远早于 {@code computeSurfaceLevel} 去读 floodedness。所以本 mixin 拦截
 * {@code computeSubstance}，也就是每个被雕刻的方块都要过的唯一入口，凡是 floodedness 标记
 * （{@code <= -1}）指向石柱洞穴的位置都拒绝放流体，把这些格子留成空气。石柱函数本身不动
 *
 * <p>返回 {@link BlockState} 的 {@code null} 不等于空气：区块填充器把 null 当作「保留默认方块」，也
 * 就是实心石头。空气必须显式返回 {@code Blocks.AIR.defaultBlockState()}，否则洞穴会被填回去
 */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class PillarsFluidBlockMixin {
    @Shadow
    private DensityFunction fluidLevelFloodednessNoise;

    @Shadow
    private Aquifer.FluidPicker globalFluidPicker;

    @Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
    private void ddt$blockPillarsFluid(
            DensityFunction.FunctionContext context, double density, CallbackInfoReturnable<BlockState> cir) {
        if (density > 0.0) {
            return; // 实心地形，原版本来就返回 null，没有什么要抽干
        }
        if (!ddt$isChrono(context)) {
            return;
        }
        DensityFunction.SinglePointContext single =
                new DensityFunction.SinglePointContext(context.blockX(), context.blockY(), context.blockZ());
        if (this.fluidLevelFloodednessNoise.compute(single) <= -1.0) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Unique
    private boolean ddt$isChrono(DensityFunction.FunctionContext context) {
        Aquifer.FluidStatus status =
                this.globalFluidPicker.computeFluid(context.blockX(), context.blockY(), context.blockZ());
        BlockState fluidType = ((FluidStatusAccessor) (Object) status).ddt$fluidType();
        return ChronoDimensionProbe.isChronoFluid(fluidType);
    }
}
