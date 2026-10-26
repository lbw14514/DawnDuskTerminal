package com.dawnduskterminal.block.entity;

import com.dawnduskterminal.registry.ModBlockEntities;
import com.dawnduskterminal.world.Experiment069Field;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 试验物品 069 的方块实体：只为了把场心登记进 {@link Experiment069Field}
 *
 * <p>没有数据、没有界面、服务端也没有 ticker：区块载入时 {@link #onLoad} 登记，区块卸载时 {@link #setRemoved} 撤掉。
 * 摆下与挖掉那两次由方块的 {@code onPlace} / {@code onRemove} 当场做掉，所以方块落地就生效
 *
 * <p>客户端那颗 ticker 挂在方块的 {@code getTicker} 上，只负责把场的立方框画出来，
 * 与本类的数据无关，见 {@code world/CubeFieldMarker}
 *
 * <p>没有 ticker 是有意的：场是「按位置查」的，不用每 tick 去扫方块，更新自己撞上来的时候才判一次
 */
public class Experiment069BlockEntity extends BlockEntity {
    public Experiment069BlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXPERIMENT_069.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Experiment069Field.track(this.level, this.worldPosition);
    }

    @Override
    public void setRemoved() {
        Experiment069Field.untrack(this.level, this.worldPosition);
        super.setRemoved();
    }
}
