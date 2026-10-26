package com.dawnduskterminal.mixin;

import com.dawnduskterminal.world.Experiment069Field;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 试验物品 069 的场：落在场里的方块更新不处理
 *
 * <p>三个入口都在 {@code BlockBehaviour.BlockStateBase} 上，原版所有路径最后都落到这三处：
 * <ul>
 *   <li>{@code handleNeighborChanged}：邻居更新唯一的投递口。更新队列里那几种更新
 *       （{@code NeighborUpdater.executeUpdate} 由 {@code CollectingNeighborUpdater} 的
 *       {@code SimpleNeighborUpdate}、{@code FullNeighborUpdate}、{@code MultiNeighborUpdate} 调）全走这里，
 *       少数方块自己直接调 {@code handleNeighborChanged} 的也一并挡住
 *   <li>{@code tick}：方块计划刻的执行口。{@code ServerLevel#tickBlock} 每 tick 从计划刻队列里取出到点的
 *       那一格，这里挡掉就等于这一拍的计划刻不存在
 *   <li>{@code randomTick}：随机刻的执行口。{@code ServerLevel#tickChunk} 对每个选中的格子调它
 * </ul>
 *
 * <p>放在执行口而不是排队口，是「更新抑制器」的本来面貌：更新照样排进队列，只是排到场上时被丢掉，
 * 于是场里的方块对一切变化装死，而场外那一格收到同一批更新时照常处理
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateUpdateSuppressMixin {
    /** 邻居更新：这一格在场里就不投递 */
    @Inject(method = "handleNeighborChanged", at = @At("HEAD"), cancellable = true)
    private void ddt$suppressNeighborUpdate(Level level, BlockPos pos, Block fromBlock, BlockPos fromPos,
                                             boolean movedByPiston, CallbackInfo ci) {
        if (Experiment069Field.suppressed(level, pos)) {
            ci.cancel();
        }
    }

    /** 方块计划刻：这一格在场里就不执行 */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ddt$suppressScheduledTick(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (Experiment069Field.suppressed(level, pos)) {
            ci.cancel();
        }
    }

    /** 方块随机刻：这一格在场里就不执行 */
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void ddt$suppressRandomTick(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (Experiment069Field.suppressed(level, pos)) {
            ci.cancel();
        }
    }
}
