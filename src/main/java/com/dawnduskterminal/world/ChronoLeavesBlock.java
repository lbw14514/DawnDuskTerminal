package com.dawnduskterminal.world;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public class ChronoLeavesBlock extends LeavesBlock {
    private final Supplier<Block> withered;

    public ChronoLeavesBlock(Properties properties, Supplier<Block> withered) {
        super(properties);
        this.withered = withered;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.decaying(state)) {
            level.setBlock(pos, this.withered.get().defaultBlockState(), 3);
        }
    }
}
