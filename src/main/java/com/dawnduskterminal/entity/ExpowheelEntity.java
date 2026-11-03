package com.dawnduskterminal.entity;

import com.dawnduskterminal.network.ExpowheelBurstPayload;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModParticles;
import com.dawnduskterminal.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * 血肉之书（试验物品 057 的加强版）打出去的那片飞轮
 *
 * <p>骨架与 {@link Experiment057BladeEntity} 相同：{@link ThrowableProjectile} 加
 * {@code setNoGravity}，空气阻力由 {@link #steer} 每 tick 把速度大小按回 {@code speed} 抵消，
 * 命中判定留在父类，本类只接管「往哪飞」与「撞上之后做什么」。
 *
 * <p>与 057 的差别按需求逐条摆在这里：
 *
 * <ul>
 *   <li>寿命 {@value #LIFETIME_TICKS} tick（6 秒），比 057 的 8 秒短
 *   <li>速度 {@value #MIN_SPEED}~{@value #MAX_SPEED} 格/tick（057 是 1.2~1.8）、每 tick 最多转
 *       {@value #TURN_DEGREES_PER_TICK} 度（057 是 6 度）。两项都只抬高一点速度、把转角抬得多得多：
 *       转弯半径是「速度 ÷ 每 tick 转角」，057 约 14.3 格，这里是 2.0 ÷ 0.419 ≈ 4.8 格，
 *       所以它拐得过来
 *   <li>命中活物只结算一次固定的 {@value #CONTACT_DAMAGE} 点接触伤害：既不是发射者的攻击力，也没有暴击，
 *       也就不再有 057 那套暴击音效与暴击粒子
 *   <li>命中活物**不弹回、不扣寿命**：057 是原路折返再扣 4 秒
 *   <li>不伤害发射者：弹体从眼睛前方 0.6 格处出生，发射者自己的判定箱就在旁边，碰上了直接跳过
 *   <li>沿途每 tick 在弹体自己的判定箱里撒 {@value #TRAIL_PARTICLES_PER_TICK} 颗
 *       {@code carminite_ember}（057 是挥砍粒子加挥砍音效）
 * </ul>
 *
 * <p>撞方块照抄 057：弹性反射，随后播该方块自己的破坏音效，实体不消失。
 * 粒子一颗都不另撒——沿途那颗余烬一直在出，碰撞再撒一片是多余的。
 *
 * <p><b>音效</b>：发射那一下 {@code expowheel_shoot} 由 {@link #launch} 播；飞行途中的
 * {@code expowheel_ambient} 是客户端挂在弹体身上的定位音（{@code client/ExpowheelSounds}），
 * 位置每帧跟着弹体走，按 16 格线性衰减，最后 1 秒再把音量收到 0 才结束——
 * 不是发射那一刻在发射者脚下放一遍，也不是到点硬切。
 *
 * <p><b>索敌</b>三档，每 tick 从头评估一次，前两档都找不到目标才退回 057 的制导：
 *
 * <ol>
 *   <li>发射者视线两侧各 {@value #SEEK_CONE_DEGREES} 度以内、**看得见**最近的敌怪，距离不限；
 *       每换一次目标（含第一次锁定）就在「目标 ↔ 发射者」那条线上撒一串附魔光点
 *   <li>否则取「弹体附近 {@value #SEEK_RANGE} 格」与「发射者附近 {@value #SEEK_RANGE} 格」这两个范围并集里
 *       最近的、**弹体看得见**的一只
 *   <li>再否则按 057 的 CLOS 制导：沿着发射者眼睛出发的那条视线飞
 * </ol>
 *
 * <p>前两档都要过一遍方块遮挡判定（{@link #canSee}）：隔着墙、隔着山、隔着一层地板一律不算目标。
 * 没有这一道，飞轮会朝墙里钻，而且几乎永远停在第 1 档——第 2、3 档就成了摆设。
 *
 * <p>前两档都只认 {@link Enemy}（原版与暮色森林的敌对生物都实现它），玩家、动物、发射者自己都不算敌怪。
 * 第 1 档里那个 {@value #SEEK_QUERY_RADIUS} 格的方盒只是「向区块要已载入的实体」的手段，不是规则里的
 * 距离上限：判据本身只有那 20 度的锥，锥里最远的那只照样会被优先于第 2 档选走。
 *
 * <p>转向仍受 {@value #TURN_DEGREES_PER_TICK} 度/tick 的上限约束：索敌给出的是「指向目标」的期望方向，
 * 弹体并不会瞬移掉头，而是斜着切过去。
 */
public class ExpowheelEntity extends ThrowableProjectile {
    /** 寿命：6 秒 */
    public static final int LIFETIME_TICKS = 120;

    /** 接触伤害，固定值：不读发射者的属性，也不吃暴击与附魔 */
    public static final float CONTACT_DAMAGE = 10.0F;

    /** 初速的两端，含两端；比 057 的 1.2~1.8 快一档，但不是把速度翻倍 */
    public static final float MIN_SPEED = 1.9F;

    public static final float MAX_SPEED = 2.1F;

    /**
     * 初速偏离视线的最大角度，单位度
     *
     * <p>与 057 同一个数：偏角 0~{@value #MAX_DEVIATION_DEGREES} 度、方位角一整圈，一定落在视线前方，
     * 再由制导或索敌把航线收回去
     */
    public static final double MAX_DEVIATION_DEGREES = 30.0D;

    /**
     * 每 tick 最多把速度方向朝期望方向转多少，单位度
     *
     * <p>057 是 6 度。转弯半径 = 速度 ÷ 每 tick 转角（弧度），057 约 14.3 格；这里转角抬到 24 度、
     * 速度只抬到 2 格出头，半径因此压到约 4.8 格，索敌才拐得过来
     */
    public static final double TURN_DEGREES_PER_TICK = 24.0D;

    /** 第 1 档索敌的锥角：视线两侧各 {@value #SEEK_CONE_DEGREES} 度 */
    public static final double SEEK_CONE_DEGREES = 20.0D;

    /** 第 2 档索敌的半径，单位格 */
    public static final double SEEK_RANGE = 24.0D;

    /**
     * 第 1 档索敌向区块要实体的方盒边长的一半，单位格
     *
     * <p>这不是规则的一部分：需求里的第 1 档只写了「视线 20 度以内」，没有距离上限。
     * 这个数只决定「去哪一片已载入的实体里挑」，比弹体 6 秒能飞的距离（约 12 格）远十倍以上
     */
    private static final double SEEK_QUERY_RADIUS = 128.0D;

    /**
     * 制导的前置距离，单位格
     *
     * <p>与 057 同一个数：期望方向指向「视线上的垂足再往前 {@value #LEAD_DISTANCE} 格」，偏距 3 格时偏角
     * 只有 atan(3/6) ≈ 27 度，转角上限追得上
     */
    private static final double LEAD_DISTANCE = 6.0D;

    /** 弹开时叠加的随机偏转，按速度的比例算 */
    private static final double BOUNCE_SPREAD = 0.12D;

    /** 弹开之后沿法线让出多远，免得下一 tick 又从方块内部撞一次 */
    private static final double SURFACE_ESCAPE = 0.05D;

    /** 生成点从眼睛沿视线往前让出这么多格，免得一出来就贴在自己的判定箱上 */
    private static final double SPAWN_OFFSET = 0.6D;

    /**
     * 沿途每 tick 撒几颗余烬
     *
     * <p>按面积给的：判定箱是 2 格宽、半格厚的扁轮子，圆盘摊开有 3 平方格上下，
     * 每 tick 两颗连一个角落都填不满；八颗摊在这片圆盘上才看得出「整片轮子在冒火星」
     */
    private static final int TRAIL_PARTICLES_PER_TICK = 8;

    /** 余烬的竖直速度：与 {@code carminite_fire} 那档一样，0.2~0.25 */
    private static final double TRAIL_MIN_RISE = 0.2D;

    private static final double TRAIL_MAX_RISE = 0.25D;

    /** 余烬的横向速度，只留一点抖动 */
    private static final double TRAIL_DRIFT = 0.01D;

    /** 发射音的响度 */
    private static final float SHOOT_VOLUME = 1.0F;

    /** 寿命到点时把炸开参数发给多远的玩家（与客户端跟踪距离同档） */
    private static final double BURST_RANGE = 64.0D * 64.0D;

    /**
     * 炸开那一声的音量
     *
     * <p>原版定位音效的听距是「音量 × 16 格」（音量超过 1 时响度被夹在 1，增加的只是听距），
     * 所以给 4 正好和上面那 64 格的粒子半径对齐：看得到的距离上就听得到
     */
    private static final float BURST_VOLUME = 8.0F;

    /** 发射时定下的速度大小，每 tick 按回它，于是阻力不再让飞轮越飞越慢 */
    private double speed;

    /** 只用来数寿命，与存档一起存 */
    private int age;

    /**
     * 第 1 档现在锁着谁（实体 id，0 表示还没锁过）
     *
     * <p>只用来判断「换目标了没有」：每换一次（含第一次锁定）就在「目标 ↔ 发射者」那条线上撒一串附魔光点
     */
    private int lastConeTargetId;

    public ExpowheelEntity(EntityType<? extends ExpowheelEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.speed = Mth.nextDouble(level.getRandom(), MIN_SPEED, MAX_SPEED);
    }

    /**
     * 由发射者打出一片飞轮：初速是「朝视线前方」的一个随机方向，快慢也随机，并在发射者身上播一声
     * {@code expowheel_shoot}
     *
     * <p>这里**不**记录发射者的任何数值：接触伤害是固定的 {@value #CONTACT_DAMAGE}
     */
    public static ExpowheelEntity launch(Level level, Player shooter) {
        ExpowheelEntity wheel = new ExpowheelEntity(ModEntities.EXPOWHEEL.get(), level);
        Vec3 look = shooter.getViewVector(1.0F);
        Vec3 origin = shooter.getEyePosition().add(look.scale(SPAWN_OFFSET));
        wheel.setPos(origin.x, origin.y, origin.z);
        wheel.setOwner(shooter);
        wheel.setDeltaMovement(randomDirectionInFront(level.getRandom(), look).scale(wheel.speed));
        wheel.hasImpulse = true;
        level.addFreshEntity(wheel);
        if (!level.isClientSide) {
            level.playSound(
                    null,
                    shooter.getX(),
                    shooter.getEyeY(),
                    shooter.getZ(),
                    ModSounds.EXPOWHEEL_SHOOT.get(),
                    SoundSource.PLAYERS,
                    SHOOT_VOLUME,
                    1.0F);
        }
        return wheel;
    }

    /**
     * 视线前方的一个随机单位方向：偏角 0~{@value #MAX_DEVIATION_DEGREES} 度、方位角一整圈
     *
     * <p>在视线周围取一组正交基 {@code side}/{@code up}，再按球坐标拼回去。视线几乎竖直时
     * {@code side} 改用另一条参考轴算，免得叉积退化成零向量
     */
    private static Vec3 randomDirectionInFront(RandomSource random, Vec3 look) {
        Vec3 axis = look.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : look.normalize();
        Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-6D) {
            side = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 up = side.cross(axis).normalize();

        double deviation = Math.toRadians(Mth.nextDouble(random, 0.0D, MAX_DEVIATION_DEGREES));
        double azimuth = random.nextDouble() * Math.PI * 2.0D;
        double spread = Math.sin(deviation);
        return axis.scale(Math.cos(deviation))
                .add(side.scale(spread * Math.cos(azimuth)))
                .add(up.scale(spread * Math.sin(azimuth)))
                .normalize();
    }

    @Override
    public void tick() {
        super.tick();
        // 粒子在各自的客户端就地生成，两边都按同一个节拍
        if (this.level().isClientSide) {
            this.spawnTrail();
            return;
        }
        this.age++;
        if (this.age > LIFETIME_TICKS) {
            this.expire();
            return;
        }
        this.steer();
    }

    /**
     * 沿途的砷铅铁余烬：撒在弹体判定箱**内部的一个圆盘**上——水平方向按半径 = 箱子宽度的一半取均匀点，
     * 竖直方向在箱高内随机，于是 2 格宽、半格厚的扁轮子摊出来就是一整片火星，
     * 与碰撞箱（2.0 x 0.5）和模型的外轮廓三处对得上，而不是一个点
     *
     * <p>竖直速度按 {@code carminite_fire} 那一档给（0.2~0.25），横向只留一点抖动；
     * 之后的上升加速度与横向阻尼在 {@code client/CarminiteEmberParticle} 里
     */
    private void spawnTrail() {
        AABB box = this.getBoundingBox();
        double radius = 0.5D * Math.min(box.getXsize(), box.getZsize());
        for (int index = 0; index < TRAIL_PARTICLES_PER_TICK; index++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double offset = radius * Math.sqrt(this.random.nextDouble());
            this.level()
                    .addParticle(
                            ModParticles.CARMINITE_EMBER.get(),
                            this.getX() + Math.cos(angle) * offset,
                            Mth.lerp(this.random.nextDouble(), box.minY, box.maxY),
                            this.getZ() + Math.sin(angle) * offset,
                            (this.random.nextDouble() - 0.5D) * TRAIL_DRIFT,
                            Mth.nextDouble(this.random, TRAIL_MIN_RISE, TRAIL_MAX_RISE),
                            (this.random.nextDouble() - 0.5D) * TRAIL_DRIFT);
        }
    }

    /**
     * 寿命耗尽：在弹体位置响一声风弹爆开，并把「就炸在这里、速度是这个」发给附近的玩家，
     * 由他们各自铺那一圈反应场粒子（{@code network/ExpowheelBurstPayload}），然后消失
     *
     * <p>为什么不由客户端自己撒：弹体一 tick 飞两格，一秒多就飞出 4 区块的跟踪范围，
     * 客户端根本活不到 120 tick，只有服务端知道它什么时候到点
     */
    private void expire() {
        Vec3 velocity = this.getDeltaMovement();
        this.level()
                .playSound(
                        null,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        SoundEvents.WIND_CHARGE_BURST.value(),
                        SoundSource.NEUTRAL,
                        BURST_VOLUME,
                        1.0F);
        if (this.level() instanceof ServerLevel server) {
            ExpowheelBurstPayload payload = new ExpowheelBurstPayload(
                    this.getX(), this.getY(), this.getZ(), (float) velocity.x, (float) velocity.y, (float) velocity.z);
            for (ServerPlayer player : server.getPlayers(candidate -> candidate.distanceToSqr(this) < BURST_RANGE)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        this.discard();
    }

    /**
     * 每 tick 定一次航向：先索敌，索不到目标才退回 057 的视线制导
     *
     * <p>不论走哪一档，最后都把速度方向按回 {@code speed}，于是「无重力 + 匀速 + 转向」三件事各归各的
     */
    private void steer() {
        double speed = this.speed;
        if (speed <= 0.0D) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 direction = motion.normalize();
        LivingEntity target = this.findTarget();
        if (target != null) {
            Vec3 aim = target.getBoundingBox().getCenter().subtract(this.position());
            if (aim.lengthSqr() > 1.0E-8D) {
                direction = turnTowards(direction, aim.normalize(), Math.toRadians(TURN_DEGREES_PER_TICK));
            }
        } else {
            direction = this.sightLineDirection(direction);
        }
        this.setDeltaMovement(direction.scale(speed));
    }

    /**
     * 057 的 CLOS 制导：把弹体往「发射者眼睛出发的那条视线」上收，同时保持匀速前进
     *
     * <p>视线是一条线（{@code 眼睛 + t × 视线方向}），不是一个方向场。所以先求弹体到这条线的垂直偏距
     * {@code lateral}，再取垂足沿视线往前 {@value #LEAD_DISTANCE} 格的那个点作为期望方向的指向点。
     * 没有发射者（下线、死亡、/summon）时按当前方向直飞
     */
    private Vec3 sightLineDirection(Vec3 fallback) {
        Entity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) {
            return fallback;
        }
        Vec3 line = owner.getViewVector(1.0F);
        if (line.lengthSqr() <= 1.0E-8D) {
            return fallback;
        }
        line = line.normalize();
        Vec3 offset = this.position().subtract(owner.getEyePosition());
        Vec3 lateral = offset.subtract(line.scale(offset.dot(line)));
        Vec3 desired = line.scale(LEAD_DISTANCE).subtract(lateral);
        if (desired.lengthSqr() <= 1.0E-8D) {
            return fallback;
        }
        return turnTowards(fallback, desired.normalize(), Math.toRadians(TURN_DEGREES_PER_TICK));
    }

    /**
     * 这一 tick 要瞄准的敌怪，按需求的三档顺序找
     *
     * @return 找不到时返回 {@code null}，调用方退回 057 的制导
     */
    @Nullable
    private LivingEntity findTarget() {
        Entity owner = this.getOwner();
        Player shooter = owner instanceof Player player && player.isAlive() ? player : null;
        if (shooter != null) {
            LivingEntity seen = this.nearestInSightCone(shooter);
            if (seen != null) {
                this.noteConeTarget(seen, shooter);
                return seen;
            }
        }
        return this.nearestAround(shooter);
    }

    /**
     * 第 1 档换目标时留下的一条光带：从目标判定箱中心一直连到发射者眼睛
     *
     * <p>只在这条目标跟上一只不同（含第一次锁定）时才撒，同一只连续锁着不重复撒。
     * 光点按 {@code ParticleTypes.ENCHANT} 的原版行为摆：{@code sendParticles} 的「速度」对这支粒子来说
     * 是落点偏移，所以这里**三轴都给 0**，粒子才正好落在线上；它自己会在寿命里往下沉 1.2 格
     * （见 {@code FlyTowardsPositionParticle#tick}），于是整条线是一串慢慢飘落的附魔光点。
     * 每 0.3 格一颗、12~48 颗，密到能一眼看出它指向哪里
     */
    private void noteConeTarget(LivingEntity target, Player shooter) {
        int id = target.getId();
        if (id == this.lastConeTargetId) {
            return;
        }
        this.lastConeTargetId = id;
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 from = shooter.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        int points = Mth.clamp((int) Math.round(from.distanceTo(to) / 0.3D), 12, 48);
        for (int index = 0; index <= points; index++) {
            Vec3 at = from.lerp(to, (double) index / (double) points);
            server.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * 第 1 档：发射者视线两侧各 {@value #SEEK_CONE_DEGREES} 度以内最近的敌怪，**不限距离**
     *
     * <p>判据是「眼睛到目标中心的单位向量与视线方向的点积不小于 cos(20°)」，因此只在视线前方找；
     * 距离只用来挑最近的一只，不设上限。
     */
    @Nullable
    private LivingEntity nearestInSightCone(Player shooter) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getViewVector(1.0F);
        if (look.lengthSqr() <= 1.0E-8D) {
            return null;
        }
        look = look.normalize();
        double minDot = Math.cos(Math.toRadians(SEEK_CONE_DEGREES));
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : this.enemiesAround(eye, SEEK_QUERY_RADIUS)) {
            Vec3 to = candidate.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance <= 1.0E-4D) {
                continue;
            }
            if (to.scale(1.0D / distance).dot(look) < minDot) {
                continue;
            }
            if (distance >= bestDistance || !this.canSee(eye, candidate)) {
                continue;
            }
            best = candidate;
            bestDistance = distance;
        }
        return best;
    }

    /**
     * 第 2 档：弹体附近 {@value #SEEK_RANGE} 格与发射者附近 {@value #SEEK_RANGE} 格这两个范围的并集里
     * 最近的、**弹体看得见**的敌怪
     *
     * <p>排序用的是「到弹体」与「到发射者」两个距离里较小的那个；发射者已经死亡或下线时只剩弹体那一半
     */
    @Nullable
    private LivingEntity nearestAround(@Nullable Player shooter) {
        List<LivingEntity> candidates = new ArrayList<>(this.enemiesAround(this.position(), SEEK_RANGE));
        if (shooter != null) {
            candidates.addAll(this.enemiesAround(shooter.position(), SEEK_RANGE));
        }
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            Vec3 center = candidate.getBoundingBox().getCenter();
            double distance = center.distanceTo(this.position());
            if (shooter != null) {
                distance = Math.min(distance, center.distanceTo(shooter.position()));
            }
            if (distance > SEEK_RANGE || distance >= bestDistance) {
                continue;
            }
            if (!this.canSee(this.position(), candidate)) {
                continue;
            }
            best = candidate;
            bestDistance = distance;
        }
        return best;
    }

    /**
     * 从 {@code from} 到目标判定箱中心有没有整段没被方块挡住
     *
     * <p>索敌必须看得见目标：没有这一道，隔着一堵墙、一座山、一层地板都会锁上去，
     * 飞轮就会朝墙里钻——全模组没有第二个穿墙索敌的东西。
     * 用 {@link ClipContext.Block#COLLIDER}：草、火把这类不挡碰撞的方块照旧不算遮挡
     */
    private boolean canSee(Vec3 from, LivingEntity target) {
        Vec3 to = target.getBoundingBox().getCenter();
        BlockHitResult hit =
                this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** 一个以 {@code center} 为中心、边长 {@code 2 × radius} 的方盒里的全部敌怪 */
    private List<LivingEntity> enemiesAround(Vec3 center, double radius) {
        AABB box = AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        return this.level().getEntitiesOfClass(LivingEntity.class, box, this::isEnemy);
    }

    /** 敌怪：实现了 {@link Enemy} 的活物，排掉发射者自己、旁观者与死物 */
    private boolean isEnemy(LivingEntity candidate) {
        if (!(candidate instanceof Enemy)) {
            return false;
        }
        if (candidate == this.getOwner() || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        return candidate.level() == this.level();
    }

    /**
     * 把单位向量 {@code from} 朝单位向量 {@code to} 转过至多 {@code maxRadians}，返回单位向量
     *
     * <p>罗德里格斯公式，绕 {@code from × to} 这条轴转。夹角本来就小于上限时直接返回 {@code to}；
     * 两者正好反向时叉积是零向量，随便挑一条垂直于 {@code from} 的轴
     */
    private static Vec3 turnTowards(Vec3 from, Vec3 to, double maxRadians) {
        double angle = Math.acos(Mth.clamp(from.dot(to), -1.0D, 1.0D));
        if (angle <= maxRadians) {
            return to;
        }
        Vec3 axis = from.cross(to);
        if (axis.lengthSqr() < 1.0E-12D) {
            Vec3 fallback = Math.abs(from.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
            axis = from.cross(fallback);
            if (axis.lengthSqr() < 1.0E-12D) {
                return to;
            }
        }
        axis = axis.normalize();
        double cosine = Math.cos(maxRadians);
        double sine = Math.sin(maxRadians);
        return from.scale(cosine).add(axis.cross(from).scale(sine)).normalize();
    }

    /**
     * 撞方块：与 057 一样弹性反射，速度沿撞击面法线的分量取反，再叠一点随机偏转，实体不消失
     *
     * <p>撞上那一刻只播该方块自己的破坏音效（{@link SoundType#getBreakSound}），不撒粒子：沿途的余烬
     * 一直在出。音效只在服务端发出，客户端也会跑这一支命中判定，不挡住就会响两遍
     */
    @Override
    protected void onHitBlock(BlockHitResult result) {
        Vec3 normal = Vec3.atLowerCornerOf(result.getDirection().getNormal());
        Vec3 motion = this.getDeltaMovement();
        Vec3 reflected = motion.subtract(normal.scale(2.0D * motion.dot(normal)));
        double spread = BOUNCE_SPREAD * Math.max(this.speed, motion.length());
        Vec3 deflected = reflected.add(new Vec3(centredRandom(), centredRandom(), centredRandom()).scale(spread));
        Vec3 bounced = deflected.lengthSqr() < 1.0E-6D ? normal : deflected.normalize();
        this.setDeltaMovement(bounced.scale(this.speed));
        Vec3 surface = result.getLocation().add(normal.scale(SURFACE_ESCAPE));
        this.setPos(surface.x, surface.y, surface.z);
        this.hasImpulse = true;
        if (!this.level().isClientSide) {
            this.playImpactSound(result.getBlockPos(), surface);
        }
    }

    /** 命中方块那一下的材质音效：破坏声，音量与音高照该方块自己的 {@link SoundType} */
    private void playImpactSound(BlockPos pos, Vec3 at) {
        BlockState state = this.level().getBlockState(pos);
        SoundType sound = state.getSoundType(this.level(), pos, this);
        this.level()
                .playSound(
                        null,
                        at.x,
                        at.y,
                        at.z,
                        sound.getBreakSound(),
                        SoundSource.BLOCKS,
                        sound.getVolume(),
                        sound.getPitch());
    }

    /** [-1, 1) 的随机数，三个轴各来一份就是「少许随机偏转」 */
    private double centredRandom() {
        return this.random.nextDouble() * 2.0D - 1.0D;
    }

    /**
     * 撞到活物：结算一次固定的 {@value #CONTACT_DAMAGE} 点接触伤害，**不**弹回、**不**扣寿命
     *
     * <p>伤害走 {@code DamageSources#thrown(this, 发射者)}：直接来源是弹体、加害者是发射者，
     * 于是击杀提示与仇恨都记在发射者头上，但它不是近战攻击，因此不读攻击力、不吃附魔与暴击。
     *
     * <p>发射者自己不受伤：弹体从眼睛前方 0.6 格出生，发射者的判定箱就在旁边，不做这一道判断，
     * 一出手就会先打自己一下
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide) {
            return;
        }
        Entity hit = result.getEntity();
        if (hit == this.getOwner()) {
            return;
        }
        if (hit instanceof LivingEntity target) {
            target.hurt(this.damageSources().thrown(this, this.getOwner()), CONTACT_DAMAGE);
        }
    }

    /** 已经用掉的寿命（tick），等于 {@code age} */
    public int getAge() {
        return this.age;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.age = compound.getInt("Age");
        this.speed = compound.contains("WheelSpeed") ? compound.getDouble("WheelSpeed") : this.speed;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Age", this.age);
        compound.putDouble("WheelSpeed", this.speed);
    }
}
