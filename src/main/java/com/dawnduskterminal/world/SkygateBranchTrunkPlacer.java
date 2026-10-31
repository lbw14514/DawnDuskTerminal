package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModTrunkPlacers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

/**
 * 天门松的树干：一根直筒树干，再沿树干整段高度撒分枝。
 *
 * <p>原版 {@code cherry_trunk_placer} 最多只长三根枝、而且两根枝贴着树顶，
 * 暮色森林的 {@code branching_trunk_placer} 虽然能长 4 根以上，但分枝起点是
 * 「树顶下固定距离」逐格往上叠的连排，覆盖不到树干中下部。参考原型文竹是分层平铺的羽片，
 * 所以这里自己算高度：分枝在 {@code branches.band} 给出的高度带里按层均匀撒开，
 * 每根枝带一个顶点，交给 {@code foliage_placer} 长一团叶。
 *
 * <p>枝的方向是任意角度：先按方位角算出圆周上的落点，再沿三维 Bresenham 直线走过去（每步落一块，
 * 主轴每步一格、副轴按比例进位），枝用的方块来自 {@code branches.provider}——取天门松木而不是原木，
 * 否则原木两端的横截面贴图会露在斜枝上。所以 45 度、30 度这类斜枝也能长出来，而且一根枝只占一条线宽。
 * 这套打法跟暮色森林苍穹木的 {@code branching_trunk_placer} 一致：它的分枝也走三维 Bresenham 直线，
 * 而不是逐格单轴步进，后者在斜向时每前进一格要落两块原木，枝会显得粗。抬升量在起枝时定一次，
 * 枝上不再补竖木。叶团的水平尺寸靠 {@code radius_offsets.branch} 逐枝抖动，树顶那团单独用
 * {@code radius_offsets.crown} 放大。
 */
public class SkygateBranchTrunkPlacer extends TrunkPlacer {
    /**
     * 分枝的全部参数。它们放在同一个对象里，是因为 {@code TrunkPlacer} 的 {@code RecordCodecBuilder}
     * 在 1.21.1 里最多只能拼到八项，九个平铺字段编不出来。
     */
    public record Branches(
        BlockStateProvider provider,
        IntProvider count,
        IntProvider length,
        BranchBand band,
        float riseChance
    ) {
        public static final Codec<Branches> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockStateProvider.CODEC.fieldOf("provider").forGetter(Branches::provider),
            IntProvider.codec(1, 16).fieldOf("count").forGetter(Branches::count),
            IntProvider.codec(1, 16).fieldOf("length").forGetter(Branches::length),
            BranchBand.CODEC.fieldOf("band").forGetter(Branches::band),
            Codec.floatRange(0.0F, 1.0F).fieldOf("rise_chance").forGetter(Branches::riseChance)
        ).apply(instance, Branches::new));
    }

    /** 分枝高度带占树干高度的比例：从 {@code min} 到 {@code max} */
    public record BranchBand(float min, float max) {
        public static final Codec<BranchBand> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.floatRange(0.0F, 1.0F).fieldOf("min").forGetter(BranchBand::min),
            Codec.floatRange(0.0F, 1.0F).fieldOf("max").forGetter(BranchBand::max)
        ).apply(instance, BranchBand::new));
    }

    /** 叶团水平尺寸偏移：枝顶逐枝抖动用 {@code branch}，树干顶那团封顶用 {@code crown} */
    public record RadiusOffsets(IntProvider branch, int crown) {
        public static final Codec<RadiusOffsets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            IntProvider.codec(-4, 4).fieldOf("branch").forGetter(RadiusOffsets::branch),
            Codec.intRange(-4, 4).fieldOf("crown").forGetter(RadiusOffsets::crown)
        ).apply(instance, RadiusOffsets::new));
    }

    public static final MapCodec<SkygateBranchTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance)
            .and(Branches.CODEC.fieldOf("branches").forGetter(placer -> placer.branches))
            .and(RadiusOffsets.CODEC.fieldOf("radius_offsets").forGetter(placer -> placer.radiusOffsets))
            .apply(instance, SkygateBranchTrunkPlacer::new)
    );
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final Branches branches;
    private final RadiusOffsets radiusOffsets;

    public SkygateBranchTrunkPlacer(
        int baseHeight,
        int heightRandA,
        int heightRandB,
        Branches branches,
        RadiusOffsets radiusOffsets
    ) {
        super(baseHeight, heightRandA, heightRandB);
        this.branches = branches;
        this.radiusOffsets = radiusOffsets;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return ModTrunkPlacers.SKYGATE_BRANCHING.get();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> setter,
        RandomSource random,
        int height,
        BlockPos origin,
        TreeConfiguration config
    ) {
        setDirtAt(level, setter, random, origin.below(), config);

        for (int y = 0; y < height; y++) {
            placeLog(level, setter, random, origin.above(y), config);
        }

        List<FoliagePlacer.FoliageAttachment> attachments = new ArrayList<>();
        // 树冠：树干正上方补一团，比枝顶那些更大，用来封顶
        attachments.add(new FoliagePlacer.FoliageAttachment(origin.above(height), this.radiusOffsets.crown(), false));

        int topLimit = Math.max(2, height - 2);
        int lowest = Mth.clamp(Math.round(height * this.branches.band().min()), 2, topLimit);
        int highest = Mth.clamp(Math.round(height * this.branches.band().max()), lowest, topLimit);
        int highestLeafY = origin.getY() + height - 1;
        int count = this.branches.count().sample(random);
        float baseAngle = random.nextFloat() * TWO_PI;
        int previousY = lowest - 2;

        for (int i = 0; i < count; i++) {
            // 按层均分再抖动，保证分枝铺满整段高度而不是挤成一团
            float t = count > 1 ? (i + (random.nextFloat() - 0.5F) * 0.4F) / (float) (count - 1) : 0.5F;
            int y = lowest + Math.round((highest - lowest) * t);
            if (y < previousY + 2) {
                y = previousY + 2;
            }

            y = Mth.clamp(y, 2, topLimit);
            previousY = y;

            // 方位角均分再抖动：落点取在圆周上，斜着长出来的枝也就有了各种角度
            float angle = baseAngle + TWO_PI * (i + random.nextFloat() * 0.6F) / count;
            int length = this.branches.length().sample(random);
            int reachX = (int) Math.round(length * Math.cos(angle));
            int reachZ = (int) Math.round(length * Math.sin(angle));
            if (reachX == 0 && reachZ == 0) {
                reachX = length;
            }

            this.growBranch(level, setter, random, config, attachments, origin.above(y), reachX, reachZ,
                this.radiusOffsets.branch().sample(random), highestLeafY);
        }

        return attachments;
    }

    private void growBranch(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> setter,
        RandomSource random,
        TreeConfiguration config,
        List<FoliagePlacer.FoliageAttachment> attachments,
        BlockPos start,
        int reachX,
        int reachZ,
        int radiusOffset,
        int highestLeafY
    ) {
        Function<BlockState, BlockState> alongBranch = axisState(
            Math.abs(reachX) >= Math.abs(reachZ) ? Direction.Axis.X : Direction.Axis.Z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int steps = Math.max(Math.abs(reachX), Math.abs(reachZ));

        // 整根枝只在起点掷一次抬升量，末端高度定下来之后走三维直线，枝上不会再长出竖木
        int rise = 0;
        if (random.nextFloat() < this.branches.riseChance()) {
            int ceiling = Math.max(0, highestLeafY - start.getY());
            rise = Math.min(ceiling, 1 + random.nextInt(Math.max(1, steps / 3 + 1)));
        }

        // 三维 Bresenham 直线：每步落一块原木，主轴每步走一格，副轴按比例进位，斜枝不会多填方块
        for (int step = 1; step <= steps; step++) {
            int x = Math.round((float) reachX * step / steps);
            int z = Math.round((float) reachZ * step / steps);
            int y = start.getY() + Math.round((float) rise * step / steps);
            BlockPos pixel = cursor.set(start.getX() + x, y, start.getZ() + z);
            if (this.validTreePos(level, pixel)) {
                setter.accept(pixel, alongBranch.apply(this.branches.provider().getState(random, pixel)));
            }
        }

        attachments.add(new FoliagePlacer.FoliageAttachment(cursor.immutable().above(), radiusOffset, false));
    }

    private static Function<BlockState, BlockState> axisState(Direction.Axis axis) {
        return state -> state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, axis) : state;
    }
}
