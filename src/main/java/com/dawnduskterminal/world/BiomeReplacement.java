package com.dawnduskterminal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

public record BiomeReplacement(ResourceKey<Biome> from, HolderSet<Biome> to) {
    public static final Codec<BiomeReplacement> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        ResourceKey.codec(Registries.BIOME).fieldOf("from").forGetter(BiomeReplacement::from),
        RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("to").forGetter(BiomeReplacement::to)
    ).apply(inst, BiomeReplacement::new));

    public List<Holder<Biome>> targets() {
        return this.to.stream().toList();
    }
}
