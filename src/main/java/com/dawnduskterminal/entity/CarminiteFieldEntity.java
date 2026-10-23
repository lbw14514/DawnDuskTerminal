package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModDamageTypes;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 砷铅铁反应场：弹体命中后留在原地的一团不可见伤害区
 *
 * <p>源 mod 把这一团做成一只 {@code PathfinderMob}（无 AI 无重力 零碰撞箱），还挂了 15 条免伤来源。
 * 这里只需要一个会 tick 的 Entity：不是生物就没有受伤判定，免伤列表整条不需要
 *
 * <p>范围是圆柱不是长方体：源 mod 用 {@code AABB(x±4, y±1.5, z±4)} 只能框出方盒子，
 * 这里按水平距离 4 格、竖直 ±1.5 格筛
 *
 * <p>伤害每 tick 都试着结算一次，真正落地的频率交给生物的受伤无敌时间：原版
 * {@code LivingEntity#hurt} 在命中后把 {@code invulnerableTime} 置 20，之后只有降到 10 以下才再吃，
 * 等量伤害在窗口内直接返回 false（{@code lastHurt} 那条超额分支走不到），所以等伤害的反应场
 * 自然就是每 10 tick 也就是 0.5 秒掉一次血，与生物的受伤表现同频
 *
 * <p>曾经卡过一个 20 tick 的间隔常量，理由是「源 mod 每 tick 一次会让玩家 3 tick 就没」。
 * 那个担心在原版这层门上不成立：等量伤害每 tick 调用也只有每 10 tick 那一次吃得下，
 * 于是间隔常量只是把频率从 0.5 秒又压到 1 秒，白丢一半伤害
 *
 * <p>伤害不带击退：本模组的 {@code carminite_field} 伤害类型挂在
 * {@code data/minecraft/tags/damage_type/no_knockback.json} 里。原版 {@code LivingEntity#hurt}
 * 每次真正落地的伤害都会给 0.4 的击退，落在场里就是把人往外推、还带一下向上的弹跳，
 * 连续几秒被打成弹球；弹体直击用的是同一个伤害类型，所以两条路一起没有了击退
 *
 * <p>每 tick 在反应场边缘画一圈 {@value #RING_POINTS} 个粒子。源 mod 把 12 个角的坐标手写成
 * 2.5 / 2.17 / 1.25 的常量组合，这里直接算 cos/sin，画出来是同一条圆
 *
 * <p>附魔加成走构造时传入的三个等级：力量加伤害与持续时间，火矢点燃，冲击给缓慢。
 * 源 mod 把这些写进实体 NBT 再逐帧读，这里普通字段就够，客户端用不到
 */
public class CarminiteFieldEntity extends Entity {
    private static final int BASE_LIFETIME = 75;
    private static final int LIFETIME_PER_POWER = 15;
    private static final float DAMAGE = 8.0F;
    private static final float DAMAGE_PER_POWER = 2.0F;
    /** 火矢：点燃秒数与每 tick 的岩浆粒子数 */
    private static final int FIRE_SECONDS = 10;
    private static final int FIRE_PARTICLES = 5;
    /** 冲击：缓慢持续时间与等级换算，冲击 I 是缓慢 II */
    private static final int PUNCH_DURATION = 10;
    /** 伤害圆柱的水平半径与竖直半高 */
    private static final double RADIUS = 4.0D;
    private static final double HALF_HEIGHT = 1.5D;
    /** 边缘粒子的半径与个数 */
    private static final double RING_RADIUS = 2.5D;
    private static final int RING_POINTS = 12;

    /** 伤害归因用。不写进存档：读档后丢归因不影响手感 */
    @Nullable
    private LivingEntity owner;
    private int power;
    private int fire;
    private int punch;
    private int lifetime = BASE_LIFETIME;
    private int age;

    public CarminiteFieldEntity(EntityType<? extends CarminiteFieldEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    /** 在命中点上方一格生成，位置与源 mod 一致 */
    public static void spawn(Level level, double x, double y, double z, @Nullable LivingEntity owner,
                             int power, int fire, int punch) {
        CarminiteFieldEntity field = new CarminiteFieldEntity(ModEntities.CARMINITE_FIELD.get(), level);
        field.setPos(x, y, z);
        field.owner = owner;
        field.power = power;
        field.fire = fire;
        field.punch = punch;
        field.lifetime = BASE_LIFETIME + LIFETIME_PER_POWER * power;
        level.addFreshEntity(field);
    }

    @Override
    public void tick() {
        this.baseTick();
        if (this.level().isClientSide) {
            this.spawnRing();
            return;
        }
        // 每 tick 都试着结算一次，频率由对方的受伤无敌时间决定，见类注释
        this.damageInField();
        // 源 mod 的岩浆粒子是每 tick 发的，不在伤害结算里
        if (this.fire > 0) {
            this.spawnFireParticles();
        }
        if (++this.age >= this.lifetime) {
            this.discard();
        }
    }

    private void damageInField() {
        DamageSource source = this.damageSources().source(ModDamageTypes.CARMINITE_FIELD, this.owner, this);
        float damage = DAMAGE + DAMAGE_PER_POWER * this.power;
        // 粗筛用方盒子，精筛在下面按圆柱
        AABB bounds = this.getBoundingBox().inflate(RADIUS);
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, bounds)) {
            // 盔甲架在源 mod 的白名单里：只出粒子不吃伤害
            if (target instanceof ArmorStand || !target.isAlive() || !this.contains(target)) {
                continue;
            }
            boolean hurt = target.hurt(source, damage);
            // 无敌窗口里这一次没吃下伤害，火与缓慢也不该跟着空刷
            if (!hurt) {
                continue;
            }
            if (this.fire > 0) {
                target.igniteForSeconds(FIRE_SECONDS);
            }
            if (this.punch > 0) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PUNCH_DURATION,
                    this.punch * 2 - 1));
            }
        }
    }

    private void spawnFireParticles() {
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LAVA,
                this.getX(), this.getY(), this.getZ(), FIRE_PARTICLES, 2.0D, 0.0D, 2.0D, 1.0D);
        }
    }

    private boolean contains(Entity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        if (dx * dx + dz * dz > RADIUS * RADIUS) {
            return false;
        }
        AABB box = target.getBoundingBox();
        return box.maxY >= this.getY() - HALF_HEIGHT && box.minY <= this.getY() + HALF_HEIGHT;
    }

    private void spawnRing() {
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = Mth.TWO_PI * i / RING_POINTS;
            this.level().addParticle(ModParticles.CARMINITE_EFFECT.get(),
                this.getX() + Math.cos(angle) * RING_RADIUS + (this.random.nextDouble() - 0.5D) * 0.6D,
                this.getY(),
                this.getZ() + Math.sin(angle) * RING_RADIUS + (this.random.nextDouble() - 0.5D) * 0.6D,
                0.7D * (this.random.nextDouble() - 0.5D),
                0.2D * this.random.nextDouble(),
                0.7D * (this.random.nextDouble() - 0.5D));
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.age = compound.getInt("Age");
        this.power = compound.getInt("Power");
        this.fire = compound.getInt("Fire");
        this.punch = compound.getInt("Punch");
        this.lifetime = BASE_LIFETIME + LIFETIME_PER_POWER * this.power;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putInt("Age", this.age);
        compound.putInt("Power", this.power);
        compound.putInt("Fire", this.fire);
        compound.putInt("Punch", this.punch);
    }
}
