package com.dawnduskterminal.mixin;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoLavaLevelMixin {
    @Inject(method = "createFluidPicker", at = @At("HEAD"), cancellable = true)
    private static void ddt$noLavaLevel(NoiseGeneratorSettings settings,
            CallbackInfoReturnable<Aquifer.FluidPicker> cir) {
        if (!settings.defaultFluid().isAir()) {
            return;
        }
        Aquifer.FluidStatus air = new Aquifer.FluidStatus(-63, Blocks.AIR.defaultBlockState());
        cir.setReturnValue((x, y, z) -> air);
    }
}
