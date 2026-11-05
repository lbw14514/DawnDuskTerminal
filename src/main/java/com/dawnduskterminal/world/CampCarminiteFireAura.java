package com.dawnduskterminal.world;

import com.dawnduskterminal.block.CampCarminiteFireBlock;
import com.dawnduskterminal.registry.ModBlocks;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 红篝火那一圈 16 格的气场：消耗度的增减在这里反过来
 *
 * <p>登记与判定照 {@link CubeField} 那一套：按维度存一张「篝火在哪」的表，方块实体装载/卸载时登记与撤登记，
 * 方块的 {@code onPlace} / {@code onRemove} 管摆下与挖掉，于是摆下那一 tick 就生效。
 * 判定不扫 33³ 格，只把现存的篝火挨个量距离，每次判定还会回头核那一格还是不是本方块、还是不是点着的
 *
 * <p><b>规则一：小增量取反。</b>点亮着的红篝火 16 格内的玩家，凡是<b>增加</b>消耗度、
 * 且<b>增加量不大于 {@value #MAX_INVERTED}</b> 的行为，改成<b>等量减少</b>消耗度（见
 * {@code mixin/CampCarminiteFireExhaustionMixin}）。原版玩家的动作全都落在这一档：
 * 挖掘 0.005、奔跑 0.1、跳跃 0.05 与 0.2、攻击 0.1 与 0.2、受伤 0.1、饥饿效果 0.005 的倍数
 *
 * <p><b>增加量大于 1 的行为照旧算增加</b>：自然回血的 6.0 与按饱和度算的那条照样进消耗度，
 * 也照样按原版「消耗度大于 4 就掉一点」扣饥饿值或饱和度，本类不碰它们
 *
 * <p><b>规则二：见底就补。</b>消耗度见底时恢复 1 点<b>饥饿值</b>，饥饿值满了才补饱和度；
 * 然后把消耗度置为 {@value #REFILL}
 *
 * <p>为什么必须先补饥饿值：原版回血有两道门，快的那道要 {@code 饥饿值 >= 20 且 饱和度 > 0}，
 * 慢的那道要 {@code 饥饿值 >= 18}，两道都在 {@code FoodData#tick} 里（只有
 * {@code FoodData#tick} 里）判。饥饿值掉到 17 以下之后，补饱和度永远补不回这两道门，
 * 玩家就会看到「怎么都不回血」——这正是之前先补饱和度那版的问题。饥饿值写在前面，就先补它
 *
 * <p>没有篝火的地方每条判定只花一次 {@code HashMap} 查表：消耗度是每秒上千次的热路径，
 * 这里不做任何比查表更贵的事
 */
public final class CampCarminiteFireAura {
    /** 生效半径：16 格，按方块中心到玩家位置算直线距离 */
    public static final double RADIUS = 16.0;

    private static final double RADIUS_SQR = RADIUS * RADIUS;

    /** 取反的上限：增加量不大于这个数才算「小增量」 */
    public static final float MAX_INVERTED = 1.0F;

    /** 每补一次之后压在消耗度上的那 4 点：见底一次要走完这 4 点才补下一次 */
    public static final float REFILL = 4.0F;

    /** 每见底一次补多少点饥饿值或饱和度 */
    private static final int RESTORE = 1;

    private static final int MAX_FOOD = 20;

    /** 每个维度里现存的篝火位置；只由服务端线程读写 */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> CENTRES = new HashMap<>();

    private CampCarminiteFireAura() {}

    /** 登记一格：方块实体装载、方块刚摆下时各来一次，重复登记没有额外代价（就是个集合） */
    public static void track(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) {
            return;
        }
        CENTRES.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos);
    }

    /** 撤掉一格：方块实体卸载、方块被挖掉或推走时各来一次，撤不存在的没有代价 */
    public static void untrack(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) {
            return;
        }
        Set<BlockPos> tracked = CENTRES.get(level.dimension());
        if (tracked == null) {
            return;
        }
        tracked.remove(pos);
        if (tracked.isEmpty()) {
            CENTRES.remove(level.dimension());
        }
    }

    /** 服务器停下：整张表清干净，别带到下一次启动 */
    public static void clear() {
        CENTRES.clear();
    }

    /**
     * 这个玩家在不在某一团点亮着的红篝火 16 格内
     *
     * <p>先量距离（不建 {@code Vec3}，直接算平方和），再核那一格还是不是本方块：方块没了就把这一条顺手丢掉，
     * 幽灵条目只可能活到它下一次被问到。核之前先问 {@code ChunkSource#hasChunk}，不会为了找方块把区块读进来
     */
    public static boolean covers(Player player) {
        Level level = player.level();
        if (level.isClientSide) {
            return false;
        }
        Set<BlockPos> tracked = CENTRES.get(level.dimension());
        if (tracked == null || tracked.isEmpty()) {
            return false;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        Iterator<BlockPos> iterator = tracked.iterator();
        while (iterator.hasNext()) {
            BlockPos centre = iterator.next();
            double dx = x - (centre.getX() + 0.5D);
            double dy = y - (centre.getY() + 0.5D);
            double dz = z - (centre.getZ() + 0.5D);
            if (dx * dx + dy * dy + dz * dz > RADIUS_SQR) {
                continue;
            }
            if (!level.getChunkSource()
                    .hasChunk(
                            SectionPos.blockToSectionCoord(centre.getX()),
                            SectionPos.blockToSectionCoord(centre.getZ()))) {
                continue;
            }
            BlockState state = level.getBlockState(centre);
            if (!state.is(ModBlocks.CAMP_CARMINITE_FIRE.get())) {
                iterator.remove();
                continue;
            }
            if (state.getValue(CampCarminiteFireBlock.LIT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 规则一：把一次「小增量」翻成等量减少，翻了就返回 {@code true}，调用方据此取消原版那次加
     *
     * <p>两道门与 {@code Player#causeFoodExhaustion} 自己的一致：创造模式的无敌玩家不记账，
     * 客户端不加消耗度。0 与负数不算增量、大于 {@value #MAX_INVERTED} 的大增量原样放行
     *
     * <p>压到 0 之后顺手结算一次：一次动作就能当场补上那一点，不用等下一 tick 的 {@link #tick(Player)}。
     * {@code FoodData#addExhaustion} 只把上限压到 40、没有下限，所以这里自己用 {@code Math.max} 卡住 0
     */
    public static boolean absorb(Player player, float exhaustion) {
        if (exhaustion <= 0.0F || exhaustion > MAX_INVERTED) {
            return false;
        }
        if (player.getAbilities().invulnerable || player.level().isClientSide) {
            return false;
        }
        if (!covers(player)) {
            return false;
        }
        FoodData food = player.getFoodData();
        food.setExhaustion(Math.max(food.getExhaustionLevel() - exhaustion, 0.0F));
        settleIfDrained(food);
        return true;
    }

    /** 规则二的安全网：走到篝火边上时消耗度本来就是 0 的那种，由每 tick 这一下补上 */
    public static void tick(Player player) {
        if (player.isSpectator() || !covers(player)) {
            return;
        }
        settleIfDrained(player.getFoodData());
    }

    /**
     * 消耗度见底就补一次：<b>先补饥饿值</b>，饥饿值满了才补饱和度，然后把它压回 {@value #REFILL}
     *
     * <p>先饥饿值是因为原版回血的那两道门都只看饥饿值（见类注释）；
     * 饥饿值满了说明那一点补不上去了，才轮到饱和度
     */
    private static void settleIfDrained(FoodData food) {
        if (food.getExhaustionLevel() > 0.0F) {
            return;
        }
        if (food.getFoodLevel() < MAX_FOOD) {
            food.setFoodLevel(food.getFoodLevel() + RESTORE);
        } else if (food.getSaturationLevel() < (float) food.getFoodLevel()) {
            food.setSaturation(food.getSaturationLevel() + RESTORE);
        }
        food.setExhaustion(REFILL);
    }
}
