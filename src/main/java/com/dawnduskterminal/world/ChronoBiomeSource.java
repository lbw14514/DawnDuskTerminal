package com.dawnduskterminal.world;

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
import java.util.Optional;
import java.util.stream.Stream;

public final class ChronoBiomeSource extends BiomeSource {
    public static final MapCodec<ChronoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        BiomeSource.CODEC.fieldOf("delegate").forGetter(ChronoBiomeSource::delegate),
        RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("bands").forGetter(ChronoBiomeSource::bands),
        RegistryCodecs.homogeneousList(Registries.BIOME).optionalFieldOf("line_biome").forGetter(ChronoBiomeSource::lineBiomeSet),
        Codec.DOUBLE.optionalFieldOf("line_half_width", 200.0D).forGetter(ChronoBiomeSource::lineHalfWidth),
        Codec.DOUBLE.optionalFieldOf("band_half_width", 1000.0D).forGetter(ChronoBiomeSource::bandHalfWidth),
        Codec.DOUBLE.optionalFieldOf("max_distance", 5000.0D).forGetter(ChronoBiomeSource::maxDistance)
    ).apply(inst, ChronoBiomeSource::new));

    private final BiomeSource delegate;
    private final HolderSet<Biome> bands;
    private final Optional<HolderSet<Biome>> lineBiomeSet;
    private final Optional<Holder<Biome>> lineBiome;
    private final double lineHalfWidth;
    private final double bandHalfWidth;
    private final double maxDistance;
    private final List<Holder<Biome>> bandList;

    public ChronoBiomeSource(BiomeSource delegate, HolderSet<Biome> bands, Optional<HolderSet<Biome>> lineBiomeSet,
                             double lineHalfWidth, double bandHalfWidth, double maxDistance) {
        this.delegate = delegate;
        this.bands = bands;
        this.lineBiomeSet = lineBiomeSet;
        this.lineBiome = lineBiomeSet.flatMap(set -> set.stream().findFirst());
        this.lineHalfWidth = lineHalfWidth;
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

    public Optional<HolderSet<Biome>> lineBiomeSet() {
        return this.lineBiomeSet;
    }

    public Optional<Holder<Biome>> lineBiome() {
        return this.lineBiome;
    }

    public double lineHalfWidth() {
        return this.lineHalfWidth;
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
        return Stream.concat(
            Stream.concat(this.bandList.stream(), this.lineBiome.stream()),
            this.delegate.possibleBiomes().stream());
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        if (this.bandList.size() < 4) {
            return this.delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        }
        double blockX = QuartPos.toBlock(quartX);
        double blockZ = QuartPos.toBlock(quartZ);
        double max = Math.max(1.0D, this.maxDistance);
        double distance = Math.abs(ChronoLineState.serverLine().distance(blockX, blockZ));
        if (this.lineBiome.isPresent() && distance <= this.lineHalfWidth) {
            return this.lineBiome.get();
        }
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
