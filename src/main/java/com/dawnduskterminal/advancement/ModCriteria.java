package com.dawnduskterminal.advancement;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCriteria {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
        DeferredRegister.create(BuiltInRegistries.TRIGGER_TYPES, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, LineParamTrigger> LINE_PARAM =
        TRIGGERS.register("line_param", LineParamTrigger::new);

    private ModCriteria() {}

    public static void register(IEventBus bus) {
        TRIGGERS.register(bus);
    }

    public static LineParamTrigger lineParam() {
        return LINE_PARAM.get();
    }
}
