package com.dawnduskterminal.block.entity;

import com.dawnduskterminal.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 试验物品 076 的方块实体：没有数据、没有界面、没有 ticker
 *
 * <p>它存在的唯一理由是有个方块实体，{@code client/Experiment076Renderer} 才有东西可画。外观不存进这里：
 * 六个面各显示哪张贴图是按「世界时间 + 坐标」现算的（见渲染器），
 * 所以同一刻所有客户端看到的是同一个样子，方块实体里不需要藏任何东西，也不用连客户端/服务端同步
 */
public class Experiment076BlockEntity extends BlockEntity {
    public Experiment076BlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXPERIMENT_076.get(), pos, state);
    }
}
