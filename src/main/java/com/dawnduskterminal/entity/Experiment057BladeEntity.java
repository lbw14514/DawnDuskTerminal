package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

/**
 * 试验物品 057 右键打出去的那片飞刃
 *
 * <p>用 {@link ThrowableProjectile}：重力由 {@code setNoGravity} 关掉，空气阻力那一条由 {@link #steer}
 * 每 tick 把速度大小按回发射时的 {@code speed} 抵消掉，命中判定（撞方块还是撞实体、先撞谁）全部留在
 * 父类里，本类只接管「往哪飞」与「撞上之后做什么」
 *
 * <p><b>制导</b>：CLOS 的原意是「让弹体沿着指挥视线飞」，视线是**从发射者眼睛出发的一条射线**，
 * 不是一个方向场。所以 {@link #steer} 每 tick 先把弹体到这条射线的垂直偏距取出来，期望速度写成
 * 「沿射线向前 + 把偏距拉回射线」，再把当前速度方向朝这个期望方向转至多
 * {@value #TURN_DEGREES_PER_TICK} 度：偏得越远回调越快，最终落在射线上并沿它前进，而不是「方向平行、
 * 位置随便」。发射者下线或死亡之后失去指令，沿当前方向直飞
 *
 * <p><b>撞方块</b>：弹性反射，即把速度沿撞击面法线的分量取反，再叠一点随机偏转，然后从面外
 * {@value #SURFACE_ESCAPE} 格处继续飞——不弹出这一小段，下一 tick 会从方块内部再撞一次同一个面。
 * 实体不消失。撞上那一下撒一片红石粒子，并播该方块自己的破坏音效（{@link SoundType#getBreakSound}）。
 * 这一支不调 {@code super.onHitBlock}：那个实现会触发方块自己的 {@code onProjectileHit}
 * （钟、靶子之类），弹一次触发一次显然不对
 *
 * <p><b>撞实体</b>：由发射者**直接**对目标结算一次这件物品的暴击伤害，也就是攻击力 × 1.5；若此刻发射者
 * 正在下落（判据照抄原版近战暴击那几条），再乘一层 1.5，即 2.25 倍。攻击力**每次命中现读**发射者的
 * {@code ATTACK_DAMAGE} 属性，锋利、亡灵杀手、节肢杀手这一档再经
 * {@link EnchantmentHelper#modifyDamage} 加到结果上（与原版近战同一个入口、同一个顺序：属性伤害乘暴击，
 * 附魔加成最后相加），因此没有任何写死的数值。暴击倍率交给 {@link CriticalHitEvent}，别的模组要改就改得动。
 * 这件物品的每一击都是暴击，所以造成伤害时**必定**配一次暴击音效（{@code entity.player.attack.crit}）与
 * 一片暴击粒子（{@code crit}）。命中之后飞刃**原路弹回**（速度方向整个取反、大小不变），
 * 并扣掉 {@value #HIT_LIFETIME_COST_TICKS} tick（4 秒）的寿命——8 秒的飞刃打中两次就没了
 *
 * <p>每 {@value #SWING_PERIOD_TICKS} tick 一声挥砍音效、一颗挥砍粒子，{@value #LIFETIME_TICKS} tick
 * （8 秒，或被命中扣完）之后撒一片红石粒子再消失
 */
public class Experiment057BladeEntity extends ThrowableProjectile {
    /** 寿命：8 秒 */
    public static final int LIFETIME_TICKS = 160;

    /** 挥砍音效与挥砍粒子的节拍：每 2 tick 一次 */
    public static final int SWING_PERIOD_TICKS = 2;

    /** 初速的两端，含两端 */
    private static final float MIN_SPEED = 1.2F;

    private static final float MAX_SPEED = 1.8F;

    /**
     * 初速偏离视线的最大角度，单位度
     *
     * <p>初速是「朝发射者视线前方」的一个随机方向：以视线为轴，偏角在 0~{@value #MAX_DEVIATION_DEGREES}
     * 度之间随机、方位角一整圈随机，因此一定落在视线前方，再由制导把它收回到视线上
     */
    public static final double MAX_DEVIATION_DEGREES = 30.0D;

    /** 每 tick 最多把速度方向朝期望方向转多少，单位度 */
    private static final double TURN_DEGREES_PER_TICK = 6.0D;

    /**
     * 制导的前置距离，单位格
     *
     * <p>期望方向不是「指向视线上的垂足」，而是「指向垂足沿视线再往前 {@value #LEAD_DISTANCE} 格的那个点」：
     * 偏距 3 格时偏角只有 atan(3/6) ≈ 27 度，转角上限追得上，于是弹体是斜着切回视线、再顺着线飞出去；
     * 若直接指向垂足，偏距 3 格时偏角就有 63 度，转角上限反而让它越冲越偏
     */
    private static final double LEAD_DISTANCE = 6.0D;

    /** 弹开时叠加的随机偏转，按速度的比例算 */
    private static final double BOUNCE_SPREAD = 0.12D;

    /** 弹开之后沿法线让出多远，免得下一 tick 又从方块内部撞一次 */
    private static final double SURFACE_ESCAPE = 0.05D;

    /** 暴击倍率：命中 1.5 倍，发射者正在下落时再叠一层 */
    private static final float CRIT_MULTIPLIER = 1.5F;

    /** 生成点从眼睛沿视线往前让出这么多格，免得一出来就贴在自己的判定箱上 */
    private static final double SPAWN_OFFSET = 0.6D;

    /** 挥砍音效这一档：80 声叠在一起，音量压到一半 */
    private static final float SWING_VOLUME = 0.5F;

    /** 命中活物的代价：扣掉 4 秒寿命（8 秒的飞刃打中两次就没了） */
    public static final int HIT_LIFETIME_COST_TICKS = 4 * 20;

    /** 撞击方块与寿命耗尽时那一片红石粒子的颗数与散开半径 */
    private static final int REDSTONE_BURST_COUNT = 32;

    private static final double REDSTONE_BURST_SPREAD = 0.35D;

    /** 暴击粒子颗数：近战暴击那一套（{@code crit}），命中点周围散开 */
    private static final int CRIT_PARTICLE_COUNT = 12;

    /**
     * 原版音量 1 的听距（格）
     *
     * <p>客户端对定位音效按「距离 ÷ (音量 × 本值)」做线性衰减：音量撑到 1 以上时响度被夹在 1，
     * 增加的是听距，所以「放大音量」等价于「把听距拉到命中点之外」
     */
    private static final double SOUND_RANGE_PER_VOLUME = 16.0D;

    /**
     * 发射者那一份暴击音效的目标响度
     *
     * <p>按 {@code 1 − 距离 / 听距 = 本值} 反推音量：0.8 表示不论命中点多远，发射者听到的都是八成音量，
     * 听距因此取成距离的 {@code 1 / (1 − 0.8) = 5} 倍。之前只按 {@code 距离 ÷ 16 + 1} 算，命中点一远，
     * 响度就掉到百分之几，等于没声音
     */
    private static final double CRIT_SOUND_GAIN_AT_LAUNCHER = 0.8D;

    /** 发射时定下的速度大小，每 tick 按回它，于是阻力不再让飞刃越飞越慢 */
    private double speed;

    /** 只用来数寿命，与存档一起存 */
    private int age;

    public Experiment057BladeEntity(EntityType<? extends Experiment057BladeEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.speed = Mth.nextDouble(level.getRandom(), MIN_SPEED, MAX_SPEED);
    }

    /**
     * 由发射者打出一片飞刃：初速是「朝视线前方」的一个随机方向，快慢也随机
     *
     * <p>方向以发射者此刻的视线为轴：偏角 0~{@value #MAX_DEVIATION_DEGREES} 度、方位角一整圈，
     * 于是它一定落在视线前方，再由制导把航线收回到视线上
     *
     * <p>这里**不**记录发射者的攻击力：伤害在每次命中时现问（见 {@link #onHitEntity}），
     * 锋利之类的近战附魔、力量效果因此当场生效，而不是发射那一刻的快照
     */
    public static Experiment057BladeEntity launch(Level level, Player shooter) {
        Experiment057BladeEntity blade = new Experiment057BladeEntity(ModEntities.EXPERIMENT_057_BLADE.get(), level);
        Vec3 look = shooter.getViewVector(1.0F);
        Vec3 origin = shooter.getEyePosition().add(look.scale(SPAWN_OFFSET));
        blade.setPos(origin.x, origin.y, origin.z);
        blade.setOwner(shooter);
        blade.setDeltaMovement(randomDirectionInFront(level.getRandom(), look).scale(blade.speed));
        blade.hasImpulse = true;
        level.addFreshEntity(blade);
        return blade;
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
        // 音效在服务端播（离得近的玩家都听得到），粒子在各自的客户端就地生成，两边都按同一个节拍
        if (this.tickCount % SWING_PERIOD_TICKS == 0) {
            this.swing();
        }
        if (this.level().isClientSide) {
            return;
        }
        this.spendLifetime(1);
        if (this.isRemoved()) {
            return;
        }
        this.steer();
    }

    /** 一声挥砍音效 + 一颗挥砍粒子，两边各管一边 */
    private void swing() {
        if (this.level().isClientSide) {
            this.level()
                    .addParticle(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
            return;
        }
        this.level()
                .playSound(
                        null,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.PLAYERS,
                        SWING_VOLUME,
                        0.9F + this.random.nextFloat() * 0.3F);
    }

    /**
     * 扣掉一段时间寿命，扣到 {@value #LIFETIME_TICKS} tick 以上就地消失
     *
     * <p>寿命按「已经活了多久」计，所以命中活物那 {@value #HIT_LIFETIME_COST_TICKS} tick（4 秒）的代价
     * 直接加在 {@code age} 上：一片 8 秒的飞刃打中两次就没了
     */
    private void spendLifetime(int ticks) {
        if (this.isRemoved()) {
            return;
        }
        this.age += ticks;
        if (this.age > LIFETIME_TICKS) {
            this.expire();
        }
    }

    /** 寿命耗尽：先撒一片红石粒子，再消失 */
    private void expire() {
        this.spawnRedstoneBurst(this.position());
        this.discard();
    }

    /** 一片红石粒子：撞击方块、寿命耗尽时各来一次 */
    private void spawnRedstoneBurst(Vec3 at) {
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(
                    DustParticleOptions.REDSTONE,
                    at.x,
                    at.y,
                    at.z,
                    REDSTONE_BURST_COUNT,
                    REDSTONE_BURST_SPREAD,
                    REDSTONE_BURST_SPREAD,
                    REDSTONE_BURST_SPREAD,
                    0.05D);
        }
    }

    /**
     * CLOS 制导：把弹体往「发射者眼睛出发的那条视线」上收，同时保持匀速前进
     *
     * <p>视线是一条线（{@code 眼睛 + t × 视线方向}），不是一个方向场。所以这里先求弹体到这条线的
     * 垂直偏距 {@code lateral}（从「眼睛到弹体」的向量里去掉沿视线的分量），再取垂足沿视线往前
     * {@value #LEAD_DISTANCE} 格的那个点作为期望方向的指向点：偏距越大偏角越大，偏距归零时方向就是视线本身。
     * 转向仍受 {@value #TURN_DEGREES_PER_TICK} 度/tick 的上限约束，于是表现为「一边向前飞一边斜着回到
     * 视线上」，最终落在线上并沿它前进
     *
     * <p>父类每 tick 乘 0.99 的空气阻力在最后一步被抹掉，于是「无重力 + 匀速 + 回到视线」三件事各归各的。
     * 没有发射者（下线、死亡、/summon）时只做匀速，不拐弯
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
        Entity owner = this.getOwner();
        if (owner != null && owner.isAlive()) {
            Vec3 line = owner.getViewVector(1.0F);
            if (line.lengthSqr() > 1.0E-8D) {
                line = line.normalize();
                Vec3 offset = this.position().subtract(owner.getEyePosition());
                Vec3 lateral = offset.subtract(line.scale(offset.dot(line)));
                Vec3 desired = line.scale(LEAD_DISTANCE).subtract(lateral);
                if (desired.lengthSqr() > 1.0E-8D) {
                    direction = turnTowards(direction, desired.normalize(), Math.toRadians(TURN_DEGREES_PER_TICK));
                }
            }
        }
        this.setDeltaMovement(direction.scale(speed));
    }

    /**
     * 把单位向量 {@code from} 朝单位向量 {@code to} 转过至多 {@code maxRadians}，返回单位向量
     *
     * <p>罗德里格斯公式，绕 {@code from × to} 这条轴转：两个单位向量的叉积与 {@code from} 垂直，
     * 因此第三项恒为 0。夹角本来就小于上限时直接返回 {@code to}，免得每次都留一点残差转不干净。
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
     * 撞方块：弹性反射，速度沿撞击面法线的分量取反，再叠一点随机偏转，实体不消失；撞上那一刻撒一片
     * 红石粒子，并播该方块自己的破坏音效
     *
     * <p>音效走 {@link SoundType#getBreakSound}，音量与音高照方块自己的
     * {@link SoundType#getVolume} / {@link SoundType#getPitch} 取，木头与玻璃听起来才不一样。
     * 音效与粒子只在服务端发出（客户端也会跑这一支命中判定，不挡住就会响两遍）
     *
     * <p>反射之后把位置挪到命中点外面一点点：命中点本身就在方块表面上，从那里起飞的下一 tick
     * 有可能又被同一个面判成「撞上」——反复弹同一个面会看起来像卡住
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
            this.spawnRedstoneBurst(surface);
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
     * 撞到活物：发射者直接结算一次这件物品的暴击伤害，并配上暴击音效与暴击粒子；随后飞刃原路弹回，
     * 并扣掉 {@value #HIT_LIFETIME_COST_TICKS} tick（4 秒）的寿命
     *
     * <p>伤害基数**每次命中现读**发射者此刻的 {@code ATTACK_DAMAGE} 属性；锋利、亡灵杀手、节肢杀手
     * 这一档走 {@link EnchantmentHelper#modifyDamage}（与 {@code AbstractArrow}、{@code Mob#doHurtTarget}
     * 同一个入口，按发射者主手的武器栈算），加在暴击之后再相加、不乘暴击——原版近战
     * {@code Player#attack} 就是这个顺序（属性伤害乘暴击，附魔加成最后相加）
     *
     * <p>倍率走 {@link CommonHooks#fireCriticalHit}：默认 1.5，发射者正在下落时 2.25（叠一层），
     * 别的模组把 {@code isCriticalHit} 改成 false 就退化成不暴击的 1.0 倍。这件物品的每一击都是暴击，
     * 所以暴击音效与粒子是每次造成伤害都放，不是偶发
     *
     * <p>原路弹回与扣寿命不看伤害有没有打进无敌帧：只要碰上了就算一次命中。飞刃本身不消失，同一只生物会
     * 连着碰好几 tick，原版无敌帧把伤害压成每 0.5 秒一下，而每一拍它都沿原路折返回去
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide) {
            return;
        }
        Vec3 at = result.getLocation();
        if (result.getEntity() instanceof LivingEntity target && this.getOwner() instanceof Player shooter) {
            boolean falling = isFalling(shooter);
            float base = (float) shooter.getAttributeValue(Attributes.ATTACK_DAMAGE);
            DamageSource source = this.damageSources().playerAttack(shooter);
            float enchantmentBonus = 0.0F;
            if (this.level() instanceof ServerLevel server) {
                enchantmentBonus =
                        EnchantmentHelper.modifyDamage(server, shooter.getWeaponItem(), target, source, base) - base;
            }
            CriticalHitEvent critical = CommonHooks.fireCriticalHit(shooter, target, true, critMultiplier(falling));
            float multiplier = critical.isCriticalHit() ? critical.getDamageMultiplier() : 1.0F;
            if (target.hurt(source, base * multiplier + enchantmentBonus)) {
                this.playCritEffects(at);
            }
        }
        this.reflectBackAlongPath();
        this.spendLifetime(HIT_LIFETIME_COST_TICKS);
    }

    /**
     * 暴击音效与暴击粒子，位置取命中点；与近战暴击用的是同一对
     *
     * <p>附近的玩家照常听到音量 1 的那一份（发射者除外）；**发射者单独收一份按距离放大音量的声音包**，
     * 音量由「希望他听到的响度」反推（见 {@link #CRIT_SOUND_GAIN_AT_LAUNCHER}），于是弹体飞出几十上百格
     * 再打中时，发射者听到的仍是八成响度，而不是随距离掉到听不见
     */
    private void playCritEffects(Vec3 at) {
        Entity owner = this.getOwner();
        this.level()
                .playSound(
                        owner instanceof Player player ? player : null,
                        at.x,
                        at.y,
                        at.z,
                        SoundEvents.PLAYER_ATTACK_CRIT,
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, CRIT_PARTICLE_COUNT, 0.25D, 0.25D, 0.25D, 0.15D);
            if (owner instanceof ServerPlayer listener && listener.connection != null) {
                double distance = listener.position().distanceTo(at);
                float volume = (float)
                        Math.max(1.0D, distance / (SOUND_RANGE_PER_VOLUME * (1.0D - CRIT_SOUND_GAIN_AT_LAUNCHER)));
                listener.connection.send(new ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_ATTACK_CRIT),
                        SoundSource.PLAYERS,
                        at.x,
                        at.y,
                        at.z,
                        volume,
                        1.0F,
                        this.random.nextLong()));
            }
        }
    }

    /**
     * 命中活物之后的反射：原路弹回，即速度方向整个取反、大小不变
     *
     * <p>既不是弹向发射者，也不是随机方向——打中什么就沿原来那条线原路返回。回程仍受制导影响，
     * 每 tick 至多转 {@value #TURN_DEGREES_PER_TICK} 度朝视线靠拢
     */
    private void reflectBackAlongPath() {
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-8D) {
            return;
        }
        this.setDeltaMovement(motion.normalize().reverse().scale(this.speed));
        this.hasImpulse = true;
    }

    /**
     * 这次命中的暴击倍率：{@value #CRIT_MULTIPLIER} 倍，发射者正在下落时再叠一层
     *
     * <p>单独拎出来是为了让规格能被直接钉住（{@code gametest/Experiment057Tests}），
     * 也让 {@link CommonHooks#fireCriticalHit} 的默认倍率有唯一来源
     */
    public static float critMultiplier(boolean falling) {
        return falling ? CRIT_MULTIPLIER * CRIT_MULTIPLIER : CRIT_MULTIPLIER;
    }

    /** 已经用掉的寿命（tick），等于 {@code age}；命中活物会额外加 {@value #HIT_LIFETIME_COST_TICKS} */
    public int getAge() {
        return this.age;
    }

    /**
     * 「此刻发射者正在下落」：判据照抄原版近战暴击那几条，只去掉致盲与疾跑（那两条与下落无关）
     *
     * <p>下落判定就是 {@code fallDistance > 0}，再排掉站在地上、扶着梯子、泡在水里与被载具带着这几种
     * 其实不算下落的情况
     */
    public static boolean isFalling(LivingEntity entity) {
        return entity.fallDistance > 0.0F
                && !entity.onGround()
                && !entity.onClimbable()
                && !entity.isInWater()
                && !entity.isPassenger();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.age = compound.getInt("Age");
        this.speed = compound.contains("BladeSpeed") ? compound.getDouble("BladeSpeed") : this.speed;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Age", this.age);
        compound.putDouble("BladeSpeed", this.speed);
    }
}
