package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModEntities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * 「自己人」判定：本模组的两种恶魂与暮色森林的迷你恶魂互不算敌人
 *
 * <p>用在喷火器（043）与恶魂陷阱（066）那两个过程的判定里。那两个过程原本只排除施放者自己，
 * 于是持块恶果开着陷阱场时，旁边的大恶魂会被拽过去、每 0.5 秒吃 7 点，它召的小鬼也一起挨打
 *
 * <p>只有一条规则：本模组的两种恶魂与 {@code twilightforest:carminite_ghastling} 算同一族。
 * 不能按「同一实体类型」判：066 的场要能打到别的玩家，两个玩家同类型但不同阵营。
 * 施放者自己本来就由调用方的 {@code != owner} 排除，这里再兜一次
 */
public final class GhastKin {
    private static final ResourceLocation GHASTLING =
        ResourceLocation.parse("twilightforest:carminite_ghastling");

    public static boolean isKin(Entity owner, Entity other) {
        if (owner == other) {
            return true;
        }
        return isGhastFamily(owner) && isGhastFamily(other);
    }

    /** 缺暮色森林时 {@code getOptional} 给空，不会误把注册表的默认值（猪）当成小鬼 */
    private static boolean isGhastFamily(Entity entity) {
        var type = entity.getType();
        return type == ModEntities.GHASTFRUITY.get()
            || type == ModEntities.GHAST_ELITE.get()
            || type == BuiltInRegistries.ENTITY_TYPE.getOptional(GHASTLING).orElse(null);
    }

    private GhastKin() {}
}
