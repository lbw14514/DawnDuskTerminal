package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModSounds;
import java.util.EnumSet;
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
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import twilightforest.entity.monster.CarminiteGhastling;
import twilightforest.init.TFDamageTypes;
import twilightforest.init.TFEntities;
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

    /** 掉落经验：与暮色恶魂的 {@code UrGhast} 同一个数 */
    public static final int XP_REWARD = 317;

    /**
     * 被打上这个标记的小鬼是大恶魂召的
     *
     * <p>{@code mixin/GhastlingShouldAttackMixin} 靠它放开暮色森林那条「玩家要看着我才打」的守卫规则；
     * 野生的砷铅铁恶魂守卫没有这个标记，行为不变
     */
    public static final String MINION_TAG = "dawnduskterminal:elite_minion";

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

    /** 主立方体 {@code thehead} 的半宽：128 单位见方 = 8 格，半宽 4 格 */
    private static final double MOUTH_HALF_WIDTH = 4.0D;

    /** 火球半径 0.5 格：出膛点要通过半径，否则球会一半埋在自己脸上 */
    private static final double FIREBALL_RADIUS = 0.5D;

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

    /** 召唤半径：8~24 格；同时也是小鬼被拽回来的距离 */
    private static final double MINION_MIN_RADIUS = 8.0D;
    private static final double MINION_MAX_RADIUS = 24.0D;

    /** 每隔这么多 tick 补一次索敌、并给没事干的小鬼指一次回家的路 */
    private static final int MINION_TEND_INTERVAL = 20;

    /**
     * 小鬼待在本体身边多远
     *
     * <p>必须大于泪滴柱子的半宽（本体 8 格宽，半宽 4 格），否则它一靠近就被泪滴吸收
     */
    private static final double MINION_HOLD_DISTANCE = 6.0D;

    /** 照看范围：再远的小鬼直接放弃，交给它自己 */
    private static final double MINION_TEND_RADIUS = 48.0D;

    /** 一轮喷三发 */
    private static final int FIREBALL_COUNT = 3;
    /** 三发之间左右各偏 8 度 */
    private static final float FIREBALL_SPREAD_DEGREES = 8.0F;

    /** 暮色森林的泪滴伤害类型；缺暮色森林时退回普通生物伤害 */


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
        // 暮色恶魂的 317 点经验，见 {@code UrGhast} 的 constructor
        this.xpReward = XP_REWARD;
        // 仍然吃碰撞：{@code noPhysics} 的实体不被任何弹体命中（见
        // {@code AbstractHurtingProjectile#canHitEntity}），拔弹会失效，而且不吃碰撞就会穿到地面以下。
        // 移动这一侧不做整条直线的碰撞预检：8 格见方的箱子在树林里找不到一条无障碍的直线，
        // 撞上东西交给实体自己的移动去滑
        this.moveControl = new EliteMoveControl(this);
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

    /**
     * 交战中不自然消失
     *
     * <p>原版 {@code Mob#checkDespawn} 有两条：离最近玩家超过 128 格直接删；离玩家超过 32 格、
     * 且 {@code noActionTime > 600}（30 秒）时每 tick 有 1/800 概率删。{@code noActionTime} 只在
     * 「贴近到 32 格以内」时清零，而这只从不靠近目标（飘着随机飞、只在远处喷火），
     * 于是战斗时永远停在第二条上，半分钟后大概率凭空消失。
     *
     * <p>有索敌期间一律按持久处理；脱战之后索敌会在 100 格外被 {@code TargetGoal} 放掉，
     * 那时恢复原版规则，不会永久占着 {@link GhastElitePopulation} 的那一个名额
     */
    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.getTarget() != null;
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
        if (this.tickCount % MINION_TEND_INTERVAL == 0) {
            this.tendMinions();
        }
        if (!this.minionsSummoned && this.getTarget() != null) {
            // 只有真的放出小鬼才算用掉这次技能
            this.minionsSummoned = this.summonMinions();
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

    /**
     * 火球出膛的位置：脸前 {@link #MOUTH_FORWARD} 格、高度取脸的中心
     *
     * <p>脸的正面是立方体的一个面，斜着飞时这条线会在 <strong>角的内部</strong> 穿出去：
     * 半宽 4 格的箱子斜 45 度时出膛点会落在 (3.25, 3.25)，还在箱子里。
     * 所以斜飞时把距离放大到 {@code (半宽 + 火球半径) / 水平分量}，正对时仍是 {@link #MOUTH_FORWARD}
     */
    public static Vec3 mouthPosition(GhastEliteEntity elite) {
        Vec3 view = elite.getViewVector(1.0F);
        double horizontal = Math.max(Math.abs(view.x), Math.abs(view.z));
        double forward = horizontal < 1.0E-4D
            ? MOUTH_FORWARD
            : Math.max(MOUTH_FORWARD, (MOUTH_HALF_WIDTH + FIREBALL_RADIUS) / horizontal);
        return new Vec3(
            elite.getX() + view.x * forward,
            elite.getY() + MOUTH_HEIGHT,
            elite.getZ() + view.z * forward);
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
     * 才会被砸到。落进这片泪滴里的迷你恶魂会被吸收：治疗自己 {@value #TEAR_HEAL} 点，然后消失
     */
    /**
     * 泪滴的范围
     *
     * <p>照暮色恶魂 {@code doTantrumDamageEffects} 的公式：自己的碰撞箱向下 16 格、竖直再放大 16 格，
     * 水平方向不放大，也就是只有自己那 8 格见方的一条柱子
     */
    public AABB tearField() {
        return this.getBoundingBox()
            .move(0.0D, -TEAR_DROP, 0.0D)
            .inflate(0.0D, TEAR_DROP, 0.0D);
    }

    private void doTearRain() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        AABB below = this.tearField();
        DamageSource tear = this.tearDamage();
        for (Player player : level.getEntitiesOfClass(Player.class, below)) {
            if (level.canSeeSkyFromBelowWater(player.blockPosition())) {
                player.hurt(tear, TEAR_DAMAGE);
            }
        }
        // 泪滴落到小鬼身上就把它吸收掉：治疗本体 {@value #TEAR_HEAL} 点，然后消失
        for (CarminiteGhastling minion : level.getEntitiesOfClass(CarminiteGhastling.class, below)) {
            minion.discard();
            this.heal(TEAR_HEAL);
        }
        level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_WATER,
            this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
            32, this.getBbWidth() * 0.75D, this.getBbHeight() * 0.5D, this.getBbWidth() * 0.75D, 0.02D);
    }

    private DamageSource tearDamage() {
        return this.damageSources().source(TFDamageTypes.GHAST_TEAR, this);
    }

    /** 一次性技能：在自身 24 格内召一批迷你恶魂，并把当前索敌共享给它们 */
    private boolean summonMinions() {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        LivingEntity target = this.getTarget();
        int spawned = 0;
        for (int i = 0; i < MINION_COUNT; i++) {
            CarminiteGhastling minion = TFEntities.CARMINITE_GHASTLING.get().create(level);
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
            // 不设 persistence：与暮色森林的 boss 小鬼一样（TF 的 makeBossMinion 也不设），
            // 战斗结束后玩家走远就自然消失，不会打一次攒一堆
            if (target != null && target.isAlive()) {
                minion.setTarget(target);
            }
            minion.getPersistentData().putBoolean(MINION_TAG, true);
            level.addFreshEntity(minion);
            spawned++;
        }
        if (spawned > 0) {
            this.playVoice(ModSounds.GHAST_UNIVERSAL_CHARGE.get());
        }
        return spawned > 0;
    }

    /**
     * 照看自己的小鬼：补索敌，没仗打的时候让它们飞回本体身边
     *
     * <p>两件事都不动它们的速度和位置，只给「下一步去哪」与「打谁」：
     *
     * <ul>
     *   <li>没有索敌的，把本体的索敌再发一次——暮色森林的 {@code NearestAttackableTargetGoal} 在看不见目标时
     *       会把 target 清成 null，所以要反复补
     *   <li>仍然没有索敌的，把移动目标指到本体身边 {@value #MINION_HOLD_DISTANCE} 格处，它们自己飞过去；
     *       有索敌的不管，交给它们自己的 AI 去打
     * </ul>
     *
     * <p>停这么远是为了不被泪滴吸收：泪滴柱子水平只有本体自己那 8 格（半宽 4 格），
     * {@value #MINION_HOLD_DISTANCE} 格在柱子外
     */
    private void tendMinions() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = this.getTarget();
        Vec3 center = this.getBoundingBox().getCenter();
        for (CarminiteGhastling minion : level.getEntitiesOfClass(CarminiteGhastling.class,
            this.getBoundingBox().inflate(MINION_TEND_RADIUS))) {
            if (minion.getTarget() == null && target != null && target.isAlive() && this.canAttack(target)) {
                minion.setTarget(target);
            }
            if (minion.getTarget() == null) {
                Vec3 hold = holdPoint(center, minion.position());
                minion.getMoveControl().setWantedPosition(hold.x, hold.y, hold.z, 1.0D);
            }
        }
    }

    /**
     * 小鬼的落脚点：本体中心朝它那一侧、水平 {@value #MINION_HOLD_DISTANCE} 格、再高一格
     *
     * <p>抽成静态方法是为了能直接测：落下距离必须大于泪滴柱子的半宽 4 格，否则它会被本体吸掉
     */
    public static Vec3 holdPoint(Vec3 eliteCenter, Vec3 minionPosition) {
        Vec3 horizontal = new Vec3(minionPosition.x - eliteCenter.x, 0.0D, minionPosition.z - eliteCenter.z);
        Vec3 direction = horizontal.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : horizontal.normalize();
        return eliteCenter.add(direction.scale(MINION_HOLD_DISTANCE)).add(0.0D, 1.0D, 0.0D);
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
            double x = 0.0D;
            double y = 0.0D;
            double z = 0.0D;
            // 原版挑点不看地形，一半的点会落在土里；8 格见方的箱子顶在那儿就再也推不动。
            // 这里最多重挑 8 次，取一个放得下的点；都不行就用最后一个，交给移动控制器自己发现卡住
            for (int attempt = 0; attempt < 8; attempt++) {
                x = this.ghast.getX() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);
                y = this.ghast.getY() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);
                z = this.ghast.getZ() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);
                AABB destination = this.ghast.getBoundingBox()
                    .move(x - this.ghast.getX(), y - this.ghast.getY(), z - this.ghast.getZ());
                if (this.ghast.level().noCollision(this.ghast, destination)) {
                    break;
                }
            }
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
     * 暮色森林 {@code NoClipMoveControl} 的推法：每 2~6 tick 沿目标方向推 0.1 的加速度
     *
     * <p>与原版恶魂的 {@code GhastMoveControl} 只差一点：不做整条直线的碰撞预检。
     * 8 格见方的箱子在树林里找不到一条十几格无障碍的直线，预检通不过就永远停在原地。
     * 实体的碰撞照常生效，撞上树干由它自己的移动去滑
     */
    private static final class EliteMoveControl extends MoveControl {
        /** 这么多 tick 几乎没挪动就算卡住 */
        private static final int STUCK_TICKS = 30;

        /** 0.05 格：贴地磨树皮时每 tick 的抖动也算「没动」 */
        private static final double STUCK_EPSILON_SQR = 0.0025D;

        private final GhastEliteEntity ghast;
        private int courseChangeCooldown;
        private Vec3 lastPosition;
        private int stuckTicks;

        private EliteMoveControl(GhastEliteEntity ghast) {
            super(ghast);
            this.ghast = ghast;
        }

        @Override
        public void tick() {
            if (this.operation != MoveControl.Operation.MOVE_TO) {
                this.lastPosition = null;
                this.stuckTicks = 0;
                return;
            }
            this.dropWaypointWhenStuck();
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

        /**
         * 一直推却推不动，就把目标点丢掉
         *
         * <p>{@link RandomFloatAroundGoal} 只在「到了」或「超过 60 格」时才换点；目标点落在
         * 地面以下时这只 8 格见方的箱子永远顶在那儿，于是它就一直贴着地面不动。
         * 丢掉之后 {@code hasWanted()} 变假，下一 tick 那条目标会自己挑一个新点
         */
        private void dropWaypointWhenStuck() {
            Vec3 current = this.ghast.position();
            if (this.lastPosition == null) {
                this.lastPosition = current;
                return;
            }
            if (current.distanceToSqr(this.lastPosition) >= STUCK_EPSILON_SQR) {
                this.lastPosition = current;
                this.stuckTicks = 0;
                return;
            }
            if (++this.stuckTicks > STUCK_TICKS) {
                this.operation = MoveControl.Operation.WAIT;
                this.lastPosition = null;
                this.stuckTicks = 0;
            }
        }
    }
}
