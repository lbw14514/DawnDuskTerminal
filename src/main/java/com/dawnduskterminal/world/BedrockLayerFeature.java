package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class BedrockLayerFeature extends Feature<BedrockLayerFeature.Config> {
    public record Config(int y, int thickness) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("y").forGetter(Config::y),
            Codec.INT.fieldOf("thickness").forGetter(Config::thickness)
        ).apply(inst, Config::new));
    }

    public BedrockLayerFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        Config cfg = context.config();
        BlockState filler = com.dawnduskterminal.registry.ModTerrainBlocks.PERIDOTITE.get().defaultBlockState();
        int baseX = origin.getX() & ~15;
        int baseZ = origin.getZ() & ~15;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int t = 0; t < Math.max(1, cfg.thickness()); t++) {
                    BlockPos pos = new BlockPos(baseX + lx, cfg.y() - t, baseZ + lz);
                    if (!level.isOutsideBuildHeight(pos)) {
                        level.setBlock(pos, filler, 2);
                    }
                }
            }
        }
        return true;
    }
}
