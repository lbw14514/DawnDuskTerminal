package com.dawnduskterminal.gametest;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class AquiferTests {
    private static final int SEA_LEVEL = 62;
    private static final ResourceLocation CHRONO = ResourceLocation.fromNamespaceAndPath("dawnduskterminal", "chrono");

    private static NoiseGeneratorSettings chronoSettings(GameTestHelper helper) {
        Registry<NoiseGeneratorSettings> registry = helper.getLevel().getServer().registryAccess()
            .registryOrThrow(Registries.NOISE_SETTINGS);
        NoiseGeneratorSettings settings = registry.get(CHRONO);
        helper.assertTrue(settings != null, "noise settings " + CHRONO + " missing");
        return settings;
    }

    @GameTest(template = "ddt_empty")
    public static void aquifersAreEnabledLikeVanilla(GameTestHelper helper) {
        NoiseGeneratorSettings settings = chronoSettings(helper);
        helper.assertTrue(settings.aquifersEnabled(), "chrono must keep aquifers enabled");
        helper.assertTrue(settings.defaultFluid().is(Blocks.WATER), "default fluid must be water so oceans fill up");
        helper.assertTrue(settings.seaLevel() == SEA_LEVEL, "sea level must stay " + SEA_LEVEL);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void undergroundStaysDry(GameTestHelper helper) {
        NoiseRouter router = chronoSettings(helper).noiseRouter();
        DensityFunction floodedness = router.fluidLevelFloodednessNoise();
        DensityFunction lava = router.lavaNoise();
        for (int y = -64; y <= 320; y += 8) {
            for (int x = -768; x <= 768; x += 256) {
                DensityFunction.FunctionContext ctx = new DensityFunction.SinglePointContext(x, y, x * 3 + 17);
                double flooded = Mth.clamp(floodedness.compute(ctx), -1.0D, 1.0D);
                if (y <= 8) {
                    helper.assertTrue(flooded <= -1.0D,
                        "floodedness at or below y 8 must be pinned to -1 so no water spawns, got "
                            + flooded + " at " + y);
                }
                helper.assertTrue(lava.compute(ctx) == 0.0D,
                    "lava noise must be a zero constant, got " + lava.compute(ctx) + " at " + y);
            }
        }
        helper.succeed();
    }
}
