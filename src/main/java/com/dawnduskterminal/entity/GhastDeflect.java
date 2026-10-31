package com.dawnduskterminal.entity;

import java.util.function.BiConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * 「挨了恶魂火球不炸、把它拨走」这一条的共用实现，恶果与大恶魂都用它
 *
 * <p>{@link #handleImpact} 挂的是 {@code ProjectileImpactEvent}：它是取消点里最早的一处，取消之后
 * {@code AbstractHurtingProjectile.tick} 会跳过 {@code hitTargetOrDeflectSelf}，于是
 * {@code LargeFireball.onHitEntity}（6 点伤害）与随后的爆炸都不会发生，火球本身还活着，
 * 正好可以在同一 tick 里被改向。事件在 {@code getDeltaMovement} 与 {@code setPos} 之前触发，
 * 写进去的位置与速度会被这一 tick 的步伐直接采用
 *
 * <p>两边都要取消：客户端自己也跑 {@code tick} 与撞击判定，只取消服务端的话，
 * 客户端那边火球已经炸没了，服务端再发速度包也画不出来。改向只在服务端做 —— 索敌对象只存在于服务端
 */
public final class GhastDeflect {
    /** 拨出去的火球至少要有这个速度，免得一发已经快停住的火球被拨成原地不动 */
    public static final double MIN_SPEED = 0.1D;

    /**
     * 把撞上来的火球改向
     *
     * <p>有索敌就朝索敌对象的眼睛，没有索敌就沿来路原样弹回；火球的所有者改成拨它的这只，
     * 于是这一发往后打中谁都算它的攻击
     *
     * @param origin 火球要挪到体外多远，见各实体的 {@code DEFLECT_ORIGIN}：必须大于碰撞箱的半对角线，
     *               否则下一 tick 火球又撞回自己身上，形成无限反弹
     */
    public static void deflect(Mob ghast, LargeFireball fireball, double origin) {
        Vec3 incoming = fireball.getDeltaMovement();
        Vec3 direction = incoming.lengthSqr() > 1.0E-6D ? incoming.normalize() : ghast.getLookAngle();
        LivingEntity target = ghast.getTarget();
        if (target != null && target.isAlive() && !target.isRemoved()) {
            Vec3 aim = target.getEyePosition().subtract(ghast.getEyePosition());
            if (aim.lengthSqr() > 1.0E-6D) {
                direction = aim.normalize();
            }
        } else {
            direction = direction.reverse();
        }

        fireball.setOwner(ghast);
        Vec3 spawn = ghast.position().add(0.0D, ghast.getBbHeight() * 0.5D, 0.0D)
            .add(direction.scale(origin));
        fireball.setPos(spawn.x, spawn.y, spawn.z);
        fireball.setDeltaMovement(direction.scale(Math.max(incoming.length(), MIN_SPEED)));
        fireball.hasImpulse = true;
    }

    /**
     * 撞击落在 {@code type} 的实例上时取消撞击，并把火球交给 {@code handler}
     *
     * @param handler 只在服务端调用，收到的火球已经不会再撞上这只生物
     */
    public static <T extends Entity> void handleImpact(
        ProjectileImpactEvent event, Class<T> type, BiConsumer<T, LargeFireball> handler
    ) {
        if (!(event.getProjectile() instanceof LargeFireball fireball)) {
            return;
        }
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        if (!type.isInstance(hit.getEntity())) {
            return;
        }
        T victim = type.cast(hit.getEntity());
        event.setCanceled(true);
        if (!victim.level().isClientSide()) {
            handler.accept(victim, fireball);
        }
    }

    private GhastDeflect() {}
}
