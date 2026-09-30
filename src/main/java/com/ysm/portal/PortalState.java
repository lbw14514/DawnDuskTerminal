package com.ysm.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PortalState(int cooldown, String returnDim, double x, double y, double z) {
    public static final Codec<PortalState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.INT.optionalFieldOf("cooldown", 0).forGetter(PortalState::cooldown),
        Codec.STRING.optionalFieldOf("return_dim", "").forGetter(PortalState::returnDim),
        Codec.DOUBLE.optionalFieldOf("x", 0.0D).forGetter(PortalState::x),
        Codec.DOUBLE.optionalFieldOf("y", 0.0D).forGetter(PortalState::y),
        Codec.DOUBLE.optionalFieldOf("z", 0.0D).forGetter(PortalState::z)
    ).apply(inst, PortalState::new));

    public static PortalState empty() {
        return new PortalState(0, "", 0.0D, 0.0D, 0.0D);
    }

    public boolean hasReturn() {
        return !this.returnDim.isEmpty();
    }

    public PortalState tick() {
        return this.cooldown <= 0 ? this : new PortalState(this.cooldown - 1, this.returnDim, this.x, this.y, this.z);
    }

    public PortalState withCooldown(int ticks) {
        return new PortalState(ticks, this.returnDim, this.x, this.y, this.z);
    }

    public PortalState withReturn(String dimension, double px, double py, double pz) {
        return new PortalState(this.cooldown, dimension, px, py, pz);
    }
}
