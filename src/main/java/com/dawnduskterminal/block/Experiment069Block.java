package com.dawnduskterminal.block;

import com.dawnduskterminal.block.entity.Experiment069BlockEntity;
import com.dawnduskterminal.world.CubeFieldMarker;
import com.dawnduskterminal.world.Experiment069Field;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 试验物品 069 摆下来的样子：反建器那一档里更高级的第二台，压出一张「更新不处理」的场
 *
 * <p>概念取自原版的更新抑制器（让一段更新链把队列挤爆，之后排进队列的更新整批丢掉），这台机器把它做成
 * 一个固定的范围：17×17×17 的方块立方，这一格是中心，往每个方向各推 {@value com.dawnduskterminal.world.CubeField#RADIUS}
 * 格。落在场里的方块更新一律不处理——邻居更新与形状更新送不进去，计划刻与随机刻到点也不执行，
 * 场里的方块于是冻在原地（沙不掉、红石不响应、活板门不动、作物不长、冰不化）。判定与登记在
 * {@link Experiment069Field}，热路径上的三个 mixin 见 {@code mixin/}
 *
 * <p>方块本身没有状态：摆下就生效，挖掉或推走就停。登记走两条路：{@link #onPlace} 让方块摆下的那一 tick
 * 就生效，方块实体的 {@code onLoad} 管区块重新载入的情形；撤登记同理，{@link #onRemove} 管挖掉与推走，
 * 方块实体的 {@code setRemoved} 管区块卸载
 *
 * <p>贴图只有一张 {@code experiment_069}：方块自己发 15 级光，贴图直接读满亮，不用再叠一层发光层。
 * 场的范围由 {@link CubeFieldMarker} 那圈立方框粒子画出来（与反应场那圈同款，只在客户端画），
 * 服务端的 ticker 是空的，一颗粒子都不发。场没有别的视觉表现，没有这圈框线玩家看不出这台机器管到哪里
 */
public class Experiment069Block extends Block implements EntityBlock {
    public Experiment069Block(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Experiment069BlockEntity(pos, state);
    }

    /** 只有客户端刻：那颗 ticker 只负责把场的立方框画出来，服务端没有任何每 tick 要做的事 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (!level.isClientSide) {
            return null;
        }
        return CubeFieldMarker::tick;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            Experiment069Field.track(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            Experiment069Field.untrack(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
