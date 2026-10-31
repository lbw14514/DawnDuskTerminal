package com.dawnduskterminal.entity;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 「大恶魂」的自然生成容量：同一个维度里、玩家加载范围内同时最多 {@value #CAP} 只
 *
 * <p>与 {@link GhastFruityPopulation} 是同一套办法，只是上限不同：自然生成上限 1，
 * 所以加载范围内已经有一只时，群系的 monster 表再怎么掷都不会再放第二只进来；
 * 打掉之后最多一个 tick 就能补上
 *
 * <p>{@code getMaxSpawnClusterSize} 管的是「一次生成最多几只」，拦不住累计数量，这里才是总量
 */
public final class GhastElitePopulation {
    /** 上限：同一维度加载范围内最多 1 只 */
    public static final int CAP = 1;

    private static final Map<ResourceKey<Level>, Snapshot> SNAPSHOTS = new HashMap<>();

    /** 当前加载中的个体数。不读缓存，测试与调试用 */
    public static int loaded(ServerLevel level) {
        return level.getEntitiesOfClass(GhastEliteEntity.class, WORLD_BOUNDS).size();
    }

    /**
     * 还能不能再放一只进来
     *
     * @return {@code true} 表示还没到上限，并顺手占掉那唯一的位置
     */
    public static boolean tryReserve(ServerLevel level) {
        long now = level.getGameTime();
        Snapshot snapshot = SNAPSHOTS.computeIfAbsent(level.dimension(), key -> new Snapshot());
        if (snapshot.gameTime != now) {
            snapshot.live = loaded(level);
            snapshot.reserved = 0;
            snapshot.gameTime = now;
        }
        if (snapshot.live + snapshot.reserved >= CAP) {
            return false;
        }
        snapshot.reserved++;
        return true;
    }

    private static final class Snapshot {
        private long gameTime = Long.MIN_VALUE;
        private int live;
        private int reserved;
    }

    /** 与 {@link GhastFruityPopulation} 同一个盒子：实体管理器只遍历加载中的区段 */
    private static final AABB WORLD_BOUNDS =
        new AABB(-3.0E7D, -3.0E7D, -3.0E7D, 3.0E7D, 3.0E7D, 3.0E7D);

    private GhastElitePopulation() {}
}
