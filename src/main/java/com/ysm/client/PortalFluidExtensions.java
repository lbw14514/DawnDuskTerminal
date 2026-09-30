package com.ysm.client;

import com.ysm.Ysm;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public final class PortalFluidExtensions implements IClientFluidTypeExtensions {
    public static final PortalFluidExtensions INSTANCE = new PortalFluidExtensions();

    public static final ResourceLocation STILL = Ysm.id("block/portal_fluid_still");
    public static final ResourceLocation FLOWING = Ysm.id("block/portal_fluid_flow");

    private PortalFluidExtensions() {}

    @Override
    public ResourceLocation getStillTexture() {
        return STILL;
    }

    @Override
    public ResourceLocation getFlowingTexture() {
        return FLOWING;
    }

    @Override
    public int getTintColor() {
        return 0xFFFFFFFF;
    }
}
