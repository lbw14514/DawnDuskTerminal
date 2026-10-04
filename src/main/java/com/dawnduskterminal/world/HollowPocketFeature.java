package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class HollowPocketFeature extends Feature<HollowPocketFeature.Config> {
    public record Config(int gridChunks, int minChunks, int maxChunks, int floorY, int topY)
        implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("grid_chunks").forGetter(Config::gridChunks),
            Codec.INT.fieldOf("min_chunks").forGetter(Config::minChunks),
            Codec.INT.fieldOf("max_chunks").forGetter(Config::maxChunks),
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
        Cell cell = cell(level.getSeed(), config.gridChunks(), config.minChunks(), config.maxChunks(),
            chunkX, chunkZ);
        int size = cell.sizeChunks();
        if (chunkX < cell.startX() - 1 || chunkX > cell.startX() + size
            || chunkZ < cell.startZ() - 1 || chunkZ > cell.startZ() + size) {
            return false;
        }
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        int y0 = config.floorY();
        int y1 = config.topY();
        long hash = cell.hash();
        double cx = (cell.startX() + size / 2.0D) * 16.0D;
        double cz = (cell.startZ() + size / 2.0D) * 16.0D;
        double baseRadius = size * 8.0D;
        BlockState air = Blocks.AIR.defaultBlockState();
        double[] dists = new double[256];
        double[] shapes = new double[256];
        boolean[] cores = new boolean[256];
        boolean[] active = new boolean[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int bx = x0 + x;
                int bz = z0 + z;
                double dx = bx + 0.5D - cx;
                double dz = bz + 0.5D - cz;
                double dist = Math.sqrt(dx * dx + dz * dz);
                double shape = shapeRadius(baseRadius, Math.atan2(dz, dx), hash, bx, bz);
                if (dist > shape + BAND) {
                    continue;
                }
                int slot = x * 16 + z;
                dists[slot] = dist;
                shapes[slot] = shape;
                cores[slot] = dist <= shape - BAND;
                active[slot] = true;
            }
        }
        LevelChunkSection[] sections = level.getChunk(chunkX, chunkZ).getSections();
        int minBuildY = level.getMinBuildHeight();
        boolean changed = false;
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            int baseY = minBuildY + si * 16;
            if (baseY + 15 < y0 || baseY > y1) {
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int slot = x * 16 + z;
                    if (!active[slot]) {
                        continue;
                    }
                    boolean core = cores[slot];
                    double shape = shapes[slot];
                    double dist = dists[slot];
                    int bx = x0 + x;
                    int bz = z0 + z;
                    for (int ly = 0; ly < 16; ly++) {
                        int y = baseY + ly;
                        if (y < y0 || y > y1) {
                            continue;
                        }
                        BlockState current = section.getBlockState(x, ly, z);
                        if (current.isAir()) {
                            continue;
                        }
                        if (!core) {
                            double edge = shape - dist;
                            double wob = wildNoise(bx, y, bz) * 12.0D + fineNoise(bx, y, bz);
                            if (edge + wob <= 0.0D) {
                                continue;
                            }
                        }
                        BlockPos pos = new BlockPos(bx, y, bz);
                        level.setBlock(pos, air, 2);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static double wildNoise(int x, int y, int z) {
        double a = Math.sin(x * 0.21D + y * 0.13D) * Math.cos(z * 0.19D - y * 0.09D);
        double b = Math.sin((x + z) * 0.37D + y * 0.07D);
        double c = Math.sin(x * 0.53D - z * 0.47D + y * 0.31D);
        double d = Math.sin(y * 0.29D + x * 0.11D - z * 0.17D);
        return a * 0.35D + b * 0.30D + c * 0.20D + d * 0.15D;
    }

    private static double fineNoise(int x, int y, int z) {
        long h = x * 31871L ^ y * 9781L ^ z * 13763L;
        h ^= h >>> 27;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 29;
        return (double) (h >>> 11) / 9007199254740992.0D * 4.0D - 2.0D;
    }

    public static final double BAND = 24.0D;
    public static final int GRID_CHUNKS = 219;
    public static final int MIN_CHUNKS = 8;
    public static final int MAX_CHUNKS = 12;

    public static int[] centerBlock(long seed, int blockX, int blockZ) {
        Cell cell = cell(seed, GRID_CHUNKS, MIN_CHUNKS, MAX_CHUNKS, blockX >> 4, blockZ >> 4);
        return new int[] {
            (cell.startX() + cell.sizeChunks() / 2) * 16 + 8,
            (cell.startZ() + cell.sizeChunks() / 2) * 16 + 8,
            cell.sizeChunks()
        };
    }

    private record Cell(int sizeChunks, int startX, int startZ, long hash) {}

    private static Cell cell(long seed, int grid, int minChunks, int maxChunks, int chunkX, int chunkZ) {
        long cellX = Math.floorDiv(chunkX, grid);
        long cellZ = Math.floorDiv(chunkZ, grid);
        long hash = cellHash(cellX, cellZ) ^ seed;
        int span = Math.max(1, maxChunks - minChunks + 1);
        int size = minChunks + (int) Math.floorMod(hash >>> 7, span);
        int startX = (int) (cellX * grid) + (int) Math.floorMod(hash, grid - size + 1);
        int startZ = (int) (cellZ * grid) + (int) Math.floorMod(hash >>> 21, grid - size + 1);
        return new Cell(size, startX, startZ, hash);
    }

    private static double shapeRadius(double baseRadius, double angle, long hash, int x, int z) {
        double p1 = ((hash >>> 5) & 1023L) * 0.00613D;
        double p2 = ((hash >>> 15) & 1023L) * 0.00731D;
        double p3 = ((hash >>> 25) & 1023L) * 0.00917D;
        double lobes = Math.sin(angle * 3.0D + p1) * 0.34D
            + Math.sin(angle * 5.0D + p2) * 0.22D
            + Math.sin(angle * 8.0D + p3) * 0.14D
            + Math.sin(angle * 13.0D + p1 + p2) * 0.09D
            + Math.sin(angle * 19.0D + p3) * 0.05D;
        double ragged = valueNoise(x, z, hash, 1.0D / 72.0D, 11L) * 0.34D
            + valueNoise(x, z, hash, 1.0D / 30.0D, 37L) * 0.32D
            + valueNoise(x, z, hash, 1.0D / 12.0D, 53L) * 0.22D
            + valueNoise(x, z, hash, 1.0D / 5.0D, 71L) * 0.12D;
        return baseRadius * (0.88D + 0.24D * lobes) * (1.0D + 0.50D * ragged);
    }

    private static double valueNoise(int x, int z, long hash, double scale, long salt) {
        double fx = x * scale;
        double fz = z * scale;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        double tx = fx - ix;
        double tz = fz - iz;
        double sx = tx * tx * (3.0D - 2.0D * tx);
        double sz = tz * tz * (3.0D - 2.0D * tz);
        double v00 = corner01(ix, iz, hash, salt);
        double v10 = corner01(ix + 1, iz, hash, salt);
        double v01 = corner01(ix, iz + 1, hash, salt);
        double v11 = corner01(ix + 1, iz + 1, hash, salt);
        double a = v00 + (v10 - v00) * sx;
        double b = v01 + (v11 - v01) * sx;
        return (a + (b - a) * sz) * 2.0D - 1.0D;
    }

    private static double corner01(int x, int z, long hash, long salt) {
        long h = x * 341873128712L + z * 132897987541L + hash + salt * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (double) ((h >>> 11) & 0xFFFFFL) / 1048576.0D;
    }

    public static boolean inZone(long seed, int grid, int minChunks, int maxChunks, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        Cell cell = cell(seed, grid, minChunks, maxChunks, chunkX, chunkZ);
        int size = cell.sizeChunks();
        if (chunkX < cell.startX() - 1 || chunkX > cell.startX() + size
            || chunkZ < cell.startZ() - 1 || chunkZ > cell.startZ() + size) {
            return false;
        }
        double cx = (cell.startX() + size / 2.0D) * 16.0D;
        double cz = (cell.startZ() + size / 2.0D) * 16.0D;
        double dx = x + 0.5D - cx;
        double dz = z + 0.5D - cz;
        double shape = shapeRadius(size * 8.0D, Math.atan2(dz, dx), cell.hash(), x, z);
        return Math.sqrt(dx * dx + dz * dz) <= shape + BAND;
    }

    public static long cellHash(long x, long z) {
        long h = x * 341873128712L + z * 132897987541L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h & 0x7FFFFFFFFFFFFFFFL;
    }
}
