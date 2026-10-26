package com.dawnduskterminal.block.entity;

import com.dawnduskterminal.registry.ModBlockEntities;
import com.dawnduskterminal.world.Experiment065Field;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 试验物品 065 的方块实体：只为了把场心登记进 {@link Experiment065Field}
 *
 * <p>没有数据、没有界面、服务端也没有 ticker：区块载入时 {@link #onLoad} 登记，区块卸载时 {@link #setRemoved} 撤掉。
 * 摆下与挖掉那两次由方块的 {@code onPlace} / {@code onRemove} 当场做掉，所以方块落地就生效
 *
 * <p>客户端那颗 ticker 挂在方块的 {@code getTicker} 上，只负责把场的立方框画出来，
 * 与本类的数据无关，见 {@code world/CubeFieldMarker}，不必等到下一 tick
 * 的方块实体装载
 *
 * <p>没有 ticker 是有意的：场是「按位置查」的，不用每 tick 去扫实体。代价是只有被伤害事件问到的那一刻才知道
 * 场在不在，但那一问本来就是伤害事件自己的事
 */
public class Experiment065BlockEntity extends BlockEntity {
    public Experiment065BlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXPERIMENT_065.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Experiment065Field.track(this.level, this.worldPosition);
    }

    @Override
    public void setRemoved() {
        Experiment065Field.untrack(this.level, this.worldPosition);
        super.setRemoved();
    }
}
