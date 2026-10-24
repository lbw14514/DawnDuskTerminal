package com.dawnduskterminal.mixin;

import com.dawnduskterminal.world.ChronoDimensionProbe;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 晨昏线维度放行了原版废弃矿井，但它的默认落点能一直顶到海平面
 *
 * 这里只对晨昏线维度把原版 {@code moveBelowSeaLevel} 换成「压进 y -64 到 -10」。不能直接用
 * {@code moveInsideHeights}：结构竖向跨度超过 55 格时它退回最小值，顶部会顶穿 -10，所以竖向放不下时
 * 优先把顶部对齐到 -10
 */
@Mixin(MineshaftStructure.class)
public abstract class MineshaftHeightMixin {
    @Redirect(
        method = "generatePiecesAndAdjust(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;)I",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;moveBelowSeaLevel(IILnet/minecraft/util/RandomSource;I)I"
        )
    )
    @SuppressWarnings("deprecation")
    private int ddt$restrictMineshaftY(StructurePiecesBuilder builder, int seaLevel, int minY, RandomSource random, int height,
                                       @Local(argsOnly = true) Structure.GenerationContext context) {
        if (ChronoDimensionProbe.isChrono(context)) {
            int bandMin = -64;
            int bandMax = -10;
            int boxMin = builder.getBoundingBox().minY();
            int span = builder.getBoundingBox().maxY() - boxMin + 1;
            int slack = bandMax - bandMin + 1 - span;
            int targetMin;
            if (slack > 1) {
                targetMin = bandMin + random.nextInt(slack);
            } else {
                // 长矿井塞不进 55 格高的带子时，保住上限：顶部对齐 -10，宁可底部多下去一点
                targetMin = Math.min(bandMin, bandMax - span + 1);
            }
            int delta = targetMin - boxMin;
            builder.offsetPiecesVertically(delta);
            return delta;
        }
        return builder.moveBelowSeaLevel(seaLevel, minY, random, height);
    }
}
