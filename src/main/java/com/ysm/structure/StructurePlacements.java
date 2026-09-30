package com.ysm.structure;

import com.ysm.Ysm;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;

public final class StructurePlacements {
    public static final List<ResourceLocation> VANILLA_REUSED = List.of(
        ResourceLocation.withDefaultNamespace("mineshaft"),
        ResourceLocation.withDefaultNamespace("ruined_portal"),
        ResourceLocation.withDefaultNamespace("shipwreck"),
        ResourceLocation.withDefaultNamespace("pillager_outpost"),
        ResourceLocation.withDefaultNamespace("desert_pyramid"),
        ResourceLocation.withDefaultNamespace("igloo"),
        ResourceLocation.withDefaultNamespace("village_plains"),
        ResourceLocation.withDefaultNamespace("village_desert"),
        ResourceLocation.withDefaultNamespace("village_savanna"),
        ResourceLocation.withDefaultNamespace("village_snowy"),
        ResourceLocation.withDefaultNamespace("village_taiga")
    );

    public static final List<ResourceLocation> TF_SMALL = List.of(
        ResourceLocation.parse("twilightforest:obsidian_pillar"),
        ResourceLocation.parse("twilightforest:cobble_tower"),
        ResourceLocation.parse("twilightforest:well"),
        ResourceLocation.parse("twilightforest:foundation"),
        ResourceLocation.parse("twilightforest:druid_hut"),
        ResourceLocation.parse("twilightforest:stalagmite"),
        ResourceLocation.parse("twilightforest:hollow_tree"),
        ResourceLocation.parse("twilightforest:hollow_stump"),
        ResourceLocation.parse("twilightforest:fallen_hollow_log"),
        ResourceLocation.parse("twilightforest:mushroom_tower"),
        ResourceLocation.parse("twilightforest:hedge_maze"),
        ResourceLocation.parse("twilightforest:graveyard"),
        ResourceLocation.parse("twilightforest:camp"),
        ResourceLocation.parse("twilightforest:ruins")
    );

    public static final List<ResourceLocation> TF_TREES = List.of(
        ResourceLocation.parse("twilightforest:tree/root"),
        ResourceLocation.parse("twilightforest:tree/mangrove"),
        ResourceLocation.parse("twilightforest:tree/darkwood"),
        ResourceLocation.parse("twilightforest:tree/rainbow_oak"),
        ResourceLocation.parse("twilightforest:tree/canopy"),
        ResourceLocation.parse("twilightforest:tree/time"),
        ResourceLocation.parse("twilightforest:tree/transformation"),
        ResourceLocation.parse("twilightforest:tree/mining"),
        ResourceLocation.parse("twilightforest:tree/sorting")
    );

    private StructurePlacements() {}

    public static void validate(Registry<Structure> structures) {
        report(structures, VANILLA_REUSED, "vanilla");
        report(structures, TF_SMALL, "twilightforest small");
        report(structures, TF_TREES, "twilightforest tree");
    }

    private static void report(Registry<Structure> structures, List<ResourceLocation> ids, String group) {
        int missing = 0;
        for (ResourceLocation id : ids) {
            if (!structures.containsKey(id)) {
                missing++;
            }
        }
        if (missing > 0) {
            Ysm.LOGGER.warn("YSM {} structure table: {} of {} ids are not registered yet", group, missing, ids.size());
        } else {
            Ysm.LOGGER.info("YSM {} structure table: all {} ids present", group, ids.size());
        }
    }
}
