package com.dawnduskterminal.mixin;

import com.dawnduskterminal.world.ChronoDimensionProbe;
import com.dawnduskterminal.world.SkyIslandDensity;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 填群系之前把空岛判据要的两个密度函数摘出来
 *
 * <p>挂 {@code createBiomes} 而不是构造函数：构造函数跑在数据包解析期，那一刻注册表还没绑定，
 * 碰 {@code generatorSettings().value()} 会直接抛 {@code Trying to access unbound value}
 *
 * <p>摘的是 {@code RandomState} 里已经换过种子的函数树，所以噪声值与真实地形一致
 *
 * <p>判据的说明见 {@link SkyIslandDensity}
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class SkyIslandDensityMixin {
    @Inject(method = "createBiomes", at = @At("HEAD"))
    private void ddt$rememberSkyIslandDensity(RandomState randomState, Blender blender,
                                              StructureManager structures, ChunkAccess chunk,
                                              CallbackInfoReturnable<ChunkAccess> cir) {
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) (Object) this;
        if (ChronoDimensionProbe.isChrono(generator.generatorSettings().value())) {
            SkyIslandDensity.remember(randomState, generator.generatorSettings().value());
        }
    }
}
