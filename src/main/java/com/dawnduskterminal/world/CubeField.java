package com.dawnduskterminal.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * 一台「反建器」压出的立方场：17×17×17，方块自己就是中心那一格，往每个方向各推 {@value #RADIUS} 格
 *
 * <p>试验物品 065 与 069 共用这一份。两台机器只是场里发生的事不同（065 让伤害作废、069 让更新作废），
 * 「场在哪、场里有没有这一格」是同一件事：按维度存一张场心表，方块实体装载/卸载时登记与撤登记，
 * 方块的 {@code onPlace} / {@code onRemove} 管摆下与挖掉，于是摆下那一 tick 就生效，不用等方块实体装载
 *
 * <p>判定不扫 17³ = 4913 格：只有现存的场心会被挨个量一遍距离。每次判定还会回头核一遍那一格还是不是
 * 这台机器（{@link #block} 给的就是它的方块），方块实体那边撤登记真漏了也不会留下一个死活不掉的幽灵场；
 * 核之前先问 {@code ChunkSource#hasChunk}，不会为了找方块把区块读进来
 *
 * <p>没有场的地方每条判定只花一次 {@code HashMap} 查表：伤害事件与方块更新都是每秒上千次的热路径，
 * 这里不做任何比查表更贵的事
 */
public final class CubeField {
    /** 半边长度：17×17×17 就是中心那一格往每个方向各推 8 格 */
    public static final int RADIUS = 8;

    /** 边长：17 */
    public static final int SIZE = RADIUS * 2 + 1;

    /** 每个维度里现存的场心 */
    private final Map<ResourceKey<Level>, Set<BlockPos>> centres = new HashMap<>();

    /** 场心那一格该是什么方块：判定时回头核一遍，用来认幽灵场 */
    private final Supplier<Block> block;

    private CubeField(Supplier<Block> block) {
        this.block = block;
    }

    /**
     * 开一张场
     *
     * <p>方块用 {@link Supplier} 传进来而不是直接传实例：注册表里的方块要等到注册完才有值，
     * 这两张场在类初始化时就建出来了
     */
    public static CubeField of(Supplier<Block> block) {
        return new CubeField(block);
    }

    /** 登记一个场心。方块实体装载、方块刚摆下时各来一次，重复登记没有额外代价（就是个集合） */
    public void track(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) {
            return;
        }
        this.centres.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos);
    }

    /** 撤掉一个场心。方块实体卸载、方块被挖掉或推走时各来一次，撤不存在的没有代价 */
    public void untrack(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) {
            return;
        }
        Set<BlockPos> tracked = this.centres.get(level.dimension());
        if (tracked == null) {
            return;
        }
        tracked.remove(pos);
        if (tracked.isEmpty()) {
            this.centres.remove(level.dimension());
        }
    }

    /** 服务器停下：整张表清干净，别带到下一次启动 */
    public void clear() {
        this.centres.clear();
    }

    /**
     * 这一格在不在某个场里
     *
     * <p>先按方块坐标卡 17³（{@code |dx|、|dy|、|dz| <= 8}），再核那一格还是不是本方块；方块没了就把这条
     * 场心顺手丢掉，幽灵场只可能活到它下一次被问到
     */
    public boolean covers(Level level, BlockPos at) {
        Set<BlockPos> tracked = this.centres.get(level.dimension());
        if (tracked == null || tracked.isEmpty()) {
            return false;
        }
        Iterator<BlockPos> iterator = tracked.iterator();
        while (iterator.hasNext()) {
            BlockPos centre = iterator.next();
            if (Math.abs(at.getX() - centre.getX()) > RADIUS
                || Math.abs(at.getY() - centre.getY()) > RADIUS
                || Math.abs(at.getZ() - centre.getZ()) > RADIUS) {
                continue;
            }
            // 方块实体在装载/卸载时登记与撤登记，这一格这时一定是载入的；真漏了也不去把区块读进来
            // （LevelReader 那几个 hasChunkAt 在 1.21.1 已废弃，不读区块的那条判定留在 ChunkSource 上）
            if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(centre.getX()),
                SectionPos.blockToSectionCoord(centre.getZ()))) {
                continue;
            }
            if (!level.getBlockState(centre).is(this.block.get())) {
                iterator.remove();
                continue;
            }
            return true;
        }
        return false;
    }
}
