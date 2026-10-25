package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModDamageTypes;
import com.dawnduskterminal.registry.ModFluids;
import com.dawnduskterminal.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * 「弦」：只在不稳定液体（portal_fluid）的液面上活动的环状生物
 *
 * <h2>为什么不用原版的自然生成</h2>
 * 原版 {@code NaturalSpawner} 的地面刷怪按高度图找固体地面，水生刷怪只认 {@code minecraft:water} 标签，
 * 而这只生物的生成条件是「液面之上是空气」的传送门流体，两者都不满足，所以由
 * {@link com.dawnduskterminal.world.StringSpawner} 在服务端按列扫高度图生成。
 *
 * <h2>活动范围</h2>
 * 每 tick 把脚底钉回所在列的液面，位置一旦离开流体就弹回上一个合法点，因此它不会飞出去，
 * 也不会沉下去。水平移动由 {@link SurfaceMoveControl} 驱动，不经过任何地面寻路。
 *
 * <h2>大小变种</h2>
 * 小型与大型是两个模型与两套贴图（见客户端渲染器），属性按模型尺寸成比例放大。
 * 生成时再叠一个 0.85 到 1.15 的随机体型偏移，血量与攻击力跟着这个偏移走。
 *
 * <h2>免疫</h2>
 * 免疫击退、燃烧、药水与传送门流体的伤害与传送。传送门流体对非玩家实体只有在带「稳定」效果时
 * 才会传送，而这只生物 {@link #canBeAffected} 恒为假，所以永远不会被传送；剩下的周期性伤害由
 * {@link #isInvulnerableTo} 挡掉。
 */
public class StringEntity extends PathfinderMob implements GeoEntity, Enemy {
    /** 大型出现的概率 */
    private static final float LARGE_CHANCE = 0.25F;
    /** 随机体型偏移的范围 */
    private static final float MIN_SIZE = 0.85F;
    private static final float MAX_SIZE = 1.15F;
    /** 小型与大型的碰撞箱宽度，模型半径分别是 1 格与 2.5 格 */
    private static final float SMALL_WIDTH = 1.8F;
    private static final float LARGE_WIDTH = 4.4F;
    /** 液面之上的定点偏移 */
    public static final double SURFACE_OFFSET = 0.02D;
    /** 接触伤害的冷却，与近战共用 */
    private static final int ATTACK_COOLDOWN = 20;
    /** 只索敌液体附近的生物，判定盒子半径 */
    private static final int PREY_FLUID_RADIUS = 2;

    private static final EntityDataAccessor<Boolean> DATA_LARGE =
        SynchedEntityData.defineId(StringEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SIZE =
        SynchedEntityData.defineId(StringEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    private boolean largeVariant;
    private float sizeScale = 1.0F;
    private int attackCooldown;

    /** 上一个确认站在液面上的位置，离开流体时弹回这里 */
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private boolean hasAnchor;

    public StringEntity(EntityType<? extends StringEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.moveControl = new SurfaceMoveControl(this);
        // Entity 构造函数跑 getDimensions 时子类字段还没初始化，这里补一次
        this.refreshDimensions();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 60.0D)
            .add(Attributes.ATTACK_DAMAGE, 10.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.34D)
            .add(Attributes.FLYING_SPEED, 0.34D)
            .add(Attributes.FOLLOW_RANGE, 24.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LARGE, false);
        builder.define(DATA_SIZE, 1.0F);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        // 优先级数字越小越先跑：索敌最高，追击次之，剩下时间一直在液面上随机游走
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(
            this, LivingEntity.class, 10, false, false, StringEntity::isValidPrey));
        this.goalSelector.addGoal(2, new StalkGoal(this));
        this.goalSelector.addGoal(3, new SurfaceWanderGoal(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 两个源动画片段全是 YSM 的 bone_pivot_abs 与 perlin_noise，转换后只剩 0/0 这类退化的表达式，
        // 姿态改由 client/StringPose 在 setCustomAnimations 里整套算出来，所以这里不注册控制器
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableCache;
    }

    // ---------------------------------------------------------------- 大小变种

    public boolean isLargeVariant() {
        return this.largeVariant;
    }

    public float getSizeScale() {
        return this.sizeScale;
    }

    /** 渲染器的整体缩放，大型的尺寸已经在模型里，所以只叠随机体型偏移 */
    public float getRenderScale() {
        return this.sizeScale;
    }

    /**
     * 设置变种并重算属性
     *
     * <p>只在服务端改属性，客户端靠同步数据拿到体型；{@link #onSyncedDataUpdated} 负责刷新碰撞箱
     */
    public void setVariant(boolean large, float scale) {
        this.largeVariant = large;
        this.sizeScale = Mth.clamp(scale, MIN_SIZE, MAX_SIZE);
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_LARGE, this.largeVariant);
            this.entityData.set(DATA_SIZE, this.sizeScale);
            applySizeAttributes();
            this.setHealth(this.getMaxHealth());
        }
        this.refreshDimensions();
    }

    private void applySizeAttributes() {
        float geometry = this.largeVariant ? 2.5F : 1.0F;
        double health = 60.0D * geometry * this.sizeScale;
        double attack = 10.0D * geometry * this.sizeScale;
        double speed = this.largeVariant ? 0.26D : 0.34D;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
        this.getAttribute(Attributes.FLYING_SPEED).setBaseValue(speed);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        // 超类构造期字段仍是 0，给个合法的兜底宽度
        float scale = this.sizeScale <= 0.0F ? 1.0F : this.sizeScale;
        float width = (this.largeVariant ? LARGE_WIDTH : SMALL_WIDTH) * scale;
        return EntityDimensions.scalable(width, width).withEyeHeight(width * 0.5F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_LARGE.equals(key)) {
            this.largeVariant = this.entityData.get(DATA_LARGE);
            this.refreshDimensions();
        } else if (DATA_SIZE.equals(key)) {
            this.sizeScale = this.entityData.get(DATA_SIZE);
            this.refreshDimensions();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        RandomSource random = level.getRandom();
        boolean large = random.nextFloat() < LARGE_CHANCE;
        float scale = MIN_SIZE + random.nextFloat() * (MAX_SIZE - MIN_SIZE);
        this.setVariant(large, scale);
        return data;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Large", this.largeVariant);
        tag.putFloat("SizeScale", this.sizeScale);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.largeVariant = tag.getBoolean("Large");
        this.sizeScale = Mth.clamp(tag.contains("SizeScale") ? tag.getFloat("SizeScale") : 1.0F,
            MIN_SIZE, MAX_SIZE);
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_LARGE, this.largeVariant);
            this.entityData.set(DATA_SIZE, this.sizeScale);
            applySizeAttributes();
            // 存档里的当前血量不能因为重算上限被回满
            this.setHealth(Math.min(this.getHealth(), this.getMaxHealth()));
        }
        this.refreshDimensions();
    }

    // ---------------------------------------------------------------- 液面约束

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.isDeadOrDying()) {
            return;
        }
        keepOnFluidSurface();
    }

    private void keepOnFluidSurface() {
        int x = Mth.floor(this.getX());
        int z = Mth.floor(this.getZ());
        double surface = findFluidSurface(this.level(), x, z, Mth.floor(this.getY()));
        if (Double.isNaN(surface)) {
            if (this.hasAnchor) {
                this.setPos(this.anchorX, this.anchorY, this.anchorZ);
            }
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        double bob = 0.02D * Math.sin(this.tickCount / 14.0D);
        double y = surface + bob;
        this.hasAnchor = true;
        this.anchorX = this.getX();
        this.anchorY = y;
        this.anchorZ = this.getZ();
        this.setPos(this.getX(), y, this.getZ());
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x, 0.0D, motion.z);
    }

    /**
     * 找一列里离 {@code nearY} 最近的传送门流体液面
     *
     * <p>只往上看一格、往下看两格：这只生物每 tick 都被钉回液面，正常情况只差不到一格；
     * 放得太宽会把岸边地下的含水层当成液面，把它拽进石头里。流体上方必须还是空气，
     * 否则是封闭含水层，同样不算。
     *
     * @return 液面上表面的 Y，找不到时返回 {@link Double#NaN}
     */
    public static double findFluidSurface(Level level, int x, int z, int nearY) {
        for (int y = nearY + 1; y >= nearY - 2; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            FluidState fluid = level.getFluidState(pos);
            if (!isPortalFluid(fluid)) {
                continue;
            }
            if (!level.getBlockState(pos.above()).isAir()) {
                continue;
            }
            return y + fluid.getHeight(level, pos) + SURFACE_OFFSET;
        }
        return Double.NaN;
    }

    public static boolean isPortalFluid(Level level, BlockPos pos) {
        return isPortalFluid(level.getFluidState(pos));
    }

    public static boolean isPortalFluid(FluidState fluid) {
        return fluid.getType() == ModFluids.PORTAL_FLUID.get()
            || fluid.getType() == ModFluids.PORTAL_FLOWING.get();
    }

    /** 目标附近有没有液体，没有就不索敌 */
    private static boolean hasPortalFluidNear(Level level, BlockPos center) {
        BlockPos min = center.offset(-PREY_FLUID_RADIUS, -PREY_FLUID_RADIUS, -PREY_FLUID_RADIUS);
        BlockPos max = center.offset(PREY_FLUID_RADIUS, PREY_FLUID_RADIUS, PREY_FLUID_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (isPortalFluid(level, pos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidPrey(LivingEntity target) {
        if (!target.isAlive() || target instanceof StringEntity) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return hasPortalFluidNear(target.level(), target.blockPosition());
    }

    // ---------------------------------------------------------------- 移动与攻击

    private Vec3 randomSurfacePoint(double radius) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = this.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = radius * (0.35D + this.getRandom().nextDouble() * 0.65D);
            int x = Mth.floor(this.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(this.getZ() + Math.sin(angle) * distance);
            double y = findFluidSurface(this.level(), x, z, Mth.floor(this.getY()));
            if (!Double.isNaN(y)) {
                return new Vec3(x + 0.5D, y, z + 0.5D);
            }
        }
        return null;
    }

    private Vec3 randomApproachPoint(LivingEntity target) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = this.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = 1.0D + this.getRandom().nextDouble() * 3.0D;
            int x = Mth.floor(target.getX() + Math.cos(angle) * radius);
            int z = Mth.floor(target.getZ() + Math.sin(angle) * radius);
            double y = findFluidSurface(this.level(), x, z, Mth.floor(this.getY()));
            if (!Double.isNaN(y)) {
                return new Vec3(x + 0.5D, y, z + 0.5D);
            }
        }
        return null;
    }

    private double wanderSpeed() {
        return this.largeVariant ? 0.9D : 1.15D;
    }

    private double stalkSpeed() {
        return this.largeVariant ? 1.25D : 1.55D;
    }

    private static boolean canHurtContact(LivingEntity candidate) {
        if (candidate instanceof StringEntity) {
            return false;
        }
        if (candidate instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return candidate.isAlive();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (this.level().isClientSide || this.isDeadOrDying() || this.attackCooldown > 0) {
            return;
        }
        AABB contact = this.getBoundingBox().inflate(0.15D);
        for (LivingEntity candidate : this.level().getEntitiesOfClass(LivingEntity.class, contact,
            StringEntity::canHurtContact)) {
            if (candidate != this && this.doHurtTarget(candidate)) {
                this.attackCooldown = ATTACK_COOLDOWN;
                break;
            }
        }
    }

    /**
     * 接触与近战都不产生击退：伤害结算后把目标水平速度清零
     *
     * <p>原版生物的近战本身不带击退，但爆炸 液体推动之类的外部速度会留在目标身上，
     * 这里统一按「不产生击退」处理
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean hurt = target.hurt(this.damageSources().mobAttack(this), damage);
        if (hurt) {
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(0.0D, motion.y, 0.0D);
            if (target instanceof LivingEntity living) {
                this.setLastHurtMob(living);
            }
        }
        return hurt;
    }

    // ---------------------------------------------------------------- 免疫

    @SuppressWarnings("deprecation")
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // 免疫击退
    }

    /** 爆炸 活塞 水流之类直接推实体的路径也一并关掉 */
    @Override
    public void push(double x, double y, double z) {
        // 免疫击退
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(ModDamageTypes.STABILITY) || super.isInvulnerableTo(source);
    }

    // ---------------------------------------------------------------- 音效与死亡

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.STRING_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 80;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.STRING_HIT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.STRING_HIT.get();
    }

    /**
     * 死亡不播倒地动画（渲染器的 {@code getDeathMaxRotation} 已覆写为 0），只放一圈末影人传送粒子
     */
    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(),
                this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
                60, 0.7D, 0.7D, 0.7D, 0.1D);
        }
        super.die(source);
    }

    // ---------------------------------------------------------------- AI

    /**
     * 只在水平面上推进，垂直位置交给 {@link #keepOnFluidSurface}
     *
     * <p>{@code zza} 是前进输入，{@code getSpeed()} 才是每 tick 的步长，两者都按 {@code MoveControl}
     * 的惯例分开设：{@code setSpeed(speedModifier * MOVEMENT_SPEED)} 与 {@code setZza(1)}
     */
    private static final class SurfaceMoveControl extends MoveControl {
        private static final float TURN_SPEED = 30.0F;

        private SurfaceMoveControl(StringEntity mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (this.operation != Operation.MOVE_TO) {
                this.mob.setZza(0.0F);
                this.mob.setSpeed(0.0F);
                return;
            }
            this.operation = Operation.WAIT;
            double dx = this.wantedX - this.mob.getX();
            double dz = this.wantedZ - this.mob.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < 0.35D) {
                this.mob.setZza(0.0F);
                this.mob.setSpeed(0.0F);
                return;
            }
            float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, TURN_SPEED));
            this.mob.yBodyRot = this.mob.getYRot();
            this.mob.yHeadRot = this.mob.getYRot();
            float speed = (float) (this.speedModifier
                * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
            this.mob.setSpeed(speed);
            this.mob.setZza(1.0F);
            this.mob.setXxa(0.0F);
            this.mob.setYya(0.0F);
            this.mob.setNoGravity(true);
        }
    }

    /** 一直换目标点，永不进入「站着不动」的行为状态 */
    private static final class SurfaceWanderGoal extends Goal {
        private final StringEntity mob;
        private int cooldown;

        private SurfaceWanderGoal(StringEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.mob.isAlive() && !this.mob.isDeadOrDying();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return;
            }
            this.cooldown = 8 + this.mob.getRandom().nextInt(10);
            Vec3 point = this.mob.randomSurfacePoint(4.0D + this.mob.getRandom().nextDouble() * 6.0D);
            if (point != null) {
                this.mob.getMoveControl().setWantedPosition(point.x, point.y, point.z,
                    this.mob.wanderSpeed());
            }
        }
    }

    /**
     * 以随机走向接近目标：每次重选一个绕在目标周围的目标点，而不是直线追
     *
     * <p>偏移量半径 1 到 4 格，所以总体趋势是靠近，但路径是折线；目标一旦离开液体附近就放弃
     */
    private static final class StalkGoal extends Goal {
        private final StringEntity mob;
        private int recalc;

        private StalkGoal(StringEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.mob.getTarget();
            return target != null && isValidPrey(target) && !this.mob.isDeadOrDying();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                return;
            }
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.recalc <= 0) {
                this.recalc = 8 + this.mob.getRandom().nextInt(8);
                Vec3 point = this.mob.randomApproachPoint(target);
                if (point != null) {
                    this.mob.getMoveControl().setWantedPosition(point.x, point.y, point.z,
                        this.mob.stalkSpeed());
                }
            } else {
                this.recalc--;
            }
            if (this.mob.isWithinMeleeAttackRange(target) && this.mob.attackCooldown <= 0
                && this.mob.doHurtTarget(target)) {
                this.mob.attackCooldown = ATTACK_COOLDOWN;
            }
        }
    }
}
