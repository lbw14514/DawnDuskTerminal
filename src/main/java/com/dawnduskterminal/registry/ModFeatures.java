package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.world.BedrockLayerFeature;
import com.dawnduskterminal.world.ChronoFeatureFilter;
import com.dawnduskterminal.world.HollowPocketFeature;
import com.dawnduskterminal.world.RootPillarFeature;
import com.dawnduskterminal.world.SkyIslandFeature;
import com.dawnduskterminal.world.TurfFeature;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
        DeferredRegister.create(Registries.FEATURE, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<Feature<?>, RootPillarFeature> ROOT_PILLAR =
        FEATURES.register("root_pillar", () -> new RootPillarFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, HollowPocketFeature> HOLLOW_POCKET =
        FEATURES.register("hollow_pocket", () -> new HollowPocketFeature(HollowPocketFeature.Config.CODEC));

    public static final DeferredHolder<Feature<?>, SkyIslandFeature> SKY_ISLAND =
        FEATURES.register("sky_island", () -> new SkyIslandFeature(SkyIslandFeature.Config.CODEC));

    public static final DeferredHolder<Feature<?>, TurfFeature> TURF =
        FEATURES.register("turf", () -> new TurfFeature(TurfFeature.Config.CODEC));

    public static final DeferredHolder<Feature<?>, BedrockLayerFeature> BEDROCK_LAYER =
        FEATURES.register("bedrock_layer", () -> new BedrockLayerFeature(BedrockLayerFeature.Config.CODEC));

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ChronoFeatureFilter>>
        CHRONO_FEATURE_FILTER = BIOME_MODIFIER_SERIALIZERS.register(
            "chrono_feature_filter", () -> ChronoFeatureFilter.CODEC);

    private ModFeatures() {}

    public static void register(IEventBus bus) {
        FEATURES.register(bus);
        BIOME_MODIFIER_SERIALIZERS.register(bus);
    }
}
