package com.dawnduskterminal.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

public final class SkyIslandLocator {
    public static final int SKY_MIN_Y = 190;
    private static final int STEP = 48;
    private static final int MAX_RADIUS = 3072;
    private static final ResourceKey<ConfiguredFeature<?, ?>> KEY = ResourceKey.create(
        Registries.CONFIGURED_FEATURE,
        ResourceLocation.fromNamespaceAndPath("dawnduskterminal", "sky_island"));

    private SkyIslandLocator() {}

    public static int[] nearest(ServerLevel level, int blockX, int blockZ) {
        SkyIslandFeature.Config cfg = config(level);
        if (cfg == null) {
            return null;
        }
        int spacing = Math.max(2, cfg.spacing());
        long seed = level.getSeed();
        int cellCX = Math.floorDiv(blockX >> 4, spacing);
        int cellCZ = Math.floorDiv(blockZ >> 4, spacing);
        int[][] cand = new int[9][3];
        long[] dist = new long[9];
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = cellCX + dx;
                int cz = cellCZ + dz;
                long hash = SkyIslandFeature.mix(cx, cz, seed);
                RandomSource random = RandomSource.create(hash);
                int ox = Math.floorMod(hash, spacing);
                int oz = Math.floorMod(hash >>> 16, spacing);
                int x = (cx * spacing + ox) * 16 + 8;
                int z = (cz * spacing + oz) * 16 + 8;
                int y = Mth.nextInt(random, cfg.minY(), cfg.maxY());
                Mth.nextInt(random, cfg.minRadius(), cfg.maxRadius());
                int thickness = Mth.nextInt(random, cfg.minThickness(), cfg.maxThickness());
                int centerY = Mth.clamp(y, cfg.minY() + thickness, cfg.maxY() - thickness);
                cand[n][0] = x;
                cand[n][1] = centerY + thickness;
                cand[n][2] = z;
                dist[n] = (long) (x - blockX) * (x - blockX) + (long) (z - blockZ) * (z - blockZ);
                n++;
            }
        }
        for (int a = 0; a < n - 1; a++) {
            int m = a;
            for (int b = a + 1; b < n; b++) {
                if (dist[b] < dist[m]) {
                    m = b;
                }
            }
            if (m != a) {
                long td = dist[a];
                dist[a] = dist[m];
                dist[m] = td;
                int[] tc = cand[a];
                cand[a] = cand[m];
                cand[m] = tc;
            }
        }
        for (int a = 0; a < n; a++) {
            int x = cand[a][0];
            int z = cand[a][2];
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (top > SKY_MIN_Y) {
                return new int[] {x, top, z};
            }
        }
        return spiral(level, blockX, blockZ);
    }

    private static int[] spiral(ServerLevel level, int blockX, int blockZ) {
        for (int ring = 0; ring <= MAX_RADIUS; ring += STEP) {
            for (int dx = -ring; dx <= ring; dx += STEP) {
                for (int dz = -ring; dz <= ring; dz += STEP) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = blockX + dx;
                    int z = blockZ + dz;
                    level.getChunk(x >> 4, z >> 4);
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    if (top > SKY_MIN_Y) {
                        return new int[] {x, top, z};
                    }
                }
            }
        }
        return null;
    }

    private static SkyIslandFeature.Config config(ServerLevel level) {
        return level.getServer().registryAccess()
            .registryOrThrow(Registries.CONFIGURED_FEATURE)
            .getOptional(KEY)
            .map(feature -> feature.config() instanceof SkyIslandFeature.Config c ? c : null)
            .orElse(null);
    }
}
