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
        long hash = cellHash(cellX, cellZ);
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
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean changed = false;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (!inside(x0 + x, z0 + z, minX, minZ, maxX, maxZ, radius)) {
                    continue;
                }
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

    private static boolean inside(int bx, int bz, int minX, int minZ, int maxX, int maxZ, double radius) {
        double cx = (minX + maxX) / 2.0D;
        double cz = (minZ + maxZ) / 2.0D;
        double dx = bx - cx;
        double dz = bz - cz;
        double angle = Math.atan2(dz, dx);
        double noise = Math.sin(angle * 3.0D) * 0.20D
            + Math.sin(angle * 5.0D + 1.3D) * 0.12D
            + Math.sin(angle * 8.0D + 2.7D) * 0.08D
            + Math.sin(angle * 13.0D + 4.1D) * 0.05D;
        double limit = radius * (1.0D + noise);
        return dx * dx + dz * dz <= limit * limit;
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
