package com.dawnduskterminal.progression;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import twilightforest.init.TFEntities;

/**
 * 本模组进度要认的三个暮色森林 boss
 *
 * <p>实体类型直接指向 {@code TFEntities} 的注册项，注册名由注册表反查：
 * 暮色森林改 ID 或改注册顺序时这里跟着变，不用手工同步字符串
 */
public enum BossKind {
    HYDRA(TFEntities.HYDRA::get, "hydra_trophy"),
    UR_GHAST(TFEntities.UR_GHAST::get, "ur_ghast_trophy"),
    SNOW_QUEEN(TFEntities.SNOW_QUEEN::get, "snow_queen_trophy");

    private final Supplier<EntityType<?>> entityType;
    private final String trophyPath;

    BossKind(Supplier<EntityType<?>> entityType, String trophyPath) {
        this.entityType = entityType;
        this.trophyPath = trophyPath;
    }

    public EntityType<?> entityType() {
        return this.entityType.get();
    }

    public ResourceLocation entityId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(this.entityType.get());
    }

    public String trophyPath() {
        return this.trophyPath;
    }

    @Nullable
    public static BossKind of(ResourceLocation entityId) {
        for (BossKind kind : values()) {
            if (kind.entityId().equals(entityId)) {
                return kind;
            }
        }
        return null;
    }
}
