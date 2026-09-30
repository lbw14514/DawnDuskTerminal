package com.dawnduskterminal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class FluidReactiveBlock extends Block {
    private final ResourceLocation triggerFluid;
    private final Block resultBlock;

    public FluidReactiveBlock(Properties properties, ResourceLocation triggerFluid, Block resultBlock) {
        super(properties);
        this.triggerFluid = triggerFluid;
        this.resultBlock = resultBlock;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        check(state, level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(pos, this, 1);
        }
    }

    private void check(BlockState state, ServerLevel level, BlockPos pos) {
        if (!touchesTrigger(level, pos)) {
            return;
        }
        level.setBlockAndUpdate(pos, resultBlock.defaultBlockState());
    }

    private boolean touchesTrigger(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            FluidState fluidState = level.getFluidState(pos.relative(dir));
            if (fluidState.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluidState.getType());
            if (id.equals(triggerFluid) || stripFlowing(id).equals(triggerFluid)) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation stripFlowing(ResourceLocation id) {
        String path = id.getPath();
        if (path.startsWith("flowing_")) {
            return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(8));
        }
        return id;
    }
}
