package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 试验物品 069 那一台反建器压出来的场：范围里的方块更新不进行处理
 *
 * <p>概念取自原版的「更新抑制器」：更新抑制器让一段更新链把队列挤爆，往后排进队列的方块更新就被整批丢掉，
 * 于是那一片区域在一段时间里对所有方块更新装死。这台机器把「一段时间」换成「一个固定的范围」——
 * 17×17×17 的方块立方（{@link CubeField}），方块自己就是中心那一格。落在场里的方块更新一律不处理：
 * 邻居更新与形状更新不送进去，计划刻与随机刻到点也不执行，场里的方块因此冻在原地（沙不掉、红石不响应、
 * 活板门不动、作物不长、冰不化），直到机器被挖掉
 *
 * <p>「场里」按<b>被更新那一格</b>算：更新总是「某一格收到了一个更新」，收到更新的那一格在场里就不处理。
 * 场里的变化照样会把更新送给场外的邻居——那是场外那一格的事，不在场的范围内
 *
 * <p>判定由三个 mixin 送进来，它们都只调 {@link #suppressed} 一句：
 * <ul>
 *   <li>{@code BlockStateUpdateSuppressMixin}：{@code BlockStateBase#handleNeighborChanged}（邻居更新）、
 *       {@code #tick}（方块计划刻）、{@code #randomTick}（方块随机刻）
 *   <li>{@code LevelShapeUpdateSuppressMixin}：{@code Level#neighborShapeChanged}（形状更新）
 *   <li>{@code FluidStateUpdateSuppressMixin}：{@code FluidState#tick}、{@code #randomTick}（流体的两种刻）
 * </ul>
 *
 * <p>没有场的地方 {@link #suppressed} 只花一次查表：方块更新是每秒上千次的热路径，这里不能有更贵的东西
 */
public final class Experiment069Field {
    /** 这台机器的场，场心认 {@code dawnduskterminal:experiment_069} */
    private static final CubeField FIELD = CubeField.of(() -> ModBlocks.EXPERIMENT_069.get());

    private Experiment069Field() {}

    /** 登记一个场心：方块实体装载、方块刚摆下时各来一次 */
    public static void track(Level level, BlockPos pos) {
        FIELD.track(level, pos);
    }

    /** 撤掉一个场心：方块实体卸载、方块被挖掉或推走时各来一次 */
    public static void untrack(Level level, BlockPos pos) {
        FIELD.untrack(level, pos);
    }

    /** 服务器停下：整张表清干净 */
    public static void clear() {
        FIELD.clear();
    }

    /**
     * 这一格在不在某个场里
     *
     * <p>mixin 侧唯一的口子。客户端照样问：客户端手里那张表是空的（场心只由服务端的方块实体的
     * {@code onLoad} 登记），于是答案永远是「不在」，不必为客户端再维护一份
     */
    public static boolean suppressed(Level level, BlockPos pos) {
        return FIELD.covers(level, pos);
    }
}
