package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.world.SkyIslandDensity;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 空岛判据必须在真实维度里能用：既要真的抓到两个密度函数，也要真的挑得出岛、又不会把地下认成岛
 *
 * <p>函数抓取失败时整个模组会静默退化成"没有空岛"，所以这条断言比几何测试更靠前
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class SkyIslandDensityTests {
    private static final ResourceKey<NoiseGeneratorSettings> CHRONO =
        ResourceKey.create(Registries.NOISE_SETTINGS, DawnDuskTerminal.id("chrono"));

    @GameTest(template = "ddt_empty")
    public static void densityFunctionsAreResolved(GameTestHelper helper) {
        remember(helper);

        helper.assertTrue(SkyIslandDensity.ready(),
            "the island predicate must resolve " + SkyIslandDensity.SKY_ISLAND + " and "
                + SkyIslandDensity.BASE_3D_NOISE);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void undergroundIsNotIsland(GameTestHelper helper) {
        remember(helper);

        for (int x = -64; x <= 64; x += 16) {
            for (int z = -64; z <= 64; z += 16) {
                helper.assertTrue(!SkyIslandDensity.isIsland(x, -50, z),
                    "solid ground at y -50 must never be an island, failed at " + x + "," + z);
            }
        }
        helper.succeed();
    }

    /** 用真实的晨昏线设置建一个带种子的 {@code RandomState}，跟世界生成走的同一条路 */
    private static void remember(GameTestHelper helper) {
        var access = helper.getLevel().getServer().registryAccess();
        NoiseGeneratorSettings settings = access.registryOrThrow(Registries.NOISE_SETTINGS)
            .getOrThrow(CHRONO);
        SkyIslandDensity.remember(
            RandomState.create(settings, access.lookupOrThrow(Registries.NOISE), 0x5EED5EEDL), settings);
    }

    @GameTest(template = "ddt_empty")
    public static void theIslandBandHasIslands(GameTestHelper helper) {
        remember(helper);

        int columns = 0;
        int islandColumns = 0;
        for (int x = -192; x <= 192; x += 4) {
            for (int z = -192; z <= 192; z += 4) {
                boolean island = false;
                for (int y = 136; y <= 310; y++) {
                    if (SkyIslandDensity.isIsland(x, y, z)) {
                        island = true;
                    }
                }
                columns++;
                if (island) {
                    islandColumns++;
                }
                for (int y = 136; y < 140; y++) {
                    helper.assertTrue(!SkyIslandDensity.isIsland(x, y, z),
                        "no island material may sit below the band, failed at " + x + "," + y + "," + z);
                }
            }
        }
        double coverage = (double) islandColumns / columns;

        helper.assertTrue(islandColumns > 0,
            "the y 140~310 band must hold island material somewhere near spawn, found none");
        // 实测约 7%，给到 12% 只是挡住"整片大陆"这种回归
        helper.assertTrue(coverage < 0.12D,
            "islands must stay islands, not a continent: " + Math.round(coverage * 100.0D) + "% of sampled columns");
        helper.succeed();
    }
}
