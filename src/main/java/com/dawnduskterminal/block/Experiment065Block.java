package com.dawnduskterminal.block;

import com.dawnduskterminal.block.entity.Experiment065BlockEntity;
import com.dawnduskterminal.world.CubeFieldMarker;
import com.dawnduskterminal.world.Experiment065Field;
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
 * 试验物品 065 摆下来的样子：反建器这一档里更高级的那一台，压出一张让人打不出伤害的场
 *
 * <p>方块本身没有状态：摆下就生效，挖掉或推走就停。场是 17×17×17 的方块立方，这一格是中心，
 * 往每个方向各推 {@value com.dawnduskterminal.world.CubeField#RADIUS} 格；范围内<b>打出的</b>伤害一律无效
 * （判定看伤害来源上的直接实体，也就是攻击者本人或那颗弹体），场外打进来的照常，
 * 摔落火焰这类没有来源实体的环境伤害也不拦。整套判定在 {@link Experiment065Field}
 *
 * <p>登记走两条路：{@link #onPlace} 让方块摆下的那一 tick 就生效，方块实体的 {@code onLoad} 管区块重新载入
 * 的情形；撤登记同理，{@link #onRemove} 管挖掉与推走，方块实体的 {@code setRemoved} 管区块卸载。
 * 两处都登记同一个位置，重了也只是集合里少写一次
 *
 * <p>贴图只有一张 {@code experiment_065}：方块自己发 15 级光，贴图直接读满亮，不用再叠一层发光层。
 * 场的范围由 {@link CubeFieldMarker} 那圈立方框粒子画出来（与反应场那圈同款，只在客户端画），
 * 服务端的 ticker 是空的，一颗粒子都不发
 */
public class Experiment065Block extends Block implements EntityBlock {
    public Experiment065Block(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Experiment065BlockEntity(pos, state);
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
            Experiment065Field.track(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            Experiment065Field.untrack(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
