package com.ysm.portal;

import com.ysm.config.YsmConfig;
import com.ysm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class PortalShape {
    public static final int MIN_WATER = 4;
    public static final int MAX_SIDE = 8;

    private final List<BlockPos> waterBlocks;
    private final List<BlockPos> frameBlocks;
    private final List<BlockPos> plantBlocks;

    private PortalShape(List<BlockPos> waterBlocks, List<BlockPos> frameBlocks, List<BlockPos> plantBlocks) {
        this.waterBlocks = List.copyOf(waterBlocks);
        this.frameBlocks = List.copyOf(frameBlocks);
        this.plantBlocks = List.copyOf(plantBlocks);
    }

    public List<BlockPos> waterBlocks() {
        return this.waterBlocks;
    }

    public List<BlockPos> frameBlocks() {
        return this.frameBlocks;
    }

    public List<BlockPos> plantBlocks() {
        return this.plantBlocks;
    }

    public static boolean isPortalFluid(BlockState state) {
        return state.is(ModBlocks.PORTAL_FLUID.get());
    }

    public static boolean isPoolWater(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isPortalFluid(state)) {
            return true;
        }
        if (!state.is(Blocks.WATER)) {
            return false;
        }
        return YsmConfig.waterIsPortal() && level.getFluidState(pos).isSourceOfType(Fluids.WATER);
    }

    public static boolean isPlantable(BlockState state) {
        return state.is(BlockTags.DIRT)
            || state.is(Blocks.FARMLAND)
            || state.is(Blocks.SOUL_SOIL)
            || state.is(Blocks.MOSS_BLOCK)
            || state.is(Blocks.CLAY);
    }

    public static boolean isPlant(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof BushBlock
            || state.is(BlockTags.CROPS)
            || state.is(BlockTags.FLOWERS)
            || state.is(BlockTags.SAPLINGS)
            || state.is(BlockTags.LEAVES)
            || state.is(BlockTags.REPLACEABLE);
    }

    public static Optional<PortalShape> find(BlockGetter level, BlockPos seed) {
        if (!isPoolWater(level, seed)) {
            return Optional.empty();
        }
        int y = seed.getY();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> water = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = new BlockPos(seed.getX(), y, seed.getZ());
        queue.add(start);
        visited.add(start);
        int minX = seed.getX();
        int maxX = seed.getX();
        int minZ = seed.getZ();
        int maxZ = seed.getZ();

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            water.add(current);
            if (water.size() > MAX_SIDE * MAX_SIDE) {
                return Optional.empty();
            }
            minX = Math.min(minX, current.getX());
            maxX = Math.max(maxX, current.getX());
            minZ = Math.min(minZ, current.getZ());
            maxZ = Math.max(maxZ, current.getZ());
            if (maxX - minX + 1 > MAX_SIDE || maxZ - minZ + 1 > MAX_SIDE) {
                return Optional.empty();
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (visited.contains(next) || next.getY() != y) {
                    continue;
                }
                if (isPoolWater(level, next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }

        if (water.size() < MIN_WATER) {
            return Optional.empty();
        }

        List<BlockPos> frame = new ArrayList<>();
        List<BlockPos> plants = new ArrayList<>();
        for (BlockPos pos : water) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos side = pos.relative(direction);
                if (visited.contains(side)) {
                    continue;
                }
                if (isPortalFluid(level.getBlockState(side))) {
                    continue;
                }
                BlockState frameState = level.getBlockState(side);
                if (!isPlantable(frameState)) {
                    return Optional.empty();
                }
                BlockPos aboveSide = side.above();
                BlockState plantState = level.getBlockState(aboveSide);
                if (!isPlant(plantState)) {
                    return Optional.empty();
                }
                if (!frame.contains(side)) {
                    frame.add(side);
                }
                if (!plants.contains(aboveSide)) {
                    plants.add(aboveSide);
                }
            }
        }

        if (frame.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PortalShape(water, frame, plants));
    }
}
