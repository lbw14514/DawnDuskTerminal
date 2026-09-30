package com.ysm.client;

import com.ysm.config.YsmConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class ShaderGuard {
    private static final String[] SHADER_MODS = {"iris", "oculus", "optifine", "embeddium"};

    private static boolean warned;

    private ShaderGuard() {}

    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (warned || !YsmConfig.shaderWarning()) {
            return;
        }
        warned = true;
        for (String modId : SHADER_MODS) {
            if (ModList.get().isLoaded(modId)) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.translatable("message.ysm.shader_warning", modId), false);
                }
                return;
            }
        }
    }
}
