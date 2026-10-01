package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class VoidRegionFeature extends Feature<VoidRegionFeature.Config> {
    public record Config(int period, int radius, int minY, int maxY) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("period").forGetter(Config::period),
            Codec.INT.fieldOf("radius").forGetter(Config::radius),
            Codec.INT.fieldOf("min_y").forGetter(Config::minY),
            Codec.INT.fieldOf("max_y").forGetter(Config::maxY)
        ).apply(inst, Config::new));
    }

    public VoidRegionFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        Config cfg = context.config();
        int period = Math.max(64, cfg.period());
        int radius = Math.max(8, cfg.radius());
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        long cellX = Math.floorDiv(chunkX * 16, period);
        long cellZ = Math.floorDiv(chunkZ * 16, period);
        long hash = mix(cellX, cellZ, level.getSeed());
        int centerX = (int) ((cellX + 0.5D) * period);
        int centerZ = (int) ((cellZ + 0.5D) * period);
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        if (x0 + 15 < centerX - radius || x0 > centerX + radius
            || z0 + 15 < centerZ - radius || z0 > centerZ + radius) {
            return false;
        }
        boolean changed = false;
        int minY = Math.max(level.getMinBuildHeight(), cfg.minY());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, cfg.maxY());
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = x0 + lx;
                int wz = z0 + lz;
                if (Math.abs(wx - centerX) > radius || Math.abs(wz - centerZ) > radius) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(wx, y, wz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static long mix(long x, long z, long seed) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h ^= z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h;
    }
}
