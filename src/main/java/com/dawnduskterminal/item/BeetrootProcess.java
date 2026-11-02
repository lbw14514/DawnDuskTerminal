package com.dawnduskterminal.item;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.network.Experiment055RayPayload;
import com.dawnduskterminal.network.Experiment055SoundPayload;
import com.dawnduskterminal.registry.ModDamageTypes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.resources.ResourceKey;

/**
 * 试验物品 055 按住右键期间的那个场
 *
 * <p>一段「按住」= 一个 {@code BeetrootProcess}。开始按住时由 {@link Experiment055Item#use} 起一段，
 * 之后每 tick 由 {@code DdtServerEvents} 推进；松手、换物品、物品碎掉、死亡、下线、换维度都会让
 * {@link #refresh()} 返回 false，于是这一段收声、退场（与 095 的镇魂曲同一套骨架）
 *
 * <p>充能（前 {@value #CHARGE_TICKS} tick = 5 秒）：影响半径从 0 线性涨到 {@value #MAX_RADIUS}，
 * 伤害系数从 0 线性涨到 {@value #MAX_DAMAGE_FACTOR}，两者都按 {@link #progress()} 读同一个进度。
 * 圈内除自己以外的活物每 tick 试一次 {@code hurt}，伤害是 {@code 系数 × (半径 − 距离)}，不绕无敌帧
 * （原版那 0.5 秒的无敌帧自然把节拍排出来，与 066、095 一致），每真打中一次就在目标身上撒一片红石粒子
 *
 * <p>充满之后（{@code age >= CHARGE_TICKS}）：半径与系数不再涨，另外每 tick 对自己也来 1 点同类型伤害
 * ——这一支的代价从这里开始
 *
 * <p>每秒（{@value #CHARGE_INTERVAL} tick）做三件事：扣 1 点耐久、把影响半径内的群系刷成
 * {@link #TARGET_BIOME}、把当前该响的那支循环音重发一遍（让这一秒里才走进圈里的人也听得见）
 */
public final class BeetrootProcess {
    /** 充能时长：100 tick 正好 5 秒，也是半径与系数的满值时刻 */
    public static final int CHARGE_TICKS = 100;
    /** 满充能时的影响半径 */
    public static final double MAX_RADIUS = 24.0D;
    /** 满充能时的伤害系数：伤害 = 系数 × (半径 − 距离) */
    public static final double MAX_DAMAGE_FACTOR = 0.3D;
    /** 每秒一次的那一拍：扣耐久、刷群系、重发音效 */
    public static final int CHARGE_INTERVAL = 20;
    /** 充满之后每 tick 对自己造成的伤害 */
    public static final float CASTER_DAMAGE = 1.0F;
    /** 「周围听得见」：与 {@code sounds.json} 里那三支的 {@code attenuation_distance} 同一个数 */
    public static final double HEARING_RADIUS = 32.0D;
    /** 停止包发多远：比听得见的范围宽一圈，按住期间走远的人也得把声道放掉 */
    public static final double STOP_RADIUS = 64.0D;
    /** 打中一次撒几颗红石粒子 */
    public static final int REDSTONE_BURST = 12;
    /** 刷群系的半径是当前影响半径的多少倍 */
    public static final double BIOME_RADIUS_SCALE = 0.7D;
    /** 射线长度是当前影响半径的多少倍：每 tick 一条随机方向的射线 */
    public static final double RAY_LENGTH_SCALE = 1.5D;
    /** 射线包发多远：与 {@code sendParticles} 的可见距离同一个数，超过这个距离的玩家收不到粒子也无所谓 */
    public static final double RAY_RADIUS = 32.0D;
    /** 刷成的群系：与 052 那颗方雷炸完之后刷的是同一个 */
    public static final ResourceKey<Biome> TARGET_BIOME =
        ResourceKey.create(Registries.BIOME, DawnDuskTerminal.id("old_growth_birch_forest"));

    private static final List<BeetrootProcess> ACTIVE = new ArrayList<>();

    private final Player caster;
    private final UUID casterId;
    private final ServerLevel level;
    private int age;
    private int charges;
    private int biomeFills;
    private int damageTries;
    private int hitsLanded;
    private int enchantRays;
    private int biomeMinY = Integer.MAX_VALUE;
    private int biomeMaxY = Integer.MIN_VALUE;
    private int soundMode = -1;
    private boolean done;

    private BeetrootProcess(ServerLevel level, Player caster) {
        this.level = level;
        this.caster = caster;
        this.casterId = caster.getUUID();
    }

    public static BeetrootProcess start(ServerLevel level, Player caster) {
        BeetrootProcess process = new BeetrootProcess(level, caster);
        ACTIVE.add(process);
        process.sendSound(Experiment055SoundPayload.MODE_CHARGE);
        return process;
    }

    /** 某个施放者手上有没有一段在跑：防止同一刻起两段（{@code use} 两端都会走到） */
    public static boolean isActive(Player caster) {
        for (BeetrootProcess process : ACTIVE) {
            if (process.casterId.equals(caster.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 测试入口：不进 {@code ACTIVE}，自己拿着 {@link #tick}；包里那三支音效照发
     * （{@code PacketDistributor} 只发给真实在线的玩家，测试服里没有，等于不发）
     */
    public static BeetrootProcess detached(ServerLevel level, Player caster) {
        return new BeetrootProcess(level, caster);
    }

    public static void tickAll() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<BeetrootProcess> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            BeetrootProcess process = iterator.next();
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
        boolean wasCharged = this.charged();
        this.age++;
        if (!wasCharged && this.charged()) {
            // 充满那一下：客户端收到它就把 charge 收掉、放一次 full
            this.sendSound(Experiment055SoundPayload.MODE_FULL);
        }
        this.strike();
        this.sendRay();
        if (this.charged()) {
            this.hurtCaster();
        }
        if (this.age % CHARGE_INTERVAL == 0) {
            this.fillBiome();
            this.chargeDurability();
            if (this.done) {
                return;
            }
            this.refreshSound();
        }
    }

    /**
     * 这一段还该不该继续：施放者活着、还在这个维度、右键还按着、手上拿的还是 055
     *
     * <p>物品碎掉之后 {@code getUseItem()} 是空的，{@code getItem()} 拿到空气，这一条自然就断了
     */
    private boolean refresh() {
        if (this.caster.isRemoved() || !this.caster.isAlive() || this.caster.level() != this.level) {
            return false;
        }
        return this.caster.isUsingItem() && this.caster.getUseItem().getItem() instanceof Experiment055Item;
    }

    /** 充能进度：0 就是刚按下，1 就是满（之后一直是 1） */
    public double progress() {
        return Math.min(this.age, CHARGE_TICKS) / (double)CHARGE_TICKS;
    }

    public boolean charged() {
        return this.age >= CHARGE_TICKS;
    }

    /** 当前影响半径：{@code MAX_RADIUS × 进度} */
    public double radius() {
        return MAX_RADIUS * this.progress();
    }

    /** 当前伤害系数：{@code MAX_DAMAGE_FACTOR × 进度} */
    public double damageFactor() {
        return MAX_DAMAGE_FACTOR * this.progress();
    }

    /** 一次命中的伤害：{@code 系数 × (半径 − 距离)}，半径之外与离谱的负数一律 0 */
    public static float damageAt(double factor, double radius, double distance) {
        if (distance < 0.0D || distance >= radius) {
            return 0.0F;
        }
        return (float)(factor * (radius - distance));
    }

    private void strike() {
        double radius = this.radius();
        if (radius <= 0.0D) {
            return;
        }
        DamageSource source = this.level.damageSources().source(ModDamageTypes.EXPERIMENT_055, this.caster);
        AABB search = this.caster.getBoundingBox().inflate(radius);
        for (LivingEntity target : this.level.getEntitiesOfClass(LivingEntity.class, search, this::isTarget)) {
            float damage = damageAt(this.damageFactor(), radius, this.caster.distanceTo(target));
            if (damage <= 0.0F) {
                continue;
            }
            this.damageTries++;
            if (target.hurt(source, damage)) {
                this.hitsLanded++;
                this.spawnRedstone(target);
            }
        }
    }

    /** 「除使用者以外的所有生物」：自己、已经倒下与旁观者之外都算，友军与创造模式那两类交给 {@code hurt} 自己判 */
    private boolean isTarget(LivingEntity other) {
        return other != this.caster && other.isAlive() && !other.isSpectator();
    }

    /** 伤害落点散一片红石：位置取包围盒中心，向上让出半格，免得半片埋在脚下 */
    private void spawnRedstone(LivingEntity target) {
        AABB box = target.getBoundingBox();
        this.level.sendParticles(DustParticleOptions.REDSTONE,
            box.getCenter().x, box.minY + box.getYsize() * 0.5D, box.getCenter().z,
            REDSTONE_BURST, box.getXsize() * 0.5D, box.getYsize() * 0.5D, box.getZsize() * 0.5D, 0.02D);
    }

    /** 充满之后，使用者自己每 tick 挨 1 点同类型伤害（节拍同样交给无敌帧） */
    private void hurtCaster() {
        DamageSource source = this.level.damageSources().source(ModDamageTypes.EXPERIMENT_055, this.caster);
        this.caster.hurt(source, CASTER_DAMAGE);
    }

    /** 每秒扣 1 点耐久；碎了就收场，别让客户端那几支曲子接着响 */
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
        }
    }

    /**
     * 把 {@code 0.7 × 影响半径} 内的群系刷成 {@link #TARGET_BIOME}
     *
     * <p>判定是**球体**：生物群系单元的中心（4×4×4 一格）到施放者的距离小于半径才换，三个轴用同一个半径。
     * 形状只有半径这一个参数，所以不是方形、不是方柱、也不是贴地的一层薄片——上次那两版（整列整列的方柱、
     * 竖直只留 ±4 的切片）都不是球
     *
     * <p>写进区块走的是原版 {@code /fillbiome} 用的那条路（{@code ChunkAccess#fillBiomesFromNoise} 加
     * {@code BiomeResolver}）：resolver 每个生物群系单元问一次「要不要换」，所以圆形的形状是逐单元判定的，
     * 一个区块一次调用就够；改完再把这些区块统一 {@code resendBiomesForChunks} 同步给客户端。
     * 只动已经载入的区块（{@code ChunkStatus.FULL} 取不到就跳过），每秒生成新地形太贵
     */
    private void fillBiome() {
        double radius = this.radius() * BIOME_RADIUS_SCALE;
        if (radius < 1.0D) {
            return;
        }
        Optional<Holder.Reference<Biome>> target = this.level.registryAccess()
            .registryOrThrow(Registries.BIOME).getHolder(TARGET_BIOME);
        if (target.isEmpty()) {
            return;
        }
        Holder<Biome> biome = target.get();
        int centreX = Mth.floor(this.caster.getX());
        int centreY = Mth.floor(this.caster.getY());
        int centreZ = Mth.floor(this.caster.getZ());
        double radiusSqr = radius * radius;
        int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(centreX - radius));
        int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(centreX + radius));
        int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(centreZ - radius));
        int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(centreZ + radius));
        List<ChunkAccess> touched = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkAccess chunk = this.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                chunk.fillBiomesFromNoise((quartX, quartY, quartZ, sampler) -> {
                    Holder<Biome> current = chunk.getNoiseBiome(quartX, quartY, quartZ);
                    // 单元中心（+2）落在球内才换：三个轴用同一个半径
                    double dx = QuartPos.toBlock(quartX) + 2 - centreX;
                    double dy = QuartPos.toBlock(quartY) + 2 - centreY;
                    double dz = QuartPos.toBlock(quartZ) + 2 - centreZ;
                    if (dx * dx + dy * dy + dz * dz > radiusSqr) {
                        return current;
                    }
                    int unitY = QuartPos.toBlock(quartY) + 2;
                    this.biomeMinY = Math.min(this.biomeMinY, unitY);
                    this.biomeMaxY = Math.max(this.biomeMaxY, unitY);
                    return biome;
                }, this.level.getChunkSource().randomState().sampler());
                chunk.setUnsaved(true);
                touched.add(chunk);
                this.biomeFills++;
            }
        }
        if (!touched.isEmpty()) {
            this.level.getChunkSource().chunkMap.resendBiomesForChunks(touched);
        }
    }

    /**
     * 每 tick 一条随机射线的**参数包**：只有方向与长度，粒子由客户端自己生成
     *
     * <p>这是「量最少的一次发包」：一颗一包地 {@code sendParticles} 每 tick 要发几十上百个包，而这里
     * 每 tick、每个施放者只发一个包，发往 32 格内（与 `sendParticles` 的可见距离同档）。
     * 射线方向在服务端摇、所有客户端看到的是同一条线；位置与长度客户端自己按实体算，
     * 详见 {@code client.Experiment055Particles}
     */
    private void sendRay() {
        double radius = this.radius();
        if (radius <= 0.0D) {
            return;
        }
        this.enchantRays++;
        Vec3 direction = randomDirection(this.level.getRandom());
        PacketDistributor.sendToPlayersNear(this.level, null,
            this.caster.getX(), this.caster.getY(), this.caster.getZ(), RAY_RADIUS,
            new Experiment055RayPayload(this.caster.getId(),
                (float)direction.x, (float)direction.y, (float)direction.z,
                (float)(radius * RAY_LENGTH_SCALE)));
    }

    /** 球面上的均匀随机单位向量：三轴高斯再归一化——「从使用者随机指到 1.5 × 影响半径 处」 */
    private static Vec3 randomDirection(RandomSource random) {
        double vx = random.nextGaussian();
        double vy = random.nextGaussian();
        double vz = random.nextGaussian();
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (length < 1.0E-4D) {
            return new Vec3(0.0D, 1.0D, 0.0D);
        }
        return new Vec3(vx / length, vy / length, vz / length);
    }

    /** 当前该响的那支：没充满循环 charge，充满之后循环 geiger
     *
     * <p>每秒重发一次是幂等的——客户端按施放者认人，已经在放同一支就什么都不做
     */
    private void refreshSound() {
        this.sendSound(this.charged()
            ? Experiment055SoundPayload.MODE_GEIGER
            : Experiment055SoundPayload.MODE_CHARGE);
    }

    private void sendSound(int mode) {
        this.soundMode = mode;
        PacketDistributor.sendToPlayersNear(this.level, null,
            this.caster.getX(), this.caster.getY(), this.caster.getZ(), HEARING_RADIUS,
            new Experiment055SoundPayload(this.caster.getId(), mode));
    }

    /** 收场：把停止包发给 64 格内的玩家，之后这一段不再 tick */
    private void finish() {
        this.done = true;
        PacketDistributor.sendToPlayersNear(this.level, null,
            this.caster.getX(), this.caster.getY(), this.caster.getZ(), STOP_RADIUS,
            new Experiment055SoundPayload(this.caster.getId(), Experiment055SoundPayload.MODE_STOP));
    }

    public int age() {
        return this.age;
    }

    /** 扣过几次耐久（创造模式下 {@code hurtAndBreak} 不真扣，所以这是「扣了几次」） */
    public int charges() {
        return this.charges;
    }

    /** 刷过几次群系（按区块列计，一次一个已载入的区块） */
    public int biomeFills() {
        return this.biomeFills;
    }

    /** 被换掉的生物群系单元里最低的那个高度（单元中心），一次都没换过时是 {@code Integer.MAX_VALUE} */
    public int biomeMinY() {
        return this.biomeMinY;
    }

    /** 被换掉的生物群系单元里最高的那个高度（单元中心），一次都没换过时是 {@code Integer.MIN_VALUE} */
    public int biomeMaxY() {
        return this.biomeMaxY;
    }

    public int damageTries() {
        return this.damageTries;
    }

    /** 发过几条射线参数包（每 tick 一条，半径还是 0 的时候不发） */
    public int enchantRays() {
        return this.enchantRays;
    }

    /** 真正打出伤害的次数：被无敌帧挡下的不算 */
    public int hitsLanded() {
        return this.hitsLanded;
    }

    /** 最后发给客户端的那一档音效，-1 表示还没发过 */
    public int soundMode() {
        return this.soundMode;
    }

    public boolean finished() {
        return this.done;
    }
}
