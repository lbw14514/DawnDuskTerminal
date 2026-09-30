package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModFluids;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

public final class FluidReactions {
    private static final List<Reaction> REACTIONS = new ArrayList<>();

    private record Reaction(Fluid a, Fluid b, Block result) {}

    private FluidReactions() {}

    static {
        REACTIONS.add(new Reaction(ModFluids.PORTAL_FLUID.get(), Fluids.WATER,
            ModTerrainBlocks.FRAGILE_CANOPY.get()));
        REACTIONS.add(new Reaction(ModFluids.PORTAL_FLUID.get(), Fluids.LAVA,
            ModTerrainBlocks.CHAOS_STONE.get()));
        REACTIONS.add(new Reaction(ModFluids.PORTAL_FLUID.get(), ModScorchingTwilight.SOURCE.get(),
            ModTerrainBlocks.SKY_STONE.get()));
        REACTIONS.add(new Reaction(ModScorchingTwilight.SOURCE.get(), Fluids.WATER,
            ModTerrainBlocks.AURORA_BLOCK.get()));
        REACTIONS.add(new Reaction(ModScorchingTwilight.SOURCE.get(), Fluids.LAVA,
            ModTerrainBlocks.BLAZING_BLOCK.get()));
    }

    public static boolean isReactive(Fluid fluid) {
        for (Reaction r : REACTIONS) {
            if (r.a() == fluid || r.b() == fluid) {
                return true;
            }
        }
        return false;
    }

    public static void handle(ServerLevel level, BlockPos pos) {
        FluidState here = level.getFluidState(pos);
        if (here.isEmpty()) {
            return;
        }
        Fluid self = here.getType();
        if (!isReactive(self)) {
            return;
        }
        for (Direction dir : Direction.values()) {
            BlockPos other = pos.relative(dir);
            FluidState there = level.getFluidState(other);
            if (there.isEmpty()) {
                continue;
            }
            Block matched = match(self, there.getType());
            if (matched == null) {
                continue;
            }
            level.setBlockAndUpdate(other, matched.defaultBlockState());
            if (level.getFluidState(pos).getType() == self) {
                level.setBlockAndUpdate(pos, matched.defaultBlockState());
            }
            return;
        }
    }

    private static Block match(Fluid a, Fluid b) {
        for (Reaction r : REACTIONS) {
            if ((r.a() == a && r.b() == b) || (r.a() == b && r.b() == a)) {
                return r.result();
            }
        }
        return null;
    }
}
