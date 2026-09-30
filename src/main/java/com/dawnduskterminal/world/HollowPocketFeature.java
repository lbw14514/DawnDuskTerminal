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

public class HollowPocketFeature extends Feature<HollowPocketFeature.Config> {
    public record Config(int minY, int maxY, int minRadius, int maxRadius) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("min_y").forGetter(Config::minY),
            Codec.INT.fieldOf("max_y").forGetter(Config::maxY),
            Codec.INT.fieldOf("min_radius").forGetter(Config::minRadius),
            Codec.INT.fieldOf("max_radius").forGetter(Config::maxRadius)
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
        RandomSource random = context.random();
        int radius = Mth.nextInt(random, config.minRadius(), config.maxRadius());
        int y0 = Mth.clamp(origin.getY(), config.minY(), config.maxY());
        int y1 = Mth.clamp(y0 + Mth.nextInt(random, 24, 56), config.minY(), config.maxY());
        int cx = origin.getX();
        int cz = origin.getZ();
        BlockState air = Blocks.AIR.defaultBlockState();
        long seedX = random.nextLong();
        long seedZ = random.nextLong();
        boolean changed = false;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double edge = wobble(seedX, seedZ, x, z, radius);
                if (edge <= 0.0D) {
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    BlockPos pos = new BlockPos(cx + x, y, cz + z);
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

    private static double wobble(long seedX, long seedZ, int x, int z, int radius) {
        double dist = Math.sqrt((double) (x * x + z * z));
        double angle = Math.atan2(z, x);
        double noise = Math.sin(angle * 3.0D + (seedX & 0xFF) * 0.1D) * 0.18D
            + Math.sin(angle * 5.0D + (seedZ & 0xFF) * 0.13D) * 0.11D
            + Math.sin(angle * 8.0D + ((seedX >> 8) & 0xFF) * 0.07D) * 0.07D;
        return radius * (1.0D + noise) - dist;
    }
}
