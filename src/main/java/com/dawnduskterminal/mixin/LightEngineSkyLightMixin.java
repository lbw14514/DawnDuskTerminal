package com.dawnduskterminal.mixin;

import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.world.ChronoLine;
import com.dawnduskterminal.world.ChronoLineState;
import com.dawnduskterminal.world.ChronoSkyLight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightEngine.class)
public abstract class LightEngineSkyLightMixin {
    @Shadow
    @Final
    protected LightChunkGetter chunkSource;

    @Unique
    private Level ddt$level() {
        return this.chunkSource.getLevel() instanceof Level level ? level : null;
    }

    @Unique
    private boolean ddt$isChrono() {
        if (!((Object) this instanceof SkyLightEngine)) {
            return false;
        }
        Level level = ddt$level();
        return level != null && level.dimension() == ModDimensions.CHRONO;
    }

    @Inject(method = "getLightValue(Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true)
    private void ddt$capSkyLight(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!ddt$isChrono()) {
            return;
        }
        Level level = ddt$level();
        if (level == null) {
            return;
        }
        int value = cir.getReturnValue();
        ChronoLine line = level.isClientSide() ? ChronoLineState.clientLine() : ChronoLineState.serverLine();
        int max = ChronoSkyLight.maxSkyLight(line, pos.getX(), pos.getZ());
        if (value > max) {
            cir.setReturnValue(max);
        }
    }
}
