package com.dawnduskterminal.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PortalState(String returnDim, double x, double y, double z) {
    public static final Codec<PortalState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                    Codec.STRING.optionalFieldOf("return_dim", "").forGetter(PortalState::returnDim),
                    Codec.DOUBLE.optionalFieldOf("x", 0.0D).forGetter(PortalState::x),
                    Codec.DOUBLE.optionalFieldOf("y", 0.0D).forGetter(PortalState::y),
                    Codec.DOUBLE.optionalFieldOf("z", 0.0D).forGetter(PortalState::z))
            .apply(inst, PortalState::new));

    public static PortalState empty() {
        return new PortalState("", 0.0D, 0.0D, 0.0D);
    }

    public boolean hasReturn() {
        return !this.returnDim.isEmpty();
    }

    public PortalState withReturn(String dimension, double px, double py, double pz) {
        return new PortalState(dimension, px, py, pz);
    }
}
