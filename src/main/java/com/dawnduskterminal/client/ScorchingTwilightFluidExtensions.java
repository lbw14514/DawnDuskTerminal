package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public final class ScorchingTwilightFluidExtensions implements IClientFluidTypeExtensions {
    public static final ScorchingTwilightFluidExtensions INSTANCE = new ScorchingTwilightFluidExtensions();

    public static final ResourceLocation STILL = DawnDuskTerminal.id("block/scorching_twilight_still");
    public static final ResourceLocation FLOWING = DawnDuskTerminal.id("block/scorching_twilight_flow");

    private ScorchingTwilightFluidExtensions() {}

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
