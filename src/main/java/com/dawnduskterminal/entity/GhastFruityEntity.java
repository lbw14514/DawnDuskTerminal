package com.dawnduskterminal.entity;

import com.dawnduskterminal.item.FireJetProcess;
import com.dawnduskterminal.item.GhastTrapProcess;
import com.dawnduskterminal.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import twilightforest.init.TFBlocks;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * 「恶果」：飘在原维度白桦林里的恶魂变体，行为照抄原版恶魂，只有挨火球这一条不同
 *
 * <h2>为什么是 {@link Ghast} 的子类，却又自己注册了一遍目标</h2>
 * 承袭 {@code Ghast} 才能拿到它的同步数据（{@code DATA_IS_CHARGING}）、爆炸威力、和平难度下的消失规则
 * 与「被反射火球打中即死」的判定，模型的三张脸也正是照它那一套状态切换的（见 {@link #currentFace()}）
 *
 * <p>但原版恶魂的四个目标都是 {@code Ghast} 的私有内部类，访问不到；而它们放的音效写死在
 * {@code levelEvent(1015 / 1016)} 里（客户端 {@code LevelRenderer} 直接播
 * {@code SoundEvents.GHAST_WARN} / {@code GHAST_SHOOT}，模组没有任何钩子），
 * 覆写 {@code getAmbientSound} 也换不掉这两声。所以这里把四个目标逐行搬过来，
 * 只把两处 {@code levelEvent} 换成模型包自带的 {@code charge} 与 {@code fireball4}；
 * 移动加速度、蓄力 20 tick、索敌判定全部与原版逐字一致，只有两处按这只生物的体型改过：
 * 索敌距离与过滤条件（见 {@link #registerGoals()} 与 {@link #hurt}）、出膛位置
 * （见 {@link #mouthPosition}，原版那 4 格是给 4 格见方的恶魂写的）
 *
 * <h2>被打中时：火球不炸，而是被拨走</h2>
 * 原版火球撞到生物时先 {@code onHitEntity} 打 6 点伤害，再无条件在自身位置爆炸并消失。
 * 想「把这一发拨走」必须赶在爆炸之前让撞击根本不发生 —— 见 {@link #onProjectileImpact}：
 * 取消 {@code ProjectileImpactEvent}，然后把火球挪到恶果身外、按当前索敌重新给方向。
 * 若此时没有索敌，就沿来路原样弹回去
 */
public class GhastFruityEntity extends Ghast implements GeoEntity {
    /** 模型包只有一条循环动画，布条与根节点一直在晃 */
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("parallel0");

    /**
     * 拨出去的火球出现在恶果身外多远
     *
     * <p>碰撞箱照几何里的 {@code Root_spin_M}：24 单位见方，即 1.5 格，半对角线约 1.30 格。
     * 取 2 格才能保证任何方向（含正下方）都落在箱外，否则下一 tick 火球又会撞上恶果自己，
     * 形成无限反弹
     */
    private static final double DEFLECT_ORIGIN = 2.0D;

    /**
     * 火球出膛的位置：脸前多远
     *
     * <p>几何的正面（北面）在体中心前 12 单位，即 0.75 格；火球自己 1 格见方（半径 0.5），
     * 所以球心放在 0.75 + 0.55 处，球口正好贴着脸。原版恶魂是 4 格见方的巨物，它那一行写的是 4 格，
     * 直接照抄会把球吐到离这只 1.5 格生物三个多格远的半空里
     */
    private static final double MOUTH_FORWARD = 1.3D;

    /** 脸的中心：{@code Root_spin_M} 立方体从 0 到 24 单位，中心在 12 单位 = 0.75 格 */
    private static final double MOUTH_HEIGHT = 0.75D;

    /**
     * 模型那三张脸的状态
     *
     * <p>必须同步：{@code getTarget()} 只在服务端有值，{@code Mob} 也从不把目标同步给客户端
     * （同步的只有 {@code DATA_MOB_FLAGS_ID} 里那一位「激进」，原版没有任何地方写它），
     * 所以客户端要知道「这只恶果现在盯上谁了」只能靠自己的数据位
     */
    private static final EntityDataAccessor<Byte> DATA_FACE = SynchedEntityData.defineId(
        GhastFruityEntity.class, EntityDataSerializers.BYTE);

    /** 平静 / 有索敌 / 蓄力中，顺序与 {@link #DATA_FACE} 里存的序号一致 */
    public enum Face {
        CALM,
        ANNOYED,
        ATTACK
    }

    /**
     * 变体手里攥着的方块。{@code NONE} 就是普通恶果
     *
     * <p>三个方块都是暮色森林的注册项，直接引用 {@code TFBlocks} 的 {@code DeferredBlock}：
     * 升级暮色森林时方块改名在编译期就会报错，不必等到实机渲染
     */
    public enum HeldBlock {
        NONE(null),
        /** 重现方块：近战，受击时有四成概率整只消失一下 */
        REAPPEARING_BLOCK(TFBlocks.REAPPEARING_BLOCK),
        /** 封装射焰方块：按 043 的喷火打 */
        ENCASED_FIRE_JET(TFBlocks.ENCASED_FIRE_JET),
        /** 恶魂陷阱：按 066 的场打 */
        GHAST_TRAP(TFBlocks.GHAST_TRAP);

        @Nullable
        private final DeferredBlock<?> block;

        HeldBlock(@Nullable DeferredBlock<?> block) {
            this.block = block;
        }

        /** 手里那块方块；{@link #NONE} 没有 */
        @Nullable
        public Block block() {
            return this.block == null ? null : this.block.get();
        }

        public boolean isVariant() {
            return this != NONE;
        }

        /** 手里那件物品对应哪种变体；不是那三个方块就返回 {@code null} */
        @Nullable
        public static HeldBlock fromItem(ItemStack stack) {
            if (stack.isEmpty()) {
                return null;
            }
            for (HeldBlock block : values()) {
                Block held = block.block();
                if (held != null && stack.is(held.asItem())) {
                    return block;
                }
            }
            return null;
        }
    }

    private static final HeldBlock[] HELD_BLOCKS = HeldBlock.values();

    /** 自然生成时成为持块变体的概率 */
    private static final float VARIANT_CHANCE = 0.25F;

    /**
     * 生成时抬高多少格
     *
     * <p>几何在脚下一层还有一块 {@code origin=[-12,-12,-12]}、{@code size=[24,12,24]} 的裙摆，
     * 即原点下方 12 单位 = 0.75 格；刷怪蛋与自然生成都按「箱子底贴地」放，不抬这一下会埋掉半只
     */
    private static final double SPAWN_LIFT = 0.75D;

    /** 重现方块变体受击时整只消失的概率，以及那次隐身持续多久（5 秒） */
    private static final float VANISH_CHANCE = 0.4F;
    private static final int VANISH_INVISIBLE_TICKS = 100;

    /** 变体同步的持有方块，序号与 {@link HeldBlock} 一致 */
    private static final EntityDataAccessor<Byte> DATA_HELD_BLOCK = SynchedEntityData.defineId(
        GhastFruityEntity.class, EntityDataSerializers.BYTE);

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    public GhastFruityEntity(EntityType<? extends GhastFruityEntity> type, Level level) {
        super(type, level);
        // 30 点生命配 15 点经验，与原版恶魂「10 点生命 5 点经验」同比
        this.xpReward = 15;
        // 父类在构造函数里装的是 Ghast.GhastMoveControl，那是包私有的内部类，只能换成自己的副本
        this.moveControl = new RandomFloatMoveControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 生命值 30，索敌 24 格（正常的实体索敌距离，不再是原版恶魂的 100 格）
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 30.0D)
            .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    /**
     * 自然生成时按 {@value #VARIANT_CHANCE} 的概率变成持块变体，持哪种方块三等概率随机
     *
     * <p>只有 {@code NATURAL} 会掷这一下：刷怪蛋、刷怪笼、{@code /summon} 拿到的是普通恶果，
     * 想要变体就写 NBT（{@code HeldBlock:1/2/3}）或让游戏自己刷
     */
    @SuppressWarnings("deprecation")
    @Override
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
        @Nullable SpawnGroupData spawnGroupData
    ) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        if (spawnType == MobSpawnType.NATURAL && this.getRandom().nextFloat() < VARIANT_CHANCE) {
            HeldBlock[] choices = {HeldBlock.REAPPEARING_BLOCK, HeldBlock.ENCASED_FIRE_JET, HeldBlock.GHAST_TRAP};
            this.setHeldBlock(choices[this.getRandom().nextInt(choices.length)]);
        }
        if (spawnType != MobSpawnType.COMMAND) {
            // 几何在脚下一层还有 12 单位（0.75 格）的裙摆，贴着地面放会埋掉半只；
            // 抬这么高，裙摆刚好落在地面上。/summon 除外，那个位置是玩家自己写的
            this.setPos(this.getX(), this.getY() + SPAWN_LIFT, this.getZ());
        }
        return data;
    }

    /**
     * 创造模式玩家手持那三个方块右击，把它变成对应的变体（方块不消耗）
     *
     * <p>只认创造模式：生存里手里拿着重现方块右击恶果，方块该照常摆下去
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isCreative()) {
            HeldBlock target = HeldBlock.fromItem(player.getItemInHand(hand));
            if (target != null) {
                if (!this.level().isClientSide() && this.getHeldBlock() != target) {
                    this.setHeldBlock(target);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
    }

    /**
     * 自然生成判定
     *
     * <p>先按原版 {@code Ghast#checkGhastSpawnRules} 的那两条来（非和平、落点合法），
     * 自然生成时再去问 {@link GhastFruityPopulation}：玩家加载范围里已经有
     * {@value GhastFruityPopulation#CAP} 只就不再生成。后者才是「容量」—— 原版那 1/20 只是稀罕度，
     * 靠它拦不住「一批一批地刷下去」
     *
     * <p>只拦 {@code NATURAL}：刷怪笼（{@code SPAWNER}）与刷怪蛋是玩家自己放的，不该被自然容量卡住
     */
    public static boolean checkGhastFruitySpawnRules(
        EntityType<GhastFruityEntity> type, ServerLevelAccessor level, MobSpawnType spawnType,
        BlockPos pos, RandomSource random
    ) {
        if (level.getDifficulty() == Difficulty.PEACEFUL
            || !checkMobSpawnRules(type, level, spawnType, pos, random)) {
            return false;
        }
        return spawnType != MobSpawnType.NATURAL || GhastFruityPopulation.tryReserve(level.getLevel());
    }

    @Override
    protected void registerGoals() {
        // 变体的近战 / 喷火 / 陷阱都在这一个目标里，优先级压过游荡，游荡只在没有目标时跑
        this.goalSelector.addGoal(4, new VariantCombatGoal(this));
        this.goalSelector.addGoal(5, new RandomFloatAroundGoal(this));
        this.goalSelector.addGoal(5, new VariantWanderGoal(this));
        this.goalSelector.addGoal(7, new GhastLookGoal(this));
        // 普通恶果用共用骨架（一发）；持块变体走 VariantCombatGoal，所以由 ready 挡掉
        this.goalSelector.addGoal(7, new GhastShootGoal<GhastFruityEntity>(this,
            ghast -> !ghast.isBlockVariant(), GhastFruityEntity::mouthPosition, this::fireAtTarget));
        // 正常的实体索敌：24 格内找玩家，不再叠原版恶魂那条 |Δy| <= 4 的过滤
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /**
     * 一次只生成一只
     *
     * <p>{@code NaturalSpawner} 用它卡同一批的数量（{@code EventHooks.getMaxSpawnClusterSize}），
     * 原版恶魂就是 1；「一个玩家加载范围里最多 4 只」是另一件事，归 {@link GhastFruityPopulation} 管，
     * 两者别混
     */
    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    /**
     * 被打中之后立刻记仇，与距离无关
     *
     * <p>原版 {@code HurtByTargetGoal} 的构造器只收 {@code PathfinderMob}，而恶魂是 {@code FlyingMob}，
     * 所以「谁打我我就打谁」这一条只能自己写：{@code super.hurt} 真的扣到血之后把攻击者设成目标，
     * 不看索敌距离。于是从 24 格以外射来的箭、丢来的药水同样会引来一发火球；
     * 火球打在身上不会走到这里（撞击在更早的 {@code ProjectileImpactEvent} 里被拨走了），
     * 火球的「还手」由 {@link #deflectFireball} 负责
     *
     * <p>持有重现方块的那一只另有四成概率把整次伤害吞掉：不扣血、没有击退与受击音，
     * 放一片红石粒子，并给自己 5 秒「没有粒子」的隐身（{@code visible=false}）
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (amount > 0.0F && !this.level().isClientSide()
            && this.getHeldBlock() == HeldBlock.REAPPEARING_BLOCK
            && this.getRandom().nextFloat() < VANISH_CHANCE) {
            this.vanish();
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide() && source.getEntity() instanceof LivingEntity attacker
            && this.canAttack(attacker) && !this.isAlliedTo(attacker)) {
            this.setTarget(attacker);
        }
        return hurt;
    }

    /** 重现方块那一招：一片红石粉尘，然后 5 秒无粒子隐身 */
    private void vanish() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(DustParticleOptions.REDSTONE,
                this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
                24, 0.6D, 0.6D, 0.6D, 0.0D);
        }
        this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, VANISH_INVISIBLE_TICKS, 0, false, false, false));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ghastfruity_idle", 0,
            state -> state.setAndContinue(IDLE)));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FACE, (byte) Face.CALM.ordinal());
        builder.define(DATA_HELD_BLOCK, (byte) HeldBlock.NONE.ordinal());
    }

    /** 客户端读这一位决定用哪套几何、脸前画哪个方块 */
    public HeldBlock getHeldBlock() {
        int id = this.entityData.get(DATA_HELD_BLOCK);
        return id > 0 && id < HELD_BLOCKS.length ? HELD_BLOCKS[id] : HeldBlock.NONE;
    }

    /**
     * 设置手里攥着的方块
     *
     * <p>变体的移动方式换成低空飞行，而 {@code MoveControl} 是构造期定死的字段，
     * 所以这里顺带换一份；客户端只读几何，不碰控制器
     */
    public void setHeldBlock(HeldBlock block) {
        this.entityData.set(DATA_HELD_BLOCK, (byte) block.ordinal());
        this.refreshHeldMoveControl();
    }

    public boolean isBlockVariant() {
        return this.getHeldBlock().isVariant();
    }

    private void refreshHeldMoveControl() {
        if (!this.level().isClientSide()) {
            this.moveControl = this.isBlockVariant() ? new LowFlightMoveControl(this) : new RandomFloatMoveControl(this);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("HeldBlock", (byte) this.getHeldBlock().ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HeldBlock")) {
            int id = tag.getByte("HeldBlock");
            this.setHeldBlock(id > 0 && id < HELD_BLOCKS.length ? HELD_BLOCKS[id] : HeldBlock.NONE);
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 服务端每 tick 写一次，值不变时 SynchedEntityData 不会标脏，也不会发包
        if (!this.level().isClientSide()) {
            this.entityData.set(DATA_FACE, (byte) this.currentFace().ordinal());
            this.aimAtTarget();
        }
    }

    /**
     * 有目标时把偏航与俯仰一起对准它的眼睛
     *
     * <p>原版恶魂只转偏航（它在空中，火球自己算弹道），而变体的喷火与陷阱走廊是沿着
     * {@code getViewVector} 走的，只转偏航就永远是水平的 —— 所以俯仰在这里每 tick 直接写，
     * 不依赖某个目标什么时候被调度
     */
    private void aimAtTarget() {
        LivingEntity target = this.getTarget();
        if (target == null || target.distanceToSqr(this) >= 4096.0D) {
            return;
        }
        Vec3 direction = target.getEyePosition().subtract(this.getEyePosition());
        double horizontal = direction.horizontalDistance();
        if (horizontal < 1.0E-4D && Math.abs(direction.y) < 1.0E-4D) {
            return;
        }
        this.setYRot(-((float) Mth.atan2(direction.x, direction.z)) * (180.0F / (float) Math.PI));
        this.setXRot(-((float) Mth.atan2(direction.y, horizontal)) * (180.0F / (float) Math.PI));
        this.yBodyRot = this.getYRot();
    }

    /** 客户端读的就是这一位，渲染器据此挑三张脸中的一张 */
    public Face getFace() {
        return switch (this.entityData.get(DATA_FACE)) {
            case 1 -> Face.ANNOYED;
            case 2 -> Face.ATTACK;
            default -> Face.CALM;
        };
    }

    /** 蓄力（即喷火球前最后 10 tick）用攻击脸，只是盯着目标用不悦脸，没目标用平静脸 */
    public Face currentFace() {
        if (this.isCharging()) {
            return Face.ATTACK;
        }
        return this.getTarget() != null ? Face.ANNOYED : Face.CALM;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableCache;
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
     * 拨开一发砸在自己身上的恶魂火球
     *
     * <p>有索敌就朝着索敌对象（眼睛高度）打回去；没有索敌就沿来路原样弹回，速度不去动它。
     * 两种做法都把火球的所有者改成恶果自己，于是这一发往后打中谁，都算恶果的攻击
     *
     * <p>改向本身在 {@link GhastDeflect}，这里只交出本体的 {@link #DEFLECT_ORIGIN} 并补一声
     */
    public void deflectFireball(LargeFireball fireball) {
        GhastDeflect.deflect(this, fireball, DEFLECT_ORIGIN);
        this.playVoice(ModSounds.GHAST_UNIVERSAL_DEFLECT.get());
    }

    /** 撞击落在恶果身上时把火球接住，见 {@link GhastDeflect#handleImpact} */
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        GhastDeflect.handleImpact(event, GhastFruityEntity.class, GhastFruityEntity::deflectFireball);
    }

    /**
     * 火球出膛的位置：脸的朝向前方 {@link #MOUTH_FORWARD} 格、高度取脸的中心 {@link #MOUTH_HEIGHT}
     *
     * <p>抽成静态方法是为了能直接测：出膛点写错时实机表现是「球在离身体几格远的半空里凭空出现」，
     * 光看代码很难发现
     */
    public static Vec3 mouthPosition(GhastFruityEntity ghast) {
        Vec3 view = ghast.getViewVector(1.0F);
        return new Vec3(
            ghast.getX() + view.x * MOUTH_FORWARD,
            ghast.getY() + MOUTH_HEIGHT,
            ghast.getZ() + view.z * MOUTH_FORWARD);
    }

    private void playVoice(SoundEvent sound) {
        GhastVoice.playVoice(this, sound);
    }

    /** 原版 {@code Ghast.RandomFloatAroundGoal}：每 16 格内的一个随机点为下一段路的终点 */
    private static final class RandomFloatAroundGoal extends Goal {
        private final GhastFruityEntity ghast;

        private RandomFloatAroundGoal(GhastFruityEntity ghast) {
            this.ghast = ghast;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.ghast.isBlockVariant()) {
                // 变体走 VariantWanderGoal：贴地飞行，而且卡住了要换目标
                return false;
            }
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

    /**
     * 原版 {@code Ghast.GhastLookGoal}：没索敌时朝着飞行方向，有索敌时朝着目标（64 格内）
     *
     * <p>比原版多一条<strong>俯仰</strong>：原版恶魂只转偏航（它在空中，火球自己算弹道），
     * 而这里喷火与陷阱的走廊是沿着施放者的 {@code getViewVector} 走的，只转偏航的话两者永远是水平的，
     * 站在地上的目标只能蹭到走廊边缘——所以有目标时把俯仰也对准目标的眼睛
     */
    private static final class GhastLookGoal extends Goal {
        private final GhastFruityEntity ghast;

        private GhastLookGoal(GhastFruityEntity ghast) {
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
            // 有目标时的偏航与俯仰由 GhastFruityEntity#aimAtTarget 每 tick 直接写；
            // 这里只管没有目标时朝着飞行方向
            if (this.ghast.getTarget() == null) {
                this.face(this.ghast.getDeltaMovement());
            }
        }

        /** 偏航与俯仰一起取：俯仰为正表示低头（与 {@code getViewVector} 的符号一致） */
        private void face(Vec3 direction) {
            double horizontal = direction.horizontalDistance();
            if (horizontal < 1.0E-4D && Math.abs(direction.y) < 1.0E-4D) {
                return;
            }
            this.ghast.setYRot(-((float) Mth.atan2(direction.x, direction.z)) * (180.0F / (float) Math.PI));
            this.ghast.setXRot(-((float) Mth.atan2(direction.y, horizontal)) * (180.0F / (float) Math.PI));
            this.ghast.yBodyRot = this.ghast.getYRot();
        }
    }

    /** 原版 {@code Ghast.GhastMoveControl}：每段路先攒 2~6 tick，再沿直线推 0.1 的加速度 */
    private static class RandomFloatMoveControl extends MoveControl {
        protected final GhastFruityEntity ghast;
        private int floatDuration;

        private RandomFloatMoveControl(GhastFruityEntity ghast) {
            super(ghast);
            this.ghast = ghast;
        }

        @Override
        public void tick() {
            if (this.operation == MoveControl.Operation.MOVE_TO) {
                if (this.floatDuration-- <= 0) {
                    this.floatDuration = this.floatDuration + this.ghast.getRandom().nextInt(5) + 2;
                    this.step(this.wantedY);
                }
            }
        }

        /** 朝 (wantedX, targetY, wantedZ) 推一步；子类只换高度 */
        protected void step(double targetY) {
            Vec3 offset = new Vec3(
                this.wantedX - this.ghast.getX(),
                targetY - this.ghast.getY(),
                this.wantedZ - this.ghast.getZ());
            double length = offset.length();
            offset = offset.normalize();
            if (this.canReach(offset, Mth.ceil(length))) {
                this.ghast.setDeltaMovement(this.ghast.getDeltaMovement().add(offset.scale(0.1D)));
            } else {
                this.operation = MoveControl.Operation.WAIT;
            }
        }

        private boolean canReach(Vec3 direction, int steps) {
            AABB box = this.ghast.getBoundingBox();
            for (int i = 1; i < steps; i++) {
                box = box.move(direction);
                if (!this.ghast.level().noCollision(this.ghast, box)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 变体的低空飞行：水平照原样推 0.1 的加速度，高度不去看目标给的 Y，而是压在目标柱地表上方
     * {@value #LOW_FLIGHT_HEIGHT} 格
     *
     * <p>这就是「像蜜蜂那样贴着地面飞」的全部内容——移动速度本身没有改动（加速度仍是 0.1），
     * 变的只是高度
     *
     * <p><b>不做整条直线的碰撞预检</b>：原版恶魂的 {@code canReach} 会沿直线一格一格试 1.5 格见方的箱子，
     * 在林子里一条十几格的直线几乎必然撞上树干或枝叶，预检通不过就永远停在原地（上一版的症状）。
     * 这里直接推加速度，撞上东西交给实体自己的移动去滑动
     */
    private static final class LowFlightMoveControl extends RandomFloatMoveControl {
        /** 贴地高度：地表往上 4 格（这个值由作者定，别再动） */
        private static final double LOW_FLIGHT_HEIGHT = 4D;

        /**
         * 临时下潜高度：{@code NaN} 表示不潜
         *
         * <p>近战变体只有降到够得着的距离以内才打得到人，而贴地高度是 4 格 ——
         * 光靠水平贴近永远差在高度上。这里允许目标把高度压下来，且只在比贴地高度更低时生效，
         * 所以游荡与远程攻击仍然停在 4 格
         */
        private double diveY = Double.NaN;

        private LowFlightMoveControl(GhastFruityEntity ghast) {
            super(ghast);
        }

        private void diveTo(double y) {
            this.diveY = y;
        }

        private void stopDive() {
            this.diveY = Double.NaN;
        }

        @Override
        protected void step(double targetY) {
            Heightmap.Types heightmap = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
            int surface = this.ghast.level().getHeight(heightmap,
                Mth.floor(this.wantedX), Mth.floor(this.wantedZ));
            double low = surface + LOW_FLIGHT_HEIGHT;
            double height = Double.isNaN(this.diveY) ? low : Math.min(low, this.diveY);
            Vec3 offset = new Vec3(
                this.wantedX - this.ghast.getX(),
                height - this.ghast.getY(),
                this.wantedZ - this.ghast.getZ());
            if (offset.lengthSqr() < 1.0E-4D) {
                this.operation = MoveControl.Operation.WAIT;
                return;
            }
            this.ghast.setDeltaMovement(this.ghast.getDeltaMovement().add(offset.normalize().scale(0.1D)));
        }
    }

    /**
     * 变体的攻击：近战、喷火、陷阱三套，共用「贴近到射程内并保持距离」的移动
     *
     * <p>喷火与陷阱直接复用 043 / 066 那两个过程：过程自己挂在 {@code ServerTickEvent.Post} 上跑，
     * 枪口与场心每 tick 重新绑到施放者的眼睛与视线，所以这里只要让这只生物盯着目标、站在射程内即可
     *
     * <p>射程内保持的距离取 {@value #HOLD_DISTANCE} 格：太远就往前压，太近就退，到了带内就不再给新目标点
     */
    private static final class VariantCombatGoal extends Goal {
        /** 近战一下 6 点，间隔 1 秒 */
        private static final float MELEE_DAMAGE = 6.0F;
        private static final int MELEE_COOLDOWN = 20;
        /** 近战够得着的距离：1.5 格碰撞箱的一半再加一点余量 */
        private static final double MELEE_REACH = 2.2D;
        /** 喷火 / 陷阱远程攻击时保持的距离带 */
        private static final double HOLD_DISTANCE = 9.0D;
        /** 一次过程结束之后歇多久再开下一次 */
        private static final int ATTACK_REST = 60;

        private final GhastFruityEntity ghast;
        private int meleeCooldown;
        private int processCooldown;

        private VariantCombatGoal(GhastFruityEntity ghast) {
            this.ghast = ghast;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.ghast.isBlockVariant() && this.ghast.getTarget() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = this.ghast.getTarget();
            if (target == null || !target.isAlive()) {
                return;
            }
            if (this.meleeCooldown > 0) {
                this.meleeCooldown--;
            }
            if (this.processCooldown > 0) {
                this.processCooldown--;
            }

            double distance = this.ghast.distanceTo(target);
            switch (this.ghast.getHeldBlock()) {
                case REAPPEARING_BLOCK -> this.melee(target, distance);
                case ENCASED_FIRE_JET -> this.ranged(target, distance, FireJetProcess.LENGTH, FireJetProcess.TOTAL_TICKS);
                case GHAST_TRAP -> this.ranged(target, distance, GhastTrapProcess.LENGTH, GhastTrapProcess.TOTAL_TICKS);
                default -> { }
            }
        }

        private void melee(LivingEntity target, double distance) {
            // 贴地高度 4 格里近战永远够不着：打到人之前先把高度压到目标脚下 + 裙摆那 0.75 格
            this.dive(target);
            if (distance > MELEE_REACH) {
                this.keepDistance(target, MELEE_REACH - 0.2D);
                return;
            }
            if (this.meleeCooldown <= 0 && this.ghast.hasLineOfSight(target)) {
                target.hurt(this.ghast.damageSources().mobAttack(this.ghast), MELEE_DAMAGE);
                this.meleeCooldown = MELEE_COOLDOWN;
            }
        }

        private void ranged(LivingEntity target, double distance, double range, int processTicks) {
            this.stopDive();
            if (distance > range - 1.0D) {
                this.keepDistance(target, Math.min(range, 12.0D));
            } else if (distance < HOLD_DISTANCE) {
                this.keepDistance(target, HOLD_DISTANCE + 2.0D);
            }

            if (distance <= range && this.processCooldown <= 0 && this.ghast.hasLineOfSight(target)) {
                if (this.ghast.level() instanceof ServerLevel level) {
                    if (this.ghast.getHeldBlock() == HeldBlock.ENCASED_FIRE_JET) {
                        FireJetProcess.start(level, this.ghast);
                    } else {
                        GhastTrapProcess.start(level, this.ghast);
                    }
                    this.processCooldown = processTicks + ATTACK_REST;
                }
            }
        }

        /** 让低空控制器把高度压到「目标脚下 + 裙摆」，只在贴地高度之下才生效 */
        private void dive(LivingEntity target) {
            if (this.ghast.getMoveControl() instanceof LowFlightMoveControl lowFlight) {
                lowFlight.diveTo(target.getY() + SPAWN_LIFT);
            }
        }

        private void stopDive() {
            if (this.ghast.getMoveControl() instanceof LowFlightMoveControl lowFlight) {
                lowFlight.stopDive();
            }
        }

        @Override
        public void stop() {
            this.stopDive();
        }

        /** 沿着当前「目标 → 自己」的方向，把落点摆到离目标 {@code distance} 格处 */
        private void keepDistance(LivingEntity target, double distance) {
            Vec3 offset = this.ghast.position().subtract(target.position());
            double length = offset.length();
            if (length < 1.0E-4D) {
                return;
            }
            Vec3 point = target.position().add(offset.scale(distance / length));
            this.ghast.getMoveControl().setWantedPosition(point.x, point.y, point.z, 1.0D);
        }
    }

    /**
     * 变体的游荡：和原版 {@code RandomFloatAroundGoal} 不同，它跑起来是持续的，并且会换目标
     *
     * <p>原版那一条每次只给一个点就结束，之后靠 {@code canUse} 的「距离不在 1~60 格」再触发；
     * 低空飞行贴着地面走，很容易被树干挡住而一直够不到那个点，于是永远不再换目标、整只停在原地。
     * 这里每 40~80 tick 换一个 6 格内的点，并且连续 30 tick 几乎没挪动（被挡住）时立刻换一个
     */
    private static final class VariantWanderGoal extends Goal {
        private static final double WANDER_RADIUS = 6.0D;
        private static final int PICK_INTERVAL_MIN = 40;
        private static final int PICK_INTERVAL_MAX = 80;
        private static final int STUCK_TICKS = 30;

        private final GhastFruityEntity ghast;
        private int ticksUntilPick;
        private int stuck;
        private Vec3 lastPosition = Vec3.ZERO;

        private VariantWanderGoal(GhastFruityEntity ghast) {
            this.ghast = ghast;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.ghast.isBlockVariant() && this.ghast.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.ticksUntilPick = 0;
            this.stuck = 0;
            this.lastPosition = this.ghast.position();
            // 打完架回来游荡：把近战那段下潜取消，重新升回贴地高度
            if (this.ghast.getMoveControl() instanceof LowFlightMoveControl lowFlight) {
                lowFlight.stopDive();
            }
        }

        @Override
        public void tick() {
            Vec3 position = this.ghast.position();
            if (position.distanceToSqr(this.lastPosition) < 0.0025D) {
                this.stuck++;
            } else {
                this.stuck = 0;
            }
            this.lastPosition = position;

            if (--this.ticksUntilPick <= 0 || this.stuck > STUCK_TICKS) {
                this.pick();
            }
        }

        private void pick() {
            RandomSource random = this.ghast.getRandom();
            double x = this.ghast.getX() + (double) ((random.nextFloat() * 2.0F - 1.0F) * WANDER_RADIUS);
            double z = this.ghast.getZ() + (double) ((random.nextFloat() * 2.0F - 1.0F) * WANDER_RADIUS);
            // 高度交给 LowFlightMoveControl 压到地表上方，这里给当前高度即可
            this.ghast.getMoveControl().setWantedPosition(x, this.ghast.getY(), z, 1.0D);
            this.ticksUntilPick = PICK_INTERVAL_MIN + random.nextInt(PICK_INTERVAL_MAX - PICK_INTERVAL_MIN + 1);
            this.stuck = 0;
        }
    }

    /**
     * 一轮一发：瞄准目标身体中段，从出膛点放出，爆炸威力沿用 {@code Ghast} 的那一份
     *
     * <p>瞄准向量从出膛点算起，否则球会从脸前出现却朝另一个方向飞
     */
    private void fireAtTarget(LivingEntity target, Vec3 muzzle) {
        Level level = this.level();
        Vec3 aim = new Vec3(
            target.getX() - muzzle.x,
            target.getY(0.5D) - muzzle.y,
            target.getZ() - muzzle.z);
        LargeFireball fireball =
            new LargeFireball(level, this, aim.normalize(), this.getExplosionPower());
        fireball.setPos(muzzle.x, muzzle.y, muzzle.z);
        level.addFreshEntity(fireball);
    }
}
