package com.dawnduskterminal.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BossProgress(boolean hydra, boolean urGhast, boolean snowQueen) {
    public static final Codec<BossProgress> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.BOOL.optionalFieldOf("hydra", false).forGetter(BossProgress::hydra),
        Codec.BOOL.optionalFieldOf("ur_ghast", false).forGetter(BossProgress::urGhast),
        Codec.BOOL.optionalFieldOf("snow_queen", false).forGetter(BossProgress::snowQueen)
    ).apply(inst, BossProgress::new));

    public static BossProgress empty() {
        return new BossProgress(false, false, false);
    }

    public boolean anyDefeated() {
        return this.hydra || this.urGhast || this.snowQueen;
    }

    public BossProgress with(BossKind kind) {
        return switch (kind) {
            case HYDRA -> new BossProgress(true, this.urGhast, this.snowQueen);
            case UR_GHAST -> new BossProgress(this.hydra, true, this.snowQueen);
            case SNOW_QUEEN -> new BossProgress(this.hydra, this.urGhast, true);
        };
    }
}
