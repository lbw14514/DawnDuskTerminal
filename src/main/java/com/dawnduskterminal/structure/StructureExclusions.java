package com.dawnduskterminal.structure;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import twilightforest.init.TFStructures;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 晨昏线维度的结构表，也是「允许生成哪些结构」的唯一出处
 *
 * <p>{@code StructureExclusionMixin} 会压制 id <em>不</em>在 {@link #ALLOWED} 里的每一个结构，所以这一
 * 张表就是全部排除策略：在这里就是允许，其余一律排除。之前的 {@code EXCLUDED_TF} / {@code EXCLUDED_VANILLA}
 * 黑名单、{@code WHITELIST} 白名单，以及 {@code StructurePlacements} 里按原版与暮色森林分组的复用表，
 * 语义互相矛盾又重叠，现在合并成这三张表。只有 {@link #ALLOWED} 影响行为，另外两张只用于启动时的校验
 * 日志
 */
public final class StructureExclusions {
    /** 白名单。名字只用于启动时的校验日志 */
    private static final Map<String, ResourceLocation> ALLOWED = new LinkedHashMap<>();

    /** 暮色森林里有、本维度有意不放行的结构。排除由「不在白名单」自然产生，这张表只为把差距写清楚 */
    private static final Map<String, ResourceLocation> UNMAPPED = new LinkedHashMap<>();

    /** 暮色森林的地标。它们不是注册的结构，靠暮色森林自己的群系标签摆放，这里只做记录 */
    private static final List<String> LANDMARKS = List.of(
        "黑曜石柱", "圆石阵", "水井", "地基遗迹", "德鲁伊小屋",
        "石笋", "空心树墩", "树丛遗迹", "墓地"
    );

    static {
        ALLOWED.put("废弃传送门", ResourceLocation.withDefaultNamespace("ruined_portal"));
        ALLOWED.put("废弃传送门 沙漠", ResourceLocation.withDefaultNamespace("ruined_portal_desert"));
        ALLOWED.put("废弃传送门 丛林", ResourceLocation.withDefaultNamespace("ruined_portal_jungle"));
        ALLOWED.put("废弃传送门 山地", ResourceLocation.withDefaultNamespace("ruined_portal_mountain"));
        ALLOWED.put("废弃传送门 海洋", ResourceLocation.withDefaultNamespace("ruined_portal_ocean"));
        ALLOWED.put("废弃传送门 沼泽", ResourceLocation.withDefaultNamespace("ruined_portal_swamp"));
        ALLOWED.put("沉船", ResourceLocation.withDefaultNamespace("shipwreck"));
        ALLOWED.put("搁浅沉船", ResourceLocation.withDefaultNamespace("shipwreck_beached"));
        ALLOWED.put("掠夺者前哨站", ResourceLocation.withDefaultNamespace("pillager_outpost"));
        ALLOWED.put("沙漠神殿", ResourceLocation.withDefaultNamespace("desert_pyramid"));
        ALLOWED.put("雪屋", ResourceLocation.withDefaultNamespace("igloo"));
        ALLOWED.put("废弃矿井", ResourceLocation.withDefaultNamespace("mineshaft"));
        ALLOWED.put("废弃矿井 平顶山", ResourceLocation.withDefaultNamespace("mineshaft_mesa"));
        ALLOWED.put("树洞", TFStructures.HOLLOW_TREE.location());
        ALLOWED.put("倒下的空心木头", TFStructures.FALLEN_TRUNK.location());
        ALLOWED.put("蘑菇城堡", TFStructures.MUSHROOM_TOWER.location());
        ALLOWED.put("营地", TFStructures.CAMP.location());
        ALLOWED.put("树篱迷宫", TFStructures.HEDGE_MAZE.location());
        ALLOWED.put("空心矿山", TFStructures.HOLLOW_HILL_SMALL.location());

        UNMAPPED.put("工兵矿山 文档未提", TFStructures.HOLLOW_HILL_MEDIUM.location());
        UNMAPPED.put("亡灵矿山 文档未提", TFStructures.HOLLOW_HILL_LARGE.location());
        UNMAPPED.put("沼泽空心树 文档未提", TFStructures.SWAMP_HOLLOW_TREE.location());
    }

    private static final Set<ResourceLocation> ALLOWED_IDS = Set.copyOf(ALLOWED.values());

    private static final Set<ResourceLocation> LOGGED = ConcurrentHashMap.newKeySet();

    private StructureExclusions() {}

    public static void validate(Registry<Structure> structures) {
        report(structures, "allow-list", ALLOWED);
        report(structures, "unmapped", UNMAPPED);
        DawnDuskTerminal.LOGGER.info("DawnDuskTerminal landmark table has {} entries, landmarks are not registered structures, they follow the twilightforest biome tags",
            LANDMARKS.size());
    }

    private static void report(Registry<Structure> structures, String group, Map<String, ResourceLocation> table) {
        int missing = 0;
        for (Map.Entry<String, ResourceLocation> entry : table.entrySet()) {
            if (!structures.containsKey(entry.getValue())) {
                DawnDuskTerminal.LOGGER.warn("DawnDuskTerminal structure table {} {} is not registered", group, entry.getValue());
                missing++;
            }
        }
        DawnDuskTerminal.LOGGER.info("DawnDuskTerminal structure table {} has {} entries {} resolvable", group, table.size(), table.size() - missing);
    }

    public static boolean isAllowed(ResourceLocation id) {
        return ALLOWED_IDS.contains(id);
    }

    public static boolean isExcluded(ResourceLocation id) {
        return !ALLOWED_IDS.contains(id);
    }

    public static void logSuppressed(ResourceLocation id) {
        if (LOGGED.add(id)) {
            DawnDuskTerminal.LOGGER.info("DawnDuskTerminal structure {} is suppressed in the chrono dimension", id);
        }
    }
}
