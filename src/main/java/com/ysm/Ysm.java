package com.ysm;

import com.ysm.client.YsmClient;
import com.ysm.config.YsmConfig;
import com.ysm.network.ModNetwork;
import com.ysm.registry.ModAttachments;
import com.ysm.registry.ModBiomeSources;
import com.ysm.registry.ModBlocks;
import com.ysm.registry.ModCreativeTabs;
import com.ysm.registry.ModEffects;
import com.ysm.registry.ModFluids;
import com.ysm.registry.ModItems;
import com.ysm.server.YsmServerEvents;
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

@Mod(Ysm.MOD_ID)
public final class Ysm {
    public static final String MOD_ID = "ysm";
    public static final Logger LOGGER = LoggerFactory.getLogger("YSM");

    public Ysm(IEventBus modBus, ModContainer container) {
        ModFluids.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModEffects.register(modBus);
        ModAttachments.register(modBus);
        ModBiomeSources.register(modBus);
        ModCreativeTabs.register(modBus);

        modBus.addListener(ModNetwork::registerPayloads);

        NeoForge.EVENT_BUS.addListener(YsmServerEvents::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(YsmServerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(YsmServerEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(YsmServerEvents::onLivingDeath);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            YsmClient.init(modBus);
        }

        container.registerConfig(ModConfig.Type.COMMON, YsmConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
