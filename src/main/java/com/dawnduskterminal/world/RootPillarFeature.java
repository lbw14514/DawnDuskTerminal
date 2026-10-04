package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class RootPillarFeature extends Feature<NoneFeatureConfiguration> {
    private static final int MIN_GAP = 2;
    private static final int MAX_PILLAR = 64;

    public RootPillarFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        ChunkPos chunkPos = new ChunkPos(origin);
        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z);
        int minY = level.getMinBuildHeight();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        LevelChunkSection[] sections = chunk.getSections();
        boolean changed = false;
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            if (!section.maybeHas(state -> state.is(BlockTags.LOGS))) {
                continue;
            }
            int baseY = minY + index * 16;
            for (int ly = 0; ly < 16; ly++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        BlockState state = section.getBlockState(lx, ly, lz);
                        if (!state.is(BlockTags.LOGS)) {
                            continue;
                        }
                        if (support(level, new BlockPos(minX + lx, baseY + ly, minZ + lz), state)) {
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private boolean support(WorldGenLevel level, BlockPos logPos, BlockState logState) {
        int minY = level.getMinBuildHeight();
        int x = logPos.getX();
        int z = logPos.getZ();
        int y = logPos.getY() - 1;
        int gap = 0;
        while (y > minY && gap <= MAX_PILLAR) {
            if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                break;
            }
            y--;
            gap++;
        }
        if (gap > MAX_PILLAR) {
            return false;
        }
        if (gap < MIN_GAP) {
            return false;
        }
        for (int fill = logPos.getY() - gap; fill < logPos.getY(); fill++) {
            level.setBlock(new BlockPos(x, fill, z), logState, 2);
        }
        return true;
    }
}
