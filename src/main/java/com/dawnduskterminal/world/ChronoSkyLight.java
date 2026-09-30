package com.dawnduskterminal.world;

import com.dawnduskterminal.config.DdtConfig;
import net.minecraft.util.Mth;

public final class ChronoSkyLight {
    private ChronoSkyLight() {}

    public static int maxSkyLight(ChronoLine line, int x, int z) {
        if (!DdtConfig.skyLightByDistance()) {
            return 15;
        }
        double param = line.param(x, z);
        double day = DdtConfig.skyLightDay();
        double center = DdtConfig.skyLightCenter();
        double night = DdtConfig.skyLightNight();
        double value = param >= 0.0D
            ? center + (day - center) * param
            : center + (center - night) * param;
        return Mth.clamp((int) Math.round(value), 0, 15);
    }
}
