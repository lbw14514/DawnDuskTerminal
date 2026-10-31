package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModSounds;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.phys.Vec3;

/**
 * 原版 {@code Ghast.GhastShootFireballGoal} 的共用骨架，恶果与大恶魂各自给出膛点与一轮开火
 *
 * <p>原版这四步是 {@code Ghast} 的私有内部类，访问不到；而它放的音效写死在
 * {@code levelEvent(1015 / 1016)} 里，模组没有钩子可拦。因此这里逐行搬过来：
 * 蓄力 20 tick、最后 10 tick 才算「正在喷火」（{@code isCharging}，模型的怒脸据此切换）、
 * 打完后冷却 40 tick、视线被挡时退蓄力、64 格以内才开火。只把两处 {@code levelEvent}
 * 换成模型包自带的 {@code charge} 与 {@code fireball4}，并交出两件事：
 *
 * <ul>
 *   <li>出膛点：{@link Muzzle}，瞄准向量必须从同一个点算起，否则球会从脸前出现却朝别处飞
 *   <li>一轮打几发：{@link Volley}，恶果一发、大恶魂三发散布
 * </ul>
 */
public final class GhastShootGoal<T extends Ghast> extends Goal {
    /** 出膛点 */
    @FunctionalInterface
    public interface Muzzle<T extends Ghast> {
        Vec3 mouth(T ghast);
    }

    /** 一轮开火：打几发、怎么摆开都在这里 */
    @FunctionalInterface
    public interface Volley {
        void fire(LivingEntity target, Vec3 muzzle);
    }

    /** 到这个 tick 就开火 */
    private static final int CHARGE_TICKS = 20;

    /** 从这一 tick 起算「正在喷火」 */
    private static final int CHARGING_FROM = 10;

    /** 打完之后的冷却：{@code CHARGE_TICKS} 减去它就是下一次蓄力的长度 */
    private static final int COOLDOWN_TICKS = 40;

    /** 64 格 */
    private static final double MAX_RANGE_SQR = 4096.0D;

    private final T ghast;
    private final Predicate<T> ready;
    private final Muzzle<T> muzzle;
    private final Volley volley;
    private int chargeTime;

    public GhastShootGoal(T ghast, Muzzle<T> muzzle, Volley volley) {
        this(ghast, entity -> true, muzzle, volley);
    }

    /**
     * @param ready 这一套能不能用；持块变体走自己的战斗目标，这里返回 {@code false}
     */
    public GhastShootGoal(T ghast, Predicate<T> ready, Muzzle<T> muzzle, Volley volley) {
        this.ghast = ghast;
        this.ready = ready;
        this.muzzle = muzzle;
        this.volley = volley;
    }

    @Override
    public boolean canUse() {
        return this.ready.test(this.ghast) && this.ghast.getTarget() != null;
    }

    @Override
    public void start() {
        this.chargeTime = 0;
    }

    @Override
    public void stop() {
        this.ghast.setCharging(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.ghast.getTarget();
        if (target == null) {
            return;
        }
        if (target.distanceToSqr(this.ghast) < MAX_RANGE_SQR && this.ghast.hasLineOfSight(target)) {
            this.chargeTime++;
            if (this.chargeTime == CHARGING_FROM) {
                GhastVoice.playVoice(this.ghast, ModSounds.GHAST_UNIVERSAL_CHARGE.get());
            }
            if (this.chargeTime == CHARGE_TICKS) {
                GhastVoice.playVoice(this.ghast, ModSounds.GHAST_UNIVERSAL_SHOOT.get());
                this.volley.fire(target, this.muzzle.mouth(this.ghast));
                this.chargeTime = -COOLDOWN_TICKS;
            }
        } else if (this.chargeTime > 0) {
            this.chargeTime--;
        }
        this.ghast.setCharging(this.chargeTime > CHARGING_FROM);
    }
}
