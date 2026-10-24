package com.dawnduskterminal.world;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.config.DdtConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 让晨昏线维度用自己那份主世界群系副本
 *
 * <p>气候表直接取原版预设（{@code minecraft:overworld}，表由 {@code OverworldBiomeBuilder} 在代码里
 * 构建），把它报出的每个群系换成 {@code dawnduskterminal} 命名空间下的同名副本
 *
 * <p>这些副本的特征表里带着晨昏线方块调色板，维度因此能在生成时就铺自己的方块，而不是先生成原版的再
 * 事后打补丁
 *
 * <p>分带交给温度：{@link ChronoTemperature} 把 {@code param} 分段映射到原版气候表的温度档，每一带只
 * 对应一档温度，于是群系全部按六要素（温度 湿度 大陆性 侵蚀 深度 怪异度）从表里选出来
 *
 * <p>选格用原版那张气候表，只在末尾追加自己的点（{@link #extra}）。原版覆盖的是 {@code [-1, 1]} 的
 * 温度区间，出界的地方是空的，所以极带（{@code |param| >= 0.8}，温度被 {@link ChronoTemperature} 推到
 * {@code ±2}）那些点一定赢，原版的群系一格不动
 *
 * <p>白地没有出界的地盘可用（它要的是昼区），所以在 {@link #replacements()} 里改指一格：热档的沙漠换白地。
 * 改指只决定那一格装哪个副本，选格仍然是六要素算出来的
 *
 * <p>占位群系不改注册名，显示名在 lang 里换。分带表见 {@code docs/worldgen.md} 的「温带分带与温度映射」
 */
public final class PaletteBiomeSource extends BiomeSource {
    /** 天门（空岛）的深度门槛 */
    private static final float FLOATREES_DEPTH = -1.6F;

    public static final MapCodec<PaletteBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        MultiNoiseBiomeSourceParameterList.CODEC.fieldOf("preset").forGetter(PaletteBiomeSource::preset),
        RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("palette").forGetter(PaletteBiomeSource::palette),
        ExtraBiome.CODEC.listOf().optionalFieldOf("extra", List.of()).forGetter(PaletteBiomeSource::extra)
    ).apply(inst, PaletteBiomeSource::new));

    /**
     * 追加到原版气候表后面的点，形状比着内联 {@code multi_noise} 的条目写
     *
     * <p>只在原版没有覆盖的地方生效：温度跨度一旦越过 {@code ±1}，原版最近的点也要付距离，这些点则是
     * 0。湿度 大陆性 侵蚀 怪异度原版都铺满 {@code -1 ~ 1}，往那些方向加点永远输
     */
    public record ExtraBiome(Climate.Parameter temperature, Climate.Parameter humidity,
                             Climate.Parameter continentalness, Climate.Parameter erosion,
                             Climate.Parameter weirdness, Holder<Biome> biome) {
        public static final Codec<ExtraBiome> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Climate.Parameter.CODEC.fieldOf("temperature").forGetter(ExtraBiome::temperature),
            Climate.Parameter.CODEC.fieldOf("humidity").forGetter(ExtraBiome::humidity),
            Climate.Parameter.CODEC.fieldOf("continentalness").forGetter(ExtraBiome::continentalness),
            Climate.Parameter.CODEC.fieldOf("erosion").forGetter(ExtraBiome::erosion),
            Climate.Parameter.CODEC.fieldOf("weirdness").forGetter(ExtraBiome::weirdness),
            Biome.CODEC.fieldOf("biome").forGetter(ExtraBiome::biome)
        ).apply(inst, ExtraBiome::new));

        /**
         * 深度按点值 0 与 1 各插一份，跟 {@code OverworldBiomeBuilder.addSurfaceBiome} 一致；
         * 写成跨度的话，深度落在 0 与 1 之间时原版要付距离、这里不付，群系就会随 y 乱跳
         */
        Stream<Pair<Climate.ParameterPoint, Holder<Biome>>> points() {
            return Stream.of(0.0F, 1.0F).map(depth -> Pair.of(
                Climate.parameters(temperature, humidity, continentalness, erosion,
                    Climate.Parameter.point(depth), weirdness, 0.0F),
                biome));
        }
    }

    private final Holder<MultiNoiseBiomeSourceParameterList> preset;
    private final HolderSet<Biome> palette;
    private final List<ExtraBiome> extra;
    /**
     * 有意延迟构建：群系源是在数据包还在解析时构造的，那一刻从 {@code #tag} 取出的 holder 还没有 key，
     * {@code unwrapKey()} 是空的，所以在构造函数里建这张表只会静默得到空表，替换等于没做。到第一个区块
     * 时标签已绑定，key 就在了。用 volatile 是因为区块生成是多线程的，构建本身幂等
     */
    private volatile Map<ResourceLocation, Holder<Biome>> replacements;
    /** 原版气候表 + {@link #extra}，构建理由同 {@link #replacements} */
    private volatile Climate.ParameterList<Holder<Biome>> extended;
    private volatile Holder<Biome> floatrees;

    /**
     * 每线程一份的晨昏线温度缓存
     *
     * <p>{@code LevelChunkSection#fillBiomesFromNoise} 的循环是 x 外层 y 中层 z 内层，同一个
     * {@code (x, z)} 在一节里被问四次、跨二十四节被问九十六次。晨昏线维度的温度只由 x/z 决定
     * （{@code noise_settings/chrono.json} 里 {@code y_scale} 与 {@code shift_y} 都是 0），所以直接缓存
     * 换好的温度，命中率在区块填充里超过 95%
     *
     * <p>缓存按采样器身份分代：换区块时采样器跟着换，整张表清空。这样既不会把上一个区块的值带过来，
     * 也不会长期持有 {@code NoiseChunk} 的函数图（采样器用弱引用比较）
     */
    private static final ThreadLocal<ShiftedTemperatureCache> SHIFTED_TEMPERATURES =
        ThreadLocal.withInitial(ShiftedTemperatureCache::new);

    public PaletteBiomeSource(Holder<MultiNoiseBiomeSourceParameterList> preset, HolderSet<Biome> palette) {
        this(preset, palette, List.of());
    }

    public PaletteBiomeSource(Holder<MultiNoiseBiomeSourceParameterList> preset, HolderSet<Biome> palette,
                              List<ExtraBiome> extra) {
        this.preset = preset;
        this.palette = palette;
        this.extra = extra;
    }

    /**
     * 主世界的气候表是代码构建的（OverworldBiomeBuilder），数据包无法把群系加进自定义气候区间。所以
     * 自建的空岛群系只能搭在气候表之前：采样深度低于阈值时给 floatrees
     */
    private Holder<Biome> floatrees() {
        Holder<Biome> holder = this.floatrees;
        if (holder == null) {
            this.floatrees = holder = copyOf("floatrees");
        }
        return holder;
    }

    /** 从调色板标签里按下标路径找同名副本，找不到返回 null */
    private Holder<Biome> copyOf(String path) {
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, DawnDuskTerminal.id(path));
        for (Holder<Biome> candidate : this.palette) {
            if (candidate.is(key)) {
                return candidate;
            }
        }
        return null;
    }

    private Map<ResourceLocation, Holder<Biome>> replacements() {
        Map<ResourceLocation, Holder<Biome>> map = this.replacements;
        if (map == null) {
            Map<ResourceLocation, Holder<Biome>> built = new HashMap<>();
            for (Holder<Biome> holder : this.palette) {
                holder.unwrapKey().ifPresent(key -> built.put(
                    ResourceLocation.withDefaultNamespace(key.location().getPath()), holder));
            }
            if (built.isEmpty()) {
                DawnDuskTerminal.LOGGER.error(
                    "DawnDuskTerminal palette biome source resolved no biome copies ({} entries in the tag); "
                        + "the chrono dimension would generate with vanilla biomes", this.palette.size());
            }
            // 同名格子自己就对上了，只有一格要改指。热档那格原版是遍地的沙漠，换成白地：昼区
            // 0.6~0.8 的温度被钉在 0.55~1.0，那档里五档湿度原版全是沙漠，不改指就整片是沙
            remap(built, "desert", copyOf("white_land"));
            this.replacements = map = Map.copyOf(built);
        }
        return map;
    }

    /** 把原版某一格改指到副本；副本不在调色板标签里就保持原样 */
    private static void remap(Map<ResourceLocation, Holder<Biome>> map, String vanillaPath, Holder<Biome> copy) {
        if (copy != null) {
            map.put(ResourceLocation.withDefaultNamespace(vanillaPath), copy);
        }
    }

    public Holder<MultiNoiseBiomeSourceParameterList> preset() {
        return this.preset;
    }

    public List<ExtraBiome> extra() {
        return this.extra;
    }

    /** 原版的表加我们的点，返回给 {@code findValue} 用；原版算法一个字没改 */
    private Climate.ParameterList<Holder<Biome>> extended() {
        Climate.ParameterList<Holder<Biome>> list = this.extended;
        if (list == null) {
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> values =
                new ArrayList<>(this.preset.value().parameters().values());
            this.extra.forEach(entry -> entry.points().forEach(values::add));
            this.extended = list = new Climate.ParameterList<>(values);
        }
        return list;
    }

    public HolderSet<Biome> palette() {
        return this.palette;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(
            Stream.concat(this.preset.value().parameters().values().stream().map(Pair::getSecond),
                this.palette.stream()),
            this.extra.stream().map(ExtraBiome::biome));
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext(
            QuartPos.toBlock(quartX), QuartPos.toBlock(quartY), QuartPos.toBlock(quartZ));
        // 原版 Climate.Sampler#sample 的六项照抄，只把温度换成晨昏线分段映射。这样结构判定 区块落盘
        // locate biome 刷怪读的是同一个群系函数，两边不可能再分叉
        double temperature = SHIFTED_TEMPERATURES.get().shifted(sampler, quartX, quartZ, context);
        Climate.TargetPoint target = Climate.target(
            (float) temperature,
            (float) sampler.humidity().compute(context),
            (float) sampler.continentalness().compute(context),
            (float) sampler.erosion().compute(context),
            (float) sampler.depth().compute(context),
            (float) sampler.weirdness().compute(context));
        Holder<Biome> floatreesHolder = floatrees();
        if (floatreesHolder != null && Climate.unquantizeCoord(target.depth()) < FLOATREES_DEPTH) {
            return floatreesHolder;
        }
        // 原版那套选点算法，只是表尾多了极带那几个点
        Holder<Biome> base = extended().findValue(target);
        ResourceLocation key = base.unwrapKey().map(ResourceKey::location).orElse(null);
        if (key == null || !"minecraft".equals(key.getNamespace())) {
            return base;
        }
        Holder<Biome> replacement = replacements().get(key);
        return replacement != null ? replacement : base;
    }

    /**
     * {@code (x, z)} 到「已换好的温度」的直映射缓存，八个槽位
     *
     * <p>八个槽位对应原版填充循环里同一 x 的四个 z（跨 y 与跨节都会命中）。换采样器时整表失效，
     * 所以不会跨区块串值；线或 {@code max_distance} 变了也失效
     */
    private static final class ShiftedTemperatureCache {
        private static final int SLOTS = 8;

        private final boolean[] valid = new boolean[SLOTS];
        private final int[] xs = new int[SLOTS];
        private final int[] zs = new int[SLOTS];
        private final double[] values = new double[SLOTS];
        private WeakReference<Climate.Sampler> sampler;
        private ChronoLine line;
        private double maxDistance;

        double shifted(Climate.Sampler current, int quartX, int quartZ,
                       DensityFunction.SinglePointContext context) {
            ChronoLine currentLine = ChronoLineState.serverLine();
            double currentMaxDistance = DdtConfig.maxDistance();
            if (this.sampler == null || this.sampler.get() != current
                || this.line != currentLine || this.maxDistance != currentMaxDistance) {
                this.sampler = new WeakReference<>(current);
                this.line = currentLine;
                this.maxDistance = currentMaxDistance;
                Arrays.fill(this.valid, false);
            }
            int slot = (quartX * 31 + quartZ) & (SLOTS - 1);
            if (this.valid[slot] && this.xs[slot] == quartX && this.zs[slot] == quartZ) {
                return this.values[slot];
            }
            double vanilla = current.temperature().compute(context);
            double shifted = ChronoTemperature.shift(vanilla, context.blockX(), context.blockZ());
            this.valid[slot] = true;
            this.xs[slot] = quartX;
            this.zs[slot] = quartZ;
            this.values[slot] = shifted;
            return shifted;
        }
    }
}
