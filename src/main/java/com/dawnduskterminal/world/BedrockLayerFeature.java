package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class BedrockLayerFeature extends Feature<BedrockLayerFeature.Config> {
    public record Config(int rangeMinY, int rangeMaxY, int thickness, int sealY,
                         int pocketGridChunks, int pocketMinChunks, int pocketMaxChunks)
        implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("range_min_y").forGetter(Config::rangeMinY),
            Codec.INT.fieldOf("range_max_y").forGetter(Config::rangeMaxY),
            Codec.INT.fieldOf("thickness").forGetter(Config::thickness),
            Codec.INT.fieldOf("seal_y").forGetter(Config::sealY),
            Codec.INT.fieldOf("pocket_grid_chunks").forGetter(Config::pocketGridChunks),
            Codec.INT.fieldOf("pocket_min_chunks").forGetter(Config::pocketMinChunks),
            Codec.INT.fieldOf("pocket_max_chunks").forGetter(Config::pocketMaxChunks)
        ).apply(inst, Config::new));
    }

    private static final int MAX_SKIRT = 8;
    private static final int SOLID_CORE = 3;
    private static final int POCKET_RING = 16;

    public BedrockLayerFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        Config cfg = context.config();
        BlockState filler = com.dawnduskterminal.registry.ModBlocks.CHRONO_CRUST.get().defaultBlockState();
        int baseX = origin.getX() & ~15;
        int baseZ = origin.getZ() & ~15;
        long seed = level.getSeed();
        boolean pocketOn = cfg.pocketGridChunks() > 0;
        boolean pocketRing = pocketOn && nearPocket(seed, baseX >> 4, baseZ >> 4, 1);
        int windowLo = Math.max(level.getMinBuildHeight(), cfg.rangeMinY());
        int windowHi = Math.min(level.getMaxBuildHeight() - 1, cfg.rangeMaxY());
        int thickness = Math.max(1, cfg.thickness());
        int baseChunkX = baseX >> 4;
        int baseChunkZ = baseZ >> 4;
        ChunkAccess[][] grid = new ChunkAccess[3][3];
        for (int cx = 0; cx < 3; cx++) {
            for (int cz = 0; cz < 3; cz++) {
                grid[cx][cz] = level.getChunk(baseChunkX - 1 + cx, baseChunkZ - 1 + cz);
            }
        }
        int minBuildY = level.getMinBuildHeight();
        int[][] base = new int[18][18];
        for (int i = 0; i < 18; i++) {
            for (int j = 0; j < 18; j++) {
                base[i][j] = Integer.MIN_VALUE;
                int x = baseX - 1 + i;
                int z = baseZ - 1 + j;
                if (pocketRing && inRing(seed, x, z)) {
                    continue;
                }
                LevelChunkSection[] sections = grid[(x >> 4) - baseChunkX + 1][(z >> 4) - baseChunkZ + 1].getSections();
                int lx = x & 15;
                int lz = z & 15;
                for (int y = windowLo; y <= windowHi; y++) {
                    int si = (y - minBuildY) >> 4;
                    if (si < 0 || si >= sections.length) {
                        continue;
                    }
                    LevelChunkSection section = sections[si];
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }
                    if (!section.getBlockState(lx, y & 15, lz).isAir()) {
                        base[i][j] = y;
                        break;
                    }
                }
            }
        }
        boolean changed = false;
        for (int i = 1; i <= 16; i++) {
            for (int j = 1; j <= 16; j++) {
                int b = base[i][j];
                if (b == Integer.MIN_VALUE) {
                    continue;
                }
                int deepest = b;
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        int nb = base[i + di][j + dj];
                        if (nb != Integer.MIN_VALUE && nb < deepest) {
                            deepest = nb;
                        }
                    }
                }
                int skirt = Math.max(deepest, b - MAX_SKIRT);
                if (skirt > cfg.sealY()) {
                    skirt = Math.min(b, cfg.sealY());
                }
                int lowest = cfg.sealY() - MAX_SKIRT;
                if (skirt < lowest) {
                    skirt = lowest;
                }
                int top = Math.min(windowHi, cfg.sealY() + thickness);
                if (skirt > top) {
                    continue;
                }
                for (int y = skirt; y <= top; y++) {
                    BlockPos target = new BlockPos(baseX - 1 + i, y, baseZ - 1 + j);
                    if (level.isOutsideBuildHeight(target)) {
                        continue;
                    }
                    if (y > b + SOLID_CORE && !level.getBlockState(target).isAir()) {
                        long h = (baseX - 1 + i) * 341873128712L + (baseZ - 1 + j) * 132897987541L + y * 0x9E3779B97F4A7C15L;
                        h ^= h >>> 33;
                        h *= 0xff51afd7ed558ccdL;
                        h ^= h >>> 33;
                        if ((h & 3L) == 0L) {
                            continue;
                        }
                    }
                    level.setBlock(target, filler, 2);
                    changed = true;
                }
            }
        }
        if (pocketRing && carvePocket(level, seed, baseX, baseZ, windowLo, windowHi)) {
            changed = true;
        }
        return changed;
    }

    private static boolean carvePocket(WorldGenLevel level, long seed, int baseX, int baseZ, int lo, int hi) {
        BlockState air = Blocks.AIR.defaultBlockState();
        net.minecraft.world.level.block.Block crust = com.dawnduskterminal.registry.ModBlocks.CHRONO_CRUST.get();
        boolean changed = false;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int x = baseX + i;
                int z = baseZ + j;
                if (!inRing(seed, x, z)) {
                    continue;
                }
                for (int y = lo; y <= hi; y++) {
                    BlockPos target = new BlockPos(x, y, z);
                    BlockState cur = level.getBlockState(target);
                    if (cur.is(Blocks.BEDROCK) || cur.is(crust)) {
                        level.setBlock(target, air, 2);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static boolean inZone(long seed, int x, int z) {
        return HollowPocketFeature.inZone(seed, HollowPocketFeature.GRID_CHUNKS,
            HollowPocketFeature.MIN_CHUNKS, HollowPocketFeature.MAX_CHUNKS, x, z);
    }

    private static boolean inRing(long seed, int x, int z) {
        return inZone(seed, x, z)
            || inZone(seed, x - POCKET_RING, z)
            || inZone(seed, x + POCKET_RING, z)
            || inZone(seed, x, z - POCKET_RING)
            || inZone(seed, x, z + POCKET_RING);
    }

    private static boolean nearPocket(long seed, int chunkX, int chunkZ, int marginChunks) {
        int grid = HollowPocketFeature.GRID_CHUNKS;
        long cellX = Math.floorDiv(chunkX, grid);
        long cellZ = Math.floorDiv(chunkZ, grid);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int px = (int) ((cellX + dx) * grid + grid / 2) * 16 + 8;
                int pz = (int) ((cellZ + dz) * grid + grid / 2) * 16 + 8;
                int[] center = HollowPocketFeature.centerBlock(seed, px, pz);
                int size = center[2];
                int startX = Math.floorDiv(center[0] - 8, 16) - size / 2;
                int startZ = Math.floorDiv(center[1] - 8, 16) - size / 2;
                if (chunkX >= startX - 1 - marginChunks && chunkX <= startX + size + marginChunks
                    && chunkZ >= startZ - 1 - marginChunks && chunkZ <= startZ + size + marginChunks) {
                    return true;
                }
            }
        }
        return false;
    }
}
