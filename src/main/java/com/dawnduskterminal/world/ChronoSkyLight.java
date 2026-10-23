package com.dawnduskterminal.world;

import com.dawnduskterminal.config.DdtConfig;
import net.minecraft.util.Mth;

public final class ChronoSkyLight {
    private ChronoSkyLight() {}

    public static int maxSkyLight(ChronoLine line, int x, int z) {
        DdtConfig.SkyLightConfig config = DdtConfig.skyLightConfig();
        if (!config.byDistance()) {
            return 15;
        }
        double param = line.param(x, z);
        double center = config.center();
        double value = param >= 0.0D
            ? center + (config.day() - center) * param
            : center + (center - config.night()) * param;
        return Mth.clamp((int) Math.round(value), 0, 15);
    }
}
