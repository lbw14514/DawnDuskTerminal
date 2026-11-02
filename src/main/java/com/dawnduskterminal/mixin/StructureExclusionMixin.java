package com.dawnduskterminal.mixin;

import com.dawnduskterminal.structure.StructureExclusions;
import com.dawnduskterminal.world.ChronoDimensionProbe;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Structure.class)
public abstract class StructureExclusionMixin {
    @Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void ddt$suppressExcludedLandmarks(
            Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (!ChronoDimensionProbe.isChrono(context)) {
            return;
        }
        Registry<Structure> registry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceLocation id = registry.getKey((Structure) (Object) this);
        if (id == null || !StructureExclusions.isExcluded(id)) {
            return;
        }
        StructureExclusions.logSuppressed(id);
        cir.setReturnValue(Optional.empty());
    }
}
