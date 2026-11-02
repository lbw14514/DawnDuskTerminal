package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.crafting.PoisonPotionIngredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModIngredients {
    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<IngredientType<?>, IngredientType<PoisonPotionIngredient>> POISON_POTION =
            INGREDIENT_TYPES.register("poison_potion", () -> PoisonPotionIngredient.TYPE);

    private ModIngredients() {}

    public static void register(IEventBus bus) {
        INGREDIENT_TYPES.register(bus);
    }
}
