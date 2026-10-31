package com.dawnduskterminal.geckolib3.core.molang.function.entity;

import com.dawnduskterminal.geckolib3.core.molang.context.IContext;
import com.dawnduskterminal.geckolib3.core.molang.function.ContextFunction;
import net.minecraft.client.player.LocalPlayer;

public abstract class LocalPlayerEntityFunction extends ContextFunction<LocalPlayer> {
    @Override
    protected boolean validateContext(IContext<?> context) {
        return context.entity() instanceof LocalPlayer;
    }
}
