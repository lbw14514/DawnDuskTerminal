package com.dawnduskterminal.mixin;

import com.dawnduskterminal.world.ChronoDimensionProbe;
import com.dawnduskterminal.world.SkyIslandClouds;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code FEATURES} 步骤末尾处理高空薄岛
 *
 * <p>{@code applyBiomeDecoration} 是 {@code ChunkStatusTasks.generateFeatures} 在铺完群系特征后调用的
 * 钩子，拿到的是整个生成区域，正好能读到同区块的地形；这里注入 {@code TAIL}，树和草已经放完，
 * 但光照和落盘还没开始
 *
 * <p>只看晨昏线维度的噪声生成器。判据沿用 {@link ChronoDimensionProbe}
 */
@Mixin(ChunkGenerator.class)
public abstract class SkyIslandCloudMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void ddt$cloudThinSkyIslands(WorldGenLevel level, ChunkAccess chunk, StructureManager structures,
                                         CallbackInfo ci) {
        if ((Object) this instanceof NoiseBasedChunkGenerator generator
            && ChronoDimensionProbe.isChrono(generator.generatorSettings().value())) {
            SkyIslandClouds.process(level, chunk);
        }
    }
}
