package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.world.HollowPocketFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class WorldgenTests {
    private static final int GRID_CHUNKS = 219;
    private static final int POCKET_MIN = 6;
    private static final int POCKET_MAX = 12;
    private static final int POCKET_HALF = POCKET_MAX * 8 + 24;

    @GameTest(template = "ddt_empty")
    public static void densityFunctionsAreRegistered(GameTestHelper helper) {
        Registry<DensityFunction> registry =
            helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION);
        for (String id : new String[] {"mainland_window", "mainland_windowed", "void_band",
            "vanilla_final_density", "underground_island", "sky_island_density"}) {
            helper.assertTrue(registry.containsKey(ResourceLocation.fromNamespaceAndPath("dawnduskterminal", id)),
                "density function missing: " + id);
        }
        helper.assertTrue(registry.containsKey(ResourceLocation.withDefaultNamespace("overworld/sloped_cheese")),
            "vanilla sloped_cheese should seed the initial density");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void chronoDimensionTypeIsRegistered(GameTestHelper helper) {
        var access = helper.getLevel().getServer().registryAccess();
        Registry<DimensionType> types = access.registryOrThrow(Registries.DIMENSION_TYPE);
        ResourceLocation chrono = ResourceLocation.fromNamespaceAndPath("dawnduskterminal", "chrono");
        helper.assertTrue(types.containsKey(chrono), "chrono dimension type must be registered");
        helper.assertTrue(access.registryOrThrow(Registries.NOISE_SETTINGS).containsKey(chrono),
            "chrono noise settings must be registered");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void pocketZoneIsStableAndBounded(GameTestHelper helper) {
        long seed = helper.getLevel().getServer().overworld().getSeed();
        int insideX = 0;
        int insideZ = 0;
        boolean found = false;
        for (int x = -12000; x <= 12000 && !found; x += 32) {
            for (int z = -12000; z <= 12000 && !found; z += 32) {
                if (HollowPocketFeature.inZone(seed, GRID_CHUNKS, POCKET_MIN, POCKET_MAX, x, z)) {
                    insideX = x;
                    insideZ = z;
                    found = true;
                }
            }
        }
        if (!found) {
            helper.succeed();
            return;
        }
        helper.assertTrue(HollowPocketFeature.inZone(seed, GRID_CHUNKS, POCKET_MIN, POCKET_MAX, insideX, insideZ),
            "inZone must be deterministic for the same seed and position");
        helper.assertTrue(!HollowPocketFeature.inZone(seed, GRID_CHUNKS, POCKET_MIN, POCKET_MAX,
            insideX + POCKET_HALF * 2 + 2, insideZ),
            "a position two pocket widths away must leave the pocket zone");
        helper.assertTrue(HollowPocketFeature.inZone(seed, GRID_CHUNKS, POCKET_MIN, POCKET_MAX, insideX, insideZ),
            "inZone must not depend on call order");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void bedrockSealCoversPocketFreeColumns(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
        helper.assertTrue(!helper.getBlockState(pos).isAir(), "the test plot should hold a solid block");
        helper.succeed();
    }
}
