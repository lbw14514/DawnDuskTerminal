package com.dawnduskterminal.structure;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureExclusions {
    public static final Map<String, ResourceLocation> EXCLUDED_TF = new LinkedHashMap<>();

    public static final Map<String, ResourceLocation> EXCLUDED_VANILLA = new LinkedHashMap<>();

    static {
        EXCLUDED_TF.put("谜题羊树丛", ResourceLocation.parse("twilightforest:quest_grove"));
        EXCLUDED_TF.put("娜迦庭院", ResourceLocation.parse("twilightforest:naga_courtyard"));
        EXCLUDED_TF.put("巫妖怪塔", ResourceLocation.parse("twilightforest:lich_tower"));
        EXCLUDED_TF.put("牛头人迷宫", ResourceLocation.parse("twilightforest:labyrinth"));
        EXCLUDED_TF.put("九头蛇巢穴", ResourceLocation.parse("twilightforest:hydra_lair"));
        EXCLUDED_TF.put("地精骑士要塞", ResourceLocation.parse("twilightforest:knight_stronghold"));
        EXCLUDED_TF.put("黑暗高塔", ResourceLocation.parse("twilightforest:dark_tower"));
        EXCLUDED_TF.put("雪怪洞窟", ResourceLocation.parse("twilightforest:yeti_cave"));
        EXCLUDED_TF.put("极光宫殿", ResourceLocation.parse("twilightforest:aurora_palace"));
        EXCLUDED_TF.put("巨魔洞窟", ResourceLocation.parse("twilightforest:troll_cave"));
        EXCLUDED_TF.put("云上小屋", ResourceLocation.parse("twilightforest:giant_house"));
        EXCLUDED_TF.put("终焉城堡", ResourceLocation.parse("twilightforest:final_castle"));

        EXCLUDED_VANILLA.put("远古城市", ResourceLocation.withDefaultNamespace("ancient_city"));
        EXCLUDED_VANILLA.put("埋藏的宝藏", ResourceLocation.withDefaultNamespace("buried_treasure"));
        EXCLUDED_VANILLA.put("林地府邸", ResourceLocation.withDefaultNamespace("mansion"));
        EXCLUDED_VANILLA.put("海底神殿", ResourceLocation.withDefaultNamespace("monument"));
        EXCLUDED_VANILLA.put("海底废墟", ResourceLocation.withDefaultNamespace("ocean_ruin_cold"));
        EXCLUDED_VANILLA.put("要塞", ResourceLocation.withDefaultNamespace("stronghold"));
        EXCLUDED_VANILLA.put("丛林神庙", ResourceLocation.withDefaultNamespace("jungle_pyramid"));
        EXCLUDED_VANILLA.put("沼泽小屋", ResourceLocation.withDefaultNamespace("swamp_hut"));
        EXCLUDED_VANILLA.put("古迹废墟", ResourceLocation.withDefaultNamespace("trail_ruins"));
        EXCLUDED_VANILLA.put("试炼密室", ResourceLocation.withDefaultNamespace("trial_chambers"));
    }

    private StructureExclusions() {}

    public static void validate(Registry<net.minecraft.world.level.levelgen.structure.Structure> structures) {
        report(structures, EXCLUDED_TF, "twilightforest");
        report(structures, EXCLUDED_VANILLA, "vanilla");
    }

    private static void report(Registry<net.minecraft.world.level.levelgen.structure.Structure> structures,
                               Map<String, ResourceLocation> table, String group) {
        int missing = 0;
        for (Map.Entry<String, ResourceLocation> entry : table.entrySet()) {
            if (!structures.containsKey(entry.getValue())) {
                DawnDuskTerminal.LOGGER.warn("DawnDuskTerminal exclusion {} {} is not a registered structure", group, entry.getValue());
                missing++;
            }
        }
        DawnDuskTerminal.LOGGER.info("DawnDuskTerminal exclusion table {} has {} entries {} resolvable", group, table.size(), table.size() - missing);
    }

    public static List<ResourceLocation> excludedStructureIds() {
        return List.copyOf(EXCLUDED_TF.values());
    }

    private static final Set<ResourceLocation> EXCLUDED_TF_IDS = Set.copyOf(EXCLUDED_TF.values());

    private static final Set<ResourceLocation> LOGGED = ConcurrentHashMap.newKeySet();

    public static final Set<ResourceLocation> WHITELIST = Set.of(
        ResourceLocation.withDefaultNamespace("ruined_portal"),
        ResourceLocation.withDefaultNamespace("ruined_portal_desert"),
        ResourceLocation.withDefaultNamespace("ruined_portal_jungle"),
        ResourceLocation.withDefaultNamespace("ruined_portal_mountain"),
        ResourceLocation.withDefaultNamespace("ruined_portal_ocean"),
        ResourceLocation.withDefaultNamespace("ruined_portal_swamp"),
        ResourceLocation.withDefaultNamespace("shipwreck"),
        ResourceLocation.withDefaultNamespace("shipwreck_beached"),
        ResourceLocation.withDefaultNamespace("pillager_outpost"),
        ResourceLocation.withDefaultNamespace("desert_pyramid"),
        ResourceLocation.withDefaultNamespace("igloo"),
        ResourceLocation.parse("twilightforest:hollow_tree"),
        ResourceLocation.parse("twilightforest:fallen_trunk"),
        ResourceLocation.parse("twilightforest:mushroom_tower"),
        ResourceLocation.parse("twilightforest:camp"),
        ResourceLocation.parse("twilightforest:hedge_maze"),
        ResourceLocation.parse("twilightforest:small_hollow_hill"));

    public static boolean isAllowed(ResourceLocation id) {
        return WHITELIST.contains(id);
    }

    public static boolean isExcluded(ResourceLocation id) {
        return !WHITELIST.contains(id);
    }

    public static void logSuppressed(ResourceLocation id) {
        if (LOGGED.add(id)) {
            DawnDuskTerminal.LOGGER.info("DawnDuskTerminal landmark {} is suppressed in the chrono dimension", id);
        }
    }
}
