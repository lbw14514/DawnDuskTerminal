package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModParticles;
import com.dawnduskterminal.world.CarminiteBlast;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * 试验物品 052 炸出来的砷铅铁碎片：一颗不画贴图、只拖着红石粒子飞的弹体
 *
 * <p>为什么继承 {@link ThrowableProjectile} 而不是 {@link net.minecraft.world.entity.projectile.Projectile}：
 * 投掷物那一支已经写好了重力（{@code getDefaultGravity} 是 0.03）、空气阻力（每 tick 乘 0.99）与
 * 撞墙撞人之后该走哪条回调，本类只剩「撞到人 / 撞到方块」两支
 *
 * <p>不画贴图：客户端给本实体注册的是 {@code NoopRenderer}，外观看不见；看得见的是每 tick 在当前位置
 * 撒的一颗砷铅铁余烬（{@code particle_carminite_ember}：红石粒子那组贴图、比原版大一半、恒满亮、往上散）
 *
 * <p>打中生物：{@value #MIN_DAMAGE}~{@value #MAX_DAMAGE} 点随机伤害，类型走
 * {@code damageSources().explosion(this, 发射者)}，也就是原版的爆炸伤害；弹体不消失，接着飞，
 * 同一只生物只吃一次（{@link #hurtEntities} 按实体 id 记）。打中那一刻在命中点放一次小型爆炸
 * （{@link CarminiteBlast#small}，粒子与主爆炸同一套）并补几团砷铅铁火焰
 *
 * <p>打中方块：同样先放一次小型爆炸、补几团火，然后消失。只有落地那一格「空旷」时才会长火，
 * 判定与爆炸补火共用 {@link CarminiteBlast#scatterFires} → {@code CarminiteFireBlock#canSurviveAt}
 *
 * <p>{@value #MAX_LIFETIME} tick 之后自己消失：万一卡在什么地方没撞到任何东西，不至于一直 tick 下去
 */
public class CarminiteShrapnelEntity extends ThrowableProjectile {
    /** 随机伤害的两端，含两端 */
    private static final int MIN_DAMAGE = 1;
    private static final int MAX_DAMAGE = 10;

    /** 初速的两端，含两端 */
    private static final float MIN_SPEED = 0.5F;
    private static final float MAX_SPEED = 1.0F;

    /** 向上偏一点，免得一大半碎片直接砸在爆炸中心 */
    private static final double UPWARD_BIAS = 0.4D;

    /** 最长活多久 */
    public static final int MAX_LIFETIME = 100;

    /** 同一只生物只挨一次：弹体不消失，会连着好 tick 碰到同一个碰撞箱 */
    private final Set<Integer> hurtEntities = new HashSet<>();

    /** 只用来数寿命，与存档一起存 */
    private int age;

    public CarminiteShrapnelEntity(EntityType<? extends CarminiteShrapnelEntity> type, Level level) {
        super(type, level);
    }

    /** 从爆炸中心甩出去：方向与快慢都随机，竖向至少带一点向上的分量 */
    public static void launch(Level level, double x, double y, double z) {
        CarminiteShrapnelEntity shrapnel = new CarminiteShrapnelEntity(ModEntities.CARMINITE_SHRAPNEL.get(), level);
        shrapnel.setPos(x, y, z);
        shrapnel.shoot(
            Mth.nextDouble(level.getRandom(), -1.0D, 1.0D),
            Mth.nextDouble(level.getRandom(), 0.0D, 1.0D) + UPWARD_BIAS,
            Mth.nextDouble(level.getRandom(), -1.0D, 1.0D),
            (float) Mth.nextDouble(level.getRandom(), MIN_SPEED, MAX_SPEED), 0.0F);
        level.addFreshEntity(shrapnel);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.spawnRedstoneParticle();
            return;
        }
        if (++this.age > MAX_LIFETIME) {
            this.discard();
        }
    }

    /** 一颗砷铅铁余烬（{@code particle_carminite_ember}），带一点抖动，拖出来就是一条红线 */
    private void spawnRedstoneParticle() {
        double jitter = 0.15D;
        this.level().addParticle(ModParticles.CARMINITE_EMBER.get(),
            this.getX() + (this.random.nextDouble() - 0.5D) * jitter,
            this.getY() + (this.random.nextDouble() - 0.5D) * jitter,
            this.getZ() + (this.random.nextDouble() - 0.5D) * jitter,
            0.0D,
            // 竖直速度与原版红石粒子那一档，横向不额外给速度
            0.2D + this.random.nextDouble() * 0.05D,
            0.0D);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        if (target instanceof LivingEntity living && this.hurtEntities.add(living.getId())) {
            float damage = Mth.nextInt(this.random, MIN_DAMAGE, MAX_DAMAGE);
            living.hurt(this.explosionDamage(), damage);
        }
        this.detonate(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            this.detonate(result.getLocation());
        }
        this.discard();
    }

    /** 小型爆炸 + 补几团火：打中生物与落地走同一条 */
    private void detonate(Vec3 point) {
        if (this.level() instanceof ServerLevel server) {
            CarminiteBlast.small(server, point.x, point.y, point.z);
        }
    }

    /** 爆炸伤害：直接来源是这颗碎片，间接来源是发射者（本方块引起时没有发射者，就是 null） */
    private DamageSource explosionDamage() {
        return this.damageSources().explosion(this, this.getOwner());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.age = compound.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Age", this.age);
    }
}
