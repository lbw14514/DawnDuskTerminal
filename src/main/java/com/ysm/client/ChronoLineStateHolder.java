package com.ysm.client;

import com.ysm.world.ChronoLine;
import com.ysm.world.ChronoLineState;
import org.joml.Vector3f;

public final class ChronoLineStateHolder {
    private ChronoLineStateHolder() {}

    public static Vector3f normal() {
        ChronoLine line = ChronoLineState.clientLine();
        Vector3f vector = new Vector3f((float) line.normalX(), 0.0F, (float) line.normalZ());
        return vector.normalize();
    }
}
