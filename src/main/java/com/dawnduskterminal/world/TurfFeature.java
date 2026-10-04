package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class TurfFeature extends Feature<TurfFeature.Config> {
    public record Config(int minY, int maxY, int soilDepth) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("min_y").forGetter(Config::minY),
            Codec.INT.fieldOf("max_y").forGetter(Config::maxY),
            Codec.INT.fieldOf("soil_depth").forGetter(Config::soilDepth)
        ).apply(inst, Config::new));
    }

    public TurfFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        Config cfg = context.config();
        BlockState top = ModBlocks.SKY_SOIL.get().defaultBlockState();
        BlockState soil = ModTerrainBlocks.EMBER_SOIL.get().defaultBlockState();
        BlockState stone = ModTerrainBlocks.SKY_STONE.get().defaultBlockState();
        int baseX = origin.getX() & ~15;
        int baseZ = origin.getZ() & ~15;
        int depth = Math.max(1, cfg.soilDepth());
        int minY = Math.max(level.getMinBuildHeight(), cfg.minY());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, cfg.maxY());
        LevelChunkSection[] sections = level.getChunk(baseX >> 4, baseZ >> 4).getSections();
        ChunkAccess chunk = level.getChunk(baseX >> 4, baseZ >> 4);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minBuildY = level.getMinBuildHeight();
        boolean changed = false;
        for (int si = sections.length - 1; si >= 0; si--) {
            LevelChunkSection section = sections[si];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            int baseY = minBuildY + si * 16;
            if (baseY > maxY || baseY + 15 < minY) {
                continue;
            }
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int ly = 15; ly >= 0; ly--) {
                        int y = baseY + ly;
                        if (y < minY || y > maxY) {
                            continue;
                        }
                        if (!section.getBlockState(lx, ly, lz).is(stone.getBlock())) {
                            continue;
                        }
                        BlockState above = ly == 15
                            ? level.getBlockState(new BlockPos(baseX + lx, y + 1, baseZ + lz))
                            : section.getBlockState(lx, ly + 1, lz);
                        if (!above.isAir()) {
                            continue;
                        }
                        if (surface.getFirstAvailable(lx, lz) - 1 != y) {
                            continue;
                        }
                        BlockPos hp = new BlockPos(baseX + lx, y, baseZ + lz);
                        level.setBlock(hp, top, 2);
                        changed = true;
                        for (int d = 1; d <= depth; d++) {
                            BlockPos sp = hp.below(d);
                            if (!level.getBlockState(sp).is(stone.getBlock())) {
                                break;
                            }
                            level.setBlock(sp, soil, 2);
                        }
                    }
                }
            }
        }
        return changed;
    }
}
