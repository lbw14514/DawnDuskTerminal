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
        long salt1 = random.nextLong();
        long salt2 = random.nextLong();
        int reach = radius;
        int centerY = Mth.clamp(y, config.minY() + thickness, config.maxY() - thickness);
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        if (x0 + 15 < centerX - reach || x0 > centerX + reach
            || z0 + 15 < centerZ - reach || z0 > centerZ + reach) {
            return false;
        }
        BlockState stone = com.dawnduskterminal.registry.ModTerrainBlocks.SKY_STONE.get().defaultBlockState();
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
                if (dist > radius) {
                    continue;
                }
                double ring = 1.0D - dist / radius;
                for (int dy = -thickness; dy <= thickness; dy++) {
                    int wy = centerY + dy;
                    double vert = 1.0D - (double) (dy * dy) / (double) (thickness * thickness);
                    double n = noise3(wx, wy, wz, salt1, salt2);
                    double d = n * 0.45D + ring * vert * 1.65D - 0.35D;
                    if (d <= 0.0D) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(wx, wy, wz);
                    if (level.isOutsideBuildHeight(pos)) {
                        continue;
                    }
                    if (!level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    BlockState fill = stone;
                    BlockState ore = oreAt(wx, wy, wz, seed);
                    if (ore != null) {
                        fill = ore;
                    }
                    level.setBlock(pos, fill, 2);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static double noise3(int x, int y, int z, long salt1, long salt2) {
        return grid3(x, y, z, 96, salt1) * 0.55D
            + grid3(x, y, z, 34, salt1 + 7L) * 0.30D
            + grid3(x, y, z, 12, salt2) * 0.15D;
    }

    private static double grid3(int x, int y, int z, int scale, long salt) {
        double fx = (double) x / scale;
        double fy = (double) y / scale;
        double fz = (double) z / scale;
        int ix = (int) Math.floor(fx);
        int iy = (int) Math.floor(fy);
        int iz = (int) Math.floor(fz);
        double tx = fx - ix;
        double ty = fy - iy;
        double tz = fz - iz;
        double sx = tx * tx * (3.0D - 2.0D * tx);
        double sy = ty * ty * (3.0D - 2.0D * ty);
        double sz = tz * tz * (3.0D - 2.0D * tz);
        double c000 = lattice3(ix, iy, iz, salt);
        double c100 = lattice3(ix + 1, iy, iz, salt);
        double c010 = lattice3(ix, iy + 1, iz, salt);
        double c110 = lattice3(ix + 1, iy + 1, iz, salt);
        double c001 = lattice3(ix, iy, iz + 1, salt);
        double c101 = lattice3(ix + 1, iy, iz + 1, salt);
        double c011 = lattice3(ix, iy + 1, iz + 1, salt);
        double c111 = lattice3(ix + 1, iy + 1, iz + 1, salt);
        double x00 = c000 + (c100 - c000) * sx;
        double x10 = c010 + (c110 - c010) * sx;
        double x01 = c001 + (c101 - c001) * sx;
        double x11 = c011 + (c111 - c011) * sx;
        double y0 = x00 + (x10 - x00) * sy;
        double y1 = x01 + (x11 - x01) * sy;
        return (y0 + (y1 - y0) * sz) * 2.0D - 1.0D;
    }

    private static double lattice3(int x, int y, int z, long salt) {
        long h = salt;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) y * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) z * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (double) (h >>> 11) * 0x1.0p-53;
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

    public static long mix(int cx, int cz, long seed) {
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
