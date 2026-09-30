package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class HollowPocketFeature extends Feature<HollowPocketFeature.Config> {
    public record Config(int minChunks, int maxChunks, int floorY, int topY) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
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
        int x0 = origin.getX() & ~15;
        int z0 = origin.getZ() & ~15;
        int y0 = config.floorY();
        int y1 = config.topY();
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean changed = false;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
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
}
