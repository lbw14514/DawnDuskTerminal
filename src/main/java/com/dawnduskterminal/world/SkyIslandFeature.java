package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class SkyIslandFeature extends Feature<SkyIslandFeature.Config> {
    public record Config(int spacing, int minY, int maxY, int minRadius, int maxRadius, int minThickness, int maxThickness) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("spacing").forGetter(Config::spacing),
            Codec.INT.fieldOf("min_y").forGetter(Config::minY),
            Codec.INT.fieldOf("max_y").forGetter(Config::maxY),
            Codec.INT.fieldOf("min_radius").forGetter(Config::minRadius),
            Codec.INT.fieldOf("max_radius").forGetter(Config::maxRadius),
            Codec.INT.fieldOf("min_thickness").forGetter(Config::minThickness),
            Codec.INT.fieldOf("max_thickness").forGetter(Config::maxThickness)
        ).apply(inst, Config::new));
    }

    public SkyIslandFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        Config config = context.config();
        int spacing = Math.max(2, config.spacing());
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        int cellX = Math.floorDiv(chunkX, spacing);
        int cellZ = Math.floorDiv(chunkZ, spacing);
        long seed = level.getSeed();
        long hash = mix(cellX, cellZ, seed);
        RandomSource random = RandomSource.create(hash);
        int originChunkX = Math.floorMod(hash, spacing);
        int originChunkZ = Math.floorMod(hash >>> 16, spacing);
        int centerX = (cellX * spacing + originChunkX) * 16 + 8;
        int centerZ = (cellZ * spacing + originChunkZ) * 16 + 8;
        int y = Mth.nextInt(random, config.minY(), config.maxY());
        int radius = Mth.nextInt(random, config.minRadius(), config.maxRadius());
        int thickness = Mth.nextInt(random, config.minThickness(), config.maxThickness());
        double phase1 = random.nextDouble() * Math.PI * 2.0D;
        double phase2 = random.nextDouble() * Math.PI * 2.0D;
        double phase3 = random.nextDouble() * Math.PI * 2.0D;
        double phase4 = random.nextDouble() * Math.PI * 2.0D;
        int reach = radius + 16;
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        if (x0 + 15 < centerX - reach || x0 > centerX + reach
            || z0 + 15 < centerZ - reach || z0 > centerZ + reach) {
            return false;
        }
        BlockState stone = com.dawnduskterminal.registry.ModTerrainBlocks.SKY_STONE.get().defaultBlockState();
        BlockState soil = com.dawnduskterminal.registry.ModBlocks.SKY_SOIL.get().defaultBlockState();
        boolean changed = false;
        int minX = Math.max(x0, centerX - reach);
        int maxX = Math.min(x0 + 15, centerX + reach);
        int minZ = Math.max(z0, centerZ - reach);
        int maxZ = Math.min(z0 + 15, centerZ + reach);
        for (int wx = minX; wx <= maxX; wx++) {
            for (int wz = minZ; wz <= maxZ; wz++) {
                int dx = wx - centerX;
                int dz = wz - centerZ;
                double dist = Math.sqrt(dx * dx + dz * dz);
                double angle = Math.atan2(dz, dx);
                double wobble = Math.sin(angle * 2.0D + phase1) * 0.34D
                    + Math.sin(angle * 3.0D + phase2) * 0.20D
                    + Math.sin(angle * 5.0D + phase3) * 0.14D
                    + Math.sin(angle * 8.0D + phase4) * 0.09D
                    + Math.sin(angle * 13.0D + phase1 * 1.7D) * 0.05D;
                double limit = radius * (1.0D + wobble);
                if (dist > limit) {
                    continue;
                }
                double edge = dist / limit;
                double edgeFade = 1.0D - edge * edge * 0.45D;
                double topNoise = Math.sin(wx * 0.17D + phase1) * 0.5D
                    + Math.sin(wz * 0.21D + phase2) * 0.3D
                    + Math.sin((wx + wz) * 0.09D + phase3) * 0.2D
                    + Math.sin(wx * 0.43D + phase4) * 0.12D
                    + Math.sin(wz * 0.37D + phase1 * 2.1D) * 0.12D;
                int top = y + (int) Math.round(topNoise * 6.0D);
                double depthNoise = Math.sin(wx * 0.13D + phase2) * 0.5D
                    + Math.cos(wz * 0.16D + phase3) * 0.5D;
                int depth = (int) Math.round(thickness * edgeFade) + (int) Math.round(depthNoise * 4.0D);
                if (depth < 2) {
                    depth = 2;
                }
                int tail = (int) Math.round(radius * 0.30D * (0.5D + 0.5D * depthNoise));
                int maxDrop = Math.max(depth, depth + tail);
                for (int dy = 0; dy <= maxDrop; dy++) {
                    if (dy > depth) {
                        double taper = 1.0D - (double) (dy - depth) / Math.max(1, tail + 1);
                        double jag = 1.0D + Math.sin(dy * 1.9D + wx * 0.11D + phase4) * 0.08D;
                        if (dist > limit * taper * jag) {
                            break;
                        }
                    }
                    BlockPos pos = new BlockPos(wx, top - dy, wz);
                    if (level.isOutsideBuildHeight(pos)) {
                        continue;
                    }
                    if (level.getBlockState(pos).isAir()) {
                        BlockState fill = dy < 2 ? soil : stone;
                        if (dy >= 2) {
                            BlockState ore = oreAt(wx, top - dy, wz, seed);
                            if (ore != null) {
                                fill = ore;
                            }
                        }
                        level.setBlock(pos, fill, 2);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static BlockState oreAt(int x, int y, int z, long seed) {
        int h = hash3(x, y, z, seed);
        double roll = (double) (h & 0xFFFF) / 65535.0D;
        if (y < 0) {
            if (roll < 0.006D) {
                return Blocks.DIAMOND_ORE.defaultBlockState();
            }
            if (roll < 0.014D) {
                return Blocks.REDSTONE_ORE.defaultBlockState();
            }
            if (roll < 0.030D) {
                return Blocks.IRON_ORE.defaultBlockState();
            }
            if (roll < 0.042D) {
                return Blocks.LAPIS_ORE.defaultBlockState();
            }
            if (roll < 0.058D) {
                return Blocks.COPPER_ORE.defaultBlockState();
            }
            if (roll < 0.064D) {
                return com.dawnduskterminal.registry.ModTerrainBlocks.SCARLET_MOLYBDENUM_ORE.get().defaultBlockState();
            }
            return null;
        }
        if (roll < 0.004D) {
            return Blocks.GOLD_ORE.defaultBlockState();
        }
        if (roll < 0.014D) {
            return Blocks.IRON_ORE.defaultBlockState();
        }
        if (roll < 0.028D) {
            return Blocks.COPPER_ORE.defaultBlockState();
        }
        if (roll < 0.046D) {
            return Blocks.COAL_ORE.defaultBlockState();
        }
        if (roll < 0.050D) {
            return com.dawnduskterminal.registry.ModTerrainBlocks.SCARLET_MOLYBDENUM_ORE.get().defaultBlockState();
        }
        return null;
    }

    private static int hash3(int x, int y, int z, long seed) {
        long h = seed;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) y * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) z * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (int) (h ^ (h >>> 32));
    }

    private static long mix(int cx, int cz, long seed) {
        long h = seed;
        h ^= (long) cx * 0x9E3779B97F4A7C15L;
        h ^= (long) cz * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return h;
    }
}
