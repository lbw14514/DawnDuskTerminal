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

public class BedrockLayerFeature extends Feature<BedrockLayerFeature.Config> {
    public record Config(int rangeMinY, int rangeMaxY, int thickness, int sealY,
                         int pocketGridChunks, int pocketChunks) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("range_min_y").forGetter(Config::rangeMinY),
            Codec.INT.fieldOf("range_max_y").forGetter(Config::rangeMaxY),
            Codec.INT.fieldOf("thickness").forGetter(Config::thickness),
            Codec.INT.fieldOf("seal_y").forGetter(Config::sealY),
            Codec.INT.fieldOf("pocket_grid_chunks").forGetter(Config::pocketGridChunks),
            Codec.INT.fieldOf("pocket_chunks").forGetter(Config::pocketChunks)
        ).apply(inst, Config::new));
    }

    private static final int MAX_SKIRT = 8;
    private static final int SOLID_CORE = 3;

    public BedrockLayerFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        Config cfg = context.config();
        BlockState filler = Blocks.BEDROCK.defaultBlockState();
        int baseX = origin.getX() & ~15;
        int baseZ = origin.getZ() & ~15;
        int windowLo = Math.max(level.getMinBuildHeight(), cfg.rangeMinY());
        int windowHi = Math.min(level.getMaxBuildHeight() - 1, cfg.rangeMaxY());
        int thickness = Math.max(1, cfg.thickness());
        long seed = level.getSeed();
        int[][] base = new int[18][18];
        for (int i = 0; i < 18; i++) {
            for (int j = 0; j < 18; j++) {
                base[i][j] = Integer.MIN_VALUE;
                int x = baseX - 1 + i;
                int z = baseZ - 1 + j;
                if (HollowPocketFeature.inZone(seed, cfg.pocketGridChunks(), cfg.pocketChunks(), x, z)) {
                    continue;
                }
                for (int y = windowLo; y <= windowHi; y++) {
                    if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
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
                int top = Math.min(windowHi, b + thickness - 1);
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
        return changed;
    }
}
