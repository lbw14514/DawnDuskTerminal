package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import com.dawnduskterminal.world.FluidReactions;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class FluidReactionTests {
    private static final BlockPos PORTAL = new BlockPos(2, 1, 2);

    @GameTest(template = "ddt_empty")
    public static void portalFluidWithWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(PORTAL, ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(PORTAL.east(), Blocks.WATER);
        FluidReactions.handle(level, helper.absolutePos(PORTAL));
        helper.assertBlockPresent(ModTerrainBlocks.FRAGILE_CANOPY.get(), PORTAL.east());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void portalFluidWithLava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(PORTAL, ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(PORTAL.east(), Blocks.LAVA);
        FluidReactions.handle(level, helper.absolutePos(PORTAL));
        helper.assertBlockPresent(ModTerrainBlocks.CHAOS_STONE.get(), PORTAL.east());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void portalFluidWithScorchingTwilight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(PORTAL, ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(PORTAL.east(), ModScorchingTwilight.BLOCK.get());
        FluidReactions.handle(level, helper.absolutePos(PORTAL));
        helper.assertBlockPresent(ModTerrainBlocks.SKY_STONE.get(), PORTAL.east());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void scorchingTwilightWithWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(PORTAL, ModScorchingTwilight.BLOCK.get());
        helper.setBlock(PORTAL.east(), Blocks.WATER);
        FluidReactions.handle(level, helper.absolutePos(PORTAL));
        helper.assertBlockNotPresent(ModScorchingTwilight.BLOCK.get(), PORTAL.east());
        helper.assertBlockNotPresent(Blocks.WATER, PORTAL.east());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void scorchingTwilightWithLava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(PORTAL, ModScorchingTwilight.BLOCK.get());
        helper.setBlock(PORTAL.east(), Blocks.LAVA);
        FluidReactions.handle(level, helper.absolutePos(PORTAL));
        helper.assertBlockPresent(ModTerrainBlocks.BLAZING_BLOCK.get(), PORTAL.east());
        helper.succeed();
    }
}
