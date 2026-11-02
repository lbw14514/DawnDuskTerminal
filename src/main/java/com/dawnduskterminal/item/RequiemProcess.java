package com.dawnduskterminal.item;

import com.dawnduskterminal.network.RequiemStartPayload;
import com.dawnduskterminal.network.RequiemStopPayload;
import com.dawnduskterminal.registry.ModDamageTypes;
import com.dawnduskterminal.registry.ModParticles;
import com.dawnduskterminal.registry.ModSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 试验物品 095（镇魂曲）按住右键期间的那一整段
 *
 * <p>一段「按住」= 一个 {@code RequiemProcess}。开始按住时由 {@link Experiment095Item#use} 起一段，
 * 之后每 tick 由 {@code DdtServerEvents} 推进；松手、换物品、物品碎掉、死亡、下线、换维度都会让
 * {@link #refresh()} 返回 false，于是这一段收声、退场
 *
 * <p>为什么不像 032/033 那两支长矛一样写在 {@code onUseTick} 里：那一路只有「右键还按着」这一种
 * 结束方式收得到货——换物品走的是 {@code stopUsingItem}，物品碎掉之后 {@code onUseTick} 直接不再被调，
 * 而这里每次结束都要给客户端发一颗停止包（否则那只循环音会一直响下去）。挂在服务端 tick 上就不必
 * 依赖任何一个回调，自己每 tick 看一眼还在不在按着就够了，与 043、066 是同一个做法
 *
 * <p>每 tick 做的事：对 32 格内除自己以外的活物调一次 {@code hurt}（伤害
 * {@code 0.5 × (32 - 距离)}，见 {@link #damageAt}，不绕无敌帧，所以实际节拍是原版那 0.5 秒一下）；
 * 每 {@value #RING_INTERVAL} tick 甩出一圈 {@value #RING_POINTS} 颗音符；每 {@value #CHARGE_INTERVAL} tick
 * 扣 1 点耐久，并把「开始放」那颗包重发一遍
 */
public final class RequiemProcess {
    /** 作用半径：伤害与音符都认这个数 */
    public static final double RADIUS = 32.0D;
    /** 每格距离换多少伤害：{@code 0.5 × (32 - 距离)}，贴脸 16 点，32 格外 0 点 */
    public static final double DAMAGE_PER_BLOCK = 0.5D;
    /** 一圈音符的间隔，10 tick 正好 0.5 秒 */
    public static final int RING_INTERVAL = 10;
    /** 一圈多少颗 */
    public static final int RING_POINTS = 24;
    /** 每圈颗数的浮动范围：24 ± 4，两个相邻的圈连颗数都不一样 */
    public static final int RING_POINTS_JITTER = 4;
    /** 音符的横向初速，3.2 格每 tick 正好是每秒 64 格 */
    public static final double OUTWARD_SPEED = 3.2D;
    /** 每颗的速度浮动：±8%，一圈 24 颗因此有快有慢，不会整齐地往外走 */
    public static final double SPEED_JITTER = 0.16D;
    /** 每颗的角度浮动：以格点间距为单位，0.7 就是最多偏 ±35% 个格点 */
    public static final double ANGLE_JITTER = 0.7D;
    /** 出生点往外的随机半径：让整圈不是一个正圆，而是一圈有厚度的环 */
    public static final double SPAWN_RADIUS = 0.7D;
    /** 出生点在竖直方向的随机幅度：整圈高低起伏 ±0.4 格 */
    public static final double SPAWN_LIFT = 0.8D;
    /** 每颗的竖直初速：只给一点点随机，真正的上飘交给粒子自己每 tick 往上托 */
    public static final double SPAWN_RISE = 0.06D;
    /** 扣耐久与重发启动包的间隔，20 tick 正好 1 秒 */
    public static final int CHARGE_INTERVAL = 20;
    /** 「周围 32 格内可以听见」，与 {@code sounds.json} 里每条曲子的 {@code attenuation_distance} 同一个数 */
    public static final double HEARING_RADIUS = 32.0D;
    /** 停止包发多远：比听得见的范围宽一圈，让按住期间走远的人也把声道放掉 */
    public static final double STOP_RADIUS = 64.0D;

    private static final List<RequiemProcess> ACTIVE = new ArrayList<>();

    private final Player caster;
    private final UUID casterId;
    private final ServerLevel level;
    private final int track;
    private int age;
    private int rings;
    private int charges;
    private int strikeTries;
    private int strikesLanded;
    private boolean done;

    private RequiemProcess(ServerLevel level, Player caster, int track) {
        this.level = level;
        this.caster = caster;
        this.casterId = caster.getUUID();
        this.track = track;
    }

    /** 物品那一路：摇一支曲子，把「开始放」发下去 */
    public static RequiemProcess start(ServerLevel level, Player caster) {
        int count = ModSounds.requiemTrackCount();
        int track = count <= 0 ? -1 : level.getRandom().nextInt(count);
        RequiemProcess process = new RequiemProcess(level, caster, track);
        ACTIVE.add(process);
        process.play();
        return process;
    }

    /** 某个施放者手上有没有一段在跑：防止同一刻起两段（`use` 两边都会走到） */
    public static boolean isActive(Player caster) {
        for (RequiemProcess process : ACTIVE) {
            if (process.casterId.equals(caster.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 测试入口：不进 {@code ACTIVE}，也不发任何包，曲子下标由调用方指定，自己拿着 {@link #tick}
     *
     * <p>伤害与音符那两条路都照走（粒子在服务端发、包里没有速度以外的随机量），所以 Gamest 能直接
     * 数每一拍落下的伤害与每一圈甩出去的颗数
     */
    public static RequiemProcess detached(ServerLevel level, Player caster, int track) {
        return new RequiemProcess(level, caster, track);
    }

    public static void tickAll() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<RequiemProcess> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            RequiemProcess process = iterator.next();
            process.tick();
            if (process.done) {
                iterator.remove();
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public void tick() {
        if (this.done) {
            return;
        }
        if (!this.refresh()) {
            this.finish();
            return;
        }
        this.age++;
        this.strike();
        if (this.age % RING_INTERVAL == 0) {
            this.emitRing();
        }
        if (this.age % CHARGE_INTERVAL == 0) {
            this.chargeDurability();
        }
    }

    /**
     * 这一段还该不该继续：施放者活着、还在这个维度、右键还按着、手上拿的还是 095
     *
     * <p>物品碎掉之后 {@code getUseItem()} 是空的，{@code getItem()} 拿到空气，这一条自然就断了
     */
    private boolean refresh() {
        if (this.caster.isRemoved() || !this.caster.isAlive() || this.caster.level() != this.level) {
            return false;
        }
        return this.caster.isUsingItem() && this.caster.getUseItem().getItem() instanceof Experiment095Item;
    }

    /** 距离换成伤害：32 格外与离谱的负数一律 0，省得后面还要每处判一次 */
    public static float damageAt(double distance) {
        if (distance < 0.0D || distance >= RADIUS) {
            return 0.0F;
        }
        return (float)((RADIUS - distance) * DAMAGE_PER_BLOCK);
    }

    private void strike() {
        DamageSource source = this.level.damageSources().source(ModDamageTypes.EXPERIMENT_095, this.caster);
        AABB search = this.caster.getBoundingBox().inflate(RADIUS);
        List<LivingEntity> targets = this.level.getEntitiesOfClass(LivingEntity.class, search, this::isTarget);
        for (LivingEntity target : targets) {
            float damage = damageAt(this.caster.distanceTo(target));
            if (damage <= 0.0F) {
                continue;
            }
            this.strikeTries++;
            if (target.hurt(source, damage)) {
                this.strikesLanded++;
            }
        }
    }

    /** 「除使用者以外的所有生物」：自己、已经倒下与旁观者之外都算，友军与创造模式那两类交给 {@code hurt} 自己判 */
    private boolean isTarget(LivingEntity other) {
        return other != this.caster && other.isAlive() && !other.isSpectator();
    }

    /**
     * 一圈音符：从身体中段朝四周甩出去，初速大约 3.2 格每 tick
     *
     * <p>整圈不是一条正圆：每一圈先整体转一个随机角度（{@link #RING_POINTS} 颗的格点因此不会和前一圈重合），
     * 颗数按 {@link #RING_POINTS_JITTER} 上下浮动，每颗再各自抖角度、抖速度、抖出生半径与高度，
     * 竖直方向只给一点点随机初速——真正的上飘由粒子自己每 tick 往上托，见
     * {@link com.dawnduskterminal.client.RequiemNoteParticle}
     *
     * <p>速度走 {@code sendParticles} 的 {@code count == 0} 那条路（一颗一包，速度按原值落地）
     */
    private void emitRing() {
        this.rings++;
        RandomSource random = this.level.getRandom();
        double centreX = this.caster.getX();
        double centreY = this.caster.getY() + this.caster.getBbHeight() * 0.5D;
        double centreZ = this.caster.getZ();
        double phase = random.nextDouble() * Mth.TWO_PI;
        int points = Math.max(4, RING_POINTS + random.nextInt(RING_POINTS_JITTER * 2 + 1) - RING_POINTS_JITTER);
        double step = Mth.TWO_PI / points;
        for (int i = 0; i < points; i++) {
            double angle = phase + step * i + (random.nextDouble() - 0.5D) * step * ANGLE_JITTER;
            double speed = OUTWARD_SPEED * (1.0D + (random.nextDouble() - 0.5D) * SPEED_JITTER);
            double radius = random.nextDouble() * SPAWN_RADIUS;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            // 水平分量取 (cos, -sin)：x 向东、z 向南，俯视是逆时针，和砷铅铁爆炸那圈同一个方向
            this.level.sendParticles(ModParticles.REQUIEM_NOTE.get(),
                centreX + cos * radius,
                centreY + (random.nextDouble() - 0.5D) * SPAWN_LIFT,
                centreZ - sin * radius,
                0,
                cos * speed,
                (random.nextDouble() - 0.5D) * SPAWN_RISE,
                -sin * speed,
                1.0D);
        }
    }

    /** 每秒扣 1 点耐久；碎了就收场，别让客户端那支曲子接着响 */
    private void chargeDurability() {
        ItemStack stack = this.caster.getUseItem();
        if (stack.isEmpty()) {
            this.finish();
            return;
        }
        this.charges++;
        stack.hurtAndBreak(1, this.caster, LivingEntity.getSlotForHand(this.caster.getUsedItemHand()));
        if (stack.isEmpty()) {
            this.finish();
            return;
        }
        this.play();
    }

    /** 让 32 格内的玩家（含施放者）各起一支循环音；重复的包在客户端会被忽略 */
    private void play() {
        PacketDistributor.sendToPlayersNear(this.level, null,
            this.caster.getX(), this.caster.getY(), this.caster.getZ(), HEARING_RADIUS,
            new RequiemStartPayload(this.caster.getId(), this.track));
    }

    /** 收场：把停止包发给 64 格内的玩家，之后这一段不再 tick */
    private void finish() {
        this.done = true;
        PacketDistributor.sendToPlayersNear(this.level, null,
            this.caster.getX(), this.caster.getY(), this.caster.getZ(), STOP_RADIUS,
            new RequiemStopPayload(this.caster.getId()));
    }

    public int age() {
        return this.age;
    }

    /** 甩出去几圈音符 */
    public int rings() {
        return this.rings;
    }

    /** 扣过几次耐久（创造模式下 {@code hurtAndBreak} 不真扣，所以这是「扣了几次」而不是「掉了几点」） */
    public int charges() {
        return this.charges;
    }

    public int strikeTries() {
        return this.strikeTries;
    }

    /** 真正打出伤害的次数：同一次施压里被无敌帧挡下的那些不算 */
    public int strikesLanded() {
        return this.strikesLanded;
    }

    public boolean finished() {
        return this.done;
    }
}
