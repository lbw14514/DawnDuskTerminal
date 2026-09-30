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
    private byte ddt$chrono = -1;

    @Unique
    private boolean ddt$isChrono() {
        if (this.ddt$chrono < 0) {
            boolean chrono = ((Object) this instanceof SkyLightEngine)
                && this.chunkSource.getLevel() instanceof Level level
                && level.dimension().equals(ModDimensions.CHRONO);
            this.ddt$chrono = (byte) (chrono ? 1 : 0);
        }
        return this.ddt$chrono == 1;
    }

    @Inject(method = "getLightValue(Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true)
    private void ddt$capSkyLight(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!this.ddt$isChrono()) {
            return;
        }
        if (!(this.chunkSource.getLevel() instanceof Level level)) {
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
