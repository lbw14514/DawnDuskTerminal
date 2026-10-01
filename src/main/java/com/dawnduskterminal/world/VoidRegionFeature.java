package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
        long cellX = Math.floorDiv((long) chunkX * 16, period);
        long cellZ = Math.floorDiv((long) chunkZ * 16, period);
        int centerX = (int) ((cellX + 0.5D) * period);
        int centerZ = (int) ((cellZ + 0.5D) * period);
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        int ring = 14;
        if (x0 + 15 < centerX - radius - ring || x0 > centerX + radius + ring
            || z0 + 15 < centerZ - radius - ring || z0 > centerZ + radius + ring) {
            return false;
        }
        long seed = level.getSeed();
        boolean changed = false;
        int minY = Math.max(level.getMinBuildHeight(), cfg.minY());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, cfg.maxY());
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = x0 + lx;
                int wz = z0 + lz;
                int nx = wx - centerX;
                int nz = wz - centerZ;
                double limit = radius + edgeWobble(wx, wz, seed);
                double chebyshev = Math.max(Math.abs(nx), Math.abs(nz));
                boolean inside = chebyshev <= limit;
                boolean inRing = !inside && chebyshev <= limit + ring;
                if (!inside && !inRing) {
                    continue;
                }
                double edgeRatio = chebyshev / limit;
                int cutTop = maxY;
                if (inside) {
                    if (edgeRatio > 0.82D) {
                        double t = (edgeRatio - 0.82D) / 0.18D;
                        double trend = t * (maxY - minY) * 0.85D;
                        double detail = edgeDetail(wx, wz, seed) * (2.0D + 8.0D * t);
                        double blob = edgeBlob(wx, wz, seed);
                        double cavity = 0.0D;
                        if (blob > 0.05D) {
                            cavity = (blob - 0.05D) * 15.0D;
                        } else if (blob < -0.05D) {
                            cavity = (blob + 0.05D) * 15.0D;
                        }
                        double micro = hash01(wx, wz, seed) * 3.0D - 1.5D;
                        cutTop = (int) Math.round(maxY - trend + detail + cavity + micro);
                        if (cutTop < minY) {
                            cutTop = minY;
                        }
                        if (cutTop > maxY) {
                            cutTop = maxY;
                        }
                    }
                }
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(wx, y, wz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (inside) {
                        if (y <= cutTop) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                            changed = true;
                        } else if (edgeRatio > 0.82D && isTreePart(state)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                            changed = true;
                        }
                    } else if (isTreePart(state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        changed = true;
                    }
                }
            }
        }
        changed |= buildCenterIsland(level, centerX, centerZ, x0, z0, minY);
        return changed;
    }

    private static double edgeWobble(int wx, int wz, long seed) {
        double seedPhase = (seed % 1000L) * 0.0037D;
        double a = Math.sin(wx * 0.0113D + seedPhase) * Math.cos(wz * 0.0097D - seedPhase);
        double b = Math.sin((wx + wz) * 0.0041D + seedPhase * 2.0D);
        double c = Math.sin(wx * 0.0031D + wz * 0.0023D + seedPhase * 3.0D);
        return 120.0D * (a * 0.5D + b * 0.3D + c * 0.2D);
    }

    private static double edgeDetail(int wx, int wz, long seed) {
        double phase = (seed % 4096L) * 0.00173D;
        double a = Math.sin(wx * 0.355D + phase) * Math.cos(wz * 0.315D - phase);
        double b = Math.sin((wx + wz) * 0.565D + phase * 2.0D);
        double c = Math.sin(wx * 0.155D + wz * 0.135D + phase * 3.0D);
        return a * 0.45D + b * 0.35D + c * 0.20D;
    }

    private static double edgeBlob(int wx, int wz, long seed) {
        double phase = (seed % 8192L) * 0.00087D;
        double a = Math.sin(wx * 0.183D + phase) * Math.cos(wz * 0.201D - phase);
        double b = Math.sin(wx * 0.11D - wz * 0.13D + phase * 2.7D);
        return a * 0.6D + b * 0.4D;
    }

    private static double hash01(int wx, int wz, long seed) {
        long h = mix(wx, wz, seed);
        return (double) (h >>> 11) * 0x1.0p-53;
    }

    private static boolean isTreePart(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    private static boolean buildCenterIsland(WorldGenLevel level, int centerX, int centerZ,
                                             int x0, int z0, int minY) {
        int reach = 22;
        if (x0 + 15 < centerX - reach || x0 > centerX + reach
            || z0 + 15 < centerZ - reach || z0 > centerZ + reach) {
            return false;
        }
        BlockState stone = com.dawnduskterminal.registry.ModTerrainBlocks.SKY_STONE.get().defaultBlockState();
        BlockState soil = com.dawnduskterminal.registry.ModBlocks.SKY_SOIL.get().defaultBlockState();
        int baseY = Math.max(minY + 12, 14);
        boolean changed = false;
        for (int wx = Math.max(x0, centerX - reach); wx <= Math.min(x0 + 15, centerX + reach); wx++) {
            for (int wz = Math.max(z0, centerZ - reach); wz <= Math.min(z0 + 15, centerZ + reach); wz++) {
                int dx = wx - centerX;
                int dz = wz - centerZ;
                double dist = Math.sqrt(dx * dx + dz * dz);
                double edge = 1.0D - dist / reach;
                if (edge <= 0.0D) {
                    continue;
                }
                int top = baseY + (int) Math.round(Math.sin(wx * 0.37D) * 1.5D
                    + Math.cos(wz * 0.41D) * 1.5D);
                int depth = 2 + (int) Math.round(edge * 7.0D);
                for (int d = 0; d < depth; d++) {
                    BlockPos pos = new BlockPos(wx, top - d, wz);
                    if (level.isOutsideBuildHeight(pos)) {
                        continue;
                    }
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, d < 2 ? soil : stone, 2);
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
