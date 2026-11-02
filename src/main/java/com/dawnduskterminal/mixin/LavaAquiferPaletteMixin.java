package com.dawnduskterminal.mixin;

import com.dawnduskterminal.registry.ModScorchingTwilight;
import com.dawnduskterminal.world.ChronoDimensionProbe;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 深部「熔岩含水层」是第二处写死的 {@code Blocks.LAVA}，数据包够不到
 *
 * <p>原版 {@code Aquifer.NoiseBasedAquifer.computeFluidType} 会把流体表面高度在 {@code -10} 及以下、
 * 且 {@code aquifer_lava} 噪声超过 0.3 的含水层格子升级成 {@code Blocks.LAVA}。
 * {@link LavaBandPaletteMixin} 只替换了 {@code NoiseBasedChunkGenerator.createFluidPicker} 产出的
 * {@code y < -54} 那一段；第二处仍会在晨昏线维度里放出原版 {@code minecraft:lava}，所以这里换成
 * enthu_fluid
 *
 * <p>维度靠流到本方法的流体类型来识别（见 {@link ChronoDimensionProbe#isChronoFluid}）：晨昏线维度里
 * 全局流体选择器（已被 {@link LavaBandPaletteMixin} 打过补丁）只会返回 portal_fluid 或 enthu_fluid，
 * 绝不会是原版的水或岩浆。其它流体一律原样走原版逻辑。原版判断 {@code status.fluidType != Blocks.LAVA}
 * 在这里变成「不是 enthu_fluid」，所以本来就是 enthu_fluid 的深部带不会被二次升级
 */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class LavaAquiferPaletteMixin {
    @Shadow
    private DensityFunction lavaNoise;

    @Inject(method = "computeFluidType", at = @At("HEAD"), cancellable = true)
    private void ddt$lavaAquiferPalette(
            int x, int y, int z, Aquifer.FluidStatus status, int fluidLevel, CallbackInfoReturnable<BlockState> cir) {
        BlockState globalType = ((FluidStatusAccessor) (Object) status).ddt$fluidType();
        if (!ChronoDimensionProbe.isChronoFluid(globalType)) {
            return; // 不是晨昏线维度，走原版逻辑
        }
        BlockState blockstate = globalType;
        if (fluidLevel <= -10
                && fluidLevel != DimensionType.WAY_BELOW_MIN_Y
                && !globalType.is(ModScorchingTwilight.BLOCK.get())) {
            double d0 = this.lavaNoise.compute(new DensityFunction.SinglePointContext(
                    Mth.floorDiv(x, 64), Mth.floorDiv(y, 40), Mth.floorDiv(z, 64)));
            if (Math.abs(d0) > 0.3) {
                blockstate = ModScorchingTwilight.BLOCK.get().defaultBlockState();
            }
        }
        cir.setReturnValue(blockstate);
    }
}
