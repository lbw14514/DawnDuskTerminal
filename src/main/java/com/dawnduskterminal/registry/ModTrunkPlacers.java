package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.world.SkygateBranchTrunkPlacer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTrunkPlacers {
    public static final DeferredRegister<TrunkPlacerType<?>> TRUNK_PLACERS =
            DeferredRegister.create(Registries.TRUNK_PLACER_TYPE, DawnDuskTerminal.MOD_ID);

    /** 天门松的带分枝树干，见 {@link SkygateBranchTrunkPlacer} */
    public static final DeferredHolder<TrunkPlacerType<?>, TrunkPlacerType<SkygateBranchTrunkPlacer>>
            SKYGATE_BRANCHING = TRUNK_PLACERS.register(
                    "skygate_branching", () -> new TrunkPlacerType<>(SkygateBranchTrunkPlacer.CODEC));

    private ModTrunkPlacers() {}

    public static void register(IEventBus bus) {
        TRUNK_PLACERS.register(bus);
    }
}
