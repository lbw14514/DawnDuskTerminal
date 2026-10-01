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
        modBus.addListener(DdtClient::registerBlockColors);
        NeoForge.EVENT_BUS.addListener(DdtClient::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(PortalDistortion::onClientTick);
        NeoForge.EVENT_BUS.addListener(PortalDistortion::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(PortalDistortion::onComputeFov);
        NeoForge.EVENT_BUS.addListener(PortalDistortion::onRenderGui);
        NeoForge.EVENT_BUS.addListener(ShaderGuard::onClientLogin);
    }

    private static void registerBlockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return -1;
            }
            if (pos != null) {
                double param = com.dawnduskterminal.world.ChronoLineState.clientLine()
                    .param(pos.getX(), pos.getZ());
                double cr;
                double cg;
                double cb;
                if (param <= 0.0D) {
                    double u = param + 1.0D;
                    cr = 150.0D + 105.0D * u;
                    cg = 176.0D + 48.0D * u;
                    cb = 236.0D - 56.0D * u;
                } else {
                    double u = param;
                    cr = 255.0D - 17.0D * u;
                    cg = 224.0D + 18.0D * u;
                    cb = 180.0D + 48.0D * u;
                }
                int r = (int) cr;
                int g = (int) cg;
                int b = (int) cb;
                return (r << 16) | (g << 8) | b;
            }
            return 0xC8CEFF;
        }, com.dawnduskterminal.registry.ModBlocks.SKY_SOIL.get());
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return -1;
            }
            if (level != null && pos != null) {
                return net.minecraft.client.renderer.BiomeColors.getAverageFoliageColor(level, pos);
            }
            return 0x48B518;
        }, com.dawnduskterminal.registry.ModTerrainBlocks.DAWN_LEAVES.get(),
            com.dawnduskterminal.registry.ModTerrainBlocks.DUSK_LEAVES.get(),
            com.dawnduskterminal.registry.ModTerrainBlocks.CHRONO_LEAVES.get());
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
