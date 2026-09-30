package com.dawnduskterminal.client;

import com.dawnduskterminal.world.ChronoLine;
import com.dawnduskterminal.world.ChronoLineState;
import org.joml.Vector3f;

public final class ChronoLineStateHolder {
    private ChronoLineStateHolder() {}

    public static Vector3f normal() {
        ChronoLine line = ChronoLineState.clientLine();
        Vector3f vector = new Vector3f((float) line.normalX(), 0.0F, (float) line.normalZ());
        return vector.normalize();
    }
}
