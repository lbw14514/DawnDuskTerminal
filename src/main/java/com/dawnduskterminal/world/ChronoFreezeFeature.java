package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * 晨昏线调色板对 {@code minecraft:freeze_top_layer} 的替代
 *
 * <p>原版那份是代码不是数据：{@code SnowAndFreezeFeature} 字面写死 {@code Blocks.SNOW.defaultBlockState()}
 * 与 {@code Blocks.ICE.defaultBlockState()}，数据包再怎么改也挡不住寒冷群系长出原版雪。我们的群系副本
 * 改成把特征表指到这里
 *
 * <p>一处有意为之的差异：原版问的是 {@code Biome#shouldFreeze}，它判断水的流体标签。本维度的水是
 * portal_fluid，不在那个标签里，所以这里也对着我们自己的流体判断，否则调色板的冰永远不会出现
 */
public class ChronoFreezeFeature extends Feature<NoneFeatureConfiguration> {
    public ChronoFreezeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                top.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                below.set(top).move(Direction.DOWN, 1);
                Biome biome = level.getBiome(top).value();

                // 原版 shouldFreeze 分两段：温度与降水的门槛，再判断水的流体标签。这里把门槛改用在我
                // 们自己的流体上，而不是绕过它，否则每个群系里每片流体表面，包括温暖群系，都会冻上
                boolean freezes = biome.shouldFreeze(level, below, false)
                        || (biome.coldEnoughToSnow(below)
                                && level.getBlockState(below).is(ModBlocks.PORTAL_FLUID.get()));
                if (freezes) {
                    level.setBlock(below, ModTerrainBlocks.FROZEN_CANOPY.get().defaultBlockState(), 2);
                }

                if (biome.shouldSnow(level, top)) {
                    level.setBlock(top, ModTerrainBlocks.CANOPY_FROST.get().defaultBlockState(), 2);
                    BlockState under = level.getBlockState(below);
                    if (under.hasProperty(SnowyDirtBlock.SNOWY)) {
                        level.setBlock(below, under.setValue(SnowyDirtBlock.SNOWY, Boolean.TRUE), 2);
                    }
                }
            }
        }
        return true;
    }
}
