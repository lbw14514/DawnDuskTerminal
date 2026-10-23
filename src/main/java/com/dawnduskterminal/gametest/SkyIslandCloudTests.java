package com.dawnduskterminal.gametest;

import com.dawnduskterminal.world.SkyIslandClouds;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 薄岛换云的几何判定
 *
 * <p>直接喂 {@link SkyIslandClouds#processColumns} 合成地形，不走区块生成
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class SkyIslandCloudTests {
    private static final int BASE_Y = 140;

    @GameTest(template = "ddt_empty")
    public static void thinPlateBecomesCloudWithAShell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState cloud = cloud(helper);
        BlockPos base = base(helper, 0, 0);

        fill(level, base, 5, 5, 2);
        BlockPos occupied = base.offset(6, 0, 6);
        level.setBlock(occupied, Blocks.DIRT.defaultBlockState(), 2);

        int replaced = run(level, base, 5, 5, 2, cloud);

        helper.assertTrue(replaced == 50, "the whole 5x5x2 plate must be replaced, got " + replaced);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                for (int y = 0; y < 2; y++) {
                    helper.assertTrue(is(level, base.offset(x, y, z), cloud),
                        "body at " + x + "," + y + "," + z + " must be cloud");
                }
            }
        }

        helper.assertTrue(is(level, base.offset(2, 3, 2), cloud), "two blocks above the top must be cloud");
        helper.assertTrue(is(level, base.offset(2, 4, 2), Blocks.AIR.defaultBlockState()),
            "three blocks above the top must stay air");
        helper.assertTrue(is(level, base.offset(-2, 0, -2), cloud),
            "two blocks out on the diagonals must be cloud");
        helper.assertTrue(is(level, base.offset(-3, 0, 0), Blocks.AIR.defaultBlockState()),
            "three blocks out must stay air");
        helper.assertTrue(is(level, occupied, Blocks.DIRT.defaultBlockState()),
            "the shell must not overwrite existing blocks");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void thickIslandIsLeftAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState cloud = cloud(helper);
        BlockPos base = base(helper, 24, 0);

        fill(level, base, 5, 5, 5);
        int replaced = run(level, base, 5, 5, 5, cloud);

        helper.assertTrue(replaced == 0, "a five thick island must not be replaced, got " + replaced);
        helper.assertTrue(is(level, base.offset(2, 2, 2), Blocks.STONE.defaultBlockState()),
            "the five thick island must stay stone");
        helper.assertTrue(is(level, base.offset(2, 7, 2), Blocks.AIR.defaultBlockState()),
            "no shell may be added to a thick island");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void oneThickColumnKeepsTheWholeIsland(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState cloud = cloud(helper);
        BlockPos base = base(helper, 48, 0);

        fill(level, base, 2, 1, 2);
        fill(level, base.offset(1, 0, 0), 1, 1, 5);

        int[] tops = new int[256];
        Arrays.fill(tops, -1);
        tops[0] = BASE_Y + 1;
        tops[1] = BASE_Y + 4;
        int replaced = SkyIslandClouds.processColumns(level, setter(level), base.getX(), base.getZ(), tops, cloud,
            SkyIslandClouds.ISLAND_MIN_TOP_Y, SkyIslandClouds.ISLAND_MAX_TOP_Y,
            SkyIslandClouds.ISLAND_SCAN_FLOOR_Y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);

        helper.assertTrue(replaced == 0, "a thick column must keep its thin neighbour stone, got " + replaced);
        helper.assertTrue(is(level, base, Blocks.STONE.defaultBlockState()), "the thin column must stay stone");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void islandsBelowTheHeightLimitAreIgnored(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState cloud = cloud(helper);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos base = new BlockPos(origin.getX() + 72, 100, origin.getZ());

        fill(level, base, 5, 5, 2);
        int[] tops = new int[256];
        Arrays.fill(tops, -1);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                tops[z * 16 + x] = 101;
            }
        }
        int replaced = SkyIslandClouds.processColumns(level, setter(level), base.getX(), base.getZ(), tops, cloud,
            140, 319, 128, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);

        helper.assertTrue(replaced == 0, "an island below y 140 must not be touched, got " + replaced);
        helper.assertTrue(is(level, base, Blocks.STONE.defaultBlockState()), "the low plate must stay stone");
        helper.succeed();
    }

    private static int run(ServerLevel level, BlockPos base, int sizeX, int sizeZ, int thickness, BlockState cloud) {
        int[] tops = new int[256];
        Arrays.fill(tops, -1);
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                tops[z * 16 + x] = base.getY() + thickness - 1;
            }
        }
        return SkyIslandClouds.processColumns(level, setter(level), base.getX(), base.getZ(), tops, cloud,
            SkyIslandClouds.ISLAND_MIN_TOP_Y, SkyIslandClouds.ISLAND_MAX_TOP_Y,
            SkyIslandClouds.ISLAND_SCAN_FLOOR_Y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }

    private static SkyIslandClouds.BlockSetter setter(ServerLevel level) {
        return (x, y, z, state) -> level.setBlock(new BlockPos(x, y, z), state, 2);
    }

    private static void fill(ServerLevel level, BlockPos base, int sizeX, int sizeZ, int height) {
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int y = 0; y < height; y++) {
                    level.setBlock(base.offset(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
    }

    private static BlockPos base(GameTestHelper helper, int offsetX, int offsetZ) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new BlockPos(origin.getX() + offsetX, BASE_Y, origin.getZ() + offsetZ);
    }

    private static BlockState cloud(GameTestHelper helper) {
        BlockState state = BuiltInRegistries.BLOCK.get(SkyIslandClouds.WISPY_CLOUD).defaultBlockState();
        helper.assertTrue(!state.isAir(), SkyIslandClouds.WISPY_CLOUD + " must be registered");
        return state;
    }

    private static boolean is(ServerLevel level, BlockPos pos, BlockState expected) {
        return level.getBlockState(pos).is(expected.getBlock());
    }
}
