package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class HollowPocketFeature extends Feature<HollowPocketFeature.Config> {
    public record Config(int gridChunks, int pocketChunks, int floorY, int topY) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("grid_chunks").forGetter(Config::gridChunks),
            Codec.INT.fieldOf("pocket_chunks").forGetter(Config::pocketChunks),
            Codec.INT.fieldOf("floor_y").forGetter(Config::floorY),
            Codec.INT.fieldOf("top_y").forGetter(Config::topY)
        ).apply(inst, Config::new));
    }

    public HollowPocketFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        Config config = context.config();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        int grid = config.gridChunks();
        int pocket = config.pocketChunks();
        long cellX = Math.floorDiv(chunkX, grid);
        long cellZ = Math.floorDiv(chunkZ, grid);
        long hash = cellHash(cellX, cellZ);
        int offsetX = (int) Math.floorMod(hash, grid - pocket + 1);
        int offsetZ = (int) Math.floorMod(hash >>> 20, grid - pocket + 1);
        int startX = (int) (cellX * grid) + offsetX;
        int startZ = (int) (cellZ * grid) + offsetZ;
        if (chunkX < startX || chunkX >= startX + pocket
            || chunkZ < startZ || chunkZ >= startZ + pocket) {
            return false;
        }
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        int y0 = config.floorY();
        int y1 = config.topY();
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean changed = false;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = y0; y <= y1; y++) {
                    BlockPos pos = new BlockPos(x0 + x, y, z0 + z);
                    if (level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    level.setBlock(pos, air, 2);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static long cellHash(long x, long z) {
        long h = x * 341873128712L + z * 132897987541L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h & 0x7FFFFFFFFFFFFFFFL;
    }
}
