package com.dawnduskterminal.mixin;

import com.dawnduskterminal.world.Experiment069Field;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 试验物品 069 的场：落在场里的流体刻不执行
 *
 * <p>流体在 {@code BlockState} 那三个入口之外还有自己一份刻：{@code ServerLevel#tickFluid} 从流体计划刻
 * 队列里取出到点的那一格调 {@link FluidState#tick}（水与岩浆的流动就是它），{@code ServerLevel#tickChunk}
 * 里选中的随机刻格子调 {@link FluidState#randomTick}（岩浆引火、水浇地之类）。挡掉这两个，场里的流体
 * 也一起冻住，不然水照样会流进场里、岩浆照样会点着场里的火
 */
@Mixin(FluidState.class)
public abstract class FluidStateUpdateSuppressMixin {
    /** 流体计划刻：这一格在场里就不执行 */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ddt$suppressScheduledTick(Level level, BlockPos pos, CallbackInfo ci) {
        if (Experiment069Field.suppressed(level, pos)) {
            ci.cancel();
        }
    }

    /** 流体随机刻：这一格在场里就不执行 */
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void ddt$suppressRandomTick(Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (Experiment069Field.suppressed(level, pos)) {
            ci.cancel();
        }
    }
}
