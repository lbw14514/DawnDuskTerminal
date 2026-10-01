package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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
        long hash = cellHash(cellX, cellZ) ^ level.getSeed();
        int startX = (int) (cellX * grid) + (int) Math.floorMod(hash, grid - pocket + 1);
        int startZ = (int) (cellZ * grid) + (int) Math.floorMod(hash >>> 20, grid - pocket + 1);
        int endX = startX + pocket;
        int endZ = startZ + pocket;
        if (chunkX < startX - 1 || chunkX >= endX + 1
            || chunkZ < startZ - 1 || chunkZ >= endZ + 1) {
            return false;
        }
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        int y0 = config.floorY();
        int y1 = config.topY();
        int minX = startX << 4;
        int minZ = startZ << 4;
        int maxX = endX << 4;
        int maxZ = endZ << 4;
        double radius = pocket * 8.0D;
        double band = BAND;
        double cx = (minX + maxX) / 2.0D;
        double cz = (minZ + maxZ) / 2.0D;
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean changed = false;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int bx = x0 + x;
                int bz = z0 + z;
                double cheb = Math.max(Math.abs(bx - cx), Math.abs(bz - cz));
                if (cheb > radius + band) {
                    continue;
                }
                boolean core = cheb <= radius - band;
                for (int y = y0; y <= y1; y++) {
                    if (!core) {
                        double edge = radius - cheb;
                        double wob = wildNoise(bx, y, bz) * 12.0D + fineNoise(bx, y, bz);
                        if (edge + wob <= 0.0D) {
                            continue;
                        }
                    }
                    BlockPos pos = new BlockPos(bx, y, bz);
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

    public static boolean inZone(long seed, int grid, int pocket, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long cellX = Math.floorDiv(chunkX, grid);
        long cellZ = Math.floorDiv(chunkZ, grid);
        long hash = cellHash(cellX, cellZ) ^ seed;
        int startX = (int) (cellX * grid) + (int) Math.floorMod(hash, grid - pocket + 1);
        int startZ = (int) (cellZ * grid) + (int) Math.floorMod(hash >>> 20, grid - pocket + 1);
        int endX = startX + pocket;
        int endZ = startZ + pocket;
        if (chunkX < startX - 1 || chunkX > endX || chunkZ < startZ - 1 || chunkZ > endZ) {
            return false;
        }
        double radius = pocket * 8.0D;
        double cx = (startX * 16 + endX * 16) / 2.0D;
        double cz = (startZ * 16 + endZ * 16) / 2.0D;
        double cheb = Math.max(Math.abs(x - cx), Math.abs(z - cz));
        return cheb <= radius + BAND;
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
