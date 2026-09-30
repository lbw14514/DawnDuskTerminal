package com.dawnduskterminal.client;

import com.dawnduskterminal.config.DdtConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class ShaderGuard {
    private static final String[] SHADER_MODS = {"iris", "oculus", "optifine"};

    private static boolean warned;

    private ShaderGuard() {}

    public static boolean shaderModLoaded() {
        for (String modId : SHADER_MODS) {
            if (ModList.get().isLoaded(modId)) {
                return true;
            }
        }
        return false;
    }

    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (warned || !DdtConfig.shaderWarning()) {
            return;
        }
        warned = true;
        for (String modId : SHADER_MODS) {
            if (ModList.get().isLoaded(modId)) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.translatable("message.dawnduskterminal.shader_warning", modId), false);
                }
                return;
            }
        }
    }
}
