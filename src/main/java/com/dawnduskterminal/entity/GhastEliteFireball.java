package com.dawnduskterminal.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 大恶魂的火球：与暮色森林的 {@code UrGhastFireball} 一样，<strong>不和其他弹体相撞</strong>
 *
 * <p>一轮三发从同一个方向散开，原版 {@code LargeFireball} 会把彼此当成可供命中的实体，
 * 于是三发在出膛的瞬间就互相引爆。暮色森林的做法是在 {@code onHitEntity} 里跳过
 * {@code AbstractHurtingProjectile}；这里直接改 {@code canHitEntity}，火球的碰撞判定根本不看弹体，
 * 打到方块或生物时仍然是原版火球的表现（6 点伤害 + 爆炸）
 */
public class GhastEliteFireball extends LargeFireball {
    public GhastEliteFireball(EntityType<? extends GhastEliteFireball> type, Level level) {
        super(type, level);
    }

    public GhastEliteFireball(Level level, LivingEntity owner, Vec3 direction) {
        super(com.dawnduskterminal.registry.ModEntities.GHAST_ELITE_FIREBALL.get(), level);
        this.setOwner(owner);
        this.setDeltaMovement(direction);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return !(entity instanceof AbstractHurtingProjectile) && super.canHitEntity(entity);
    }
}
