package com.dawnduskterminal.mixin;

import com.dawnduskterminal.world.Experiment069Field;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 试验物品 069 的场：落在场里的形状更新不处理
 *
 * <p>形状更新是「邻居换了，我该不该改外形」那一支（栅栏连不连、红石粉朝向、火把还挂不挂得住），
 * 原版的排队口是 {@code BlockStateBase#updateNeighbourShapes} 按六个方向逐个调
 * {@code LevelAccessor#neighborShapeChanged}，真正落到 {@code Level#neighborShapeChanged} 上。
 * 在那里按<b>被更新那一格</b>判一次，场里那一格的形状更新就不进队列，它保持摆下时的形状
 *
 * <p>挡在这里而不是 {@code BlockStateBase#updateShape} 上：后者除了这条队列，还被结构模板落地、
 * 区块升级数据这些地方直接调，挡它会把那些路径一起挡歪；这一条只有方块更新队列会走
 */
@Mixin(Level.class)
public abstract class LevelShapeUpdateSuppressMixin {
    @Inject(method = "neighborShapeChanged", at = @At("HEAD"), cancellable = true)
    private void ddt$suppressShapeUpdate(Direction direction, BlockState sourceState, BlockPos pos,
                                         BlockPos sourcePos, int flags, int recursionLeft, CallbackInfo ci) {
        if (Experiment069Field.suppressed((Level) (Object) this, pos)) {
            ci.cancel();
        }
    }
}
