package com.dawnduskterminal.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

public final class SkyIslandLocator {
    public static final int SKY_MIN_Y = 190;
    private static final int STEP = 48;
    private static final int MAX_RADIUS = 3072;

    private SkyIslandLocator() {}

    public static int[] nearest(ServerLevel level, int blockX, int blockZ) {
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
}
