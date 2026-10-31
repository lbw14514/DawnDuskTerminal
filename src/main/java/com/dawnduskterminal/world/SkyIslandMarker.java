package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModDensityFunctions;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * 值完全直通的密度函数标记，只为了在生成期把两项认出来
 *
 * <p>{@code RandomState} 会给噪声换种子，换完之后 {@code HolderHolder} 变成直接持有者，
 * 引用键就没了（{@code unwrapKey()} 是空的）。所以按名字找 {@code sky_island_density} 与
 * {@code base_3d_noise} 在生成期是做不到的。给它们各套一层自己的类型，认类型就行
 *
 * <p>除了标记本身不改变任何求值结果：{@code compute} / {@code fillArray} / 上下界全部转发
 */
public record SkyIslandMarker(DensityFunction wrapped) implements DensityFunction {
    public static final MapCodec<SkyIslandMarker> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        DensityFunction.DIRECT_CODEC.fieldOf("argument").forGetter(SkyIslandMarker::wrapped)
    ).apply(instance, SkyIslandMarker::new));

    @Override
    public double compute(FunctionContext context) {
        return this.wrapped.compute(context);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        this.wrapped.fillArray(array, provider);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new SkyIslandMarker(this.wrapped.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return this.wrapped.minValue();
    }

    @Override
    public double maxValue() {
        return this.wrapped.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return ModDensityFunctions.SKY_ISLAND_MARKER_CODEC;
    }
}
