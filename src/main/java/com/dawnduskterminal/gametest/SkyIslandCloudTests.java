package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
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
 * 岛底垫云的几何判定
 *
 * <p>直接喂 {@link SkyIslandClouds#decorate} 合成地形和固定判据，不走区块生成
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class SkyIslandCloudTests {
    private static final int BASE_Y = 140;
    private static final long SEED = 0x5C0DE5L;

    @GameTest(template = "ddt_empty")
    public static void cloudHangsUnderThePlate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState cloud = cloud(helper);
        BlockPos base = base(helper, 0, 0);
        fill(level, base, 5, 5, 3);

        int placed = run(level, base, 5, 5, 3, cloud, (x, y, z) -> true);

        helper.assertTrue(placed > 0, "a floating plate must get cloud under it");
        helper.assertTrue(hasCloudBelow(level, base, cloud), "the plate must have cloud hanging under it");
        helper.assertTrue(is(level, base.offset(2, 1, 2), Blocks.STONE.defaultBlockState()),
            "the plate top must stay stone");
        helper.assertTrue(is(level, base.offset(2, 2, 2), Blocks.STONE.defaultBlockState()),
            "the plate top must stay stone");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void noCloudAboveThePlate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState cloud = cloud(helper);
        BlockPos base = base(helper, 40, 0);
        fill(level, base, 5, 5, 3);

        run(level, base, 5, 5, 3, cloud, (x, y, z) -> true);

        for (int x = -4; x <= 8; x++) {
            for (int z = -4; z <= 8; z++) {
                for (int y = 1; y <= 8; y++) {
                    helper.assertTrue(!is(level, base.offset(x, y, z), cloud),
                        "cloud must never grow at or above the plate top, found at " + x + "," + y + "," + z);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void terrainColumnIsLeftAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState cloud = cloud(helper);
        BlockPos base = base(helper, 80, 0);
        fill(level, base, 5, 5, 3);

        int placed = run(level, base, 5, 5, 3, cloud, (x, y, z) -> false);

        helper.assertTrue(placed == 0, "a column the density says is terrain must not get cloud, got " + placed);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void thinPlateKeepsEveryBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState cloud = cloud(helper);
        BlockPos base = base(helper, 120, 0);
        fill(level, base, 5, 5, 2);

        run(level, base, 5, 5, 2, cloud, (x, y, z) -> true);

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                for (int y = 0; y < 2; y++) {
                    helper.assertTrue(is(level, base.offset(x, y, z), Blocks.STONE.defaultBlockState()),
                        "a two thick plate must keep every block, greedy at " + x + "," + y + "," + z);
                }
            }
        }
        helper.assertTrue(hasCloudBelow(level, base, cloud), "the cloud must still hang under it");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void thickPlateKeepsAtLeastTwoLayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState cloud = cloud(helper);
        BlockPos base = base(helper, 160, 0);
        fill(level, base, 5, 5, 6);

        run(level, base, 5, 5, 6, cloud, (x, y, z) -> true);

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.assertTrue(is(level, base.offset(x, 5, z), Blocks.STONE.defaultBlockState()),
                    "the top layer must never be eroded");
                helper.assertTrue(is(level, base.offset(x, 4, z), Blocks.STONE.defaultBlockState()),
                    "the second layer from the top must never be eroded");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void singleColumnIslandStillGetsClouds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState cloud = cloud(helper);
        BlockPos base = base(helper, 240, 0);
        fill(level, base, 1, 1, 3);

        int placed = run(level, base, 1, 1, 3, cloud, (x, y, z) -> true);

        helper.assertTrue(placed > 0, "even a one column island must get cloud, got " + placed);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void sameSeedGivesTheSameShape(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState cloud = cloud(helper);
        BlockPos first = base(helper, 200, 0);
        BlockPos second = base(helper, 200, 40);
        fill(level, first, 5, 5, 3);
        fill(level, second, 5, 5, 3);

        int firstPlaced = run(level, first, 5, 5, 3, cloud, (x, y, z) -> true);
        int secondPlaced = run(level, second, 5, 5, 3, cloud, (x, y, z) -> true);

        helper.assertTrue(firstPlaced > 0 && secondPlaced > 0,
            "both plates must get cloud, got " + firstPlaced + " and " + secondPlaced);
        helper.succeed();
    }

    private static int run(ServerLevel level, BlockPos base, int sizeX, int sizeZ, int thickness,
                           BlockState cloud, SkyIslandClouds.IslandProbe probe) {
        int[] tops = new int[256];
        Arrays.fill(tops, -1);
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                tops[z * 16 + x] = base.getY() + thickness - 1;
            }
        }
        return SkyIslandClouds.decorate(level, (x, y, z, state) -> level.setBlock(new BlockPos(x, y, z), state, 2),
            base.getX(), base.getZ(), tops, cloud, probe, SEED,
            SkyIslandClouds.ISLAND_MIN_TOP_Y, level.getMaxBuildHeight() - 1,
            SkyIslandClouds.ISLAND_SCAN_FLOOR_Y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
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

    private static boolean hasCloudBelow(ServerLevel level, BlockPos base, BlockState cloud) {
        int reach = SkyIslandClouds.VEIL_SPREAD + 2;
        for (int x = -reach; x <= 4 + reach; x++) {
            for (int z = -reach; z <= 4 + reach; z++) {
                for (int y = -1; y >= -SkyIslandClouds.VEIL_DEPTH - 2; y--) {
                    if (is(level, base.offset(x, y, z), cloud)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean is(ServerLevel level, BlockPos pos, BlockState expected) {
        return level.getBlockState(pos).is(expected.getBlock());
    }
}
