package com.ysm.client;

import com.ysm.world.ChronoLine;
import com.ysm.world.ChronoLineState;
import net.minecraft.world.phys.Vec3;

public final class ChronoLineStateHolder {
    private ChronoLineStateHolder() {}

    public static Vec3 normal() {
        ChronoLine line = ChronoLineState.clientLine();
        return new Vec3(line.normalX(), 0.0D, line.normalZ()).normalize();
    }
}
