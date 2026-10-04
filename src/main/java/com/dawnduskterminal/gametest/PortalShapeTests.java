package com.dawnduskterminal.gametest;

import com.dawnduskterminal.portal.PortalBuilder;
import com.dawnduskterminal.portal.PortalShape;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class PortalShapeTests {
    private static final int POOL_Y = 1;
    private static final int POOL_MIN = 3;
    private static final int POOL_MAX = 5;

    private static void buildFloor(GameTestHelper helper, net.minecraft.world.level.block.Block floor) {
        for (int x = POOL_MIN - 1; x <= POOL_MAX + 1; x++) {
            for (int z = POOL_MIN - 1; z <= POOL_MAX + 1; z++) {
                helper.setBlock(new BlockPos(x, POOL_Y - 1, z), floor);
            }
        }
    }

    private static void buildPool(GameTestHelper helper) {
        for (int x = POOL_MIN; x <= POOL_MAX; x++) {
            for (int z = POOL_MIN; z <= POOL_MAX; z++) {
                helper.setBlock(new BlockPos(x, POOL_Y, z), Blocks.WATER);
            }
        }
    }

    private static void buildRim(GameTestHelper helper, net.minecraft.world.level.block.Block rim,
                                 net.minecraft.world.level.block.Block plant) {
        for (int x = POOL_MIN - 1; x <= POOL_MAX + 1; x++) {
            for (int z = POOL_MIN - 1; z <= POOL_MAX + 1; z++) {
                boolean edge = x == POOL_MIN - 1 || x == POOL_MAX + 1 || z == POOL_MIN - 1 || z == POOL_MAX + 1;
                if (!edge) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, POOL_Y, z);
                helper.setBlock(pos, rim);
                if (plant != null) {
                    helper.setBlock(pos.above(), plant);
                }
            }
        }
    }

    @GameTest(template = "ddt_empty")
    public static void plantedPoolIsPortal(GameTestHelper helper) {
        buildFloor(helper, ModTerrainBlocks.SKY_STONE.get());
        buildPool(helper);
        buildRim(helper, ModBlocks.SKY_SOIL.get(), Blocks.SHORT_GRASS);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(PortalShape.find(level, helper.absolutePos(new BlockPos(POOL_MIN, POOL_Y, POOL_MIN))).isPresent(),
            "planted pool should be recognised as a portal shape");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void barrenPoolIsNotPortal(GameTestHelper helper) {
        buildFloor(helper, ModTerrainBlocks.SKY_STONE.get());
        buildPool(helper);
        buildRim(helper, Blocks.GLASS, null);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(PortalShape.find(level, helper.absolutePos(new BlockPos(POOL_MIN, POOL_Y, POOL_MIN))).isEmpty(),
            "a glass rim must not count as a portal edge");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void poolWithoutPlantsIsNotPortal(GameTestHelper helper) {
        buildFloor(helper, ModTerrainBlocks.SKY_STONE.get());
        buildPool(helper);
        buildRim(helper, ModBlocks.SKY_SOIL.get(), null);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(PortalShape.find(level, helper.absolutePos(new BlockPos(POOL_MIN, POOL_Y, POOL_MIN))).isEmpty(),
            "open air above the rim must not count as plants");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void loneWaterBlockIsNotPortal(GameTestHelper helper) {
        buildFloor(helper, ModTerrainBlocks.SKY_STONE.get());
        helper.setBlock(new BlockPos(POOL_MIN, POOL_Y, POOL_MIN), Blocks.WATER);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(PortalShape.find(level, helper.absolutePos(new BlockPos(POOL_MIN, POOL_Y, POOL_MIN))).isEmpty(),
            "four water blocks are the minimum");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void buildFillsPoolWithPortalFluid(GameTestHelper helper) {
        buildFloor(helper, ModTerrainBlocks.SKY_STONE.get());
        buildPool(helper);
        buildRim(helper, ModBlocks.SKY_SOIL.get(), Blocks.SHORT_GRASS);
        ServerLevel level = helper.getLevel();
        BlockPos seed = helper.absolutePos(new BlockPos(POOL_MIN, POOL_Y, POOL_MIN));
        helper.assertTrue(PortalBuilder.build(level, seed), "build should succeed on a valid pool");
        for (int x = POOL_MIN; x <= POOL_MAX; x++) {
            for (int z = POOL_MIN; z <= POOL_MAX; z++) {
                helper.assertBlockPresent(ModBlocks.PORTAL_FLUID.get(), new BlockPos(x, POOL_Y, z));
            }
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void portalBlockSurvivesWithNeighbour(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(new BlockPos(3, 1, 2), ModBlocks.PORTAL_FLUID.get());
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(2, 1, 2));
        level.getBlockState(abs).randomTick(level, abs, level.random);
        helper.assertBlockPresent(ModBlocks.PORTAL_FLUID.get(), new BlockPos(2, 1, 2));
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void isolatedPortalBlockSelfHeals(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.PORTAL_FLUID.get());
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(2, 1, 2));
        level.getBlockState(abs).randomTick(level, abs, level.random);
        helper.assertBlockNotPresent(ModBlocks.PORTAL_FLUID.get(), new BlockPos(2, 1, 2));
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void portalFluidIsCompleteWithTwoNeighbours(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(new BlockPos(3, 1, 2), ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(new BlockPos(2, 1, 3), ModBlocks.PORTAL_FLUID.get());
        ServerLevel level = helper.getLevel();
        helper.assertTrue(PortalShape.isCompletePortalPart(level, helper.absolutePos(new BlockPos(2, 1, 2))),
            "the corner of an L has two neighbours so it counts as a complete part");
        helper.assertTrue(
            !PortalShape.isCompletePortalPart(level, helper.absolutePos(new BlockPos(3, 1, 2))),
            "a block with a single neighbour is not complete");
        helper.succeed();
    }
}
