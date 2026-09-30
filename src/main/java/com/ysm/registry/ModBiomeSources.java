package com.ysm.registry;

import com.mojang.serialization.MapCodec;
import com.ysm.Ysm;
import com.ysm.world.ChronoBiomeSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBiomeSources {
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
        DeferredRegister.create(Registries.BIOME_SOURCE, Ysm.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<? extends BiomeSource>> CHRONO =
        BIOME_SOURCES.register("chrono", () -> ChronoBiomeSource.CODEC);

    private ModBiomeSources() {}

    public static void register(IEventBus bus) {
        BIOME_SOURCES.register(bus);
    }
}
