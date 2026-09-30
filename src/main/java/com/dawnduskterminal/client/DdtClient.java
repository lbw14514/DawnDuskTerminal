package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.config.DdtConfig;
import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.registry.ModFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class DdtClient {
    private DdtClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(DdtClient::registerDimensionEffects);
        modBus.addListener(DdtClient::registerFluidExtensions);
        NeoForge.EVENT_BUS.addListener(DdtClient::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ShaderGuard::onClientLogin);
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        if (!DdtConfig.customSky()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(ModDimensions.CHRONO)) {
            return;
        }
        if (!ShaderGuard.shaderModLoaded()) {
            return;
        }
        ChronoSkyRenderer.render(level, event.getPartialTick().getGameTimeDeltaPartialTick(false),
            event.getModelViewMatrix(), event.getCamera(), event.getProjectionMatrix(), null);
    }

    private static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(DawnDuskTerminal.id("chrono"), new ChronoDimensionEffects());
    }

    private static void registerFluidExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(PortalFluidExtensions.INSTANCE, ModFluids.PORTAL_FLUID_TYPE.get());
    }
}
