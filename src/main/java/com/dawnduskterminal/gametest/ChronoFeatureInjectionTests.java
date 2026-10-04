package com.dawnduskterminal.gametest;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class ChronoFeatureInjectionTests {
    private static final String[] PROBES = {
        "dawnduskterminal:normal",
        "dawnduskterminal:dusk_zone",
        "dawnduskterminal:earth_light"
    };

    private static final String[] REQUIRED = {
        "dawnduskterminal:turf",
        "dawnduskterminal:bedrock_layer",
        "dawnduskterminal:root_pillar",
        "dawnduskterminal:hollow_pocket",
        "dawnduskterminal:chrono_trees"
    };

    private static final String[] BANNED = {
        "minecraft:ore_dirt",
        "minecraft:ore_gravel",
        "minecraft:underwater_magma",
        "minecraft:disk_sand",
        "minecraft:disk_clay",
        "minecraft:disk_gravel",
        "minecraft:spring_water",
        "minecraft:spring_lava",
        "minecraft:lake_lava_underground",
        "minecraft:lake_lava_surface",
        "minecraft:monster_room",
        "minecraft:amethyst_geode",
        "minecraft:freeze_top_layer",
        "minecraft:trees_plains",
        "minecraft:trees_birch",
        "minecraft:glow_lichen",
        "minecraft:patch_sugar_cane",
        "minecraft:patch_pumpkin",
        "minecraft:ore_coal_upper",
        "minecraft:ore_diamond",
        "twilightforest:mayapple",
        "twilightforest:tree/twilight_oak_tree",
        "twilightforest:legacy_iron_ore"
    };

    @GameTest(template = "ddt_empty")
    public static void chronoBiomesReceiveIslandAndTerrainFeatures(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.BIOME);
        Registry<PlacedFeature> placed = helper.getLevel().getServer().registryAccess()
            .registryOrThrow(Registries.PLACED_FEATURE);
        Set<ResourceLocation> banned = new HashSet<>();
        for (String id : BANNED) {
            banned.add(ResourceLocation.parse(id));
        }
        for (String probeId : PROBES) {
            Biome biome = biomes.get(ResourceLocation.parse(probeId));
            helper.assertTrue(biome != null, "biome " + probeId + " must exist");
            Set<ResourceLocation> ids = new HashSet<>();
            for (HolderSet<PlacedFeature> step : biome.getGenerationSettings().features()) {
                for (Holder<PlacedFeature> holder : step) {
                    ResourceLocation id = placed.getKey(holder.value());
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }
            for (String required : REQUIRED) {
                helper.assertTrue(ids.contains(ResourceLocation.parse(required)),
                    "biome " + probeId + " missing " + required);
            }
            for (ResourceLocation id : banned) {
                helper.assertTrue(!ids.contains(id), "biome " + probeId + " still has " + id);
            }
        }
        helper.succeed();
    }
}
