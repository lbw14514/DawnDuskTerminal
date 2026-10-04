package com.dawnduskterminal.world;

import com.dawnduskterminal.DawnDuskTerminal;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

public record ChronoFeatureFilter(HolderSet<Biome> biomes) implements BiomeModifier {
    public static final MapCodec<ChronoFeatureFilter> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(ChronoFeatureFilter::biomes)
    ).apply(inst, ChronoFeatureFilter::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.REMOVE || !this.biomes.contains(biome)) {
            return;
        }
        for (Decoration step : Decoration.values()) {
            builder.getGenerationSettings().getFeatures(step).removeIf(holder -> !holder.unwrapKey()
                .map(key -> key.location().getNamespace().equals(DawnDuskTerminal.MOD_ID))
                .orElse(true));
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
