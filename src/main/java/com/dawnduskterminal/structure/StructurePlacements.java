package com.dawnduskterminal.structure;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StructurePlacements {
    public static final Map<String, ResourceLocation> VANILLA_REUSED = new LinkedHashMap<>();

    public static final Map<String, ResourceLocation> TF_REUSED = new LinkedHashMap<>();

    public static final Map<String, ResourceLocation> TF_UNMAPPED = new LinkedHashMap<>();

    public static final List<String> TF_LANDMARKS = List.of(
        "黑曜石柱", "圆石阵", "水井", "地基遗迹", "德鲁伊小屋",
        "石笋", "空心树墩", "树丛遗迹", "墓地"
    );

    static {
        VANILLA_REUSED.put("废弃矿井", ResourceLocation.withDefaultNamespace("mineshaft"));
        VANILLA_REUSED.put("废弃传送门", ResourceLocation.withDefaultNamespace("ruined_portal"));
        VANILLA_REUSED.put("沉船", ResourceLocation.withDefaultNamespace("shipwreck"));
        VANILLA_REUSED.put("掠夺者前哨站", ResourceLocation.withDefaultNamespace("pillager_outpost"));
        VANILLA_REUSED.put("沙漠神殿", ResourceLocation.withDefaultNamespace("desert_pyramid"));
        VANILLA_REUSED.put("雪屋", ResourceLocation.withDefaultNamespace("igloo"));
        VANILLA_REUSED.put("村庄 平原", ResourceLocation.withDefaultNamespace("village_plains"));
        VANILLA_REUSED.put("村庄 沙漠", ResourceLocation.withDefaultNamespace("village_desert"));
        VANILLA_REUSED.put("村庄 热带草原", ResourceLocation.withDefaultNamespace("village_savanna"));
        VANILLA_REUSED.put("村庄 雪原", ResourceLocation.withDefaultNamespace("village_snowy"));
        VANILLA_REUSED.put("村庄 针叶林", ResourceLocation.withDefaultNamespace("village_taiga"));

        TF_REUSED.put("树洞", ResourceLocation.parse("twilightforest:hollow_tree"));
        TF_REUSED.put("倒下的空心木头", ResourceLocation.parse("twilightforest:fallen_trunk"));
        TF_REUSED.put("蘑菇城堡", ResourceLocation.parse("twilightforest:mushroom_tower"));
        TF_REUSED.put("营地", ResourceLocation.parse("twilightforest:camp"));
        TF_REUSED.put("树篱迷宫", ResourceLocation.parse("twilightforest:hedge_maze"));
        TF_REUSED.put("空心矿山", ResourceLocation.parse("twilightforest:small_hollow_hill"));

        TF_UNMAPPED.put("工兵矿山 文档未提", ResourceLocation.parse("twilightforest:medium_hollow_hill"));
        TF_UNMAPPED.put("亡灵矿山 文档未提", ResourceLocation.parse("twilightforest:large_hollow_hill"));
        TF_UNMAPPED.put("沼泽空心树 文档未提", ResourceLocation.parse("twilightforest:swamp_hollow_tree"));
    }

    private StructurePlacements() {}

    public static void validate(Registry<Structure> structures) {
        report(structures, VANILLA_REUSED, "vanilla reused");
        report(structures, TF_REUSED, "twilightforest reused");
        report(structures, TF_UNMAPPED, "twilightforest unmapped");
        DawnDuskTerminal.LOGGER.info("DawnDuskTerminal landmark table has {} entries, landmarks are not registered structures, they follow the twilightforest biome tags",
            TF_LANDMARKS.size());
    }

    private static void report(Registry<Structure> structures, Map<String, ResourceLocation> table, String group) {
        int missing = 0;
        for (Map.Entry<String, ResourceLocation> entry : table.entrySet()) {
            if (!structures.containsKey(entry.getValue())) {
                DawnDuskTerminal.LOGGER.warn("DawnDuskTerminal structure table {} {} is not registered", group, entry.getValue());
                missing++;
            }
        }
        DawnDuskTerminal.LOGGER.info("DawnDuskTerminal structure table {} has {} entries {} resolvable", group, table.size(), table.size() - missing);
    }
}
