package com.dawnduskterminal.item;

import com.dawnduskterminal.particle.JetFlameParticleOptions;
import com.dawnduskterminal.registry.ModDamageTypes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 试验物品 043 的那一整段喷火过程
 *
 * <p>逐拍照抄暮色森林 {@code twilightforest:encased_fire_jet} 方块：预热 80 tick，计数 20/40/60
 * 各放 8 颗岩浆粒子加一声 {@code block.lava.pop}，到 80 切火焰；火焰跑 61 拍，每 2 tick 一颗大烟加五路
 * 大火焰（中心一路、上下左右各偏 1 格，初速沿轴 0.75），每 4 tick 一声 {@code entity.ghast.shoot}，
 * 每 5 tick 对伤害立方体里的活物造成伤害并点燃，最后自己结束
 *
 * <p>与方块的区别只有朝向：枪口是玩家的眼睛往前 1 格，整个伤害立方体与四路火苗跟着视线转，
 * 每 tick 重新绑到玩家身上（{@code refresh}），玩家下线/死亡/换维度就断掉；施放期间把点火那只手换成
 * 别的东西，剩下的火改成从脚下朝正上方喷、连玩家自己一起烧
 *
 * <p>过程挂在 {@code ServerTickEvent.Post} 上推进（{@code tickAll} / {@code clear}），
 * 不依赖玩家拿着物品，所以单击一次就够，松手与换物品都不打断
 */

public final class FireJetProcess {
    public static final int POP_TICKS = 80;
    public static final int FLAME_TICKS = 61;
    public static final int TOTAL_TICKS = 141;
    public static final int COOLDOWN_TICKS = 60;
    public static final int POP_INTERVAL = 20;
    public static final int FLAME_PARTICLE_INTERVAL = 2;
    public static final int FLAME_SOUND_INTERVAL = 4;
    public static final int DAMAGE_INTERVAL = 5;
    public static final float DAMAGE = 4.0F;
    public static final int FIRE_TICKS = 300;
    public static final double LENGTH = 14.0;
    public static final double RADIUS = 2.0;
    public static final double MUZZLE_OFFSET = 1.0;
    private static final double FLAME_SPEED = 0.75;
    private static final double FLAME_INWARD = 0.05;
    private static final List<FireJetProcess> ACTIVE = new ArrayList<>();
    private final ServerLevel level;
    private final Player owner;
    private final InteractionHand hand;
    private Vec3 muzzle;
    private Vec3 direction;
    private Vec3 right;
    private Vec3 up;
    private boolean aimed;
    private int age;
    private int pops;
    private int flameTicks;
    private int damagePulses;
    private int targetsHit;
    private int hurtLanded;
    private boolean done;

    private FireJetProcess(ServerLevel level, Player owner, InteractionHand hand) {
        this.level = level;
        this.owner = owner;
        this.hand = hand;
        this.aimed = this.holdsTheItem();
        this.updateAim();
    }

    public static void start(ServerLevel level, Player owner, InteractionHand hand) {
        FireJetProcess process = new FireJetProcess(level, owner, hand);
        ACTIVE.add(process);
        process.playStartSound();
    }

    public static FireJetProcess detached(ServerLevel level, Player owner) {
        return detached(level, owner, InteractionHand.MAIN_HAND);
    }

    public static FireJetProcess detached(ServerLevel level, Player owner, InteractionHand hand) {
        return new FireJetProcess(level, owner, hand);
    }

    public static void tickAll() {
        if (!ACTIVE.isEmpty()) {
            Iterator<FireJetProcess> iterator = ACTIVE.iterator();

            while (iterator.hasNext()) {
                FireJetProcess process = iterator.next();
                process.tick();
                if (process.done) {
                    iterator.remove();
                }
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public void tick() {
        if (!this.done) {
            if (!this.refresh()) {
                this.done = true;
            } else {
                this.age++;
                if (this.age <= 80) {
                    if (this.age < 80 && this.age % 20 == 0) {
                        this.pop();
                    }
                } else {
                    int flame = this.age - 80;
                    if (flame >= 61) {
                        flame = 0;
                        this.done = true;
                    }

                    this.flameTicks++;
                    if (flame == 1) {
                        this.playFlameStartSound();
                    }

                    if (flame % 2 == 0) {
                        this.spawnFlameParticles();
                    }

                    if (flame % 4 == 0) {
                        this.playActiveSound();
                    }

                    if (flame % 5 == 0) {
                        this.damage();
                    }
                }
            }
        }
    }

    private boolean refresh() {
        if (!this.owner.isRemoved() && this.owner.isAlive() && this.owner.level() == this.level) {
            this.aimed = this.holdsTheItem();
            this.updateAim();
            return true;
        } else {
            return false;
        }
    }

    private boolean holdsTheItem() {
        return this.owner.getItemInHand(this.hand).getItem() instanceof Experiment043Item;
    }

    private void updateAim() {
        if (this.aimed) {
            this.aimAtLook();
        } else {
            this.aimFromFeet();
        }
    }

    private void aimAtLook() {
        Vec3 look = this.owner.getViewVector(1.0F);
        if (look.lengthSqr() < 1.0E-6) {
            look = new Vec3(0.0, 0.0, 1.0);
        }

        this.setAxes(look.normalize());
        this.muzzle = this.owner.getEyePosition().add(this.direction.scale(1.0));
    }

    private void aimFromFeet() {
        this.setAxes(new Vec3(0.0, 1.0, 0.0));
        this.muzzle = this.owner.position();
    }

    private void setAxes(Vec3 direction) {
        this.direction = direction;
        Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-6) {
            side = direction.cross(new Vec3(1.0, 0.0, 0.0));
        }

        this.right = side.normalize();
        this.up = this.right.cross(direction).normalize();
    }

    private void pop() {
        this.pops++;
        this.level.sendParticles(ParticleTypes.LAVA, this.muzzle.x, this.muzzle.y, this.muzzle.z, 8, 0.0, 0.0, 0.0, 0.0);
        this.level
            .playSound(
                null,
                this.muzzle.x,
                this.muzzle.y,
                this.muzzle.z,
                SoundEvents.LAVA_POP,
                SoundSource.BLOCKS,
                0.2F + this.level.getRandom().nextFloat() * 0.2F,
                0.9F + this.level.getRandom().nextFloat() * 0.15F
            );
    }

    private void spawnFlameParticles() {
        this.level.sendParticles(ParticleTypes.LARGE_SMOKE, this.muzzle.x, this.muzzle.y, this.muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
        Vec3 forward = this.direction.scale(0.75);
        this.spawnFlame(0.0, 0.0, forward);
        this.spawnFlame(-1.0, 0.0, this.right.scale(0.05).add(forward));
        this.spawnFlame(0.0, -1.0, this.up.scale(0.05).add(forward));
        this.spawnFlame(1.0, 0.0, this.right.scale(-0.05).add(forward));
        this.spawnFlame(0.0, 1.0, this.up.scale(-0.05).add(forward));
    }

    private void spawnFlame(double lateral, double vertical, Vec3 velocity) {
        Vec3 point = this.muzzle.add(this.right.scale(lateral)).add(this.up.scale(vertical));
        this.level.sendParticles(new JetFlameParticleOptions(velocity), point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private void damage() {
        this.damagePulses++;
        DamageSource source = this.level.damageSources().source(ModDamageTypes.EXPERIMENT_043, this.owner);
        boolean burnOwner = !this.aimed;
        AABB broad = new AABB(this.muzzle, this.muzzle.add(this.direction.scale(14.0))).inflate(6.0);
        List<LivingEntity> targets = this.level
            .getEntitiesOfClass(LivingEntity.class, broad, other -> (burnOwner || other != this.owner) && !other.fireImmune() && this.insideJet(other));
        this.targetsHit = this.targetsHit + targets.size();

        for (LivingEntity target : targets) {
            if (target.hurt(source, 4.0F)) {
                this.hurtLanded++;
            }

            target.setRemainingFireTicks(300);
        }
    }

    private boolean insideJet(LivingEntity target) {
        AABB box = target.getBoundingBox();
        Vec3 center = box.getCenter();
        Vec3 relative = center.subtract(this.muzzle);
        double extent = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) * 0.5;
        double along = relative.dot(this.direction);
        return !(along < -extent) && !(along > 14.0 + extent)
            ? Math.abs(relative.dot(this.right)) <= 2.0 + extent && Math.abs(relative.dot(this.up)) <= 2.0 + extent
            : false;
    }

    private void playStartSound() {
        this.level.playSound(null, this.muzzle.x, this.muzzle.y, this.muzzle.z, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, 0.6F);
    }

    private void playFlameStartSound() {
        this.level.playSound(null, this.muzzle.x, this.muzzle.y, this.muzzle.z, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 1.0F, this.pitch());
    }

    private void playActiveSound() {
        this.level.playSound(null, this.muzzle.x, this.muzzle.y, this.muzzle.z, SoundEvents.GHAST_SHOOT, SoundSource.BLOCKS, 1.0F, this.pitch());
    }

    private float pitch() {
        return this.level.getRandom().nextFloat() * 0.7F + 0.3F;
    }

    public int age() {
        return this.age;
    }

    public int pops() {
        return this.pops;
    }

    public int flameTicks() {
        return this.flameTicks;
    }

    public int damagePulses() {
        return this.damagePulses;
    }

    public boolean aimed() {
        return this.aimed;
    }

    public int targetsHit() {
        return this.targetsHit;
    }

    public int hurtLanded() {
        return this.hurtLanded;
    }

    public boolean finished() {
        return this.done;
    }
}
