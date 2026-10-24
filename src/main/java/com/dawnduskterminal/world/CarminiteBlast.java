package com.dawnduskterminal.world;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.block.CarminiteFireBlock;
import com.dawnduskterminal.block.Experiment052Block;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModParticles;
import com.dawnduskterminal.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 砷铅铁那一套爆炸：试验物品 052 的主爆炸（{@link #large}）与碎片的小爆炸（{@link #small}）
 *
 * <p>走的都是 {@code ServerLevel#explode} 那个带粒子与音效参数的十二参重载：它把爆炸包
 * （{@code ClientboundExplodePacket}）连同本模组的粒子类型与音效一起发给客户端，所以爆炸里
 * 一颗原版 {@code explosion} / {@code explosion_emitter} 都没有
 *
 * <p>粒子本体是 {@code particle_carminite_blast}：与反应场那种 {@code particle_carminite_effect}
 * 共用同一套八帧贴图，只是帧走得更慢、活得更久（见 {@code client/CarminiteParticle#blastProvider}）。
 * 分布照原版爆炸的性子来——<b>落在球形范围里，半径 = 威力 × {@value #SPHERE_RADIUS_PER_POWER}</b>
 * （原版 {@code Explosion#explode} 里那个 {@code radius * 2.0F} 的波及范围），颗数随威力涨，
 * 每颗的初速只是沿半径向外的<em>小</em>速度
 *
 * <p>粒子位置与初速都得一颗一颗算，所以用 {@code sendParticles} 的 {@code count == 0} 那条路：
 * 客户端拿到的三个偏移量直接当速度用（{@code ClientPacketListener#handleParticleEvent} 的第一条分支）
 *
 * <p>除了这圈粒子，还各补一颗烟花白闪（{@code minecraft:flash}）与附近地面的几团 {@code carminite_fire}
 */
public final class CarminiteBlast {
    /** 主爆炸的威力，砷铅铁反应堆那一档 */
    public static final float POWER = 6.0F;

    /** 碎片那次小爆炸的威力 */
    public static final float SMALL_POWER = 2.0F;

    /** 爆炸把附近刷成的生物群系 */
    public static final ResourceKey<Biome> BLAST_BIOME =
        ResourceKey.create(Registries.BIOME, DawnDuskTerminal.id("old_growth_birch_forest"));

    /** 刷生物群系的半径系数：威力 × 它（方块），至少 {@value #MIN_BIOME_RADIUS} 格 */
    private static final double BIOME_RADIUS_PER_POWER = 2.0D;
    private static final int MIN_BIOME_RADIUS = 4;

    /** 球形范围的半径：就是威力本身，不乘系数。威力 6 → 半径 6 格，威力 2 → 2 格 */
    private static final double SPHERE_RADIUS_PER_POWER = 1.0D;

    /** 球形范围里撒几颗：威力 × 它，夹在上下限之间。威力 8 → 144 颗，威力 2 → 36 颗 */
    private static final double PARTICLES_PER_POWER = 18.0D;
    private static final int MIN_PARTICLES = 24;
    private static final int MAX_PARTICLES = 160;

    /** 向外那点速度：小，只够慢慢散开 */
    private static final double OUTWARD_SPEED_MIN = 0.05D;
    private static final double OUTWARD_SPEED_MAX = 0.20D;

    /**
     * 旋臂：按角度铺开一圈，越外圈转过的角度越多，水平分量取 {@code (cos θ, -sin θ)} 就是俯视逆时针。
     * 这是这一套爆炸最早的那层粒子，球形那层是后来加的，两层一起放
     */
    private static final int RING_POINTS = 48;
    private static final double RING_TURNS = 1.5D;
    private static final int SMALL_RING_POINTS = 16;
    private static final double SMALL_RING_TURNS = 1.0D;
    private static final double RING_SPEED_INNER = 0.45D;
    private static final double RING_SPEED_OUTER = 1.35D;
    private static final double SMALL_RING_SPEED = 0.65D;
    private static final double RING_SPEED_Y = 0.35D;

    /**
     * 补几团火也跟威力走，与原版一样：原版爆炸是拿被炸掉的方块位置按 1/3 概率点火，
     * 所以火量随威力涨（体积涨得更快，这里按线性给，免得一地火海）。威力 8 → 24 团，威力 2 → 6 团
     */
    private static final double FIRES_PER_POWER = 3.0D;
    private static final int MIN_FIRES = 3;

    /** 一次爆炸最多连带引爆几个同类方块（挑离爆心最近的几个） */
    private static final int MAX_CHAINED_NEIGHBOURS = 5;

    /** 连锁方块的引爆延时：随机 1~10 tick */
    private static final int MIN_CHAIN_DELAY = 1;
    private static final int MAX_CHAIN_DELAY = 10;

    /** 主爆炸进行中的旗子：只有它立着的时候，Detonate 事件里才去摘掉同类方块 */
    private static boolean chainingNeighbours;

    /** 原版那声爆炸，小爆炸用；{@code SoundEvents} 里这一类字段本身就是注册表里的 {@code Holder.Reference} */
    private static final Holder<SoundEvent> VANILLA_EXPLOSION_SOUND = SoundEvents.GENERIC_EXPLODE;

    /**
     * 小爆炸的伤害结算器：不伤生物、不击退
     *
     * <p>碎片打中生物那 1~10 点伤害是另算的，小型爆炸只负责视觉与补火；
     * 击退在 {@code Explosion#explode} 里是独立于伤害判定的一步，所以两个都要关掉
     */
    private static final ExplosionDamageCalculator NO_ENTITY_EFFECT = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return 0.0F;
        }
    };

    /**
     * 主爆炸：威力 {@value #POWER}、破坏方块、伤生物、播 {@code experiment_052_explode}
     *
     * <p>音效只有这一处会放，正常那声原版爆炸响不再出现（粒子与音效都换成了本模组的）
     */
    public static void large(ServerLevel level, double x, double y, double z) {
        // 这一圈还得管住附近的同类方块：把「正在连锁」的旗子立起来，Detonate 事件里把它们
        // 从破坏名单里摘出来、直接排到下一 tick 引爆（见 protectArmedBlocks）
        chainingNeighbours = true;
        try {
            explode(level, null, null, null, x, y, z, POWER, Level.ExplosionInteraction.BLOCK,
                ModSounds.EXPERIMENT_052_EXPLODE);
        } finally {
            chainingNeighbours = false;
        }
        // 每次爆炸都把附近刷成同一片森林，这是设定里「砷铅铁把周围同化」的表现
        spreadBiome(level, x, y, z, POWER);
        // 两层粒子：先铺一圈逆时针旋臂（这一套原本就有的），再按威力撒一遍球形范围的
        ring(level, x, y, z, RING_POINTS, RING_TURNS, RING_SPEED_INNER, RING_SPEED_OUTER, RING_SPEED_Y);
        spray(level, x, y, z, POWER);
        flash(level, x, y, z);
        scatterFiresByPower(level, BlockPos.containing(x, y, z), POWER);
    }

    /**
     * 主爆炸进行中处理「范围内的同类方块」：
     * <ol>
     *   <li>这一类方块全都从这次爆炸的破坏名单里摘掉——<b>处在范围内、暂时没被连锁到的那些也不会被炸毁</b>，
     *       它们就原样立在那里，等后续的连锁或别的爆炸来处理
     *   <li>其中离爆心最近的 {@value #MAX_CHAINED_NEIGHBOURS} 个被连锁引爆：它们不走进点火那套
     *       （不放音效、不等 3 秒引信），状态直接点亮，排一个
     *       {@value #MIN_CHAIN_DELAY}~{@value #MAX_CHAIN_DELAY} tick 的随机计划刻，到点自己炸
     * </ol>
     *
     * <p>{@code ExplosionEvent.Detonate#getAffectedBlocks()} 返回的就是 {@code Explosion#getToBlow()}，
     * {@code finalizeExplosion} 之后照着它拆方块，所以从名单里摘掉就等于「这次不拆它」
     *
     * <p>只在 {@link #large} 的那一次爆炸里生效（旗子在调用前后包好），碎片的小爆炸不碰方块，
     * 也就不需要排除谁
     */
    public static void protectArmedBlocks(ExplosionEvent.Detonate event) {
        if (!chainingNeighbours) {
            return;
        }
        Level level = event.getLevel();
        Block armed = ModBlocks.EXPERIMENT_052.get();
        List<BlockPos> neighbours = new ArrayList<>();
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (level.getBlockState(pos).is(armed)) {
                neighbours.add(pos.immutable());
            }
        }
        if (neighbours.isEmpty()) {
            return;
        }
        // 先整类豁免：范围内的同类方块一颗都不许被这次爆炸拆掉
        event.getAffectedBlocks().removeAll(neighbours);
        // 再挑最近的几个连锁引爆，其余保持未点亮地留在原地
        Vec3 center = event.getExplosion().center();
        neighbours.sort(Comparator.comparingDouble(pos -> center.distanceToSqr(Vec3.atCenterOf(pos))));
        int chained = Math.min(MAX_CHAINED_NEIGHBOURS, neighbours.size());
        for (int i = 0; i < chained; i++) {
            BlockPos pos = neighbours.get(i);
            // 跳过点火：不播音效、不排 3 秒引信，只把状态点亮（到点的 tick 认这个状态）
            BlockState state = level.getBlockState(pos);
            level.setBlock(pos, state.setValue(Experiment052Block.ACTIVE, true), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, armed,
                Mth.nextInt(level.getRandom(), MIN_CHAIN_DELAY, MAX_CHAIN_DELAY));
        }
    }

    /**
     * 碎片的小爆炸：威力 {@value #SMALL_POWER}，不破坏方块、不伤生物、不击退，
     * 粒子与主爆炸同一套（旋臂 + 球形两层），音效是原版那声爆炸
     */
    public static void small(ServerLevel level, double x, double y, double z) {
        explode(level, null, null, NO_ENTITY_EFFECT, x, y, z, SMALL_POWER, Level.ExplosionInteraction.NONE,
            VANILLA_EXPLOSION_SOUND);
        spreadBiome(level, x, y, z, SMALL_POWER);
        ring(level, x, y, z, SMALL_RING_POINTS, SMALL_RING_TURNS,
            SMALL_RING_SPEED * 0.6D, SMALL_RING_SPEED, RING_SPEED_Y * 0.5D);
        spray(level, x, y, z, SMALL_POWER);
        flash(level, x, y, z);
        scatterFiresByPower(level, BlockPos.containing(x, y, z), SMALL_POWER);
    }

    /**
     * 真正调 {@code ServerLevel#explode} 的那一层
     *
     * <p>粒子参数两档都传 {@code particle_carminite_blast}：这个重载会把它们放进爆炸包，
     * 客户端在爆炸中心按「威力是否达到 2」挑其中一个放一颗，两个都填成同一种粒子就没有差别
     */
    private static void explode(ServerLevel level, @Nullable Entity source, @Nullable DamageSource damageSource,
                                @Nullable ExplosionDamageCalculator calculator, double x, double y, double z,
                                float power, Level.ExplosionInteraction interaction, Holder<SoundEvent> sound) {
        level.explode(source, damageSource, calculator, x, y, z, power, false, interaction,
            ModParticles.CARMINITE_BLAST.get(), ModParticles.CARMINITE_BLAST.get(), sound);
    }

    /** 一颗烟花白闪：爆炸那一刻的强光 */
    private static void flash(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /**
     * 一圈逆时针旋臂：按角度铺开，越外圈转过的角度越多、速度越快
     *
     * <p>水平分量取 {@code (cos θ, -sin θ)}：在 Minecraft 的坐标里（x 向东、z 向南）就是从东往北转，
     * 俯视是逆时针。初速走 {@code sendParticles} 的 {@code count == 0} 那条路，一颗一颗发才控制得住方向
     */
    private static void ring(ServerLevel level, double x, double y, double z, int points, double turns,
                             double speedInner, double speedOuter, double speedY) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < points; i++) {
            double progress = (double) i / points;
            double angle = Mth.TWO_PI * progress * turns;
            double speed = speedInner + (speedOuter - speedInner) * progress;
            double vx = Math.cos(angle) * speed;
            double vz = -Math.sin(angle) * speed;
            double vy = (random.nextDouble() - 0.5D) * speedY;
            level.sendParticles(ModParticles.CARMINITE_BLAST.get(), x, y, z, 0, vx, vy, vz, 1.0D);
        }
    }

    /**
     * 球形范围里的粒子：半径随威力走，颗数随威力走，每颗沿半径向外慢慢散开
     *
     * <p>球内均匀取点用「随机方向 + 半径乘立方根」：体积与半径的三次方成正比，
     * 直接对半径取均匀会让粒子全挤在球心附近
     */
    private static void spray(ServerLevel level, double x, double y, double z, float power) {
        RandomSource random = level.getRandom();
        double radius = power * SPHERE_RADIUS_PER_POWER;
        int count = Mth.clamp((int) (power * PARTICLES_PER_POWER), MIN_PARTICLES, MAX_PARTICLES);
        for (int i = 0; i < count; i++) {
            Vec3 direction = randomDirection(random);
            double distance = radius * Math.cbrt(random.nextDouble());
            double px = x + direction.x * distance;
            double py = y + direction.y * distance;
            double pz = z + direction.z * distance;
            double speed = OUTWARD_SPEED_MIN + random.nextDouble() * (OUTWARD_SPEED_MAX - OUTWARD_SPEED_MIN);
            level.sendParticles(ModParticles.CARMINITE_BLAST.get(), px, py, pz, 0,
                direction.x * speed, direction.y * speed, direction.z * speed, 1.0D);
        }
    }

    /** 均匀的随机单位向量：三轴高斯再归一化 */
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

    /**
     * 把附近刷成 {@link #BLAST_BIOME}：走原版 {@code /fillbiome} 的那条实现
     * （{@code FillBiomeCommand#fill}），它会按 4×4×4 的生物群系单元改数据、并给附近玩家发包同步
     *
     * <p>先手动把半径覆盖到的区块载进来：那个实现只认已经载入的区块，否则直接返回错误。改失败
     * （区块没载入、或改的体积超过 {@code commandModificationBlockLimit}）就跳过，不抛异常——
     * 爆炸本身已经生效，不该因为群系改不动而炸掉服务端
     */
    private static void spreadBiome(ServerLevel level, double x, double y, double z, float power) {
        Optional<Holder.Reference<Biome>> biome = level.registryAccess().registryOrThrow(Registries.BIOME)
            .getHolder(BLAST_BIOME);
        if (biome.isEmpty()) {
            return;
        }
        int radius = Math.max(MIN_BIOME_RADIUS, (int) Math.round(power * BIOME_RADIUS_PER_POWER));
        int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(x) - radius);
        int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(x) + radius);
        int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(z) - radius);
        int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(z) + radius);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(y) - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(y) + radius);
        BlockPos from = BlockPos.containing(x - radius, minY, z - radius);
        BlockPos to = BlockPos.containing(x + radius, maxY, z + radius);
        FillBiomeCommand.fill(level, from, to, biome.get());
    }

    /** 按威力补火：团数、撒开半径、往下找的格数都随威力涨 */
    private static void scatterFiresByPower(ServerLevel level, BlockPos center, float power) {
        int count = Math.max(MIN_FIRES, (int) Math.round(power * FIRES_PER_POWER));
        int radius = Math.max(2, Math.round(power));
        int depth = Math.max(1, Math.round(power / 3.0F));
        scatterFires(level, center, radius, depth, count);
    }

    /**
     * 在爆炸中心附近的地面上补几团砷铅铁火焰
     *
     * <p>逐列上下找：爆炸把地面削掉一层以后，火要落回新的地面上；站不住的格子跳过
     */
    public static void scatterFires(ServerLevel level, BlockPos center, int radius, int depth, int count) {
        RandomSource random = level.getRandom();
        int placed = 0;
        for (int attempt = 0; attempt < count * 4 && placed < count; attempt++) {
            BlockPos column = center.offset(Mth.nextInt(random, -radius, radius), 0,
                Mth.nextInt(random, -radius, radius));
            for (int dy = 1; dy >= -depth; dy--) {
                BlockPos candidate = column.offset(0, dy, 0);
                if (!CarminiteFireBlock.canSurviveAt(level, candidate)) {
                    continue;
                }
                level.setBlockAndUpdate(candidate, ModBlocks.CARMINITE_FIRE.get().defaultBlockState());
                placed++;
                break;
            }
        }
    }

    private CarminiteBlast() {}
}
