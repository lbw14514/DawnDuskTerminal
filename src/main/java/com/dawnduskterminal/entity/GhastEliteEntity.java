package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModSounds;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 「大恶魂」：暮色恶魂的精英模式
 *
 * <p>除了「会改天气、受伤减免」这类暮色恶魂专属机制之外，行为与它相似：飘着飞、按原版恶魂的节奏喷火球，
 * 外加两样自己的东西 ——
 *
 * <ul>
 *   <li>常驻：每 10 tick 落一阵泪滴。玩家站在天光下就被砸 3 点（伤害类型借用暮色森林的
 *       {@code twilightforest:ghast_tear}）；泪滴落在迷你恶魂身上时把那一只吸收掉并治疗自己 2 点
 *   <li>一次性：第一次锁定目标时，在自身 24 格内召一批 {@code twilightforest:carminite_ghastling}
 *       并把目标共享给它们
 * </ul>
 *
 * <p>与「恶果」一样，火球撞到它身上会被拨走：有索敌就砸向索敌对象，没有就原路弹回，
 * 反弹时用 {@code affectionate_scream}
 */
public class GhastEliteEntity extends Ghast implements GeoEntity {
    /** 生命值 250 */
    public static final float MAX_HEALTH = 250.0F;

    /** 索敌范围 100 格 */
    public static final double FOLLOW_RANGE = 100.0D;

    /** 模型包只有一条循环动画：触手与腿的摆动（YSM 的 perlin 通道由渲染器逐帧补） */
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("parallel0");

    /**
     * 生成时抬高多少格
     *
     * <p>几何在原点下方还有一块 {@code origin=[-64,-48,-64]}、{@code size=[128,48,128]} 的裙摆，
     * 即脚下 48 单位 = 3 格；贴地放会埋掉三格，所以抬这三格
     */
    private static final double SPAWN_LIFT = 3.0D;

    /** 脸在体中心前方 128 单位 / 2 = 4 格处；火球自己半径 0.5 格，所以 4.6 格刚好贴着脸 */
    private static final double MOUTH_FORWARD = 4.6D;

    /** 脸的中心：头立方体 0~128 单位，中心 64 单位 = 4 格 */
    private static final double MOUTH_HEIGHT = 4.0D;

    /** 拨出去的火球出现在身外多远：8 格箱子的半对角线约 6.93 格，取 7.5 保证任何方向都在箱外 */
    private static final double DEFLECT_ORIGIN = 7.5D;

    /** 泪滴每 10 tick 落一次（暮色恶魂的 tantrum 也是这个节奏） */
    private static final int TEAR_INTERVAL = 10;

    /** 泪滴砸玩家的伤害 */
    private static final float TEAR_DAMAGE = 3.0F;

    /** 泪滴吸收一只迷你恶魂之后治疗的点数（暮色恶魂吃小鬼也是 2 点） */
    private static final float TEAR_HEAL = 2.0F;

    /** 泪滴覆盖范围：碰撞箱向下 16 格、竖直再放大 16 格（与暮色恶魂一致，水平方向不放大） */
    private static final double TEAR_DROP = 16.0D;

    /** 一次召唤的迷你恶魂数量 */
    private static final int MINION_COUNT = 6;

    /** 召唤半径：8~24 格 */
    private static final double MINION_MIN_RADIUS = 8.0D;
    private static final double MINION_MAX_RADIUS = 24.0D;

    /** 每隔这么多 tick 把当前索敌再共享一次给身边还没目标的迷你恶魂 */
    private static final int MINION_RESHARE_INTERVAL = 100;

    /** 一轮喷三发 */
    private static final int FIREBALL_COUNT = 3;
    /** 三发之间左右各偏 8 度 */
    private static final float FIREBALL_SPREAD_DEGREES = 8.0F;

    /** 暮色森林的泪滴伤害类型；缺暮色森林时退回普通生物伤害 */
    private static final ResourceKey<DamageType> GHAST_TEAR =
        ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("twilightforest:ghast_tear"));

    /** 暮色森林的迷你恶魂 */
    private static final ResourceLocation GHASTLING =
        ResourceLocation.parse("twilightforest:carminite_ghastling");

    /**
     * 脸上那两张薄片的状态，必须同步
     *
     * <p>{@code getTarget()} 只在服务端有值，客户端要知道「现在有没有盯人」只能靠数据位；
     * 蓄力（{@code isCharging()}）本来就在原版恶魂的同步数据里，这里一并折进来，渲染器只读这一位
     */
    private static final EntityDataAccessor<Byte> DATA_FACE = SynchedEntityData.defineId(
        GhastEliteEntity.class, EntityDataSerializers.BYTE);

    /** 平静 / 有索敌 / 蓄力中，顺序与 {@link #DATA_FACE} 里存的序号一致 */
    public enum Face {
        CALM,
        ANNOYED,
        ATTACK
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean minionsSummoned;

    public GhastEliteEntity(EntityType<? extends GhastEliteEntity> type, Level level) {
        super(type, level);
        // 与暮色恶魂一样：不参与碰撞，用无碰撞的移动控制器推加速度。8 格见方的箱子在树林里
        // 只要还吃碰撞就寸步难行（上一版「不会飞」就是这么来的）
        this.noPhysics = true;
        this.moveControl = new NoClipMoveControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Ghast.createAttributes()
            .add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new RandomFloatAroundGoal(this));
        this.goalSelector.addGoal(7, new EliteLookGoal(this));
        this.goalSelector.addGoal(7, new GhastShootGoal<GhastEliteEntity>(this,
            GhastEliteEntity::mouthPosition, this::fireVolley));
        // 普通索敌：100 格内找玩家（跟索敌范围来自 FOLLOW_RANGE），没有原版恶魂那条 |Δy| <= 4 的过滤
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.GHAST_UNIVERSAL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GHAST_UNIVERSAL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GHAST_UNIVERSAL_DEATH.get();
    }

    /** 一次只生成一只；「加载范围内最多 1 只」归 {@link GhastElitePopulation} 管 */
    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        this.entityData.set(DATA_FACE, (byte) this.currentFace().ordinal());
        if (this.tickCount % TEAR_INTERVAL == 0) {
            this.doTearRain();
        }
        this.absorbTouchingMinions();
        if (!this.minionsSummoned && this.getTarget() != null) {
            // 只有真的放出小鬼才算用掉这次技能
            this.minionsSummoned = this.summonMinions();
        }
        if (this.getTarget() != null && this.tickCount % MINION_RESHARE_INTERVAL == 0) {
            this.shareTarget();
        }
    }

    /** 蓄力中 → 攻击脸；有索敌 → 不悦脸；否则平静 */
    public Face getFace() {
        return switch (this.entityData.get(DATA_FACE)) {
            case 1 -> Face.ANNOYED;
            case 2 -> Face.ATTACK;
            default -> Face.CALM;
        };
    }

    private Face currentFace() {
        if (this.isCharging()) {
            return Face.ATTACK;
        }
        return this.getTarget() == null ? Face.CALM : Face.ANNOYED;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FACE, (byte) Face.CALM.ordinal());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("MinionsSummoned", this.minionsSummoned);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.minionsSummoned = tag.getBoolean("MinionsSummoned");
    }

    /** 被打中之后立刻记仇，与距离无关 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide() && source.getEntity() instanceof LivingEntity attacker
            && this.canAttack(attacker) && !this.isAlliedTo(attacker)) {
            this.setTarget(attacker);
        }
        return hurt;
    }

    @SuppressWarnings("deprecation")
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty, MobSpawnType spawnType,
        @Nullable SpawnGroupData spawnGroupData
    ) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        if (spawnType != MobSpawnType.COMMAND) {
            // 裙摆在脚下 3 格，抬起来才不会埋进地里；/summon 的坐标由玩家自己写，不动
            this.setPos(this.getX(), this.getY() + SPAWN_LIFT, this.getZ());
        }
        return data;
    }

    /**
     * 自然生成判定：和平难度不刷；自然生成时再问 {@link GhastElitePopulation}，加载范围内已有一只就拒绝
     */
    public static boolean checkGhastEliteSpawnRules(
        EntityType<GhastEliteEntity> type, ServerLevelAccessor level, MobSpawnType spawnType,
        BlockPos pos, RandomSource random
    ) {
        if (level.getDifficulty() == Difficulty.PEACEFUL
            || !checkMobSpawnRules(type, level, spawnType, pos, random)) {
            return false;
        }
        return spawnType != MobSpawnType.NATURAL || GhastElitePopulation.tryReserve(level.getLevel());
    }

    /** 火球出膛点：脸前 {@link #MOUTH_FORWARD} 格、脸中心高度 {@link #MOUTH_HEIGHT} */
    public static Vec3 mouthPosition(GhastEliteEntity elite) {
        Vec3 view = elite.getViewVector(1.0F);
        return new Vec3(
            elite.getX() + view.x * MOUTH_FORWARD,
            elite.getY() + MOUTH_HEIGHT,
            elite.getZ() + view.z * MOUTH_FORWARD);
    }

    /**
     * 把撞上来的火球改向
     *
     * <p>改向本身在 {@link GhastDeflect}，这里只交出本体的 {@link #DEFLECT_ORIGIN} 并补一声
     */
    public void deflectFireball(LargeFireball fireball) {
        GhastDeflect.deflect(this, fireball, DEFLECT_ORIGIN);
        this.playVoice(ModSounds.GHAST_UNIVERSAL_DEFLECT.get());
    }

    /** 撞击落在大恶魂身上时把火球接住，见 {@link GhastDeflect#handleImpact} */
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        GhastDeflect.handleImpact(event, GhastEliteEntity.class, GhastEliteEntity::deflectFireball);
    }

    private void playVoice(SoundEvent sound) {
        GhastVoice.playVoice(this, sound);
    }

    /**
     * 一阵泪滴
     *
     * <p>范围照暮色恶魂：自己的碰撞箱向下 16 格、竖直再放大 16 格；玩家必须站在能看见天光的位置
     * 才会被砸到。范围内还活着的迷你恶魂会被吸收：治疗自己 {@value #TEAR_HEAL} 点，然后消失
     */
    private void doTearRain() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        // 暮色恶魂的 doTantrumDamageEffects：自己的碰撞箱向下 16 格、竖直再放大 16 格，
        // 水平方向不放大；站在天光下的玩家被砸 3 点
        AABB below = this.getBoundingBox().move(0.0D, -TEAR_DROP, 0.0D).inflate(0.0D, TEAR_DROP, 0.0D);
        DamageSource tear = this.tearDamage();
        for (Player player : level.getEntitiesOfClass(Player.class, below)) {
            if (level.canSeeSkyFromBelowWater(player.blockPosition())) {
                player.hurt(tear, TEAR_DAMAGE);
            }
        }
        // 同一处的小鬼被泪滴往上顶（暮色恶魂的原样），吸收是另一条：贴身才算
        EntityType<?> ghastling = BuiltInRegistries.ENTITY_TYPE.get(GHASTLING);
        if (ghastling != null) {
            for (Entity minion : level.getEntitiesOfClass(Entity.class, below, entity -> entity.getType() == ghastling)) {
                minion.push(0.0D, 1.0D, 0.0D);
            }
        }
        level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_WATER,
            this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
            32, this.getBbWidth() * 0.75D, this.getBbHeight() * 0.5D, this.getBbWidth() * 0.75D, 0.02D);
    }

    /**
     * 贴身的小鬼被吸收：暮色恶魂在 {@code customServerAiStep} 里每 tick 检查自己的箱子放大 1 格，
     * 命中的小鬼放一次生成动画后 discard，本体回 2 点
     */
    private void absorbTouchingMinions() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        EntityType<?> ghastling = BuiltInRegistries.ENTITY_TYPE.get(GHASTLING);
        if (ghastling == null) {
            return;
        }
        for (Entity minion : level.getEntitiesOfClass(Entity.class,
            this.getBoundingBox().inflate(1.0D), entity -> entity.getType() == ghastling)) {
            minion.discard();
            this.heal(TEAR_HEAL);
        }
    }

    private DamageSource tearDamage() {
        if (this.level() instanceof ServerLevel level
            && level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(GHAST_TEAR).isPresent()) {
            return this.damageSources().source(GHAST_TEAR, this);
        }
        return this.damageSources().mobAttack(this);
    }

    /** 一次性技能：在自身 24 格内召一批迷你恶魂，并把当前索敌共享给它们 */
    private boolean summonMinions() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        EntityType<?> ghastling = BuiltInRegistries.ENTITY_TYPE.get(GHASTLING);
        if (ghastling == null) {
            return false;
        }
        LivingEntity target = this.getTarget();
        int spawned = 0;
        for (int i = 0; i < MINION_COUNT; i++) {
            Entity minion = ghastling.create(level);
            if (minion == null) {
                continue;
            }
            double angle = this.getRandom().nextDouble() * Mth.TWO_PI;
            double distance = MINION_MIN_RADIUS
                + this.getRandom().nextDouble() * (MINION_MAX_RADIUS - MINION_MIN_RADIUS);
            double x = this.getX() + Math.cos(angle) * distance;
            double z = this.getZ() + Math.sin(angle) * distance;
            double y = this.getY() + this.getBbHeight() * 0.5D + (this.getRandom().nextDouble() - 0.5D) * 12.0D;
            minion.moveTo(x, y, z, this.getRandom().nextFloat() * 360.0F, 0.0F);
            // 别开在树干或地里：被卡住的小鬼几 tick 就闷死了
            for (int attempt = 0; attempt < 8 && !level.noCollision(minion); attempt++) {
                y += 2.0D;
                minion.moveTo(x, y, z, minion.getYRot(), 0.0F);
            }
            if (minion instanceof Mob mob) {
                mob.setPersistenceRequired();
                if (target != null && target.isAlive()) {
                    mob.setTarget(target);
                }
            }
            level.addFreshEntity(minion);
            spawned++;
        }
        if (spawned > 0) {
            this.playVoice(ModSounds.GHAST_UNIVERSAL_CHARGE.get());
        }
        return spawned > 0;
    }

    /** 把索敌再共享一次：身边 32 格内还没目标的迷你恶魂也来打同一个 */
    private void shareTarget() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            return;
        }
        EntityType<?> ghastling = BuiltInRegistries.ENTITY_TYPE.get(GHASTLING);
        if (ghastling == null) {
            return;
        }
        for (Entity entity : this.level().getEntitiesOfClass(Entity.class,
            this.getBoundingBox().inflate(32.0D), entity -> entity.getType() == ghastling)) {
            if (entity instanceof Mob mob && mob.getTarget() == null) {
                mob.setTarget(target);
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ghast_elite_idle", 0,
            state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /**
     * 原版 {@code Ghast.RandomFloatAroundGoal}：每 16 格内的一个随机点为下一段路的终点
     *
     * <p>移动行为照抄原版恶魂；只把 {@code canReach} 的整条直线预检去掉了 —— 这只 8 格见方的箱子
     * 在树林里几乎找不到一条十几格无阻的直线，留着它就永远停在原地
     */
    private static final class RandomFloatAroundGoal extends Goal {
        private final GhastEliteEntity ghast;

        private RandomFloatAroundGoal(GhastEliteEntity ghast) {
            this.ghast = ghast;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MoveControl control = this.ghast.getMoveControl();
            if (!control.hasWanted()) {
                return true;
            }
            double x = control.getWantedX() - this.ghast.getX();
            double y = control.getWantedY() - this.ghast.getY();
            double z = control.getWantedZ() - this.ghast.getZ();
            double distance = x * x + y * y + z * z;
            return distance < 1.0D || distance > 3600.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            RandomSource random = this.ghast.getRandom();
            double x = this.ghast.getX() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);
            double y = this.ghast.getY() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);
            double z = this.ghast.getZ() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);
            this.ghast.getMoveControl().setWantedPosition(x, y, z, 1.0D);
        }
    }

    /** 原版 {@code Ghast.GhastLookGoal}：没有索敌时朝着飞行方向，有索敌时朝着目标（64 格内） */
    private static final class EliteLookGoal extends Goal {
        private final GhastEliteEntity ghast;

        private EliteLookGoal(GhastEliteEntity ghast) {
            this.ghast = ghast;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.ghast.getTarget() == null) {
                Vec3 movement = this.ghast.getDeltaMovement();
                this.ghast.setYRot(-((float) Mth.atan2(movement.x, movement.z)) * (180.0F / (float) Math.PI));
                this.ghast.yBodyRot = this.ghast.getYRot();
            } else {
                LivingEntity target = this.ghast.getTarget();
                if (target.distanceToSqr(this.ghast) < 4096.0D) {
                    double x = target.getX() - this.ghast.getX();
                    double z = target.getZ() - this.ghast.getZ();
                    this.ghast.setYRot(-((float) Mth.atan2(x, z)) * (180.0F / (float) Math.PI));
                    this.ghast.yBodyRot = this.ghast.getYRot();
                }
            }
        }
    }

    /**
     * 一轮三发，左右各偏 {@value #FIREBALL_SPREAD_DEGREES} 度
     *
     * <p>节奏、蓄力与视线判定都在共用的 {@link GhastShootGoal} 里；这里只管把三发摆开
     */
    private void fireVolley(LivingEntity target, Vec3 muzzle) {
        Level level = this.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 aim = target.getEyePosition().subtract(muzzle);
        if (aim.lengthSqr() < 1.0E-6D) {
            return;
        }
        aim = aim.normalize();
        Vec3 right = new Vec3(-aim.z, 0.0D, aim.x);
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        }
        right = right.normalize();
        for (int i = 0; i < FIREBALL_COUNT; i++) {
            double spread = Math.toRadians(FIREBALL_SPREAD_DEGREES * (i - 1));
            Vec3 direction = aim.yRot((float) spread);
            // 三发沿射线拉开 2 格、左右再错开 1.2 格：出膛时彼此至少隔 1 格，
            // 加上 canHitEntity 不认弹体，不会再出现「三发互相引爆」
            Vec3 spawn = muzzle
                .add(aim.scale(i * 2.0D))
                .add(right.scale((i - 1) * 1.2D));
            GhastEliteFireball fireball = new GhastEliteFireball(level, this, direction);
            fireball.setPos(spawn.x, spawn.y, spawn.z);
            serverLevel.addFreshEntity(fireball);
        }
    }

    /**
     * 暮色森林的 {@code NoClipMoveControl}：每 2~6 tick 沿目标方向推 0.1 的加速度，
     * 不做碰撞预检也不做碰撞（实体本身 {@code noPhysics}）
     */
    private static final class NoClipMoveControl extends MoveControl {
        private final GhastEliteEntity ghast;
        private int courseChangeCooldown;

        private NoClipMoveControl(GhastEliteEntity ghast) {
            super(ghast);
            this.ghast = ghast;
        }

        @Override
        public void tick() {
            if (this.operation != MoveControl.Operation.MOVE_TO) {
                return;
            }
            double dx = this.wantedX - this.ghast.getX();
            double dy = this.wantedY - this.ghast.getY();
            double dz = this.wantedZ - this.ghast.getZ();
            if (this.courseChangeCooldown-- > 0) {
                return;
            }
            this.courseChangeCooldown += this.ghast.getRandom().nextInt(5) + 2;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 1.0E-4D) {
                this.operation = MoveControl.Operation.WAIT;
                return;
            }
            this.ghast.setDeltaMovement(this.ghast.getDeltaMovement()
                .add(dx / distance * 0.1D, dy / distance * 0.1D, dz / distance * 0.1D));
        }
    }
}
