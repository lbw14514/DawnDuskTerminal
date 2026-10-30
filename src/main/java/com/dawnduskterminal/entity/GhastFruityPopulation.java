package com.dawnduskterminal.entity;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 「恶果」的自然生成容量：同一个维度里、玩家加载范围内同时最多 {@value #CAP} 只
 *
 * <p>需求是「一个玩家加载的刷怪范围最多 4 个」，而 {@code getMaxSpawnClusterSize} 只管
 * 「一次生成最多几只」，不管累计数量：群系的 monster 表里只有这一种生物时，光靠它会一批接一批地
 * 刷下去，整片刷怪范围都是它们 —— 每批几只由 {@code getMaxSpawnClusterSize} 决定（现在按原版恶魂取 1），
 * 总量则必须在这里拦
 *
 * <p>所以这里直接数「当前加载中的个体」。加载中的区块跟着玩家走，数出来的就是玩家刷怪范围里的数量；
 * 区块卸载时实体离开本维度，自然从计数里消失
 *
 * <p>计数每 tick 只做一次：第一次问到时数一遍，之后同一 tick 内的询问读缓存，并用「占位」
 * 把已经放行的生成记上，因此同一 tick 里连续生成也不会突破上限。下一个 tick 重新数，
 * 占位归零 —— 打掉一只之后最多等一个 tick 就又能补上
 */
public final class GhastFruityPopulation {
    /** 上限：同一维度加载范围内最多 4 只 */
    public static final int CAP = 4;

    private static final Map<ResourceKey<Level>, Snapshot> SNAPSHOTS = new HashMap<>();

    /** 当前加载中的个体数。不读缓存，测试与调试用 */
    public static int loaded(ServerLevel level) {
        // 整个世界范围：实体管理器只会遍历加载中的区段，所以这就是「加载中的个体」
        return level.getEntitiesOfClass(GhastFruityEntity.class, WORLD_BOUNDS).size();
    }

    /**
     * 还能不能再放一只进来
     *
     * @return {@code true} 表示还没到上限，并顺手占掉一个位置；调用方随即生成的那一只就占这个位置
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

    /**
     * 水平方向覆盖整个世界、竖直方向覆盖整个建筑高度
     *
     * <p>不用 {@code level.getWorldBounds()} 之类的接口，是因为实体管理器本来就不会返回加载区段之外的实体，
     * 这个盒子只是给查询划定一个足够大的范围
     */
    private static final AABB WORLD_BOUNDS =
        new AABB(-3.0E7D, -3.0E7D, -3.0E7D, 3.0E7D, 3.0E7D, 3.0E7D);

    private GhastFruityPopulation() {}
}
