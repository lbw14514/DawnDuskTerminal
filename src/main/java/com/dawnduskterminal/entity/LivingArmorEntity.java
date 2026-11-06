package com.dawnduskterminal.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import twilightforest.entity.projectile.ChainBlock;
import twilightforest.init.TFEntities;
import twilightforest.init.TFItems;
import twilightforest.init.TFSounds;

/**
 * 「活盔甲」：一具会走路的空盔甲
 *
 * <h2>本体不显示</h2>
 * 这只生物自己没有外观，看得见的只有它身上那套护甲与手上那件武器 —— 渲染侧的做法见
 * {@code client/LivingArmorRenderer}：人形骨架照常算姿势，但一个像素都不画，护甲层与手持层照常往上贴。
 * 于是它变成一副自己会动的盔甲，而不是一个穿盔甲的人
 *
 * <h2>数值：与玩家同档</h2>
 * 生命 30，移动速度与攻击距离照玩家取：
 *
 * <ul>
 *   <li>移动：属性 0.3。玩家行走是 4.317 格每秒，寻路生物的水平速度约等于「属性² × 44」格每秒
 *       （僵尸 0.23 折合 2.3，玩家自己要跑到 4.3 得给到 0.31），0.3 与之相差不到一成。没有直接写 0.1
 *       —— 那是玩家的属性值，但玩家的移动是直接输入，生物的寻路速度按平方折算，同一个数字在两边不等价
 *   <li>攻击：底数 1.0（玩家的基础攻击力），具体伤害全部来自主手那件武器的属性修饰符，
 *       所以抽到骑士剑就是骑士剑的伤害，抽到铁木锄就只有锄的伤害 —— 与原版玩家手持同一件物品时一致
 *   <li>攻击距离：3 格，即玩家的实体交互距离（{@code Player#entityInteractionRange}）
 *   <li>攻击间隔：{@code 20 / 攻击速度}，也就是玩家那把武器挥满一次的时间（骑士剑 1.6 攻速 → 13 tick）
 * </ul>
 *
 * <h2>链锤与盾牌</h2>
 * 主手是链锤（{@code twilightforest:block_and_chain}）时不再近身，改成把链锤甩出去 —— 直接造一枚
 * 暮色森林自己的 {@link ChainBlock}，所以飞行、命中、回手、磨损全是链锤本身那一套，这里只负责瞄准与冷却。
 * 副手是骑士盾牌时，对手进到近战范围（正面）或自己刚挨过打的那段时间里会举盾（{@link #updateShield}），
 * 挡不挡得住交给原版 {@code isDamageSourceBlocked}；磨损与斧头破盾照 {@code Player} 那两条写，
 * 见 {@link #hurtCurrentlyUsedShield} 与 {@link #blockUsingShield}
 */
public class LivingArmorEntity extends Monster {
    /**
     * 近战够得着的距离，玩家的实体交互距离是 3 格
     *
     * <p>比原版近战生物宽（僵尸约 2.5）：这条生物的攻击方式本来就该按玩家算
     */
    private static final double MELEE_REACH = 3.0D;

    /**
     * 链锤的投掷距离上限
     *
     * <p>取 16 是因为 {@code ChainBlock} 自己就在离主人 16 格时强制回手（{@code MAX_CHAIN}）：
     * 超过这个距离扔出去，锤子飞到一半就会往回走，够不到人
     */
    private static final double FLAIL_RANGE = 16.0D;

    /** 拿链锤时要保持的最小距离：贴脸扔锤子看不出「甩」这个动作，先退开再扔 */
    private static final double FLAIL_MIN_RANGE = 4.0D;

    /** 链锤初速（格 / tick），与玩家扔出去时同一个 1.5 一档略高一点，补偿生物没有玩家的抬枪补偿 */
    private static final float FLAIL_VELOCITY = 2.0F;

    /** 链锤的重力（格 / tick²），与 {@code ChainBlock#getDefaultGravity} 同值，瞄准时用来抬高落点 */
    private static final double FLAIL_GRAVITY = 0.05D;

    /** 两次投掷之间歇多久 */
    private static final int FLAIL_COOLDOWN = 30;

    /**
     * 出手之后盾收起来多少 tick
     *
     * <p>出手的那一下不能同时举着盾，这里让它在挥击前后 {@value #SHIELD_SWING_LOWER_TICKS} tick 里
     * 既不挡、也不摆举盾姿势
     */
    public static final int SHIELD_SWING_LOWER_TICKS = 8;

    /** 斧头破盾之后封多久，与原版 {@code Player#disableShield} 的 100 tick 同一个值（5 秒） */
    public static final int SHIELD_DISABLED_TICKS = 100;

    /** 挨过一下之后盾牌至少再举多久（tick）：远处射来的箭，挨过一次之后才会举盾 */
    public static final int SHIELD_HOLD_TICKS = 60;

    /**
     * 举盾时把移动输入缩到这个比例
     *
     * <p>玩家那一侧是 {@code input * 0.2F}（{@code LocalPlayer} 695–699），但生物的「输入 → 位移」
     * 不是线性的：实测输入缩到 0.2 只剩 0.118 的位移比，反解出要取 0.3 才落在玩家举盾那两成上
     * （见 {@code LivingArmorTests#guardSpeedIsTwentyPercentOfWalk}）
     */
    public static final float GUARD_SPEED_FACTOR = 0.3F;

    /** 斧头破盾剩下的 tick；{@code 0} 表示盾牌还能用。只在服务端有意义 */
    private int shieldDisabledTicks;

    /** 「刚挨过打」的剩余 tick：这段时间里盾牌一直举着 */
    private int shieldHoldTicks;

    /** 出手之后还剩多少 tick 不举盾；{@code 0} 表示可以举 */
    private int shieldDownTicks;

    /** 当前这一下会不会被盾挡掉：只在 {@link #hurt} 调用 {@code super.hurt} 的那一小段里为真 */
    private boolean blockedThisHit;

    /**
     * 疾跑：目标拉开的距离超过这个数就开始跑
     *
     * <p>8 格是作者给的值；与近战距离 3 之间留出这段，就是「跑过去」与「贴脸」的分界
     */
    private static final double SPRINT_DISTANCE = 8.0D;

    /** 暴击倍率：与原版 {@code Player#attack} 的 1.5 一致 */
    private static final float CRIT_MULTIPLIER = 1.5F;

    /** 生命低于最大值的这个比例就算「半血以下」，行为整体换档 */
    private static final float WOUNDED_HEALTH_RATIO = 0.5F;

    /** 找靠山与找玩家的搜索半径 */
    private static final double ALLY_SEARCH_RADIUS = 16.0D;

    /** 靠山在这个距离以内就不再挪窝，直接贴上去打 */
    private static final double ALLY_KEEP_DISTANCE = 6.0D;

    /** 找靠山隔多久扫一次场：每 tick 扫一遍 16 格内的生物太贵 */
    private static final int ALLY_RESCAN_TICKS = 20;

    /** 逃跑时看多远的玩家 */
    private static final double FLEE_SEARCH_RADIUS = 16.0D;

    /** 逃跑时一次跑到离当前位置多远的地方 */
    private static final double FLEE_DISTANCE = 12.0D;

    /** 行动速度：正常追击 1.0，逃跑与追靠山快一档 */
    private static final double CHASE_SPEED = 1.0D;

    private static final double RALLY_SPEED = 1.2D;

    /** 疾跑修饰符是否挂在身上 */
    private boolean sprintCharging;

    /** 半血以下跟随的靠山；{@code null} 表示正在逃 */
    @Nullable
    private Mob ally;

    /** 距离下一次扫场还剩多少 tick */
    private int allyRescanTicks;

    public LivingArmorEntity(EntityType<? extends LivingArmorEntity> type, Level level) {
        super(type, level);
        // 30 点生命配 10 点经验，与恶果「30 点生命 15 点经验」同一比例
        this.xpReward = 10;
    }

    /**
     * 破盾的倒计时与疾跑的状态在这里走
     *
     * <p>破盾倒计时与原版玩家的物品冷却一样不持久化，读档后重新可用；疾跑在「没有目标又没受伤」时收掉，
     * 免得目标丢了之后还挂着 1.3 倍速度乱走
     */
    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            if (this.shieldDisabledTicks > 0) {
                this.shieldDisabledTicks--;
            }
            if (this.shieldHoldTicks > 0) {
                this.shieldHoldTicks--;
            }
            if (this.shieldDownTicks > 0) {
                this.shieldDownTicks--;
            }
            this.updateShield();
            if (this.isBlocking() && this.isSprintCharging()) {
                this.setSprintCharging(false);
            }
            if (this.getTarget() == null && !this.isWounded()) {
                this.setSprintCharging(false);
            }
        }
    }

    /**
     * 属性表
     *
     * <p><b>{@code ATTACK_SPEED} 必须显式加一条</b>：它不在 {@code LivingEntity.createLivingAttributes} 里
     * （原版生物打人不需要冷却，所以生物的基础表里根本没这个属性），而主手武器的攻速修饰符要靠它才生效。
     * 漏了它的症状是 {@code getAttributeValue(ATTACK_SPEED)} 直接抛
     * {@code Can't find attribute minecraft:generic.attack_speed} —— 第一次挥击就把存档崩掉，而不是数值不对
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.ATTACK_SPEED, 4.0D);
    }

    /**
     * 生成时穿装备
     *
     * <p>不看 {@link MobSpawnType}：这只生物暂时不自然生成，刷怪蛋与 {@code /summon} 是仅有的两条路，
     * 若只给 {@code NATURAL} 穿装备，从刷怪蛋放出来的会是一只空盔甲架。掉落概率沿用 {@code Mob} 的默认值
     */
    @SuppressWarnings("deprecation")
    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        LivingArmorLoadout.equip(this);
        return data;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 半血以下压过一切：逃跑，或者贴着靠山跟着打，见 WoundedGoal
        this.goalSelector.addGoal(1, new WoundedGoal(this));
        // 健康时：链锤拉开距离扔，斧头起跳劈，其余贴脸砍；追远目标时疾跑，见 ArmorAttackGoal
        this.goalSelector.addGoal(2, new ArmorAttackGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, CHASE_SPEED));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // 半血以下不主动找玩家：那时候由 WoundedGoal 决定是逃还是跟着靠山打
        this.targetSelector.addGoal(
                2, new NearestAttackableTargetGoal<>(this, Player.class, true, candidate -> !this.isWounded()));
    }

    /** 主手是不是链锤 */
    public boolean hasFlail() {
        return this.getMainHandItem().is(TFItems.BLOCK_AND_CHAIN.get());
    }

    /** 副手是不是骑士盾牌 */
    public boolean hasShield() {
        return this.getOffhandItem().is(TFItems.KNIGHTMETAL_SHIELD.get());
    }

    /**
     * 受击：盾牌判定交给原版，这里只补「挨了一下就举住盾」与记仇
     *
     * <p>挡不挡得住由 {@code LivingEntity#hurt} 里那条原版分支判（{@link #isBlocking}
     * 决定「算不算挡着」，{@code isDamageSourceBlocked} 决定方向与标签），这里不另写判定
     *
     * <p>记仇那一段：<b>不管隔多远，谁打就钉谁</b>。原版 {@code HurtByTargetGoal} 还要过一遍
     * {@code TargetingConditions}，而且在 {@code Mob.serverAiStep} 里的执行顺序排在攻击目标之后，
     * 远距离挨了一箭再「等下一 tick 的目标选择」会慢一拍。半血以下不钉：那时候该做的是逃
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (amount > 0.0F && !this.level().isClientSide()) {
            this.shieldHoldTicks = SHIELD_HOLD_TICKS;
        }
        // 这一下会不会被盾挡掉，要在 super.hurt 之前问：挡盾分支在里面跑，出去就看不到了
        this.blockedThisHit = amount > 0.0F && this.isDamageSourceBlocked(source);
        boolean hurt;
        try {
            hurt = super.hurt(source, amount);
        } finally {
            this.blockedThisHit = false;
        }
        if (hurt
                && !this.level().isClientSide()
                && !this.isWounded()
                && source.getEntity() instanceof LivingEntity attacker
                && attacker != this
                && this.canAttack(attacker)
                && !this.isAlliedTo(attacker)) {
            this.setTarget(attacker);
        }
        return hurt;
    }

    /**
     * 挡住的那一下：只有盾牌声，没有受伤音，也不吃击退
     *
     * <ol>
     *   <li>判据是 {@link #blockedThisHit}，不是 {@link #isBlocking}：前者是「这一下真的被盾挡下了」，
     *       后者只是「副手有盾且没在出手窗口」（{@code hurt} 之前置好、每次结算后即清空，
     *       见 {@link #hurt}）。背后打中、破盾那一下、斧头破盾之后都是「有盾但没挡住」，
     *       拿 {@code isBlocking} 当判据会让普通受伤音永远不播</li>
     *   <li>原版 {@code LivingEntity#hurt} 里那句 {@code playHurtSound} 是无条件的
     *       （{@code hurt} 的 1240 行，整下挡掉也照播），所以照抄过来挡住时照样「嗷」一声</li>
     *   <li>盾牌声在这里<b>由服务端播</b>（发成 {@code ClientboundSoundPacket}，声音与字幕都出来）。
     *       原版那条是广播 entity event 29 交给客户端，在这只生物上实测没有声音，见 {@link #handleEntityEvent}</li>
     * </ol>
     */
    @Override
    protected void playHurtSound(DamageSource source) {
        if (this.blockedThisHit) {
            this.playSound(
                    SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + this.level().random.nextFloat() * 0.4F);
            return;
        }
        super.playHurtSound(source);
    }

    /**
     * 接管 entity event 29：盾牌声已经由 {@link #playHurtSound} 在服务端播了，
     * 这里把原版那条客户端播放掐掉，免得同一下出两声
     */
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 29) {
            return;
        }
        super.handleEntityEvent(id);
    }

    /**
     * 挡住的那一下不吃击退
     *
     * <p>原版 {@code LivingEntity#hurt} 的击退段（1213~1228 行）不看「有没有被盾挡住」，
     * 被挡下的那一下照样走到 {@code knockback}。这里把「刚被盾挡下的那一下」吞掉：
     * {@link #blockedThisHit} 在 {@link #hurt} 调用 {@code super.hurt} 之前置好，出来即清空
     */
    @Override
    public void knockback(double strength, double x, double z) {
        if (this.blockedThisHit) {
            return;
        }
        super.knockback(strength, x, z);
    }

    /**
     * 举盾 / 收盾：只管外观与朝向，挡不挡由 {@link #isBlocking} 决定
     *
     * <p>{@code startUsingItem} 让手臂把盾立在胸前（客户端读的是物品使用状态），
     * {@link #faceThreat} 把头转向对手（原版按头的朝向判正反面）。
     * 这两条都是「看起来像在举盾」的部分，与挡伤判定解耦
     */
    private void updateShield() {
        if (this.shouldRaiseShield()) {
            this.startUsingItem(InteractionHand.OFF_HAND);
            this.faceThreat();
        } else if (this.getUseItem().is(TFItems.KNIGHTMETAL_SHIELD.get())) {
            this.stopUsingItem();
        }
    }

    /**
     * 该不该举着盾：只在两种时候举，别的时候一律收着
     *
     * <ol>
     *   <li><b>对手就在正面的攻击范围里</b>：目标活着、距离不超过 {@link #MELEE_REACH}、
     *       而且在自己头朝向的正前方。逃跑时目标被清空、正面也朝着逃跑方向，
     *       所以不会出现「背对着人举盾」</li>
     *   <li><b>刚挨过打</b>：{@value #SHIELD_HOLD_TICKS} tick 以内，远处射来的箭也算</li>
     * </ol>
     */
    private boolean shouldRaiseShield() {
        if (!this.hasShield() || this.isShieldDisabled() || this.shieldDownTicks > 0) {
            return false;
        }
        if (this.shieldHoldTicks > 0) {
            return true;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (this.distanceToSqr(target) > MELEE_REACH * MELEE_REACH) {
            return false;
        }
        return this.isInFront(target);
    }

    /** {@code other} 是不是在这只生物头朝向的正前方（水平面上点积 &gt; 0） */
    private boolean isInFront(LivingEntity other) {
        Vec3 toOther = new Vec3(other.getX() - this.getX(), 0.0D, other.getZ() - this.getZ());
        if (toOther.lengthSqr() < 1.0E-4D) {
            return true;
        }
        Vec3 look = this.calculateViewVector(0.0F, this.getYHeadRot());
        return look.dot(toOther.normalize()) > 0.0D;
    }

    /**
     * 把头转向当前目标
     *
     * <p>原版判「正面」用的是头的朝向（{@code calculateViewVector(0, getYHeadRot())} 与来向的点积）。
     * 生物的 {@code LookControl} 是渐变的，还会被别的目标、闲逛拽走，头没转过来的那几 tick 里
     * 正面的攻击也会被当成侧面。举盾时直接把头定在对手身上
     */
    private void faceThreat() {
        LivingEntity threat = this.getTarget();
        if (threat == null || !threat.isAlive()) {
            return;
        }
        double dx = threat.getX() - this.getX();
        double dz = threat.getZ() - this.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        this.setYHeadRot((float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
    }

    /**
     * 算不算「挡着」：副手有盾、并且没被斧头封住、也不是刚出手的那 8 tick
     *
     * <p>不要求「正在用物品」，也不要求举够几 tick —— 那两个状态在实战里都出现过
     * 「盾看着举着、拳照样打进来」。正反面、{@code #minecraft:bypasses_shield}、穿透箭
     * 仍然按原版 {@code isDamageSourceBlocked} 判
     */
    @Override
    public boolean isBlocking() {
        return this.hasShield() && !this.isShieldDisabled() && this.shieldDownTicks <= 0;
    }

    /**
     * 举盾时把移动输入按 {@link #GUARD_SPEED_FACTOR} 缩
     *
     * <p>{@code MoveControl} 每 tick 按移动速度属性设好 {@code speed}，这里在它之上再乘一层，
     * 于是 {@code travel} 拿到的就是被砍掉八成之后的值，与玩家举盾时的输入缩放等价。
     * 不能用属性修饰符做这件事：实测属性 0.3 → 4.73 格每秒，属性 0.06 只剩 0.05 格每秒
     */
    @Override
    public float getSpeed() {
        float base = super.getSpeed();
        return this.isBlocking() ? base * GUARD_SPEED_FACTOR : base;
    }

    /**
     * 挡下时磨盾牌，照原版 {@code Player#hurtCurrentlyUsedShield}
     *
     * <p>{@code LivingEntity} 里这个方法是空的（普通生物本来不会举盾），所以磨损自己接：
     * 挡下的伤害不低于 {@code ShieldItem.MINIMUM_DURABILITY_DAMAGE}（3）时扣 {@code 1 + 伤害取整}，
     * 盾牌被打碎就把副手清空、收盾，再补一声 {@code SHIELD_BREAK}
     */
    @Override
    protected void hurtCurrentlyUsedShield(float amount) {
        if (this.level().isClientSide()) {
            return;
        }
        ItemStack shield = this.getOffhandItem();
        if (!shield.is(TFItems.KNIGHTMETAL_SHIELD.get()) || amount < ShieldItem.MINIMUM_DURABILITY_DAMAGE) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        shield.hurtAndBreak(1 + Mth.floor(amount), serverLevel, this, broken -> {
            this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            this.stopUsingItem();
            this.playSound(
                    SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.getRandom().nextFloat() * 0.4F);
        });
    }

    /** 挡下之后：先照原版把来犯者推开，再看对面那一手能不能破盾（{@code Player#blockUsingShield}） */
    @Override
    protected void blockUsingShield(LivingEntity attacker) {
        super.blockUsingShield(attacker);
        if (attacker.canDisableShield()) {
            this.disableShield();
        }
    }

    /** 盾牌被斧头破掉之后的 100 tick 里不举盾 */
    public boolean isShieldDisabled() {
        return this.shieldDisabledTicks > 0;
    }

    /** 斧头破盾：封 {@value #SHIELD_DISABLED_TICKS} tick、收盾，并播一声盾牌碎裂 */
    public void disableShield() {
        if (this.level().isClientSide()) {
            return;
        }
        this.shieldDisabledTicks = SHIELD_DISABLED_TICKS;
        this.stopUsingItem();
        this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.getRandom().nextFloat() * 0.4F);
    }

    /**
     * 出手：把盾收起来 {@value #SHIELD_SWING_LOWER_TICKS} tick
     *
     * <p>挥击的那一下不能同时举着盾。收的既是判定（{@link #isBlocking}），也是姿势
     */
    private void lowerShieldForAttack() {
        if (!this.hasShield()) {
            return;
        }
        this.shieldDownTicks = SHIELD_SWING_LOWER_TICKS;
        if (this.getUseItem().is(TFItems.KNIGHTMETAL_SHIELD.get())) {
            this.stopUsingItem();
        }
    }

    /**
     * 把链锤甩向目标
     *
     * <p>锤子本体是暮色森林的 {@link ChainBlock}：给它主人（也就是这只生物）与主手那一栈物品，
     * 飞行、命中伤害、撞方块回弹、到手回手、每次命中磨一点耐久，全都是它自己那一份，这里只做两件事 ——
     * 把方向定准、补一声投掷音
     *
     * <p>瞄准要比直线多抬一点：链锤有自己的重力，飞 12 格掉将近 1 格，照眼睛连线扔会打在目标脚边
     */
    public void throwFlail(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack flail = this.getMainHandItem();
        ChainBlock block = new ChainBlock(TFEntities.CHAIN_BLOCK.get(), level, this, InteractionHand.MAIN_HAND, flail);
        double distance = Math.sqrt(this.distanceToSqr(target));
        double drop = 0.5D * FLAIL_GRAVITY * Mth.square(distance / FLAIL_VELOCITY);
        Vec3 aim = target.getEyePosition().add(0.0D, drop, 0.0D).subtract(block.position());
        if (aim.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 direction = aim.normalize();
        block.shoot(direction.x, direction.y, direction.z, FLAIL_VELOCITY, 0.5F);
        level.playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                TFSounds.BLOCK_AND_CHAIN_FIRED.get(),
                SoundSource.HOSTILE,
                0.5F,
                1.0F / (this.getRandom().nextFloat() * 0.4F + 1.2F));
        level.addFreshEntity(block);
        this.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * 主手那把武器挥满一次要多少 tick，与玩家的攻击冷却同一个算法（20 / 攻击速度）
     *
     * <p>属性取不到时按徒手的 4.0 算，不抛异常：这条一旦抛，崩的是整个服务端刻
     */
    public int attackInterval() {
        AttributeInstance attackSpeed = this.getAttribute(Attributes.ATTACK_SPEED);
        double attacksPerSecond = attackSpeed == null ? 4.0D : attackSpeed.getValue();
        return Math.max(4, Mth.ceil(20.0D / Math.max(attacksPerSecond, 0.5D)));
    }

    /** 主手是不是斧类：{@code AxeItem} 兜底，这样没挂进 {@code #minecraft:axes} 的模组斧也算 */
    public boolean isHoldingAxe() {
        ItemStack stack = this.getMainHandItem();
        return stack.getItem() instanceof AxeItem || stack.is(ItemTags.AXES);
    }

    /** 这个距离够不够得着：链锤看投掷距离，其余看近战距离 */
    public boolean canAttackFrom(double distance) {
        return distance <= (this.hasFlail() ? FLAIL_RANGE : MELEE_REACH);
    }

    /**
     * 打一下：主手是链锤就甩出去（冷却由调用方管），其余走近战
     *
     * <p>近战自己算伤害而不是调 {@code Mob#doHurtTarget}，理由见 {@link #meleeAttack}
     */
    public void attackTarget(LivingEntity target) {
        this.lowerShieldForAttack();
        if (this.hasFlail()) {
            this.throwFlail(target);
            return;
        }
        this.swing(InteractionHand.MAIN_HAND);
        this.meleeAttack(target);
    }

    /**
     * 近战结算，顺序照原版 {@code Player#attack}：附魔加成 → 暴击 ×1.5 → hurt → 击退 → 附魔后效
     *
     * <p>没有直接用 {@code Mob#doHurtTarget}：那个方法把伤害在内部算死了，塞不进暴击倍率。
     * 这里把它的每一步照抄一遍（附魔 {@code modifyDamage}、击退、{@code doPostAttackEffects}、
     * {@code setLastHurtMob}），只在中间多一条暴击；疾跑攻击额外 +1 击退与那一声
     * {@code PLAYER_ATTACK_KNOCKBACK} 也是原版 {@code Player#attack} 里的
     */
    private void meleeAttack(LivingEntity target) {
        DamageSource source = this.damageSources().mobAttack(this);
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (this.level() instanceof ServerLevel level) {
            damage = EnchantmentHelper.modifyDamage(level, this.getMainHandItem(), target, source, damage);
        }
        boolean crit = this.isCritAttack();
        if (crit) {
            damage *= CRIT_MULTIPLIER;
        }
        if (!target.hurt(source, damage)) {
            return;
        }
        float knockback = this.getKnockback(target, source);
        if (this.isSprinting()) {
            knockback += 1.0F;
            this.level()
                    .playSound(
                            null,
                            this.getX(),
                            this.getY(),
                            this.getZ(),
                            SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                            SoundSource.NEUTRAL,
                            1.0F,
                            1.0F);
        }
        if (knockback > 0.0F) {
            float yaw = this.getYRot() * ((float) Math.PI / 180.0F);
            target.knockback(knockback * 0.5F, Mth.sin(yaw), -Mth.cos(yaw));
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
        }
        if (this.level() instanceof ServerLevel level) {
            EnchantmentHelper.doPostAttackEffects(level, target, source);
        }
        this.setLastHurtMob(target);
        if (crit) {
            this.playCritFeedback(target);
        }
    }

    /**
     * 现在这一下能不能打出暴击，七条判据照原版 {@code Player#attack} 一条不少：
     * 正在下落、脚不在地上、不在梯子上、不在水里、没失明、不是乘客、并且<b>不在疾跑</b>
     *
     * <p>最后那条容易被漏掉：疾跑攻击在 1.21 里算的是击退（额外 +1 击退），不算暴击。
     * 所以「起跳劈」之前必须先把疾跑停掉，否则跳得再准也只是个击退攻击
     */
    public boolean isCritAttack() {
        return this.fallDistance > 0.0F
                && !this.onGround()
                && !this.onClimbable()
                && !this.isInWater()
                && !this.hasEffect(MobEffects.BLINDNESS)
                && !this.isPassenger()
                && !this.isSprinting();
    }

    /** 暴击的反馈：攻击者身上一声 {@code entity.player.attack.crit}，靶子周围一圈 CRIT 粒子 */
    private void playCritFeedback(LivingEntity target) {
        this.level()
                .playSound(
                        null,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        SoundEvents.PLAYER_ATTACK_CRIT,
                        SoundSource.NEUTRAL,
                        1.0F,
                        1.0F);
        if (this.level() instanceof ServerLevel level) {
            // 原版玩家的暴击粒子是 ClientboundAnimatePacket 的第 4 号动作，客户端围着靶子撒一串 CRIT；
            // 生物没有玩家那套动画包，这里用同样贴图的粒子在服务端补上
            level.sendParticles(
                    ParticleTypes.CRIT, target.getX(), target.getY(0.5D), target.getZ(), 16, 0.3D, 0.3D, 0.3D, 0.1D);
        }
    }

    /** 疾跑修饰符是否挂着 */
    public boolean isSprintCharging() {
        return this.sprintCharging;
    }

    /**
     * 开 / 关疾跑
     *
     * <p>玩家的疾跑是 {@code Player#setSprinting} 给移动速度挂一条 +30% 的瞬时修饰符；生物身上没有那一套，
     * 所以这里自己挂同一条，并把 {@code sprinting} 标志一起打开（姿势看这个标志）。
     * 只在状态真的变了时才动属性表：每 tick 摘挂修饰符会让属性脏标记一直亮着
     */
    public void setSprintCharging(boolean sprinting) {
        // 举着盾不给疾跑：原版玩家开始疾跑的条件里有 !isUsingItem()（LocalPlayer 730 行）
        boolean effective = sprinting && !this.isBlocking();
        if (this.sprintCharging == effective) {
            return;
        }
        this.sprintCharging = effective;
        // 只调原版：它挂的就是 minecraft:sprinting（+30%），自己再挂一条会乘成 +69%
        this.setSprinting(effective);
    }

    /** 半血以下：这条生物的行为从这一刻整体换档，见 {@code WoundedGoal} */
    public boolean isWounded() {
        return this.getHealth() < this.getMaxHealth() * WOUNDED_HEALTH_RATIO;
    }

    /**
     * 附近够格当靠山的敌怪，没有就 {@code null}
     *
     * <p>「半血以上的地面类型敌怪」写成这样：是敌怪（{@code Enemy}）、会自己寻路（{@code PathfinderMob}）、
     * 不是飞行生物、不在水里、也不属于 {@code #minecraft:aquatic} —— 也就是说走地上、会自己追人打的那一类。
     * 血要严格多过一半，掉到一半以下的不算
     */
    @Nullable
    public Mob scanForAlly() {
        AABB box = this.getBoundingBox().inflate(ALLY_SEARCH_RADIUS);
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob candidate : this.level().getEntitiesOfClass(Mob.class, box, this::isUsableAlly)) {
            double distance = this.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 这一只够不够格当靠山。{@link #followedAlly} 每 tick 都用它复查，所以靠山掉血会立刻被丢掉 */
    public boolean isUsableAlly(LivingEntity candidate) {
        return candidate != this
                && candidate.isAlive()
                && candidate instanceof Enemy
                && candidate instanceof PathfinderMob
                && !(candidate instanceof FlyingMob)
                && !candidate.isInWater()
                && !candidate.getType().is(EntityTypeTags.AQUATIC)
                && candidate.getHealth() > candidate.getMaxHealth() * WOUNDED_HEALTH_RATIO;
    }

    /** 当前跟着的靠山；它一旦掉到半血以下或死掉，这里立刻返回 {@code null} */
    @Nullable
    public Mob followedAlly() {
        if (this.ally != null && !this.isUsableAlly(this.ally)) {
            this.ally = null;
        }
        return this.ally;
    }

    /** 每 {@value #ALLY_RESCAN_TICKS} tick 扫一次场找靠山；手上已经有一只可用的就不换 */
    public void rescanAlly() {
        if (this.allyRescanTicks > 0) {
            this.allyRescanTicks--;
            return;
        }
        this.allyRescanTicks = ALLY_RESCAN_TICKS;
        if (this.followedAlly() == null) {
            this.ally = this.scanForAlly();
        }
    }

    /** 最近的玩家，{@code radius} 格内没有就是 {@code null} */
    @Nullable
    public Player nearestPlayer(double radius) {
        return this.level().getNearestPlayer(this, radius);
    }

    /**
     * 一声环境音都没有
     *
     * <p>空盔甲不该像生物那样呼吸与低吼；走动、受击与倒下用原版铁傀儡那几件金属声，
     * 那是唯一一套现成的、不需要另外录制的金属碰撞音
     */
    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 0.15F, 1.0F);
    }

    /**
     * 打人与走位
     *
     * <p>主手是链锤时只做远程：进到 {@value #FLAIL_RANGE} 格内、出了 {@value #FLAIL_MIN_RANGE} 格才扔，
     * 太远就往前压、太近就退开，站着不动只是因为已经站在合适的距离上了。
     *
     * <p>其余武器照近战打：走到 {@value #MELEE_REACH} 格内挥一下，伤害走 {@code Mob#doHurtTarget}，
     * 也就是「按主手的属性修饰符算伤害、附魔照常生效」的那条路，与玩家挥剑时的结算完全同源
     */
    private static final class ArmorAttackGoal extends Goal {
        /** 重算一次寻路的间隔：原版 {@code MeleeAttackGoal} 也是隔几 tick 才重算，每 tick 一条新路径太贵 */
        private static final int PATH_RECALC = 10;

        private final LivingArmorEntity armor;

        /** 还剩多少 tick 才能再打一下 */
        private int cooldown;

        /** 还剩多少 tick 才重算一次寻路 */
        private int pathCooldown;

        private ArmorAttackGoal(LivingArmorEntity armor) {
            this.armor = armor;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.armor.getTarget();
            return target != null && target.isAlive() && !this.armor.isWounded();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.cooldown = 0;
            this.pathCooldown = 0;
        }

        @Override
        public void stop() {
            this.armor.getNavigation().stop();
            this.armor.setSprintCharging(false);
        }

        @Override
        public void tick() {
            LivingEntity target = this.armor.getTarget();
            if (target == null) {
                return;
            }
            this.armor.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.cooldown > 0) {
                this.cooldown--;
            }
            if (this.pathCooldown > 0) {
                this.pathCooldown--;
            }
            double distance = this.armor.distanceTo(target);
            // 拉开 8 格以上就疾跑；跑起来撞到东西就跳一下。近了自然停下（疾跑会挡掉暴击，见 isCritAttack）
            this.armor.setSprintCharging(distance > SPRINT_DISTANCE);
            if (this.armor.isSprintCharging() && this.armor.onGround() && this.armor.horizontalCollision) {
                this.armor.getJumpControl().jump();
            }
            if (this.armor.hasFlail()) {
                this.flail(target, distance);
            } else if (this.armor.isHoldingAxe()) {
                this.axeCrit(target, distance);
            } else {
                this.melee(target, distance);
            }
        }

        private void flail(LivingEntity target, double distance) {
            this.armor.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (distance > FLAIL_RANGE) {
                this.moveToward(target);
                return;
            }
            if (distance < FLAIL_MIN_RANGE) {
                this.retreat(target, FLAIL_MIN_RANGE + 2.0D);
                return;
            }
            this.armor.getNavigation().stop();
            if (this.cooldown <= 0 && this.armor.hasLineOfSight(target)) {
                // 走 attackTarget 而不是直接 throwFlail：出手那一下要把盾收起来（lowerShieldForAttack），
                // 链锤分支与近战分支必须同一条路，否则骑士套扔链锤时盾还举着，见 LivingArmorEntity#attackTarget
                this.armor.attackTarget(target);
                this.cooldown = FLAIL_COOLDOWN;
            }
        }

        private void melee(LivingEntity target, double distance) {
            // 原版近战目标每 tick 都 setLookAt：头跟着对手转。挡盾判定按头的朝向算正面
            this.armor.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (distance > MELEE_REACH) {
                this.moveToward(target);
                return;
            }
            this.armor.getNavigation().stop();
            if (this.cooldown <= 0 && this.armor.hasLineOfSight(target)) {
                this.armor.attackTarget(target);
                this.cooldown = this.armor.attackInterval();
            }
        }

        /**
         * 斧头那一套：走到够得着的距离 → 起跳 → <b>开始下落的那一下</b>出手（暴击 ×1.5）→ 等冷却
         *
         * <p>为什么要跳：暴击的判据里有「正在下落」与「不在疾跑」，起跳之后靠重力落下来那个窗口才能打出暴击。
         * 所以这里先把疾跑停掉（冲过来时是疾跑着的），再跳；落地前 {@link LivingArmorEntity#isCritAttack()}
         * 一旦成立就出手
         *
         * <p>跳完还没落地的那几 tick 里冷却已经是 0，但 {@code isCritAttack} 不成立就不会打 —— 空中往上那段
         * 与刚起跳那一 tick 都不出手，看起来就是「跳起来、落下时劈下去」
         */
        private void axeCrit(LivingEntity target, double distance) {
            this.armor.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (distance > MELEE_REACH) {
                this.moveToward(target);
                return;
            }
            this.armor.getNavigation().stop();
            if (this.cooldown > 0) {
                return;
            }
            if (this.armor.onGround()) {
                this.armor.setSprintCharging(false);
                this.armor.getJumpControl().jump();
                return;
            }
            if (this.armor.isCritAttack() && this.armor.hasLineOfSight(target)) {
                this.armor.attackTarget(target);
                this.cooldown = this.armor.attackInterval();
            }
        }

        /** 朝着目标重算一条路径，隔 {@value #PATH_RECALC} tick 一次 */
        private void moveToward(LivingEntity target) {
            if (this.pathCooldown > 0) {
                return;
            }
            this.pathCooldown = PATH_RECALC;
            this.armor.getNavigation().moveTo(target, CHASE_SPEED);
        }

        /** 沿着「目标 → 自己」的方向退到离目标 {@code distance} 格的位置 */
        private void retreat(LivingEntity target, double distance) {
            Vec3 offset = this.armor.position().subtract(target.position());
            double length = offset.length();
            if (length < 1.0E-4D) {
                return;
            }
            Vec3 point = target.position().add(offset.scale(distance / length));
            this.armor.getNavigation().moveTo(point.x, point.y, point.z, CHASE_SPEED);
        }
    }

    /**
     * 半血以下：先逃，再找靠山
     *
     * <p>每 tick 只有两个分支：
     *
     * <ul>
     *   <li>有能用的靠山（见 {@link #isUsableAlly}）：不逃了，贴着它走，<b>目标直接取它的目标</b>，
     *       于是「它打谁就打谁」；靠山自己掉到半血以下时 {@link #followedAlly} 会立刻把它丢掉，
     *       下一 tick 自动退回逃跑分支 —— 这就是「如果跟随对象也掉到半血以下，继续逃离」
     *   <li>没有靠山：清空目标（不再主动打人），朝最近的玩家反方向跑，一路疾跑
     * </ul>
     *
     * <p>这个目标排在攻击目标之前（优先级 1 对 2），两者都占 MOVE 标志，所以半血以下时正常那套攻击走位
     * 整个被压住 —— 逃跑与「跟着打」都只在这一处发生，不会两边抢导航
     */
    private static final class WoundedGoal extends Goal {
        private final LivingArmorEntity armor;

        /** 跟着靠山打时也要守攻击冷却 */
        private int cooldown;

        private WoundedGoal(LivingArmorEntity armor) {
            this.armor = armor;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.armor.isWounded();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.cooldown = 0;
            this.armor.rescanAlly();
        }

        @Override
        public void stop() {
            this.armor.getNavigation().stop();
            this.armor.setSprintCharging(false);
            this.armor.setTarget(null);
        }

        @Override
        public void tick() {
            if (this.cooldown > 0) {
                this.cooldown--;
            }
            this.armor.rescanAlly();
            Mob ally = this.armor.followedAlly();
            if (ally == null) {
                this.flee();
            } else {
                this.support(ally);
            }
        }

        /** 跟着靠山：站在它旁边，打它的目标 */
        private void support(Mob ally) {
            LivingEntity target = ally.getTarget();
            if (target != null && !target.isAlive()) {
                target = null;
            }
            this.armor.setTarget(target);
            double toAlly = this.armor.distanceTo(ally);
            // 离靠山太远就先跑过去；贴住了就原地打，别在它身上乱撞
            if (toAlly > ALLY_KEEP_DISTANCE) {
                this.armor.getNavigation().moveTo(ally, RALLY_SPEED);
                this.armor.setSprintCharging(true);
            } else {
                this.armor.getNavigation().stop();
                this.armor.setSprintCharging(false);
            }
            if (target == null) {
                return;
            }
            this.armor.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distance = this.armor.distanceTo(target);
            if (this.cooldown <= 0 && this.armor.canAttackFrom(distance) && this.armor.hasLineOfSight(target)) {
                this.armor.attackTarget(target);
                this.cooldown = this.armor.attackInterval();
            }
        }

        /** 逃：朝最近的玩家反方向跑，跑到 {@value #FLEE_DISTANCE} 格开外 */
        private void flee() {
            this.armor.setTarget(null);
            Player threat = this.armor.nearestPlayer(FLEE_SEARCH_RADIUS);
            if (threat == null) {
                this.armor.setSprintCharging(false);
                return;
            }
            Vec3 away = this.armor.position().subtract(threat.position());
            if (away.lengthSqr() < 1.0E-4D) {
                // 正好站在玩家身上：随便挑个方向也比原地不动强
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            Vec3 point = this.armor.position().add(away.normalize().scale(FLEE_DISTANCE));
            this.armor.getNavigation().moveTo(point.x, point.y, point.z, RALLY_SPEED);
            this.armor.setSprintCharging(true);
            this.armor.getLookControl().setLookAt(threat, 30.0F, 30.0F);
        }
    }
}
