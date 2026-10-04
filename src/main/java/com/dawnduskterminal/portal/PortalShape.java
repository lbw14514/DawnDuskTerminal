package com.dawnduskterminal.portal;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class PortalShape {
    public static final int MIN_WATER = 4;
    public static final int MAX_WATER = 64;

    public static final TagKey<Block> PORTAL_FLUID_TAG =
        TagKey.create(Registries.BLOCK, DawnDuskTerminal.id("portal/fluid"));

    public static final TagKey<Block> PORTAL_EDGE_TAG =
        TagKey.create(Registries.BLOCK, DawnDuskTerminal.id("portal/edge"));

    public static final TagKey<Block> PORTAL_DECO_TAG =
        TagKey.create(Registries.BLOCK, DawnDuskTerminal.id("portal/decoration"));

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

    public static int countNeighbors(net.minecraft.world.level.LevelReader level, BlockPos pos) {
        int n = 0;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).is(ModBlocks.PORTAL_FLUID.get())) {
                n++;
            }
        }
        return n;
    }

    public static boolean isCompletePortalPart(net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return countNeighbors(level, pos) >= 2;
    }

    public static boolean isPoolBlock(BlockState state) {
        return state.is(PORTAL_FLUID_TAG);
    }

    public static boolean isPoolWater(BlockGetter level, BlockPos pos) {
        return isPoolBlock(level.getBlockState(pos));
    }

    public static boolean isPlantable(BlockState state) {
        return state.is(PORTAL_EDGE_TAG);
    }

    public static boolean isPlant(BlockState state) {
        return state.is(PORTAL_DECO_TAG);
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

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            water.add(current);
            if (water.size() > MAX_WATER) {
                return Optional.empty();
            }
            BlockPos floor = current.below();
            int guard = 0;
            while (isPoolWater(level, floor) && guard++ < 16) {
                floor = floor.below();
            }
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
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

        Set<BlockPos> ring = new java.util.LinkedHashSet<>();
        for (BlockPos pos : water) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos side = pos.relative(direction);
                if (visited.contains(side) || isPoolWater(level, side)) {
                    continue;
                }
                if (!isPlantable(level.getBlockState(side))) {
                    return Optional.empty();
                }
                ring.add(side);
            }
        }

        if (ring.isEmpty()) {
            return Optional.empty();
        }

        List<BlockPos> frame = new ArrayList<>(ring);
        List<BlockPos> plants = new ArrayList<>();
        for (BlockPos side : ring) {
            BlockPos aboveSide = side.above();
            BlockState above = level.getBlockState(aboveSide);
            if (isPlant(above) && !above.isAir()) {
                plants.add(aboveSide);
            }
        }

        if (plants.size() < Math.max(2, ring.size() / 4)) {
            return Optional.empty();
        }
        return Optional.of(new PortalShape(water, frame, plants));
    }
}
