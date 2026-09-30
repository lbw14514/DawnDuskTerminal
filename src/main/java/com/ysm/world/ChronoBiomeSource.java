package com.ysm.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.stream.Stream;

public final class ChronoBiomeSource extends BiomeSource {
    public static final MapCodec<ChronoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        BiomeSource.CODEC.fieldOf("delegate").forGetter(ChronoBiomeSource::delegate),
        RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("bands").forGetter(ChronoBiomeSource::bands),
        Codec.DOUBLE.optionalFieldOf("band_half_width", 1000.0D).forGetter(ChronoBiomeSource::bandHalfWidth),
        Codec.DOUBLE.optionalFieldOf("max_distance", 5000.0D).forGetter(ChronoBiomeSource::maxDistance)
    ).apply(inst, ChronoBiomeSource::new));

    private final BiomeSource delegate;
    private final HolderSet<Biome> bands;
    private final double bandHalfWidth;
    private final double maxDistance;
    private final List<Holder<Biome>> bandList;

    public ChronoBiomeSource(BiomeSource delegate, HolderSet<Biome> bands, double bandHalfWidth, double maxDistance) {
        this.delegate = delegate;
        this.bands = bands;
        this.bandHalfWidth = bandHalfWidth;
        this.maxDistance = maxDistance;
        this.bandList = bands.stream().toList();
    }

    public BiomeSource delegate() {
        return this.delegate;
    }

    public HolderSet<Biome> bands() {
        return this.bands;
    }

    public double bandHalfWidth() {
        return this.bandHalfWidth;
    }

    public double maxDistance() {
        return this.maxDistance;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(this.bandList.stream(), this.delegate.possibleBiomes().stream());
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        if (this.bandList.size() < 4) {
            return this.delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        }
        double blockX = QuartPos.toBlock(quartX);
        double blockZ = QuartPos.toBlock(quartZ);
        double max = Math.max(1.0D, this.maxDistance);
        double innerLimit = Math.min(0.95D, this.bandHalfWidth / max);
        double param = ChronoLineState.serverLine().param(blockX, blockZ);
        double magnitude = Math.abs(param);
        if (magnitude <= innerLimit) {
            return this.delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        }
        double t = (magnitude - innerLimit) / Math.max(1.0E-6D, 1.0D - innerLimit);
        boolean outer = t >= 0.5D;
        int index = param > 0.0D ? (outer ? 3 : 2) : (outer ? 0 : 1);
        return this.bandList.get(Math.min(index, this.bandList.size() - 1));
    }
}
