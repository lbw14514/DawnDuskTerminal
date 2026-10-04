package com.dawnduskterminal.block;

import com.dawnduskterminal.world.FluidReactions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

public class ScorchingTwilightBlock extends LiquidBlock {
    public ScorchingTwilightBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        scheduleSpread(state, level, pos);
        clearPlants(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        scheduleSpread(state, level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        scheduleSpread(state, level, pos);
        FluidReactions.handle(level, pos);
        clearPlants(level, pos);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide) {
            return;
        }
        if (entity instanceof LivingEntity living) {
            living.lavaHurt();
        }
    }

    private static void scheduleSpread(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty()) {
            return;
        }
        level.scheduleTick(pos, fluidState.getType(), Math.max(1, fluidState.getType().getTickDelay(level)));
    }

    private void clearPlants(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        for (Direction dir : Direction.values()) {
            BlockPos target = pos.relative(dir);
            BlockState state = level.getBlockState(target);
            if (state.isAir() || state.is(this) || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (!state.canBeReplaced()) {
                continue;
            }
            level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
