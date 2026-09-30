package com.ysm.structure;

import com.ysm.Ysm;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class StructureExclusions {
    public static final List<ResourceLocation> EXCLUDED_TF = List.of(
        ResourceLocation.parse("twilightforest:quest_ram_ruins"),
        ResourceLocation.parse("twilightforest:naga_courtyard"),
        ResourceLocation.parse("twilightforest:witch_hut"),
        ResourceLocation.parse("twilightforest:labyrinth"),
        ResourceLocation.parse("twilightforest:hydra_lair"),
        ResourceLocation.parse("twilightforest:goblin_knight_stronghold"),
        ResourceLocation.parse("twilightforest:dark_tower"),
        ResourceLocation.parse("twilightforest:yeti_cave"),
        ResourceLocation.parse("twilightforest:aurora_palace"),
        ResourceLocation.parse("twilightforest:troll_cave"),
        ResourceLocation.parse("twilightforest:cloud_cottage"),
        ResourceLocation.parse("twilightforest:final_castle")
    );

    public static final List<ResourceLocation> EXCLUDED_VANILLA = List.of(
        ResourceLocation.withDefaultNamespace("ancient_city"),
        ResourceLocation.withDefaultNamespace("buried_treasure"),
        ResourceLocation.withDefaultNamespace("woodland_mansion"),
        ResourceLocation.withDefaultNamespace("monument"),
        ResourceLocation.withDefaultNamespace("ocean_ruin_cold"),
        ResourceLocation.withDefaultNamespace("ocean_ruin_warm"),
        ResourceLocation.withDefaultNamespace("stronghold"),
        ResourceLocation.withDefaultNamespace("jungle_pyramid"),
        ResourceLocation.withDefaultNamespace("swamp_hut"),
        ResourceLocation.withDefaultNamespace("trail_ruins"),
        ResourceLocation.withDefaultNamespace("trial_chambers")
    );

    private StructureExclusions() {}

    public static void validate(Registry<net.minecraft.world.level.levelgen.structure.Structure> structures) {
        for (ResourceLocation id : EXCLUDED_TF) {
            if (!structures.containsKey(id)) {
                Ysm.LOGGER.info("YSM exclusion target {} is not present, nothing to exclude", id);
            }
        }
    }
}
