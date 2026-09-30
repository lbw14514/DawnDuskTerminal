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
    public record Config(int minRadius, int maxRadius, int floorY, int topY) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("min_radius").forGetter(Config::minRadius),
            Codec.INT.fieldOf("max_radius").forGetter(Config::maxRadius),
            Codec.INT.fieldOf("floor_y").forGetter(Config::floorY),
            Codec.INT.fieldOf("top_y").forGetter(Config::topY)
        ).apply(inst, Config::new));
    }

    private static final int CHUNK = 16;

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
        int cx = origin.getX();
        int cz = origin.getZ();
        int y0 = config.floorY();
        int y1 = config.topY();
        long seedX = random.nextLong();
        long seedZ = random.nextLong();
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean changed = false;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (wobble(seedX, seedZ, x, z, radius) <= 0.0D) {
                    continue;
                }
                int bx = cx + x;
                int bz = cz + z;
                if (bx >> 4 != cx >> 4 || bz >> 4 != cz >> 4) {
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
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

    private static double wobble(long seedX, long seedZ, int x, int z, int radius) {
        double dist = Math.sqrt((double) (x * x + z * z));
        double angle = Math.atan2(z, x);
        double noise = Math.sin(angle * 3.0D + (seedX & 0xFF) * 0.1D) * 0.20D
            + Math.sin(angle * 5.0D + (seedZ & 0xFF) * 0.13D) * 0.13D
            + Math.sin(angle * 8.0D + ((seedX >> 8) & 0xFF) * 0.07D) * 0.08D
            + Math.sin(angle * 13.0D + ((seedZ >> 8) & 0xFF) * 0.11D) * 0.05D;
        return radius * (1.0D + noise) - dist;
    }
}
