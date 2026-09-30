package com.dawnduskterminal;

import com.dawnduskterminal.client.DdtClient;
import com.dawnduskterminal.config.DdtConfig;
import com.dawnduskterminal.network.ModNetwork;
import com.dawnduskterminal.registry.ModAttachments;
import com.dawnduskterminal.registry.ModBiomeSources;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModCreativeTabs;
import com.dawnduskterminal.registry.ModEffects;
import com.dawnduskterminal.registry.ModFluids;
import com.dawnduskterminal.advancement.ModCriteria;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModSounds;
import com.dawnduskterminal.server.DdtServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(DawnDuskTerminal.MOD_ID)
public final class DawnDuskTerminal {
    public static final String MOD_ID = "dawnduskterminal";
    public static final Logger LOGGER = LoggerFactory.getLogger("DawnDuskTerminal");

    public DawnDuskTerminal(IEventBus modBus, ModContainer container) {
        ModFluids.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModEffects.register(modBus);
        ModAttachments.register(modBus);
        ModSounds.register(modBus);
        ModCriteria.register(modBus);
        ModBiomeSources.register(modBus);
        ModCreativeTabs.register(modBus);

        modBus.addListener(ModNetwork::registerPayloads);

        NeoForge.EVENT_BUS.addListener(DdtServerEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(DdtServerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(DdtServerEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(DdtServerEvents::onLivingDeath);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            DdtClient.init(modBus);
        }

        container.registerConfig(ModConfig.Type.COMMON, DdtConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
