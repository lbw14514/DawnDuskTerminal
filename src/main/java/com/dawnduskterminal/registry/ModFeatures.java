package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.world.HollowPocketFeature;
import com.dawnduskterminal.world.RootPillarFeature;
import com.dawnduskterminal.world.SkyIslandFeature;
import com.dawnduskterminal.world.TurfFeature;
import com.dawnduskterminal.world.VoidRegionFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
        DeferredRegister.create(Registries.FEATURE, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<Feature<?>, RootPillarFeature> ROOT_PILLAR =
        FEATURES.register("root_pillar", () -> new RootPillarFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, HollowPocketFeature> HOLLOW_POCKET =
        FEATURES.register("hollow_pocket", () -> new HollowPocketFeature(HollowPocketFeature.Config.CODEC));

    public static final DeferredHolder<Feature<?>, SkyIslandFeature> SKY_ISLAND =
        FEATURES.register("sky_island", () -> new SkyIslandFeature(SkyIslandFeature.Config.CODEC));

    public static final DeferredHolder<Feature<?>, VoidRegionFeature> VOID_REGION =
        FEATURES.register("void_region", () -> new VoidRegionFeature(VoidRegionFeature.Config.CODEC));

    public static final DeferredHolder<Feature<?>, TurfFeature> TURF =
        FEATURES.register("turf", () -> new TurfFeature(TurfFeature.Config.CODEC));

    private ModFeatures() {}

    public static void register(IEventBus bus) {
        FEATURES.register(bus);
    }
}
