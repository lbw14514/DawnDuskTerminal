package com.ysm.progression;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public enum BossKind {
    HYDRA("twilightforest:hydra", "hydra_trophy"),
    UR_GHAST("twilightforest:ur_ghast", "ur_ghast_trophy"),
    SNOW_QUEEN("twilightforest:snow_queen", "snow_queen_trophy");

    private final ResourceLocation entityId;
    private final String trophyPath;

    BossKind(String entityId, String trophyPath) {
        this.entityId = ResourceLocation.parse(entityId);
        this.trophyPath = trophyPath;
    }

    public ResourceLocation entityId() {
        return this.entityId;
    }

    public String trophyPath() {
        return this.trophyPath;
    }

    @Nullable
    public static BossKind of(ResourceLocation entityId) {
        for (BossKind kind : values()) {
            if (kind.entityId.equals(entityId)) {
                return kind;
            }
        }
        return null;
    }
}
