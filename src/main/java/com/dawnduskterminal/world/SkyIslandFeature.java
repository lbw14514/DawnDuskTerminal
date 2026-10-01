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
        int cellX = Math.floorDiv(origin.getX(), spacing << 4);
        int cellZ = Math.floorDiv(origin.getZ(), spacing << 4);
        long seed = level.getSeed();
        long hash = mix(cellX, cellZ, seed);
        RandomSource random = RandomSource.create(hash);
        int originChunkX = Math.floorMod(hash, spacing);
        int originChunkZ = Math.floorMod(hash >>> 16, spacing);
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        if (chunkX - cellX * spacing != originChunkX || chunkZ - cellZ * spacing != originChunkZ) {
            return false;
        }
        int centerX = (cellX * spacing + originChunkX) * 16 + 8;
        int centerZ = (cellZ * spacing + originChunkZ) * 16 + 8;
        int y = Mth.nextInt(random, config.minY(), config.maxY());
        int radius = Mth.nextInt(random, config.minRadius(), config.maxRadius());
        int thickness = Mth.nextInt(random, config.minThickness(), config.maxThickness());
        int tail = radius / 2 + Mth.nextInt(random, 3, 8);
        BlockState stone = com.dawnduskterminal.registry.ModTerrainBlocks.SKY_STONE.get().defaultBlockState();
        BlockState soil = com.dawnduskterminal.registry.ModBlocks.SKY_SOIL.get().defaultBlockState();
        boolean changed = false;
        int r = radius + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) {
                    continue;
                }
                double edge = dist / radius;
                double edgeFade = 1.0D - edge * edge * 0.35D;
                int top = y + (int) Math.round(edgeFade * Mth.nextInt(random, 0, 1));
                int depth = (int) Math.round(thickness * edgeFade);
                for (int dy = 0; dy < tail; dy++) {
                    double taper = 1.0D - (double) dy / tail;
                    if (dy > depth && dist > radius * taper) {
                        break;
                    }
                    if (dy > depth + 12) {
                        break;
                    }
                    BlockPos pos = new BlockPos(centerX + dx, top - dy, centerZ + dz);
                    if (!level.isOutsideBuildHeight(pos) && level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, dy < 2 ? soil : stone, 2);
                        changed = true;
                    }
                }
            }
        }
        return changed;
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
