package com.dawnduskterminal.mixin;

import com.dawnduskterminal.registry.ModScorchingTwilight;
import com.dawnduskterminal.world.ChronoDimensionProbe;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 晨昏线调色板里唯一一块数据包够不到的地方
 *
 * <p>{@code NoiseBasedChunkGenerator.createFluidPicker} 把 {@code min(-54, seaLevel)} 以下整段写死成
 * {@code Blocks.LAVA}；那一段不是由 {@code default_fluid} 推导出来的，噪声设置改不动。它上面的一切
 * 已经来自 {@code settings.defaultFluid()}，而晨昏线设置把它设成了 portal_fluid，所以那部分调色板是
 * 齐的
 *
 * <p>这里靠噪声设置识别维度（见 {@link ChronoDimensionProbe}），因为作用域里没有 level。
 * 处理器直接接管整个方法而不是改一个参数，因为参数注入看不到外层方法的参数；万一原版改了这个选择
 * 器，{@code HEAD} 注入依然生效，跑的仍是这份拷贝，所以升级 Minecraft 时要盯的就是这一处
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class LavaBandPaletteMixin {
    @Inject(method = "createFluidPicker", at = @At("HEAD"), cancellable = true)
    private static void ddt$paletteLavaBand(
            NoiseGeneratorSettings settings, CallbackInfoReturnable<Aquifer.FluidPicker> cir) {
        if (!ChronoDimensionProbe.isChrono(settings)) {
            return;
        }
        Aquifer.FluidStatus lava =
                new Aquifer.FluidStatus(-54, ModScorchingTwilight.BLOCK.get().defaultBlockState());
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus fluid = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        cir.setReturnValue((x, y, z) -> y < Math.min(-54, seaLevel) ? lava : fluid);
    }
}
