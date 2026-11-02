package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * 判断一段世界生成代码是不是在给晨昏线维度干活
 *
 * <p>判据是噪声设置的 {@code default_fluid}：只有 {@code dawnduskterminal:chrono} 把它设成
 * portal_fluid（见 AGENTS.md 的硬约束）。这个判据在 mixin 拿得到的每个作用域里都在——噪声设置本身、
 * 区块生成器、区块生成上下文——而且不需要 level，所以它是全仓库统一的识别方式
 *
 * <p>含水层与流体填充那两处只看得到流体选择器吐出的类型，用 {@link #isChronoFluid} 判断
 */
public final class ChronoDimensionProbe {
    private ChronoDimensionProbe() {}

    public static boolean isChrono(NoiseGeneratorSettings settings) {
        return settings.defaultFluid().is(ModBlocks.PORTAL_FLUID.get());
    }

    public static boolean isChrono(Structure.GenerationContext context) {
        return context.chunkGenerator() instanceof NoiseBasedChunkGenerator generator
                && isChrono(generator.generatorSettings().value());
    }

    /** 晨昏线维度的全局流体选择器只会吐出这两种流体，别的流体一律是原版维度 */
    public static boolean isChronoFluid(BlockState fluidType) {
        return fluidType.is(ModBlocks.PORTAL_FLUID.get()) || fluidType.is(ModScorchingTwilight.BLOCK.get());
    }
}
