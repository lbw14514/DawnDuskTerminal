package com.dawnduskterminal.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import twilightforest.init.TFSounds;
import twilightforest.init.TFEntities;
import twilightforest.init.TFDamageTypes;
import twilightforest.init.TFBlocks;

/**
 * 试验物品 076 被挖掉之后跑的那场反应：逐拍照抄暮色森林的砷铅铁反应堆
 *
 * <p>反编译 {@code twilightforest:block/entity/CarminiteReactorBlockEntity#tick} 得到的过程，
 * 计数每 tick 加一，动作全落在计数是 5 的倍数的那些拍上：
 *
 * <ol>
 *   <li><b>计数 5</b>：往自己周围 3x3x3 摆一圈假金块与假钻石块。<b>我们跳过这一拍</b>——
 *       这是用户要的差别，我们的方块没有假方块，直接从后面的塌陷接上</li>
 *   <li><b>计数 85~329</b>（{@code counter - 80} 落在 10~249）：以自己为中心掏一个越来越大的空心球，
 *       半径每 40 拍加一，一直掏到 6</li>
 *   <li><b>计数 80~280</b>：把同一个位置灌成反应堆碎片（{@code twilightforest:reactor_debris}）</li>
 *   <li><b>计数 120~280</b>：以自己往三个轴各偏 3 格的那个点为心，再掏、再灌；
 *       其中「覆盖」那一档把方块换成地狱岩（八分之一概率换成下界石英矿或下界金矿），
 *       条件合适时上方再点一把火</li>
 *   <li><b>计数 160~320</b>：第三个心，同上</li>
 *   <li><b>计数 350</b>：引爆（半径 4、带火），并在 sec 与 ter 两个点上各刷三只砷铅铁恶魂幼体，
 *       反应到此结束</li>
 * </ol>
 *
 * <p>还有一件只在客户端做的事：计数到 250 为止，每 5 拍放一声 {@code twilightforest:block.twilightforest.reactor.ambient}，
 * 音量与音高都是「计数 / 100」，所以那一阵轰鸣是一路涨上去的。我们是服务端跑这套流程，
 * 就改成向附近广播这一声，效果一样
 *
 * <p>暮色森林那边这套流程挂在反应堆的方块实体上；我们的方块被挖掉之后方块实体跟着没了，
 * 所以状态放在这个类里，由 {@code ServerTickEvent.Post} 推进，和 043 的喷火、066 的恶魂陷阱场同一个做法
 *
 * <p>只用字符串 ID 与标签引用暮色森林（{@code reactor_debris}、{@code carminite_reactor_immune}、
 * {@code carminite_reactor_ores}、{@code carminite_ghastling}、那条声音、那个伤害类型），
 * 代码里不 import 它的任何一个类
 */
public final class Experiment076Reaction {
    /** 整场反应的长度：350 tick，也就是 17.5 秒 */
    public static final int TOTAL_TICKS = 350;

    /** 每隔这么多拍做一次事 */
    public static final int STEP_TICKS = 5;

    /** 主团块的基准半径，掏到 {@link #BASE_RADIUS}+... 为止 */
    public static final int BASE_RADIUS = 10;

    /** 半径每这么多拍加一 */
    public static final int PHASE_TICKS = 40;

    /** 环境音放到第几拍为止 */
    public static final int AMBIENT_UNTIL = 250;

    /** sec / ter 两个团块在每个轴上偏几格 */
    private static final int SATELLITE_OFFSET = 3;

    /** 反应堆不动的那批方块：暮色森林自己的保护标签（里头套着 {@code common_protections}） */
    private static final TagKey<Block> IMMUNE =
        BlockTags.create(ResourceLocation.parse("twilightforest:carminite_reactor_immune"));

    /** 覆盖阶段有八分之一概率把方块换成这个标签里的矿，原版只有下界石英矿与下界金矿两个 */
    private static final TagKey<Block> ORES =
        BlockTags.create(ResourceLocation.parse("twilightforest:carminite_reactor_ores"));


    private static final List<Experiment076Reaction> ACTIVE = new ArrayList<>();

    private final ServerLevel level;
    private final BlockPos origin;
    private final int secX;
    private final int secY;
    private final int secZ;
    private final int terX;
    private final int terY;
    private final int terZ;
    private int counter;

    private Experiment076Reaction(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
        RandomSource random = level.getRandom();
        this.secX = side(random);
        this.secY = side(random);
        this.secZ = side(random);
        int x = side(random);
        int y = side(random);
        int z = side(random);
        // 两个团块正好落在同一个角上就翻一下第三个，否则它们重合，看着像只有两次爆炸
        if (secX == x && secY == y && secZ == z) {
            x = -x;
            y = -y;
            z = -z;
        }
        this.terX = x;
        this.terY = y;
        this.terZ = z;
    }

    /** 在 {@code origin} 起一场反应。同一格已经在跑就不重复起 */
    public static void start(ServerLevel level, BlockPos origin) {
        if (level.isDebug()) {
            return;
        }
        BlockPos immutable = origin.immutable();
        for (Experiment076Reaction reaction : ACTIVE) {
            if (reaction.level == level && reaction.origin.equals(immutable)) {
                return;
            }
        }
        ACTIVE.add(new Experiment076Reaction(level, immutable));
    }

    /**
     * 服务端每 tick 推一次，见 {@code DdtServerEvents#onServerTick}
     *
     * <p>跑的是副本：{@code tick} 里会改方块，改方块会连锁触发邻居的移除，被连锁带走的方块也可能起新的反应，
     * 直接在 {@link #ACTIVE} 上迭代就是 {@code ConcurrentModificationException}
     */
    public static void tickAll() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        List<Experiment076Reaction> snapshot = new ArrayList<>(ACTIVE);
        for (Experiment076Reaction reaction : snapshot) {
            if (reaction.tick()) {
                ACTIVE.remove(reaction);
            }
        }
    }

    /** 服务器停下时整批清掉，别留到下一次启动 */
    public static void clear() {
        ACTIVE.clear();
    }

    /** 现在有几场反应在跑。测试用 */
    public static int activeCount() {
        return ACTIVE.size();
    }

    /** 推一拍，返回是否跑完了 */
    private boolean tick() {
        counter++;
        if (counter % STEP_TICKS == 0) {
            if (counter <= AMBIENT_UNTIL) {
                playAmbient();
            }
            collapse();
        }
        if (counter >= TOTAL_TICKS) {
            finish();
            return true;
        }
        return false;
    }

    /**
     * 计数是 5 的倍数那一拍要做的全部塌陷动作
     *
     * <p>三个偏移量 {@code -80} / {@code -120} / {@code -160} 与 40 拍的相位都是照反编译结果写的，
     * 改一个数就和反应堆对不上了
     */
    private void collapse() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState debris = debrisState();

        // 主团块：先按越来越大的半径掏空，再灌碎片
        int primary = counter - 80;
        if (primary >= BASE_RADIUS && primary <= 249) {
            drawBlob(origin, (primary - BASE_RADIUS) / PHASE_TICKS, air, primary - BASE_RADIUS, false);
        }
        if (primary <= 200) {
            drawBlob(origin, primary / PHASE_TICKS, debris, counter, false);
        }

        // 第二个心：掏空之后进入覆盖档（换地狱岩、点火的都是那一档）
        BlockPos secondary = origin.offset(secX, secY, secZ);
        int second = counter - 120;
        if (second >= BASE_RADIUS && second <= 129) {
            drawBlob(secondary, (second - BASE_RADIUS) / PHASE_TICKS, air, second - BASE_RADIUS, false);
        }
        if (second >= 0 && second <= 160) {
            drawBlob(secondary, second / PHASE_TICKS, air, second, true);
        }

        // 第三个心
        BlockPos tertiary = origin.offset(terX, terY, terZ);
        int third = counter - 160;
        if (third >= BASE_RADIUS && third <= 129) {
            drawBlob(tertiary, (third - BASE_RADIUS) / PHASE_TICKS, air, third - BASE_RADIUS, false);
        }
        if (third >= 0 && third <= 160) {
            drawBlob(tertiary, third / PHASE_TICKS, air, third, true);
        }
    }

    /**
     * 画一层团块的壳：只处理「粗细正好等于半径」的那些格子，所以每次只换掉薄薄一层外面
     *
     * <p>八个卦限由 {@code (progress + x) % 8} 轮着来、{@code (progress + y) % 8} 传给
     * {@link #transformBlock} 当相位，壳于是被拆成很多小撮、分很多拍才长完，看起来就是一团在慢慢塌
     */
    private void drawBlob(BlockPos centre, int radius, BlockState filler, int progress, boolean override) {
        for (int x = 0; x <= radius; x++) {
            int xPhase = (progress + x) % 8;
            for (int y = 0; y <= radius; y++) {
                int yPhase = (progress + y) % 8;
                for (int z = 0; z <= radius; z++) {
                    int thickness;
                    if (x >= y && x >= z) {
                        thickness = x + (int) (Math.max(y, z) * 0.5D + Math.min(y, z) * 0.25D);
                    } else if (y >= x && y >= z) {
                        thickness = y + (int) (Math.max(x, z) * 0.5D + Math.min(x, z) * 0.25D);
                    } else {
                        thickness = z + (int) (Math.max(x, y) * 0.5D + Math.min(x, y) * 0.25D);
                    }
                    if (thickness != radius) {
                        continue;
                    }
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    switch (xPhase) {
                        case 0 -> transformBlock(centre.offset(x, y, z), filler, yPhase, override);
                        case 1 -> transformBlock(centre.offset(x, y, -z), filler, yPhase, override);
                        case 2 -> transformBlock(centre.offset(-x, y, z), filler, yPhase, override);
                        case 3 -> transformBlock(centre.offset(-x, y, -z), filler, yPhase, override);
                        case 4 -> transformBlock(centre.offset(x, -y, z), filler, yPhase, override);
                        case 5 -> transformBlock(centre.offset(x, -y, -z), filler, yPhase, override);
                        case 6 -> transformBlock(centre.offset(-x, -y, z), filler, yPhase, override);
                        default -> transformBlock(centre.offset(-x, -y, -z), filler, yPhase, override);
                    }
                }
            }
        }
    }

    /**
     * 换掉一个格子
     *
     * <p>不会动的方块一律放过：暮色森林的保护标签，以及挖不动的方块（硬度 -1，基岩与屏障那类）
     *
     * @param phase    {@code (progress + y) % 8}，覆盖档拿它决定上方点不点火（能整除 3 才点）
     * @param override 覆盖档：把方块换成地狱岩或矿，而不是换成 {@code filler}
     */
    private void transformBlock(BlockPos pos, BlockState filler, int phase, boolean override) {
        BlockState current = level.getBlockState(pos);
        boolean solid = current.getBlock() != Blocks.AIR;
        if (solid && (current.is(IMMUNE) || current.getDestroySpeed(level, pos) == -1.0F)) {
            return;
        }
        if (phase == 0 && solid) {
            // 2001 是原版的「方块被敲碎」事件，客户端会放碎屑粒子与音效
            level.levelEvent(2001, pos, Block.getId(current));
        }
        if (override && solid) {
            level.setBlock(pos, overridden(), 3);
            if (level.isEmptyBlock(pos.above()) && phase % 3 == 0) {
                level.setBlock(pos.above(), Blocks.FIRE.defaultBlockState(), 3);
            }
        } else {
            level.setBlock(pos, filler, 3);
        }
    }

    /** 覆盖档换上去的东西：八分之一概率是矿，其余是地狱岩 */
    private BlockState overridden() {
        if (level.getRandom().nextInt(8) == 0) {
            Optional<HolderSet.Named<Block>> ores = BuiltInRegistries.BLOCK.getTag(ORES);
            if (ores.isPresent()) {
                Optional<Holder<Block>> ore = ores.get().getRandomElement(level.getRandom());
                if (ore.isPresent()) {
                    return ore.get().value().defaultBlockState();
                }
            }
        }
        return Blocks.NETHERRACK.defaultBlockState();
    }

    /** 收尾：引爆，并在 sec / ter 两个点上各刷三只砷铅铁恶魂幼体 */
    private void finish() {
        // 我们那块方块在被挖掉的时候就没了，这一下只是兜底
        level.destroyBlock(origin, false);
        level.explode(null, reactorDamage(), null,
            origin.getX(), origin.getY(), origin.getZ(), 4.0F, true, Level.ExplosionInteraction.BLOCK);
        for (int i = 0; i < 3; i++) {
            spawnGhastling(origin.getX() + secX, origin.getY() + secY, origin.getZ() + secZ);
            spawnGhastling(origin.getX() + terX, origin.getY() + terY, origin.getZ() + terZ);
        }
    }

    private void spawnGhastling(int x, int y, int z) {
        Entity entity = TFEntities.CARMINITE_GHASTLING.get().create(level);
        if (entity == null) {
            return;
        }
        RandomSource random = level.getRandom();
        entity.moveTo(
            x - 1.5D + random.nextFloat() * 3.0D,
            y - 1.5D + random.nextFloat() * 3.0D,
            z - 1.5D + random.nextFloat() * 3.0D,
            random.nextFloat() * 360.0F,
            0.0F);
        level.addFreshEntity(entity);
    }

    /** 反应堆那条伤害类型，与方块自己用的同一条 */
    private DamageSource reactorDamage() {
        return level.registryAccess()
            .registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolder(TFDamageTypes.REACTOR)
            .<DamageSource>map(DamageSource::new)
            .orElseGet(() -> level.damageSources().explosion(null, null));
    }

    private void playAmbient() {
        SoundEvent sound = TFSounds.REACTOR_AMBIENT.get();
        float pitch = counter / 100.0F;
        level.playSound(null, origin.getX() + 0.5D, origin.getY() + 0.5D, origin.getZ() + 0.5D,
            sound, SoundSource.BLOCKS, pitch, pitch);
    }

    /** 灌进团块里的反应堆碎片 */
    private static BlockState debrisState() {
        return TFBlocks.REACTOR_DEBRIS.get().defaultBlockState();
    }

    private static int side(RandomSource random) {
        return random.nextBoolean() ? SATELLITE_OFFSET : -SATELLITE_OFFSET;
    }
}
