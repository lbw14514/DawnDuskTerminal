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

    /**
     * 光照引擎与维度是一一对应的，这些判断没必要每次 getLightValue 都重做
     *
     * <p>非天光引擎在第一次调用时就会解析成 false，此后只走一个 boolean 判断
     */
    @Unique
    private boolean ddt$chronoResolved;
    @Unique
    private boolean ddt$chrono;
    @Unique
    private Level ddt$cachedLevel;

    @Unique
    private void ddt$resolveChrono() {
        this.ddt$chronoResolved = true;
        this.ddt$cachedLevel = this.chunkSource.getLevel() instanceof Level level ? level : null;
        this.ddt$chrono = this.ddt$cachedLevel != null
            && this.ddt$cachedLevel.dimension() == ModDimensions.CHRONO;
    }

    @Unique
    private boolean ddt$isChrono() {
        if (!((Object) this instanceof SkyLightEngine)) {
            return false;
        }
        if (!this.ddt$chronoResolved) {
            ddt$resolveChrono();
        }
        return this.ddt$chrono;
    }

    @Inject(method = "getLightValue(Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true)
    private void ddt$capSkyLight(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!ddt$isChrono()) {
            return;
        }
        Level level = this.ddt$cachedLevel;
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
