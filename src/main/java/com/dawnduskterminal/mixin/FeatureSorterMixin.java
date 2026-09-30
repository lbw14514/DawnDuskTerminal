package com.dawnduskterminal.mixin;

import com.dawnduskterminal.compat.FeatureOrderCompat;
import com.dawnduskterminal.config.DdtConfig;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Function;

@Mixin(FeatureSorter.class)
public class FeatureSorterMixin {
    @Inject(method = "buildFeaturesPerStep", at = @At("HEAD"), cancellable = true)
    private static <T> void dawnduskterminal$tolerateFeatureCycles(
            List<T> biomes,
            Function<T, List<HolderSet<PlacedFeature>>> features,
            boolean reportCycle,
            CallbackInfoReturnable<List<FeatureSorter.StepFeatureData>> callback) {
        if (!DdtConfig.tolerateFeatureCycles()) {
            return;
        }
        callback.setReturnValue(FeatureOrderCompat.buildFeaturesPerStep(biomes, features));
    }
}
