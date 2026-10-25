package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModDamageTypes;
import com.dawnduskterminal.registry.ModEntities;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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
 * 也不会沉下去。水平移动由 {@link StringMoveControl} 驱动，不经过任何地面寻路：
 * 行为层每 8 到 17 tick 给一个落在液面上的目标点，控制器每 tick 朝它推一次，
 * 推进量由 {@link #getFlyingSpeed} 从速度属性折算（原版这一格对非玩家操控的生物是常量，
 * 速度属性不参与）。
 *
 * <h2>待机音</h2>
 * 原版的待机音有随机静音期，这只生物要一直有声：当前这条还剩 8 tick 时起下一条，
 * 两条重叠着听不出断点，见 {@link #tickAmbientSound}。
 *
 * <h2>大小变种</h2>
 * 小型与大型是两个模型与两套贴图（见客户端渲染器），血量与攻击力按模型尺寸成比例放大。
 * 生成时再叠一个 0.85 到 1.15 的随机体型偏移，血量、攻击力与移速都跟着这个偏移走：
 * 大型的速度基准是小型的 1.35 倍，同一档里越大的个体也越快。
 *
 * <h2>行为变种</h2>
 * {@link Kind} 分三种：{@link Kind#SURFACE 滑行型}贴液面走，只打脚边有流体的目标；
 * {@link Kind#FLYER 低空飞行型}在液面上方两格盘旋，索敌不看流体，一旦锁定目标就脱离液面自由飞，
 * 空中与岸上的目标都追得到；{@link Kind#SWIMMER 游动型}泡在流体里，只打流体里的目标。
 * 自然生成只出滑行型，另外两种由滑行型挨打时分裂出来。
 *
 * <h2>受伤分裂</h2>
 * 滑行型每次受到高于 2 点的伤害有 10% 概率裂成一对——低空飞行型与游动型各一，自己消失，
 * 伴随末影人传送音与传送粒子。分裂出来的个体也是滑行型以外的变种，不会再分裂
 *
 * <h2>免疫</h2>
 * 免疫击退、燃烧、药水与传送门流体的伤害与传送。传送门流体对非玩家实体只有在带「稳定」效果时
 * 才会传送，而这只生物 {@link #canBeAffected} 恒为假，所以永远不会被传送；剩下的周期性伤害由
 * {@link #isInvulnerableTo} 挡掉。
 *
 * <h2>自发光</h2>
 * 全身贴图不受光照压暗（emissive），做法在客户端 {@code client/StringRenderer} 的渲染层上，
 * 服务端没有对应状态。
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
    /** 小型的基准移动速度，大型再乘 {@link #LARGE_SPEED_FACTOR}，两者都再乘随机体型偏移 */
    private static final double SMALL_BASE_SPEED = 0.80D;
    /** 大型相对小型的移动速度倍率：体型越大越快 */
    private static final double LARGE_SPEED_FACTOR = 1.35D;

    /**
     * 索敌的液面距离：目标脚部方块周围一格（3×3×3）之内必须有传送门流体
     *
     * <p>「弦」离不开液面，够不到的敌人只会把它钓在岸上遛。站在离液面一格以内的目标，
     * 它横推一步就贴得上，这是一个确实能打到的距离；隔着两格的目标按这条判定直接放弃，
     * 追不过去也不陪着跑
     */
    private static final int PREY_REACH_RADIUS = 1;

    /** 低空飞行型的盘旋高度，液面之上 */
    private static final double LOW_FLIGHT_HEIGHT = 2.0D;
    /** 游动型贴着池底往上这么多 */
    private static final double SWIM_DEPTH = 0.2D;
    /** 游动型的身体要塞进液面之下：脚最多离液面这么近 */
    private static final double SWIM_SURFACE_MARGIN = 0.9D;
    /** 单次伤害高于这个值才可能分裂 */
    private static final float SPLIT_MIN_DAMAGE = 2.0F;
    /** 分裂概率 */
    private static final float SPLIT_CHANCE = 0.10F;
    /** 分裂出来的低空飞行型比原体高这么多，直接离开流体悬空 */
    private static final double SPLIT_FLYER_LIFT = 1.5D;

    private static final Kind[] KINDS = Kind.values();

    /**
     * 行为变种
     *
     * <p>三种共用模型、贴图与属性，区别只在移动约束与索敌范围：
     * 滑行型贴液面，只打脚边有流体的目标；低空飞行型在液面上方盘旋，索敌不看流体，
     * 锁定目标后脱离液面自由飞；游动型泡在流体里，只打流体里的目标
     *
     * <p>序号进同步数据与存档，只在末尾追加
     */
    public enum Kind {
        SURFACE,
        FLYER,
        SWIMMER
    }

    private static Kind kindById(int id) {
        return id >= 0 && id < KINDS.length ? KINDS[id] : Kind.SURFACE;
    }

    /**
     * 待机音的长度，187 tick
     *
     * <p>{@code string_ambient_1..3} 取自同一段素材，三个文件都是 413192 个采样、44100 Hz，
     * 即 9.369 秒 = 187.4 tick。排期按整 tick 走，取 187
     */
    private static final int AMBIENT_LENGTH_TICKS = 187;
    /** 下一条在当前这条的尾巴前这么多 tick 起播，两条重叠，听感上就没有断点 */
    private static final int AMBIENT_OVERLAP_TICKS = 8;
    /** 排期周期：一条的时长减去重叠量 */
    private static final int AMBIENT_PERIOD_TICKS = AMBIENT_LENGTH_TICKS - AMBIENT_OVERLAP_TICKS;

    private static final EntityDataAccessor<Boolean> DATA_LARGE =
        SynchedEntityData.defineId(StringEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SIZE =
        SynchedEntityData.defineId(StringEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_KIND =
        SynchedEntityData.defineId(StringEntity.class, EntityDataSerializers.BYTE);

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    private boolean largeVariant;
    private float sizeScale = 1.0F;
    private Kind kind = Kind.SURFACE;
    private int attackCooldown;
    /** 距离下一条待机音还有多少 tick */
    private int ambientTimer = AMBIENT_PERIOD_TICKS / 2;

    /** 上一个确认站在液面上的位置，离开流体时弹回这里 */
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private boolean hasAnchor;

    public StringEntity(EntityType<? extends StringEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.applyKind();
        // Entity 构造函数跑 getDimensions 时子类字段还没初始化，这里补一次
        this.refreshDimensions();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 60.0D)
            .add(Attributes.ATTACK_DAMAGE, 10.0D)
            .add(Attributes.MOVEMENT_SPEED, SMALL_BASE_SPEED)
            .add(Attributes.FLYING_SPEED, SMALL_BASE_SPEED)
            .add(Attributes.FOLLOW_RANGE, 24.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LARGE, false);
        builder.define(DATA_SIZE, 1.0F);
        builder.define(DATA_KIND, (byte) Kind.SURFACE.ordinal());
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        // 构造期里跑，此时变种还没定（默认滑行型）：真正的行为表在 applyKind 里按变种建
    }

    /**
     * 按变种装整套运动系统：控制器、行为表
     *
     * <p>变种在生成、读档、分裂时才会被设上，构造期只知道默认值，所以这里要能重来一遍。
     * 三种变种的运动都借用原版现成实现，不自己搓：
     * <ul>
     *   <li>滑行型：{@link StringMoveControl}，只给水平推进，垂直位置由液面钉死</li>
     *   <li>低空飞行型：原版 {@link FlyingMoveControl}（蜜蜂就是用这个，{@code maxTurn 20}、
     *       {@code hoversInPlace true}）+ {@link FlyingPathNavigation} + {@link MeleeAttackGoal}，
     *       寻路、转向、俯仰、垂直推进全归原版</li>
     *   <li>游动型：仿 {@code AbstractFish.FishMoveControl} 的 {@link FluidMoveControl}——进水有浮力、
     *       速度渐变、只改朝向，配合 {@link #travel} 里那段鱼的移动分支</li>
     * </ul>
     */
    private void applyKind() {
        this.setNoGravity(true);
        this.moveControl = switch (this.kind) {
            case SURFACE -> new StringMoveControl(this);
            case FLYER -> new FlyingMoveControl(this, 20, true);
            case SWIMMER -> new FluidMoveControl(this);
        };
        if (!this.level().isClientSide) {
            this.rebuildGoals();
        }
    }

    /**
     * 行为表，只主动索敌玩家
     *
     * <p>飞行型用原版 {@link MeleeAttackGoal} 追人——它自己会走寻路，飞行型的
     * {@link FlyingPathNavigation} 能把三维路径算到空中与岸上；目标没了以后由 {@link ReturnToFluidGoal}
     * 回液体上空。滑行型与游动型离不开流体，仍用直接给目标点的 {@link StalkGoal}
     */
    private void rebuildGoals() {
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(
            this, Player.class, 10, false, false, this::isValidPrey));
        switch (this.kind) {
            case SURFACE -> {
                this.goalSelector.addGoal(2, new StalkGoal(this));
                this.goalSelector.addGoal(3, new WanderGoal(this));
            }
            case FLYER -> {
                this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0D, false));
                this.goalSelector.addGoal(4, new FlyerWanderGoal(this));
                this.goalSelector.addGoal(5, new ReturnToFluidGoal(this));
            }
            case SWIMMER -> {
                this.goalSelector.addGoal(2, new StalkGoal(this));
                this.goalSelector.addGoal(3, new SwimWanderGoal(this));
            }
        }
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

    // ---------------------------------------------------------------- 行为变种

    public Kind getKind() {
        return this.kind;
    }

    /**
     * 换行为变种
     *
     * <p>属性不随变种变，但运动系统与行为表整套不同，所以走 {@link #applyKind} 重装；
     * 同步数据让客户端跟上
     */
    public void setKind(Kind kind) {
        this.kind = kind;
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_KIND, (byte) kind.ordinal());
        }
        this.applyKind();
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

    /**
     * 按体型重算属性
     *
     * <p>血量与攻击力按几何倍率 2.5 放大（大型的模型是 2.5 倍半径），再乘随机体型偏移。
     * 移速走同一条路：大型基准是小型的 {@link #LARGE_SPEED_FACTOR} 倍，再乘同一个随机偏移，
     * 于是「体型越大移速越快」在一档之内也成立——不再是两档各写一个死值
     */
    private void applySizeAttributes() {
        float geometry = this.largeVariant ? 2.5F : 1.0F;
        double health = 60.0D * geometry * this.sizeScale;
        double attack = 10.0D * geometry * this.sizeScale;
        double speed = SMALL_BASE_SPEED * (this.largeVariant ? LARGE_SPEED_FACTOR : 1.0D)
            * this.sizeScale;
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
        } else if (DATA_KIND.equals(key)) {
            this.kind = kindById(this.entityData.get(DATA_KIND));
            this.applyKind();
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
        // 一群「弦」同时刷出来时不要齐声开口
        this.ambientTimer = random.nextInt(AMBIENT_PERIOD_TICKS);
        return data;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Large", this.largeVariant);
        tag.putFloat("SizeScale", this.sizeScale);
        tag.putByte("Kind", (byte) this.kind.ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.largeVariant = tag.getBoolean("Large");
        this.sizeScale = Mth.clamp(tag.contains("SizeScale") ? tag.getFloat("SizeScale") : 1.0F,
            MIN_SIZE, MAX_SIZE);
        this.kind = kindById(tag.getByte("Kind"));
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_LARGE, this.largeVariant);
            this.entityData.set(DATA_SIZE, this.sizeScale);
            this.entityData.set(DATA_KIND, (byte) this.kind.ordinal());
            applySizeAttributes();
            // 存档里的当前血量不能因为重算上限被回满
            this.setHealth(Math.min(this.getHealth(), this.getMaxHealth()));
        }
        // 不依赖 DATA_KIND 的同步回调：那个回调只在值真的变了的时候触发，
        // 而运动系统与重力标记必须在读档后一定按当前变种重装一遍
        // （原版 Entity#load 会先把 NoGravity 按存档覆盖掉，存档里没有这个键就是 false）
        this.applyKind();
        this.refreshDimensions();
    }

    // ---------------------------------------------------------------- 活动范围

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.isDeadOrDying()) {
            return;
        }
        switch (this.kind) {
            case SURFACE -> keepOnFluidSurface();
            case SWIMMER -> keepSubmerged();
            // 低空飞行型不夹位置：高度交给 FlyingMoveControl 与行为层给的目标点
            case FLYER -> { }
        }
        tickAmbientSound();
    }

    private void keepOnFluidSurface() {
        int x = Mth.floor(this.getX());
        int z = Mth.floor(this.getZ());
        double surface = findFluidSurface(this.level(), x, z, Mth.floor(this.getY()));
        if (Double.isNaN(surface)) {
            if (this.hasAnchor) {
                this.setPos(this.anchorX, this.anchorY, this.anchorZ);
            }
            // 目标点还在液体外，这 tick 弹回来、下 tick 又往外推，就变成在岸边抖；
            // 作废掉让行为层马上重选一个新点
            ((StringMoveControl) this.moveControl).cancelTarget();
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
     * 游动：泡在流体里，脚压在池底旁边，离开流体就弹回锚点
     *
     * <p>垂直位置每 tick 夹一次：从滑行型分裂出来时它落在液面上，这一夹把它按进水里；
     * 池子只有一格深时上下界会重合，那就是贴底游
     */
    private void keepSubmerged() {
        int x = Mth.floor(this.getX());
        int z = Mth.floor(this.getZ());
        FluidSpan span = fluidSpan(this.level(), x, z, Mth.floor(this.getY()));
        if (span == null) {
            if (this.hasAnchor) {
                this.setPos(this.anchorX, this.anchorY, this.anchorZ);
            }
            ((FluidMoveControl) this.moveControl).cancelTarget();
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        double lower = span.bottom() + SWIM_DEPTH;
        double upper = Math.max(lower, span.top() - SWIM_SURFACE_MARGIN);
        double y = Mth.clamp(this.getY(), lower, upper);
        this.hasAnchor = true;
        this.anchorX = this.getX();
        this.anchorY = y;
        this.anchorZ = this.getZ();
        this.setPos(this.getX(), y, this.getZ());
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x, 0.0D, motion.z);
    }

    /**
     * 鱼与飞行两套移动分支，各自照抄原版
     *
     * <p><b>游动型</b>照 {@code AbstractFish#travel}：原版鱼在水里不走 {@code LivingEntity} 那套空中/地面摩擦，
     * 而是自己推、自己衰减——{@code moveRelative(0.01)} 推一下、{@code move} 走、速度整体乘 0.9，
     * 没有目标时每 tick 往下沉一点。这里把 {@code isInWater()} 换成传送门流体（本模组的流体不是
     * {@code #minecraft:water}，原版那个判定不会命中它），其余数值一字不改
     *
     * <p><b>飞行型</b>照 {@code FlyingMob#travel}（恶魂与幻翼的父类）：<strong>整段不扣重力</strong>，
     * 推力取 {@code getFlyingSpeed()}、摩擦 0.91，水面 0.8 / 岩浆 0.5 的衰减照抄。这一点是关键——
     * 光靠 {@code setNoGravity(true)} 飘着的飞行生物，重力标记一旦被别的路径关掉（读档、NBT、
     * 别的代码调 {@code setNoGravity(false)}）就会自由落体；原版飞行生物从来不掉，
     * 是因为它们的 {@code travel} 里压根没有重力项
     */
    @Override
    public void travel(Vec3 input) {
        if (this.kind == Kind.SWIMMER && this.isEffectiveAi() && this.isEyeInPortalFluid()) {
            this.moveRelative(0.01F, input);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
            if (this.getTarget() == null) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.005D, 0.0D));
            }
            return;
        }
        if (this.kind == Kind.FLYER) {
            if (this.isInWater()) {
                this.moveRelative(0.02F, input);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8F));
            } else if (this.isInLava()) {
                this.moveRelative(0.02F, input);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
            } else {
                BlockPos ground = this.getBlockPosBelowThatAffectsMyMovement();
                float friction = 0.91F;
                if (this.onGround()) {
                    friction = this.level().getBlockState(ground)
                        .getFriction(this.level(), ground, this) * 0.91F;
                }
                this.moveRelative(this.getFlyingSpeed(), input);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale((double) friction));
            }
            this.calculateEntityAnimation(false);
            return;
        }
        super.travel(input);
    }

    /**
     * 飞行型与游动型都不吃摔伤，照 {@code FlyingMob#checkFallDamage} 写成空实现
     *
     * <p>光把 {@code travel} 的重力去掉还不够：俯冲（追击地面上的目标时必然会俯冲）会让
     * {@code fallDistance} 累计，落地照样结算摔伤；传送门流体不是 {@code #minecraft:water}，
     * 进水也不会像原版水那样把 {@code fallDistance} 清零。空实现把累计与结算一起关掉
     */
    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // 不吃落地伤害
    }

    /** 一列里连续传送门流体的上下界：{@code bottom} 是最低那格的底，{@code top} 是最高那格的液面 */
    private record FluidSpan(double bottom, double top) {}

    /**
     * 找一列里的传送门流体区间
     *
     * <p>先在 {@code nearY} 上下两格之内命中一格流体，再沿这一列往上往下各探到边界；
     * 两点都只走这一列，不会像三维搜索那样把岸边的地底含水层当成自己的水体
     *
     * @return 找不到流体时返回 {@code null}
     */
    private static FluidSpan fluidSpan(Level level, int x, int z, int nearY) {
        BlockPos found = null;
        for (int dy = 2; dy >= -2; dy--) {
            BlockPos pos = new BlockPos(x, nearY + dy, z);
            if (isPortalFluid(level, pos)) {
                found = pos;
                break;
            }
        }
        if (found == null) {
            return null;
        }
        int bottom = found.getY();
        while (bottom > level.getMinBuildHeight()
            && isPortalFluid(level, new BlockPos(x, bottom - 1, z))) {
            bottom--;
        }
        int top = found.getY();
        while (top < level.getMaxBuildHeight()
            && isPortalFluid(level, new BlockPos(x, top + 1, z))) {
            top++;
        }
        BlockPos topPos = new BlockPos(x, top, z);
        return new FluidSpan(bottom, top + level.getFluidState(topPos).getHeight(level, topPos));
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

    private boolean isEyeInPortalFluid() {
        return isPortalFluid(this.level().getFluidState(BlockPos.containing(this.getEyePosition())));
    }

    /**
     * 按高度图找一列的液面，任何高度都能用
     *
     * <p>{@link #findFluidSurface} 只在当前位置上下三格里找（滑行型每 tick 被钉在液面，够用）；
     * 低空飞行型在两格以上甚至岸上高空，得靠高度图：{@code WORLD_SURFACE} 把流体也算作非空气，
     * 取到的那一格空气下面就是液面。与 {@link com.dawnduskterminal.world.StringSpawner} 同一套判定
     *
     * @return 液面上表面的 Y，找不到时返回 {@link Double#NaN}
     */
    private static double findSurfaceByHeightmap(Level level, int x, int z) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (surfaceY <= level.getMinBuildHeight() || surfaceY >= level.getMaxBuildHeight()) {
            return Double.NaN;
        }
        BlockPos fluidPos = new BlockPos(x, surfaceY - 1, z);
        FluidState fluid = level.getFluidState(fluidPos);
        if (!isPortalFluid(fluid)) {
            return Double.NaN;
        }
        return surfaceY - 1 + fluid.getHeight(level, fluidPos) + SURFACE_OFFSET;
    }

    public static boolean isPortalFluid(FluidState fluid) {
        return fluid.getType() == ModFluids.PORTAL_FLUID.get()
            || fluid.getType() == ModFluids.PORTAL_FLOWING.get();
    }

    /**
     * 目标脚边有没有液体：以脚部方块为中心 {@link #PREY_REACH_RADIUS} 格之内
     *
     * <p>站在液面里的目标天然命中（脚下那一格就是流体）；站在岸上的目标要求流体就在脚边一格，
     * 也就是它横推一步能贴上来打到的位置。两格以外的岸边目标不索敌——这只生物离不开液面，
     * 追不过去的目标只会变成遛它
     */
    private static boolean hasPortalFluidWithinReach(Level level, BlockPos feet) {
        int r = PREY_REACH_RADIUS;
        BlockPos min = feet.offset(-r, -r, -r);
        BlockPos max = feet.offset(r, r, r);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (isPortalFluid(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 索敌判定，按变种分档
     *
     * <p>三种变种都只主动索敌玩家，差别在液体条件：滑行型只能打到脚边一格有流体的玩家；
     * 低空飞行型不看流体，空中与岸上一律算；游动型出不了水，只打泡在流体里的玩家
     */
    public boolean isValidPrey(LivingEntity target) {
        if (!(target instanceof Player player) || !player.isAlive()) {
            return false;
        }
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        return switch (this.kind) {
            case SURFACE -> hasPortalFluidWithinReach(player.level(), player.blockPosition());
            case FLYER -> true;
            case SWIMMER -> isPortalFluid(player.level(), player.blockPosition());
        };
    }

    /**
     * 刷怪蛋的落点必须挨着传送门流体
     *
     * <p>落点那一格自己是流体（在池子里点），或者它正下方是流体（点池边方块，实体落在液面上方那一格）
     * 才准放；点在干地上直接拒绝。喷溅出来的蛋与发射器走同一条判定
     */
    public static boolean canSpawnAt(Level level, BlockPos pos) {
        return isPortalFluid(level, pos) || isPortalFluid(level, pos.below());
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

    /** 巡航目标点：滑行型落在液面，游动型落在水体内；飞行型走寻路，用不到这个 */
    @Nullable
    private Vec3 wanderPoint(double radius) {
        return switch (this.kind) {
            case SURFACE -> randomSurfacePoint(radius);
            case SWIMMER -> randomSwimPoint(radius);
            case FLYER -> null;
        };
    }

    /** 追击目标点：滑行型要落在液面上，游动型要落在水体内；飞行型走寻路，用不到这个 */
    @Nullable
    private Vec3 approachPoint(LivingEntity target) {
        return switch (this.kind) {
            case SURFACE -> randomApproachPoint(target);
            case SWIMMER -> randomSubmergedApproachPoint(target);
            case FLYER -> null;
        };
    }

    /**
     * 低空飞行型的盘旋点：附近某列液面上方 {@link #LOW_FLIGHT_HEIGHT} 格
     *
     * <p>用高度图找液面，飞在两格以上乃至岸上高空时也找得到；蜜蜂的巡航点也是这样围绕自己
     * 随机取一个点，然后交给寻路（见 {@code Bee.BeeWanderGoal}）
     */
    @Nullable
    private Vec3 randomHoverPoint(double radius) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = this.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = radius * (0.35D + this.getRandom().nextDouble() * 0.65D);
            int x = Mth.floor(this.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(this.getZ() + Math.sin(angle) * distance);
            double surface = findSurfaceByHeightmap(this.level(), x, z);
            if (!Double.isNaN(surface)) {
                return new Vec3(x + 0.5D, surface + LOW_FLIGHT_HEIGHT, z + 0.5D);
            }
        }
        return null;
    }

    /**
     * 最近一处可以盘旋的液面上空：先看自己这一列，再按环向外采样
     *
     * <p>追丢目标后可能停在岸上任意高度，这一列下面没有流体就往外找，找到就把盘旋高度定在
     * 液面上方 {@link #LOW_FLIGHT_HEIGHT} 格
     *
     * @return 找不到时返回 {@code null}
     */
    @Nullable
    private Vec3 nearestFluidHoverPoint(double maxRadius) {
        double here = findSurfaceByHeightmap(this.level(), Mth.floor(this.getX()), Mth.floor(this.getZ()));
        if (!Double.isNaN(here)) {
            return new Vec3(this.getX(), here + LOW_FLIGHT_HEIGHT, this.getZ());
        }
        for (int ring = 1; ring <= 3; ring++) {
            double distance = maxRadius * ring / 3.0D;
            for (int step = 0; step < 8; step++) {
                double angle = step * Math.PI / 4.0D;
                int x = Mth.floor(this.getX() + Math.cos(angle) * distance);
                int z = Mth.floor(this.getZ() + Math.sin(angle) * distance);
                double surface = findSurfaceByHeightmap(this.level(), x, z);
                if (!Double.isNaN(surface)) {
                    return new Vec3(x + 0.5D, surface + LOW_FLIGHT_HEIGHT, z + 0.5D);
                }
            }
        }
        return null;
    }

    /**
     * 已经停在某列液面上方 {@link #LOW_FLIGHT_HEIGHT} 格附近？
     *
     * <p>低空飞行型的回家行为用它判断「是不是已经到家了」；用高度图找液面，所以飞多高都成立
     */
    private boolean isHoveringOverFluid() {
        double surface = findSurfaceByHeightmap(this.level(), Mth.floor(this.getX()),
            Mth.floor(this.getZ()));
        return !Double.isNaN(surface)
            && Math.abs(this.getY() - (surface + LOW_FLIGHT_HEIGHT)) < 1.5D;
    }

    /**
     * 游动型的巡航点：附近某一列水体里的随机深度
     *
     * <p>仿 {@code BehaviorUtils.getRandomSwimmablePos}：在附近找个可游的格子，深度随机取在
     * 池底到液面之下之间。这里的「可游」是传送门流体，不是原版水
     */
    @Nullable
    private Vec3 randomSwimPoint(double radius) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = this.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = radius * (0.35D + this.getRandom().nextDouble() * 0.65D);
            int x = Mth.floor(this.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(this.getZ() + Math.sin(angle) * distance);
            FluidSpan span = fluidSpan(this.level(), x, z, Mth.floor(this.getY()));
            if (span != null) {
                double lower = span.bottom() + SWIM_DEPTH;
                double upper = Math.max(lower, span.top() - SWIM_SURFACE_MARGIN);
                double y = Mth.lerp(this.getRandom().nextDouble(), lower, upper);
                return new Vec3(x + 0.5D, y, z + 0.5D);
            }
        }
        return null;
    }

    /** 游动型的追击点：目标所在列得有水体，高度不硬掰（垂直分量由 {@link FluidMoveControl} 给） */
    @Nullable
    private Vec3 randomSubmergedApproachPoint(LivingEntity target) {
        double angle = this.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = 1.0D + this.getRandom().nextDouble() * 3.0D;
        int x = Mth.floor(target.getX() + Math.cos(angle) * radius);
        int z = Mth.floor(target.getZ() + Math.sin(angle) * radius);
        if (fluidSpan(this.level(), x, z, Mth.floor(this.getY())) == null) {
            return null;
        }
        return new Vec3(x + 0.5D, target.getY(), z + 0.5D);
    }

    /**
     * 巡航倍率
     *
     * <p>大小两档共用同一个倍率：大小差异已经落在 {@code MOVEMENT_SPEED} 属性上（大型 1.35 倍），
     * 这里再给大型压低一档，等于把它的速度优势又抹平回去
     */
    private static double wanderSpeed() {
        return 1.15D;
    }

    /** 追击倍率，同样两档共用 */
    private static double stalkSpeed() {
        return 1.55D;
    }

    /**
     * 空中的每 tick 推进量，折算自 {@link StringMoveControl} 写下的速度
     *
     * <p>原版这条路对非玩家操控的生物返回恒定的 0.02，<strong>完全不看速度属性</strong>
     * （{@code LivingEntity#getFlyingSpeed} 只在有玩家乘客时才用 {@code getSpeed()}），
     * 于是「倍率 × 移动速度」这一步白算：大小变种的差异、追击比巡航快这一档全部失效。
     *
     * <p>这里的 0.045 是按原版那个 0.02 反推的：空中摩擦 0.91 把每 tick 的推进积分成
     * 约 1/0.09 ≈ 11 倍的终速。小型基准 0.80 的移动速度乘 1.15 的巡航倍率再乘 0.045，
     * 折出来约 9.2 格每秒；追击那档 12.4 格每秒。大型基准是 1.35 倍（1.08），
     * 对应 12.4 与 16.7 格每秒；随机体型 0.85~1.15 也一起乘。想整体调快调慢就动这个系数
     */
    @Override
    protected float getFlyingSpeed() {
        return (float) (this.getSpeed() * 0.045D);
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

    // ---------------------------------------------------------------- 受伤分裂

    /**
     * 滑行型挨到重击时可能裂成一对
     *
     * <p>门槛是「单次伤害高于 2 点」，概率 10%（{@link #SPLIT_CHANCE}）。只有滑行型会裂：
     * 两个新变种自己不再裂，否则一次打架会指数级繁殖
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.kind == Kind.SURFACE
            && amount > SPLIT_MIN_DAMAGE && this.isAlive()
            && this.getRandom().nextFloat() < SPLIT_CHANCE) {
            this.splitIntoPair();
        }
        return hurt;
    }

    /**
     * 裂成一低空飞行型 + 一游动型，自己消失
     *
     * <p>声音与粒子照末影人传送：这不是死亡，是原地换了个形态，所以走 {@code discard} 而不是
     * {@code die}——不掉战利品、不算击杀、不播倒地动画
     */
    private void splitIntoPair() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double centerY = this.getY() + this.getBbHeight() * 0.5D;
        serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), centerY, this.getZ(),
            60, 0.7D, 0.7D, 0.7D, 0.1D);
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
        // 飞行型抬高一点直接悬空，游动型留在原位、由 keepSubmerged 按进水里
        this.spawnKind(serverLevel, Kind.FLYER, SPLIT_FLYER_LIFT);
        this.spawnKind(serverLevel, Kind.SWIMMER, 0.0D);
        this.discard();
    }

    /** 生成一个同样体型、指定变种与高度的兄弟个体 */
    private void spawnKind(ServerLevel level, Kind kind, double lift) {
        StringEntity spawned = ModEntities.STRING.get().create(level);
        if (spawned == null) {
            return;
        }
        spawned.moveTo(this.getX(), this.getY() + lift, this.getZ(),
            this.getRandom().nextFloat() * 360.0F, 0.0F);
        spawned.setKind(kind);
        spawned.setVariant(this.largeVariant, this.sizeScale);
        level.addFreshEntity(spawned);
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

    /**
     * 原版的待机音这里不报，改由 {@link #tickAmbientSound} 按音效长度排期
     *
     * <p>原版对生物待机音的处理是「每隔一段随机间隔掷一次，命中就播一条」，中间必然有静音期；
     * 这只生物要一直有声。返回 {@code null} 时 {@code LivingEntity#playAmbientSound} 什么也不做，
     * 原版那条路就关掉了
     */
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    /**
     * 一条接一条地播待机音：当前这条还剩 {@link #AMBIENT_OVERLAP_TICKS} tick 时起下一条
     *
     * <p>周期是常量，因为三个变体取自同一段素材、长度一致（见 {@link #AMBIENT_LENGTH_TICKS}）；
     * 客户端每次自己随机挑一个变体，服务端不需要知道挑了哪条。音高固定 1.0 —— 变调会改变实际
     * 时长，排期就对不上了
     */
    private void tickAmbientSound() {
        if (this.ambientTimer > 0) {
            this.ambientTimer--;
            return;
        }
        this.ambientTimer = AMBIENT_PERIOD_TICKS;
        this.playSound(ModSounds.STRING_AMBIENT.get(), 1.0F, 1.0F);
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
     * 滑行型的控制器：只给水平推进，垂直位置由 {@link #keepOnFluidSurface} 钉住
     *
     * <p>{@code Mob#setSpeed} 会顺手把 {@code zza} 也写成同一个值，所以这里只要再补一次
     * {@code setZza(1)} 让输入方向明确朝前即可。不能照抄原版 {@code MoveControl}：它一进
     * {@code MOVE_TO} 就先把状态改回 {@code WAIT}，假设行为层每 tick 重给目标点
     * （{@code PathNavigation} 正是这么做的）；这里的行为每 8 到 17 tick 才给一次，
     * 照抄的结果是「推一 tick、滑一程」，实测速度只有设定值的十分之一
     */
    private static final class StringMoveControl extends MoveControl {
        private static final float TURN_SPEED = 30.0F;
        /** 到这个距离以内算到达，停下等行为层给下一个目标点 */
        private static final double ARRIVE_DISTANCE = 0.35D;

        private StringMoveControl(StringEntity mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (this.operation != Operation.MOVE_TO) {
                stop();
                return;
            }
            double dx = this.wantedX - this.mob.getX();
            double dz = this.wantedZ - this.mob.getZ();
            if (Math.sqrt(dx * dx + dz * dz) < ARRIVE_DISTANCE) {
                this.operation = Operation.WAIT;
                stop();
                return;
            }
            float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, TURN_SPEED));
            this.mob.yBodyRot = this.mob.getYRot();
            this.mob.yHeadRot = this.mob.getYRot();
            this.mob.setSpeed((float) (this.speedModifier
                * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            this.mob.setXxa(0.0F);
            this.mob.setZza(1.0F);
            this.mob.setYya(0.0F);
            this.mob.setNoGravity(true);
        }

        /** 目标点作废：被流体边界弹回来时用，下一 tick 起不再往那边推 */
        private void cancelTarget() {
            this.operation = Operation.WAIT;
            stop();
        }

        private void stop() {
            this.mob.setZza(0.0F);
            this.mob.setYya(0.0F);
            this.mob.setSpeed(0.0F);
        }
    }

    /**
     * 游动型的控制器：照抄 {@code AbstractFish.FishMoveControl}
     *
     * <p>原版鱼的做法：眼睛泡在水里时每 tick 补 0.005 的浮力；速度按 0.125 的比重渐变过去，
     * 不瞬间拉满；按目标点补一点垂直推力；只改朝向、不硬掰俯仰；横向推进量靠
     * {@code Mob#setSpeed}（它内部会把 {@code zza} 一起写掉）。
     *
     * <p>只换掉一处判定：{@code isEyeInFluid(FluidTags.WATER)} → {@link #isEyeInPortalFluid()}。
     * 本模组的流体不是 {@code #minecraft:water}，原版那个判定不会命中它
     */
    private static final class FluidMoveControl extends MoveControl {
        private final StringEntity string;

        private FluidMoveControl(StringEntity mob) {
            super(mob);
            this.string = mob;
        }

        @Override
        public void tick() {
            if (this.string.isEyeInPortalFluid()) {
                this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(0.0D, 0.005D, 0.0D));
            }
            if (this.operation != Operation.MOVE_TO) {
                this.mob.setSpeed(0.0F);
                return;
            }
            float speed = (float) (this.speedModifier
                * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
            this.mob.setSpeed(Mth.lerp(0.125F, this.mob.getSpeed(), speed));
            double dx = this.wantedX - this.mob.getX();
            double dy = this.wantedY - this.mob.getY();
            double dz = this.wantedZ - this.mob.getZ();
            if (dy != 0.0D) {
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance > 1.0E-4D) {
                    this.mob.setDeltaMovement(this.mob.getDeltaMovement()
                        .add(0.0D, this.mob.getSpeed() * (dy / distance) * 0.1D, 0.0D));
                }
            }
            if (dx != 0.0D || dz != 0.0D) {
                float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
                this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, 90.0F));
                this.mob.yBodyRot = this.mob.getYRot();
            }
            if (Math.sqrt(dx * dx + dz * dz) < 0.35D && Math.abs(dy) < 0.5D) {
                this.operation = Operation.WAIT;
                this.mob.setSpeed(0.0F);
            }
        }

        private void cancelTarget() {
            this.operation = Operation.WAIT;
            this.mob.setZza(0.0F);
            this.mob.setYya(0.0F);
            this.mob.setSpeed(0.0F);
        }
    }

    /** 滑行型：一直换目标点，永不进入「站着不动」的行为状态 */
    private static final class WanderGoal extends Goal {
        private final StringEntity mob;
        private int cooldown;

        private WanderGoal(StringEntity mob) {
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
                    wanderSpeed());
            }
        }
    }

    /**
     * 游动型的巡航：照 {@code AbstractFish.FishSwimGoal} 的写法
     *
     * <p>原版的鱼用 {@code RandomSwimmingGoal(mob, 1.0, 40)}：每 40 tick 掷一次、挑一个
     * 附近可游的格子，然后靠寻路游过去。这里的可游格子是传送门流体的水体（原版那个
     * {@code getRandomSwimmablePos} 只认水），其余结构照搬：挑点 → 给目标点 → 游到之前不换
     */
    private static final class SwimWanderGoal extends Goal {
        private final StringEntity mob;

        private SwimWanderGoal(StringEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.mob.getTarget() == null && this.mob.isAlive()
                && this.mob.getMoveControl().hasWanted() == false
                && this.mob.getRandom().nextInt(40) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return this.mob.getMoveControl().hasWanted();
        }

        @Override
        public void start() {
            Vec3 point = this.mob.randomSwimPoint(8.0D);
            if (point != null) {
                this.mob.getMoveControl().setWantedPosition(point.x, point.y, point.z, 1.0D);
            }
        }
    }

    /**
     * 飞行型的巡航：照蜜蜂 {@code Bee.BeeWanderGoal} 的写法
     *
     * <p>蜜蜂是「寻路空了 + 十次里掷中一次」就挑一个点，再用
     * {@code navigation.moveTo(navigation.createPath(...), 1.0)} 飞过去；飞行、转向、俯仰、
     * 垂直推进都归原版 {@link FlyingMoveControl}。这里挑的是附近液面上方的盘旋点，
     * 所以没有目标时它一直在液体上空转
     */
    private static final class FlyerWanderGoal extends Goal {
        private final StringEntity mob;

        private FlyerWanderGoal(StringEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.mob.getTarget() == null && this.mob.getNavigation().isDone()
                && this.mob.getRandom().nextInt(10) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return this.mob.getNavigation().isInProgress();
        }

        @Override
        public void start() {
            Vec3 point = this.mob.randomHoverPoint(8.0D);
            if (point != null) {
                this.mob.getNavigation().moveTo(
                    this.mob.getNavigation().createPath(BlockPos.containing(point), 1), 1.0D);
            }
        }
    }

    /**
     * 目标没了就回液体上空
     *
     * <p>追丢、打死目标以后它可能停在岸上或任何高度。这一条在「没有目标、寻路也空着、
     * 自己又不在液面上方两格」时触发：远处先寻路飞回去，近了就照蜜蜂在巢口悬停的做法，
     * 每 tick 用很小的速度给一次目标点（{@code Bee} 的 {@code getMoveControl().setWantedPosition(hoverPos, 0.35F)}）
     */
    private static final class ReturnToFluidGoal extends Goal {
        private final StringEntity mob;
        @Nullable
        private Vec3 hover;

        private ReturnToFluidGoal(StringEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.mob.getTarget() != null || !this.mob.isAlive()) {
                return false;
            }
            if (!this.mob.getNavigation().isDone() || this.mob.isHoveringOverFluid()) {
                return false;
            }
            return this.mob.getRandom().nextInt(20) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return this.hover != null && this.mob.getTarget() == null;
        }

        @Override
        public void start() {
            this.hover = this.mob.nearestFluidHoverPoint(32.0D);
        }

        @Override
        public void stop() {
            this.hover = null;
        }

        @Override
        public void tick() {
            if (this.hover == null) {
                return;
            }
            if (this.mob.position().distanceTo(this.hover) > 3.0D) {
                if (this.mob.getNavigation().isDone()) {
                    this.mob.getNavigation().moveTo(
                        this.mob.getNavigation().createPath(BlockPos.containing(this.hover), 1), 1.0D);
                }
            } else {
                this.mob.getMoveControl().setWantedPosition(this.hover.x, this.hover.y, this.hover.z, 0.35D);
            }
        }
    }

    /**
     * 以随机走向接近目标：每次重选一个绕在目标周围的目标点，而不是直线追
     *
     * <p>滑行型与游动型离不开液体，所以它们直接给目标点、不走寻路；飞行型交给
     * 原版 {@link MeleeAttackGoal}。能不能继续跟由 {@link StringEntity#isValidPrey} 按变种决定
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
            return target != null && this.mob.isValidPrey(target) && !this.mob.isDeadOrDying();
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
                Vec3 point = this.mob.approachPoint(target);
                if (point != null) {
                    this.mob.getMoveControl().setWantedPosition(point.x, point.y, point.z,
                        stalkSpeed());
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
