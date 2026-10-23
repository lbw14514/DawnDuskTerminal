package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;

/**
 * {@code OreVeinifier} 写死的矿脉填充物，重映射到晨昏线调色板
 *
 * <p>原版大矿脉不是特征，是代码生成的：{@code OreVeinifier.VeinType} 把方块状态烤进两个枚举常量——
 *
 * <pre>
 * COPPER(Blocks.COPPER_ORE, Blocks.RAW_COPPER_BLOCK, Blocks.GRANITE,  0,  50)
 * IRON  (Blocks.DEEPSLATE_IRON_ORE, Blocks.RAW_IRON_BLOCK, Blocks.TUFF, -60, -8)
 * </pre>
 *
 * <p>所以 {@code ore_veins_enabled: true} 时，数据包说什么都没用，维度照样长原版花岗岩与原版凝灰岩。
 * 矿石方块本身不动，只换这两种代替石头的填充物，这正是调色板要的
 */
public final class ChronoVeinPalette {
    private ChronoVeinPalette() {}

    private static final class Holder {
        static final BlockState OCHRE = ModTerrainBlocks.OCHRE.get().defaultBlockState();
        static final BlockState TWILIGHT_STONE = ModTerrainBlocks.TWILIGHT_STONE.get().defaultBlockState();
    }

    /**
     * @return 替换后的状态；本方块不是写死的矿脉填充物时原样返回。{@code null} 直接透传：区块方块规则
     *     用 null 表示「保留默认方块」，而这是最常见的情况
     */
    public static BlockState remap(BlockState state) {
        if (state == null) {
            return null;
        }
        // 只有两个写死的填充物，直接比较 Block 比每方块查一次 IdentityHashMap 便宜
        if (state.getBlock() == Blocks.GRANITE) {
            return Holder.OCHRE;
        }
        if (state.getBlock() == Blocks.TUFF) {
            return Holder.TWILIGHT_STONE;
        }
        return state;
    }

    /**
     * 包住区块方块规则，让写死的矿脉填充物变成调色板方块
     *
     * <p>{@code BlockStateFiller#calculate} 是 {@code @Nullable}，用 {@code null} 表示「保留默认方
     * 块」，而这是绝大多数情况，null 必须直接透传。弄错的话几乎每个方块都会 NPE，区块永远生成不完，所以
     * 这段放在这个可测的方法里，而不是内联进 mixin
     */
    public static NoiseChunk.BlockStateFiller wrap(NoiseChunk.BlockStateFiller original) {
        return context -> {
            BlockState state = original.calculate(context);
            return state == null ? null : remap(state);
        };
    }
}
