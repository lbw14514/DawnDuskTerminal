package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class ChronoBiomeSource extends BiomeSource {
    public static final MapCodec<ChronoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        BiomeSource.CODEC.fieldOf("delegate").forGetter(ChronoBiomeSource::delegate),
        RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("bands").forGetter(ChronoBiomeSource::bands),
        RegistryCodecs.homogeneousList(Registries.BIOME).optionalFieldOf("line_biome").forGetter(ChronoBiomeSource::lineBiomeSet),
        BiomeReplacement.CODEC.listOf().optionalFieldOf("replacements", List.of()).forGetter(ChronoBiomeSource::replacements),
        RegistryCodecs.homogeneousList(Registries.BIOME).optionalFieldOf("fallback").forGetter(ChronoBiomeSource::fallbackSet),
        Codec.DOUBLE.optionalFieldOf("line_half_width", 200.0D).forGetter(ChronoBiomeSource::lineHalfWidth),
        Codec.DOUBLE.optionalFieldOf("band_half_width", 1000.0D).forGetter(ChronoBiomeSource::bandHalfWidth),
        Codec.DOUBLE.optionalFieldOf("max_distance", 5000.0D).forGetter(ChronoBiomeSource::maxDistance)
    ).apply(inst, ChronoBiomeSource::new));

    private final BiomeSource delegate;
    private final HolderSet<Biome> bands;
    private final Optional<HolderSet<Biome>> lineBiomeSet;
    private final Optional<Holder<Biome>> lineBiome;
    private final List<BiomeReplacement> replacements;
    private final Map<ResourceKey<Biome>, List<Holder<Biome>>> replacementMap;
    private final Optional<HolderSet<Biome>> fallbackSet;
    private final List<Holder<Biome>> fallbackList;
    private final double lineHalfWidth;
    private final double bandHalfWidth;
    private final double maxDistance;
    private final List<Holder<Biome>> bandList;

    public ChronoBiomeSource(BiomeSource delegate, HolderSet<Biome> bands, Optional<HolderSet<Biome>> lineBiomeSet,
                             List<BiomeReplacement> replacements, Optional<HolderSet<Biome>> fallbackSet,
                             double lineHalfWidth, double bandHalfWidth, double maxDistance) {
        this.delegate = delegate;
        this.bands = bands;
        this.lineBiomeSet = lineBiomeSet;
        this.lineBiome = lineBiomeSet.flatMap(set -> set.stream().findFirst());
        this.replacements = replacements;
        Map<ResourceKey<Biome>, List<Holder<Biome>>> map = new HashMap<>();
        for (BiomeReplacement replacement : replacements) {
            map.put(replacement.from(), replacement.targets());
        }
        this.replacementMap = Map.copyOf(map);
        this.fallbackSet = fallbackSet;
        this.fallbackList = fallbackSet.map(set -> set.stream().toList()).orElse(List.of());
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

    public List<BiomeReplacement> replacements() {
        return this.replacements;
    }

    public Optional<HolderSet<Biome>> fallbackSet() {
        return this.fallbackSet;
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
        Stream<Holder<Biome>> replaced = this.replacementMap.values().stream().flatMap(List::stream);
        return Stream.concat(
            Stream.concat(this.bandList.stream(), this.lineBiome.stream()),
            Stream.concat(
                Stream.concat(this.delegate.possibleBiomes().stream(), replaced),
                this.fallbackList.stream()));
    }

    private static int mix(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return h ^ (h >> 16);
    }

    private static final double BOUNDARY_JITTER = 60.0D;

    private static final double BOUNDARY_SCALE = 128.0D;

    private static double lattice(int x, int z) {
        return (double) (mix(x, z) & 0xFFFF) / 65535.0D;
    }

    private static double boundaryJitter(double x, double z) {
        double fx = x / BOUNDARY_SCALE;
        double fz = z / BOUNDARY_SCALE;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        double tx = fx - ix;
        double tz = fz - iz;
        double sx = tx * tx * (3.0D - 2.0D * tx);
        double sz = tz * tz * (3.0D - 2.0D * tz);
        double v00 = lattice(ix, iz);
        double v10 = lattice(ix + 1, iz);
        double v01 = lattice(ix, iz + 1);
        double v11 = lattice(ix + 1, iz + 1);
        double a = v00 + (v10 - v00) * sx;
        double b = v01 + (v11 - v01) * sx;
        double v = a + (b - a) * sz;
        return (v * 2.0D - 1.0D) * BOUNDARY_JITTER;
    }

    private Holder<Biome> applyReplacement(Holder<Biome> biome, int quartX, int quartZ) {
        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key != null) {
            List<Holder<Biome>> targets = this.replacementMap.get(key);
            if (targets != null && !targets.isEmpty()) {
                int index = Math.floorMod(mix(quartX, quartZ), targets.size());
                return targets.get(index);
            }
        }
        if (this.fallbackList.isEmpty()) {
            return biome;
        }
        int index = Math.floorMod(mix(quartX, quartZ), this.fallbackList.size());
        return this.fallbackList.get(index);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        if (this.bandList.size() < 4) {
            return this.delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        }
        double blockX = QuartPos.toBlock(quartX);
        double blockZ = QuartPos.toBlock(quartZ);
        double max = Math.max(1.0D, this.maxDistance);
        double signed = ChronoLineState.serverLine().distance(blockX, blockZ) + boundaryJitter(blockX, blockZ);
        double distance = Math.abs(signed);
        if (this.lineBiome.isPresent() && distance <= this.lineHalfWidth) {
            return this.lineBiome.get();
        }
        double innerLimit = Math.min(0.95D, this.bandHalfWidth / max);
        double param = Mth.clamp(signed / max, -1.0D, 1.0D);
        double magnitude = Math.abs(param);
        if (magnitude <= innerLimit) {
            return applyReplacement(this.delegate.getNoiseBiome(quartX, quartY, quartZ, sampler), quartX, quartZ);
        }
        double t = (magnitude - innerLimit) / Math.max(1.0E-6D, 1.0D - innerLimit);
        boolean outer = t >= 0.5D;
        int index = param > 0.0D ? (outer ? 3 : 2) : (outer ? 0 : 1);
        return this.bandList.get(Math.min(index, this.bandList.size() - 1));
    }
}
