package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.world.SkyIslandMarker;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 密度函数的自定义类型，目前只有一个值直通的标记，见 {@link SkyIslandMarker} */
public final class ModDensityFunctions {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> TYPES =
        DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<? extends DensityFunction>>
        MARKER = TYPES.register("sky_island_marker", () -> SkyIslandMarker.CODEC);

    /** 每个 {@code DensityFunction} 实例都要回报一个，且必须与注册的那个是同一个对象 */
    public static final KeyDispatchDataCodec<SkyIslandMarker> SKY_ISLAND_MARKER_CODEC =
        KeyDispatchDataCodec.of(SkyIslandMarker.CODEC);

    private ModDensityFunctions() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
