package com.dawnduskterminal.item;

import com.dawnduskterminal.entity.GhastKin;
import com.dawnduskterminal.registry.ModDamageTypes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;
import twilightforest.init.TFParticleType;
import twilightforest.init.TFSounds;

/**
 * 试验物品 066 那本恶魂陷阱书压出来的场：挂在施放者身上的一个吸口，把视线走廊里的活物往吸口里拽，
 * 每 tick 试一次 7 点伤害
 *
 * <p>整套照暮色森林 {@code twilightforest:ghast_trap} 方块写。方块那边（{@code GhastTrapBlockEntity#tickActive}）
 * 是：场心是方块自己那一格，场里的生物<b>每 tick</b> 被 {@code setDeltaMovement} 拽向场心
 * （{@code (场心 - 生物) * 0.1}），并且每 tick 试一次伤害；<b>粒子与音效也逐拍照抄</b>，见
 * {@link #spawnParticles()} 与 {@link #playTrapSound()}。本模组这份的区别：没有「先杀恶魂充能」那一段，
 * 单击就开；场心从方块换成<b>施放者自己</b>——眼睛往前 {@value #TRAP_OFFSET} 格那个点，每 tick 跟着视线转
 * （与 043 的枪口同一个做法），所以场上的一切都是朝<b>施放者</b>被吸过来的；能吸到多远换成
 * 「从眼睛出发、{@value #LENGTH} 格长、轴线外 {@value #RADIUS} 格粗的一条走廊」，
 * 判定与 {@link FireJetProcess} 那套右边/上边投影同一个写法
 *
 * <p><b>伤害就是每 tick 打一次，不排节拍</b>：原版 {@code LivingEntity#hurt} 自己那套无敌帧会把连续施压
 * 自动压成<b>每 0.5 秒一下</b>，所以 120 tick 里正好 12 下、每下 {@value #DAMAGE} 点。不要绕过它：伤害类型
 * 不进 {@code #minecraft:bypasses_cooldown}，无敌帧就是这个场的节奏本身
 *
 * <p>对象是<b>所有活物</b>：怪物、动物、玩家都会被拽、都会掉血——这本书以后要装到敌怪身上打玩家；
 * <b>掉落物</b>也一样被吸过去（伤害只落在活物身上），所以拿它捡地上的东西是顺手的用法。
 * 所以不能只认 {@code Mob}。唯一排除的是施放者自己（不然吸口就在自己脸上），与观察者模式。伤害走
 * {@link ModDamageTypes#EXPERIMENT_066}，那条类型在 {@code no_knockback} 里，不然伤害自带的那点推力
 * 会和牵引互相抵消
 *
 * <p>过程整段挂在 {@code ServerTickEvent.Post} 上推进（{@code tickAll} / {@code clear}），施放者死了、
 * 换维度了就断掉。手里换掉东西不影响——书已经翻开了
 */
public final class GhastTrapProcess {
    /** 整段多久：6 秒 */
    public static final int TOTAL_TICKS = 120;
    /** 每一下 7 点；每 tick 试一次，实际落下的节奏由无敌帧定成每 0.5 秒一次 */
    public static final float DAMAGE = 7.0F;
    /** 吸程：走廊从眼睛往前 16 格 */
    public static final double LENGTH = 16.0;
    /** 走廊半粗：轴线外 4 格都算在场里 */
    public static final double RADIUS = 4.0;
    /** 牵引速度系数，照暮色那块方块的 0.1 */
    public static final double PULL = 0.1;
    /**
     * 场心在施放者眼睛往前多少格：恶魂陷阱的场心是方块自己那一格，这里的场心挂在施放者身上，
     * 每 tick 跟着视线一起转，跟 043 的枪口同一个做法
     */
    public static final double TRAP_OFFSET = 1.0;
    /**
     * 恶魂陷阱那圈往里收的粒子，{@code assets/twilightforest/particles/ghast_trap.json} 那一支。
     * 只用资源 ID 取，不 import 暮色森林（缺它的 jar 也要能起）
     */
    public static final SimpleParticleType TRAP_PARTICLE = TFParticleType.GHAST_TRAP.get();
    /** 收场那阵大烟，方块的 {@code counter > 100} 分支用的同一支 */
    public static final SimpleParticleType TRAP_SMOKE = TFParticleType.HUGE_SMOKE.get();
    /** 预热音，方块在前 {@value #WARMUP_TICKS} tick 每 tick 放一次 */
    public static final SoundEvent TRAP_WARMUP_SOUND = TFSounds.GHAST_TRAP_WARMUP.get();
    /** 通电音，{@value #WARMUP_TICKS}~{@value #SPINDOWN_FROM} tick 每 tick 放一次 */
    public static final SoundEvent TRAP_ON_SOUND = TFSounds.GHAST_TRAP_ON.get();
    /** 收场音，{@value #SPINDOWN_FROM} tick 之后每 tick 放一次 */
    public static final SoundEvent TRAP_SPINDOWN_SOUND = TFSounds.GHAST_TRAP_SPINDOWN.get();
    /** 方块的相位分界：0~29 预热、30~79 通电、80 起收场 */
    public static final int WARMUP_TICKS = 30;
    public static final int SPINDOWN_FROM = 80;
    /** 方块的 {@code counter > 100} 之后每 4 tick 冒一阵大烟 */
    public static final int SMOKE_FROM = 100;
    public static final int SMOKE_INTERVAL = 4;
    /** 音效与方块一样：音量 1、音高 4 */
    public static final float SOUND_VOLUME = 1.0F;
    public static final float SOUND_PITCH = 4.0F;
    private static final List<GhastTrapProcess> ACTIVE = new ArrayList<>();
    private final ServerLevel level;
    private final LivingEntity owner;
    private Vec3 eye;
    private Vec3 direction;
    private Vec3 right;
    private Vec3 up;
    private int age;
    private int damageTries;
    private int hurtLanded;
    private int targetsCaught;
    private int itemsDragged;
    private boolean done;

    private GhastTrapProcess(ServerLevel level, LivingEntity owner) {
        this.level = level;
        this.owner = owner;
        this.updateAim();
    }

    /**
     * 给这个施放者开一个场；已经有在跑的就直接返回那一个，不叠第二个
     *
     * <p>043 与 066 的物品冷却自己就挡住了重复施放，这里挡的是别的调用方（命令、别的 AI、调试代码）
     */
    public static GhastTrapProcess start(ServerLevel level, LivingEntity owner) {
        for (GhastTrapProcess running : ACTIVE) {
            if (running.owner == owner) {
                return running;
            }
        }
        GhastTrapProcess process = new GhastTrapProcess(level, owner);
        ACTIVE.add(process);
        return process;
    }

    /** 测试与调试用：不进 {@link #ACTIVE}，自己 hold 着 tick */
    public static GhastTrapProcess detached(ServerLevel level, LivingEntity owner) {
        return new GhastTrapProcess(level, owner);
    }

    public static void tickAll() {
        if (!ACTIVE.isEmpty()) {
            Iterator<GhastTrapProcess> iterator = ACTIVE.iterator();

            while (iterator.hasNext()) {
                GhastTrapProcess process = iterator.next();
                process.tick();
                if (process.done) {
                    iterator.remove();
                }
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    /** 某个施放者手上还有几个场在跑：测试里别的用例同时开着别的场，全局计数会串台 */
    public static int activeCount(LivingEntity owner) {
        int count = 0;
        for (GhastTrapProcess process : ACTIVE) {
            if (process.owner == owner) {
                count++;
            }
        }
        return count;
    }

    public void tick() {
        if (this.done) {
            return;
        }
        if (!this.refresh()) {
            this.done = true;
            return;
        }

        this.age++;
        this.spawnParticles();
        this.playTrapSound();
        this.pull();
        this.damage();
        if (this.age >= TOTAL_TICKS) {
            this.done = true;
        }
    }

    /** 施放者还在、还活着、还在同一个维度就把吸口重新绑到他眼睛与视线上 */
    private boolean refresh() {
        if (this.owner.isRemoved() || !this.owner.isAlive() || this.owner.level() != this.level) {
            return false;
        }
        this.updateAim();
        return true;
    }

    private void updateAim() {
        Vec3 look = this.owner.getViewVector(1.0F);
        if (look.lengthSqr() < 1.0E-6) {
            look = new Vec3(0.0, 0.0, 1.0);
        }

        this.setAxes(look.normalize());
        this.eye = this.owner.getEyePosition();
    }

    private void setAxes(Vec3 look) {
        this.direction = look;
        this.right = perpendicularRight(look);
        this.up = this.right.cross(look).normalize();
    }

    private static Vec3 perpendicularRight(Vec3 direction) {
        Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-6) {
            side = direction.cross(new Vec3(1.0, 0.0, 0.0));
        }

        return side.normalize();
    }

    /** 场心：施放者眼睛往前 {@value #TRAP_OFFSET} 格，每 tick 跟着视线转 */
    public Vec3 trapCenter() {
        return this.eye.add(this.direction.scale(TRAP_OFFSET));
    }

    /**
     * 每一拍把场里的东西往<b>场心</b>（也就是施放者身上）拽：速度照暮色那块方块，
     * {@code (场心 - 目标) * 0.1}，不封顶、不加别的花样。活物与掉落物一样拽，伤害只落在活物身上
     */
    private void pull() {
        Vec3 center = this.trapCenter();
        for (LivingEntity target : this.targets()) {
            target.setDeltaMovement(center.subtract(target.position()).scale(PULL));
            target.hasImpulse = true;
        }
        for (ItemEntity item : this.items()) {
            item.setDeltaMovement(center.subtract(item.position()).scale(PULL));
            item.hasImpulse = true;
            this.itemsDragged++;
        }
    }

    /**
     * 每 tick 试一次。原版无敌帧会把连续的 {@code hurt} 自己排成每 0.5 秒一次，
     * 所以这里不排节拍、也不绕冷却
     */
    private void damage() {
        this.damageTries++;
        DamageSource source = this.level.damageSources().source(ModDamageTypes.EXPERIMENT_066, this.owner);
        for (LivingEntity target : this.targets()) {
            // 暮色恶魂的免伤在 UrGhast#hurt 里按相位打折，先把相位压回去，见 UrGhastPhaseSuppressor
            UrGhastPhaseSuppressor.avoidTantrum(target);
            if (target.hurt(source, DAMAGE)) {
                this.hurtLanded++;
            }
        }
    }

    /** 走廊粗筛用的立方：从眼睛到射程末端，两边各放 {@value #RADIUS} 加一格 */
    private AABB broadBox() {
        return new AABB(this.eye, this.eye.add(this.direction.scale(LENGTH))).inflate(RADIUS + 1.0);
    }

    /** 场里那一批活物：视线走廊里活着的活物，施放者自己与观察模式不算 */
    private List<LivingEntity> targets() {
        List<LivingEntity> found = this.level.getEntitiesOfClass(LivingEntity.class, this.broadBox(),
            entity -> !GhastKin.isKin(this.owner, entity) && entity.isAlive() && !entity.isSpectator()
                && this.insideField(entity));
        this.targetsCaught = this.targetsCaught + found.size();
        return found;
    }

    /**
     * 走廊判定：把活物包围盒中心投到「视线轴 × 右 × 上」这三条轴上，沿轴在 0~{@value #LENGTH} 之间、
     * 两条横轴都在 {@value #RADIUS} 加包围盒半径以内就算在场里
     */
    /** 场里那一批掉落物：走廊里、还在地上的东西，一样往里吸，但打不了它们 */
    private List<ItemEntity> items() {
        return this.level.getEntitiesOfClass(ItemEntity.class, this.broadBox(), this::insideField);
    }

    /** 走廊判定，见 {@link #insideField(Vec3, Vec3, Entity)} */
    private boolean insideField(Entity entity) {
        return insideField(this.eye, this.direction, entity);
    }

    /** 同一条判定，纯几何版本：只吃「眼睛 + 视线 + 目标」，不依赖场在不在跑 */
    public static boolean insideField(Vec3 eye, Vec3 look, Entity entity) {
        Vec3 direction = look.normalize();
        Vec3 right = perpendicularRight(direction);
        Vec3 up = right.cross(direction).normalize();
        AABB box = entity.getBoundingBox();
        Vec3 relative = box.getCenter().subtract(eye);
        double extent = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) * 0.5;
        double along = relative.dot(direction);
        if (along < -extent || along > LENGTH + extent) {
            return false;
        }

        return Math.abs(relative.dot(right)) <= RADIUS + extent
            && Math.abs(relative.dot(up)) <= RADIUS + extent;
    }

    /**
     * 恶魂陷阱那一圈粒子，逐项照搬 {@code GhastTrapBlockEntity#tickActive} 的客户端分支，场心就是方块中心：
     * {@code vx = cos(counter/10) * 2.5}、{@code vy = 20}、{@code vz = sin(counter/10) * 2.5}，
     * 一拍五颗 {@code twilightforest:ghast_trap}——两条对着转的满速臂、一条半速臂、两圈四分之一的近螺旋，
     * 全部错在同一个场心上；{@code counter > 100} 之后每 4 tick 再补一颗 {@code twilightforest:huge_smoke}
     * （偏移 {@code cos*0.05, 0.25, sin*0.05}）
     *
     * <p>恶魂陷阱的粒子是「从 {@code 场心 + 偏移} 收进场心」，所以这五颗就是五条往吸口里收的螺旋臂，
     * 收的是<b>施放者身上那个点</b>，不是走廊上的几个点
     */
    private void spawnParticles() {
        Vec3 center = this.trapCenter();
        double spin = this.age / 10.0;
        double vx = Math.cos(spin) * 2.5;
        double vy = 20.0;
        double vz = Math.sin(spin) * 2.5;
        this.spawnParticle(TRAP_PARTICLE, center, this.particleOffset(vx, vy, vz));
        this.spawnParticle(TRAP_PARTICLE, center, this.particleOffset(-vx, vy, -vz));
        this.spawnParticle(TRAP_PARTICLE, center, this.particleOffset(-vx, vy / 2.0, vz));
        this.spawnParticle(TRAP_PARTICLE, center, this.particleOffset(vx / 2.0, vy / 4.0, vz / 2.0));
        this.spawnParticle(TRAP_PARTICLE, center, this.particleOffset(-vx / 2.0, vy / 4.0, -vz / 2.0));
        if (this.age > SMOKE_FROM && this.age % SMOKE_INTERVAL == 0) {
            this.spawnParticle(TRAP_SMOKE, center, this.particleOffset(Math.cos(spin) * 0.05, 0.25, Math.sin(spin) * 0.05));
        }
    }

    /**
     * 把方块那三个偏移搬到<b>视线</b>上：方块的吸口朝世界 Y，{@code vy = 20} 就是「从二十格高空收下来」；
     * 这里的吸口朝施放者的视线，所以「吸口向」那一份喂给 {@code direction}，另外两份喂给右轴与上轴
     * ——粒子沿视线从远处收进吸口，而不是从脑门上方往下掉
     */
    public Vec3 particleOffset(double spinRight, double mouth, double spinUp) {
        return this.right.scale(spinRight).add(this.direction.scale(mouth)).add(this.up.scale(spinUp));
    }

    /**
     * 发一颗粒子。{@code count = 0} 是关键：客户端那条分支走
     * {@code addParticle(type, x, y, z, maxSpeed * xDist, maxSpeed * yDist, maxSpeed * zDist)}，
     * 速度按原值落地、不掺随机，正好等于方块客户端 {@code addParticle(...)} 的六个参数
     */
    private void spawnParticle(ParticleOptions particle, Vec3 center, Vec3 offset) {
        this.level.sendParticles(particle, center.x, center.y, center.z, 0, offset.x, offset.y, offset.z, 1.0);
    }

    /**
     * 恶魂陷阱的三支音效，与方块同样逐拍放：前 {@value #WARMUP_TICKS} tick 预热音，
     * 到 {@value #SPINDOWN_FROM} tick 通电音，之后收场音，音量 {@value #SOUND_VOLUME}、音高 {@value #SOUND_PITCH}，
     * 都从场心出
     */
    private void playTrapSound() {
        SoundEvent sound = this.age < WARMUP_TICKS ? TRAP_WARMUP_SOUND
            : this.age < SPINDOWN_FROM ? TRAP_ON_SOUND : TRAP_SPINDOWN_SOUND;
        Vec3 center = this.trapCenter();
        this.level.playSound(null, center.x, center.y, center.z, sound, SoundSource.BLOCKS, SOUND_VOLUME, SOUND_PITCH);
    }

    public int age() {
        return this.age;
    }

    public int damageTries() {
        return this.damageTries;
    }

    public int hurtLanded() {
        return this.hurtLanded;
    }

    public int targetsCaught() {
        return this.targetsCaught;
    }

    public int itemsDragged() {
        return this.itemsDragged;
    }

    public boolean finished() {
        return this.done;
    }
}
