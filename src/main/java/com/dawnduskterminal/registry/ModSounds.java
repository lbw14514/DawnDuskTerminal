package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_OPEN =
        register("portal_open");

    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_ENTER =
        register("portal_enter");

    public static final DeferredHolder<SoundEvent, SoundEvent> PORTAL_EXIT =
        register("portal_exit");

    public static final DeferredHolder<SoundEvent, SoundEvent> CHRONO_AMBIENT =
        register("chrono_ambient");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(DawnDuskTerminal.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
