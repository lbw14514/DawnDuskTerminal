package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.world.PortalSicknessEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
        DeferredRegister.create(Registries.MOB_EFFECT, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> PORTAL_SICKNESS =
        EFFECTS.register("portal_sickness", PortalSicknessEffect::new);

    private ModEffects() {}

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }
}
