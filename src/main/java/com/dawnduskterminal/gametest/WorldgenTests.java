package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import com.dawnduskterminal.world.ChronoLine;
import com.dawnduskterminal.world.ChronoLineState;
import com.dawnduskterminal.world.ChronoTemperature;
import com.dawnduskterminal.world.ChronoVeinPalette;
import com.dawnduskterminal.world.PaletteBiomeSource;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.io.BufferedReader;
import java.io.IOException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 晨昏线维度自己拥有地形与群系：噪声设置直接指向模组方块，群系是忠实副本且特征表也铺同样的方块。
 * 两者合起来让维度在生成时就铺好自己的调色板，不需要对成品区块再做一遍处理
 *
 * <p>注意：{@code GameTestServer} 的世界由 {@code WorldPresets.FLAT} 构建，只带原版维度，所以任何需要
 * 晨昏线 {@code ServerLevel} 的地方都得处理它在这里不存在的情况
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class WorldgenTests {
    private static final String MOD_ID = "dawnduskterminal";
    private static final ResourceLocation CHRONO = ResourceLocation.fromNamespaceAndPath(MOD_ID, "chrono");
    private static final TagKey<Biome> HOLLOW_TREE_BIOMES =
        TagKey.create(Registries.BIOME, ResourceLocation.parse("twilightforest:valid_hollow_tree_biomes"));

    /** 移植后的主世界噪声路由需要的所有密度函数 */
    private static final String[] PORTED_DENSITY_FUNCTIONS = {
        "shift_x",
        "shift_z",
        "y",
        "overworld/base_3d_noise",
        "overworld/continents",
        "overworld/erosion",
        "overworld/depth",
        "overworld/factor",
        "overworld/jaggedness",
        "overworld/offset",
        "overworld/ridges",
        "overworld/ridges_folded",
        "overworld/sloped_cheese",
        "overworld/caves/entrances",
        "overworld/caves/noodle",
        "overworld/caves/pillars",
        "overworld/caves/spaghetti_2d",
        "overworld/caves/spaghetti_2d_thickness_modulator",
        "overworld/caves/spaghetti_roughness_function",
    };

    /** 那些密度函数读取的、与种子相关的噪声参数 */
    private static final String[] PORTED_NOISE_PARAMETERS = {
        "continentalness", "erosion", "ridge", "temperature", "vegetation", "jagged", "offset",
        "surface", "surface_swamp", "calcite", "gravel", "powder_snow", "ice", "packed_ice",
        "cave_cheese", "cave_layer", "cave_entrance", "spaghetti_2d", "spaghetti_roughness",
        "noodle", "pillar", "ore_vein_a", "ore_vein_b", "ore_veininess", "ore_gap",
        "aquifer_barrier", "aquifer_fluid_level_floodedness", "aquifer_fluid_level_spread", "aquifer_lava",
    };

    /** 维度必须有副本的主世界群系抽样 */
    /** 自建群系，没有同名原版副本，只查模组侧注册 */
    private static final String[] OWN_BIOMES = {
        "floatrees", "white_land", "blue_desert", "polar_dawn", "polar_dusk"
    };

    private static final String[] COPIED_BIOMES = {
        "plains", "sunflower_plains", "ocean", "deep_ocean", "frozen_ocean", "forest",
        "taiga", "jungle", "swamp", "mushroom_fields", "stony_peaks", "deep_dark", "lush_caves",
        "dripstone_caves",
    };

    private static NoiseGeneratorSettings chronoSettings(GameTestHelper helper) {
        Registry<NoiseGeneratorSettings> registry = helper.getLevel().getServer().registryAccess()
            .registryOrThrow(Registries.NOISE_SETTINGS);
        NoiseGeneratorSettings settings = registry.get(CHRONO);
        helper.assertTrue(settings != null, "noise settings " + CHRONO + " missing");
        return settings;
    }

    /** 收集一棵密度函数树读取到的噪声键 */
    private static Set<ResourceLocation> noiseKeys(DensityFunction function) {
        Set<ResourceLocation> keys = new LinkedHashSet<>();
        function.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction inner) {
                return inner;
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder holder) {
                holder.noiseData().unwrapKey().ifPresent(key -> keys.add(key.location()));
                return holder;
            }
        });
        return keys;
    }

    @GameTest(template = "ddt_empty")
    public static void chronoDimensionTypeIsRegistered(GameTestHelper helper) {
        var access = helper.getLevel().getServer().registryAccess();
        Registry<DimensionType> types = access.registryOrThrow(Registries.DIMENSION_TYPE);
        helper.assertTrue(types.containsKey(CHRONO), "chrono dimension type must be registered");
        helper.assertTrue(access.registryOrThrow(Registries.NOISE_SETTINGS).containsKey(CHRONO),
            "chrono noise settings must be registered");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void portedDensityFunctionsAreRegistered(GameTestHelper helper) {
        Registry<DensityFunction> registry =
            helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION);
        for (String id : PORTED_DENSITY_FUNCTIONS) {
            helper.assertTrue(registry.containsKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, id)),
                "ported density function missing: " + id);
        }
        // 移植是复制而不是替换：原版原件必须仍然完好
        helper.assertTrue(registry.containsKey(ResourceLocation.withDefaultNamespace("overworld/sloped_cheese")),
            "vanilla overworld/sloped_cheese must stay registered");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void portedNoiseParametersAreRegistered(GameTestHelper helper) {
        Registry<NormalNoise.NoiseParameters> registry =
            helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.NOISE);
        for (String id : PORTED_NOISE_PARAMETERS) {
            helper.assertTrue(registry.containsKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, id)),
                "ported noise parameter missing: " + id);
        }
        helper.succeed();
    }

    /**
     * 温度噪声的频率是手调的，不是原版那一个
     *
     * <p>原版主世界用的是 {@code firstOctave -10}，波长 `4096 / 1024` 格，比七带里任何一带都宽，所以它只会
     * 让边界在几公里上慢慢漂，带内部看不出结构。把首八度抬到 {@code -7} 后，波长 `512 / 128` 格：宽的那个
     * 让边界在带内凹凹凸凸，窄的那个把「跟晨昏线平行」的群系边界打散。幅度一个字没改，所以温度抖动幅度与
     * {@code ChronoTemperature} 的泄漏上界都不变
     *
     * <p>重跑 {@code tools/port_overworld_worldgen.py} 会把原版的 {@code -10} 写回来（脚本是忠实拷贝），这个
     * 断言就是那时候的报警
     */
    @GameTest(template = "ddt_empty")
    public static void temperatureNoiseRunsAtTheBandScale(GameTestHelper helper) {
        Registry<NormalNoise.NoiseParameters> registry =
            helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.NOISE);
        NormalNoise.NoiseParameters params = registry.getOrThrow(ResourceKey.create(Registries.NOISE,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "temperature")));
        helper.assertTrue(params.firstOctave() == -7,
            "the chrono temperature noise must stay at firstOctave -7 (512 / 128 block wavelengths); "
                + "the vanilla -10 only wanders over kilometres, got " + params.firstOctave());
        helper.assertTrue(params.amplitudes().size() == 6 && params.amplitudes().getDouble(0) == 1.5D,
            "the amplitude fingerprint is still vanilla's, only the frequency was retuned");
        helper.succeed();
    }

    /**
     * 把噪声参数搬进自己命名空间的全部意义。引擎把每个噪声场都建成
     * {@code NormalNoise.create(randomFactory.fromHashOf(key.location()), params)}，由世界种子决定，所以
     * {@code dawnduskterminal:*} 的键会得到一个与 {@code minecraft:*} 不同、且由种子派生的场，而原版原件
     * 保留自己的场
     */
    @GameTest(template = "ddt_empty")
    public static void terrainNoiseIsSeedDerived(GameTestHelper helper) {
        NoiseRouter router = chronoSettings(helper).noiseRouter();
        Set<ResourceLocation> keys = new LinkedHashSet<>();
        for (DensityFunction function : List.of(
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.fluidLevelSpreadNoise(),
                router.lavaNoise(),
                router.temperature(),
                router.vegetation(),
                router.continents(),
                router.erosion(),
                router.depth(),
                router.ridges(),
                router.initialDensityWithoutJaggedness(),
                router.finalDensity(),
                router.veinToggle(),
                router.veinRidged(),
                router.veinGap())) {
            keys.addAll(noiseKeys(function));
        }
        helper.assertTrue(!keys.isEmpty(), "the chrono router must read at least one noise parameter");
        for (ResourceLocation key : keys) {
            helper.assertTrue(MOD_ID.equals(key.getNamespace()),
                "router noise " + key + " is still in the vanilla namespace, so its field would be "
                    + "identical to the Overworld's instead of seed-derived");
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void paletteBiomeCopiesAreRegistered(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.BIOME);
        for (String name : COPIED_BIOMES) {
            helper.assertTrue(biomes.containsKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, name)),
                "chrono biome copy missing: " + name);
            helper.assertTrue(biomes.containsKey(ResourceLocation.withDefaultNamespace(name)),
                "the vanilla biome must stay untouched next to its copy: " + name);
        }
        for (String name : OWN_BIOMES) {
            helper.assertTrue(biomes.containsKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, name)),
                "self-made chrono biome missing: " + name);
        }
        helper.succeed();
    }

    /**
     * 复制群系的意义：副本必须铺*我们的*特征，这样维度生成时的花岗岩 闪长岩 安山岩 凝灰岩 岩浆 雪
     * 才会以模组方块出现
     */
    @GameTest(template = "ddt_empty")
    public static void paletteBiomeCopiesCarryOurFeatures(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.BIOME);
        Biome plains = biomes.get(ResourceLocation.fromNamespaceAndPath(MOD_ID, "plains"));
        helper.assertTrue(plains != null, MOD_ID + ":plains must be registered");
        Set<ResourceLocation> featureIds = new LinkedHashSet<>();
        for (HolderSet<PlacedFeature> step : plains.getGenerationSettings().features()) {
            for (Holder<PlacedFeature> holder : step) {
                holder.unwrapKey().ifPresent(key -> featureIds.add(key.location()));
            }
        }
        helper.assertTrue(!featureIds.isEmpty(), "the biome copy must keep its feature list");
        helper.assertTrue(featureIds.stream().anyMatch(id -> MOD_ID.equals(id.getNamespace())),
            "the biome copy must place our own features, found " + featureIds);
        helper.succeed();
    }

    /**
     * 高大暮色橡树（Robust Twilight Oak）是暮色森林的结构 {@code twilightforest:hollow_tree}，不是 placed_feature，
     * 它的群系选择器是 {@code #twilightforest:valid_hollow_tree_biomes}。那份标签原本只列暮色森林自己的群系，
     * 本模组的数据包把副本追加进去。标签一旦丢失，巨树只是不再出现，不会有任何报错
     */
    @GameTest(template = "ddt_empty")
    public static void hollowTreesAcceptTheChronoBiomes(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.BIOME);
        HolderSet.Named<Biome> valid = biomes.getTag(HOLLOW_TREE_BIOMES).orElse(null);
        helper.assertTrue(valid != null, HOLLOW_TREE_BIOMES.location() + " must resolve");
        for (String name : new String[] {
                "forest", "birch_forest", "old_growth_birch_forest", "flower_forest", "dark_forest",
                "mushroom_fields", "savanna"}) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, name));
            Holder<Biome> biome = biomes.getHolder(key).orElse(null);
            helper.assertTrue(biome != null, "chrono biome copy missing: " + name);
            helper.assertTrue(valid.contains(biome),
                MOD_ID + ":" + name + " must be in " + HOLLOW_TREE_BIOMES.location());
        }
        helper.succeed();
    }

    /**
     * 大型暮色橡树的地面门槛是 placed_feature 里的 {@code would_survive}，判定的是暮色橡树树苗；
     * 那个树苗注册成原版 {@code SaplingBlock}，{@code canSurvive} 只认 {@code #minecraft:dirt}。霞土霞草
     * 一旦掉出这个标签，树会静默消失，所以这里直接问方块本身
     */
    @GameTest(template = "ddt_empty")
    public static void twilightOakSaplingSurvivesOnRosyGround(GameTestHelper helper) {
        for (Block ground : new Block[] {ModTerrainBlocks.ROSY_GRASS.get(), ModTerrainBlocks.ROSY_DIRT.get()}) {
            helper.assertTrue(ground.defaultBlockState().is(BlockTags.DIRT),
                ground.getName().getString() + " must stay in the #minecraft:dirt tag the sapling checks");
        }
        Block sapling = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("twilightforest:twilight_oak_sapling"));
        helper.assertTrue(sapling != Blocks.AIR,
            "twilightforest:twilight_oak_sapling must be registered, Twilight Forest is a required dependency");
        BlockPos groundPos = new BlockPos(1, 1, 1);
        helper.setBlock(groundPos, ModTerrainBlocks.ROSY_GRASS.get());
        helper.assertTrue(sapling.defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(groundPos).above()),
            "the sapling must accept rosy grass as ground, otherwise no twilight oak can be placed on the chrono surface");
        helper.succeed();
    }

    /** 群系副本是照原版手抄的特征表，抄漏一条不会有任何报错，只是那种树不再出现 */
    @GameTest(template = "ddt_empty")
    public static void paletteBiomeCopiesRequestTwilightOaks(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.BIOME);
        ResourceLocation largeOak = ResourceLocation.parse("twilightforest:tree/large_twilight_oak_tree");
        for (String name : new String[] {"forest", "birch_forest", "old_growth_birch_forest", "savanna"}) {
            Biome biome = biomes.get(ResourceLocation.fromNamespaceAndPath(MOD_ID, name));
            helper.assertTrue(biome != null, "chrono biome copy missing: " + name);
            Set<ResourceLocation> featureIds = new LinkedHashSet<>();
            for (HolderSet<PlacedFeature> step : biome.getGenerationSettings().features()) {
                for (Holder<PlacedFeature> holder : step) {
                    holder.unwrapKey().ifPresent(key -> featureIds.add(key.location()));
                }
            }
            helper.assertTrue(featureIds.contains(largeOak),
                MOD_ID + ":" + name + " must keep requesting " + largeOak);
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void paletteBiomeSourceIsRegistered(GameTestHelper helper) {
        Registry<MapCodec<? extends BiomeSource>> registry =
            helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.BIOME_SOURCE);
        helper.assertTrue(registry.containsKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, "palette")),
            "the chrono biome source type must be registered");
        helper.succeed();
    }

    /**
     * 读维度 JSON 本身：{@code GameTestServer} 的 {@code level_stem} 注册表只有三条原版维度，模组维度
     * 起不来，所以这里直接把 {@code dimension/chrono.json} 里那段 {@code biome_source} 用 codec 解一遍。
     * {@code extra} 是唯一只能从 JSON 验的字段，其它测试都是手工 new 的
     */
    @GameTest(template = "ddt_empty")
    public static void chronoDimensionDeclaresThePaletteSource(GameTestHelper helper) {
        var access = helper.getLevel().getServer().registryAccess();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "dimension/chrono.json");
        JsonObject dimension;
        try (BufferedReader reader = helper.getLevel().getServer().getResourceManager()
            .getResource(id).orElseThrow().openAsReader()) {
            dimension = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("cannot read " + id, e);
        }
        BiomeSource source = BiomeSource.CODEC
            .parse(RegistryOps.create(JsonOps.INSTANCE, access),
                dimension.getAsJsonObject("generator").getAsJsonObject("biome_source"))
            .getOrThrow();
        helper.assertTrue(source instanceof PaletteBiomeSource,
            "chrono must use the palette biome source, got " + source);
        PaletteBiomeSource chrono = (PaletteBiomeSource) source;
        helper.assertTrue(chrono.preset().is(ResourceKey.create(
                Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
                ResourceLocation.withDefaultNamespace("overworld"))),
            "the palette source must read the vanilla overworld parameter list");
        helper.assertTrue(chrono.extra().size() == 3,
            "the dimension JSON must declare the two polar cells, got " + chrono.extra().size());
        List<ResourceKey<Biome>> possible = chrono.possibleBiomes().stream()
            .map(holder -> holder.unwrapKey().orElseThrow())
            .toList();
        for (String path : new String[] {"polar_dawn", "polar_dusk"}) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
            helper.assertTrue(possible.contains(key),
                "the extra biome must be reported as possible, missing " + path);
        }
        helper.succeed();
    }

    /** 保留下来，让移植后的特征排序仍然被跑到，尽管别处都不需要它 */
    @GameTest(template = "ddt_empty")
    public static void configuredFeaturesAreRegistered(GameTestHelper helper) {
        Registry<ConfiguredFeature<?, ?>> registry =
            helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        helper.assertTrue(registry.containsKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, "ore_granite")),
            "the palette copy of the granite ore feature must be registered");
        helper.assertTrue(registry.containsKey(ResourceLocation.withDefaultNamespace("ore_granite")),
            "the vanilla granite ore feature must stay registered for the overworld");
        helper.succeed();
    }

    /** 原版大矿脉在 {@code OreVeinifier} 里写死填充物；这里验的是替换结果 */
    @GameTest(template = "ddt_empty")
    public static void veinFillersUseThePalette(GameTestHelper helper) {
        helper.assertTrue(ChronoVeinPalette.remap(Blocks.GRANITE.defaultBlockState())
                .is(ModTerrainBlocks.OCHRE.get()),
            "the copper vein filler (granite) must become ochre");
        helper.assertTrue(ChronoVeinPalette.remap(Blocks.TUFF.defaultBlockState())
                .is(ModTerrainBlocks.TWILIGHT_STONE.get()),
            "the iron vein filler (tuff) must become twilight_stone");
        helper.assertTrue(ChronoVeinPalette.remap(Blocks.STONE.defaultBlockState()).is(Blocks.STONE),
            "unrelated blocks must pass through untouched");
        // 区块方块规则用 null 表示「保留默认方块」，这是最常见的情况
        helper.assertTrue(ChronoVeinPalette.remap(null) == null,
            "a null block state must pass through: it means 'leave the default block here'");
        // 包装器必须把这个 null 透传，而不是对它 NPE，那正是当初让区块生成卡死的 bug
        helper.assertTrue(ChronoVeinPalette.wrap(context -> null).calculate(null) == null,
            "the wrapped rule must forward a null result untouched");
        helper.assertTrue(ChronoVeinPalette.wrap(context -> Blocks.GRANITE.defaultBlockState())
                .calculate(null).is(ModTerrainBlocks.OCHRE.get()),
            "the wrapped rule must still remap a real vein filler");
        helper.succeed();
    }

    /**
     * 证明群系替换真的发生了：构造维度声明的那同一个源，检查原版主世界预设能报出的每个群系是否都变成
     * 了我们的副本。这条失败就说明维度在用原版群系生成，因而也在用原版特征
     */
    @GameTest(template = "ddt_empty")
    public static void paletteBiomeSourceSubstitutesThePreset(GameTestHelper helper) {
        var access = helper.getLevel().getServer().registryAccess();
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        Registry<MultiNoiseBiomeSourceParameterList> presets =
            access.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        Holder<MultiNoiseBiomeSourceParameterList> overworld = presets.getHolderOrThrow(ResourceKey.create(
            Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, ResourceLocation.withDefaultNamespace("overworld")));
        HolderSet<Biome> palette = biomes.getTag(TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "palette_biomes"))).orElseThrow();
        helper.assertTrue(palette.size() > 40,
            "the palette tag must resolve to the biome copies, got " + palette.size());

        BiomeSource delegate = MultiNoiseBiomeSource.createFromPreset(overworld);
        PaletteBiomeSource source = new PaletteBiomeSource(overworld, palette);
        Set<ResourceLocation> leaked = new LinkedHashSet<>();
        int sampled = 0;
        for (double temperature : new double[] {-1.5D, -1.0D, -0.5D, 0.0D, 0.5D, 1.0D, 1.5D}) {
            for (double humidity : new double[] {-1.0D, 0.0D, 1.0D}) {
                for (double continentalness : new double[] {-1.0D, -0.5D, 0.0D, 0.5D, 1.0D}) {
                    for (double erosion : new double[] {-1.0D, 0.0D, 1.0D}) {
                        for (double weirdness : new double[] {-1.0D, -0.5D, 0.0D, 0.5D, 1.0D}) {
                            Climate.Sampler sampler = new Climate.Sampler(
                                DensityFunctions.constant(temperature),
                                DensityFunctions.constant(humidity),
                                DensityFunctions.constant(continentalness),
                                DensityFunctions.constant(erosion),
                                DensityFunctions.constant(0.0D),
                                DensityFunctions.constant(weirdness),
                                List.of());
                            Holder<Biome> got = source.getNoiseBiome(0, 16, 0, sampler);
                            sampled++;
                            ResourceLocation key = got.unwrapKey().map(ResourceKey::location).orElse(null);
                            if (key == null || !MOD_ID.equals(key.getNamespace())) {
                                leaked.add(key);
                            }
                        }
                    }
                }
            }
        }
        helper.assertTrue(leaked.isEmpty(),
            "these biomes were not substituted out of " + sampled + " samples: " + leaked);

        // 昼区：温度被钉在热档，气候表报出的原版沙漠换成白地
        ResourceKey<Biome> vanillaDesert = ResourceKey.create(Registries.BIOME,
            ResourceLocation.withDefaultNamespace("desert"));
        ResourceKey<Biome> whiteLand = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "white_land"));
        int desertSamples = 0;
        for (double temperature : new double[] {0.55D, 1.0D}) {
            for (double humidity : new double[] {-1.0D, -0.5D}) {
                for (double continentalness : new double[] {0.3D, 1.0D}) {
                    for (double erosion : new double[] {-1.0D, 0.0D, 1.0D}) {
                        for (double weirdness : new double[] {-1.0D, -0.5D, 0.5D, 1.0D}) {
                            Climate.Sampler sampler = new Climate.Sampler(
                                DensityFunctions.constant(temperature),
                                DensityFunctions.constant(humidity),
                                DensityFunctions.constant(continentalness),
                                DensityFunctions.constant(erosion),
                                DensityFunctions.constant(0.0D),
                                DensityFunctions.constant(weirdness),
                                List.of());
                            if (!delegate.getNoiseBiome(0, 16, 0, sampler).is(vanillaDesert)) {
                                continue;
                            }
                            desertSamples++;
                            Holder<Biome> got = source.getNoiseBiome(0, 16, 0, sampler);
                            helper.assertTrue(got.is(whiteLand),
                                "vanilla desert must become the white land copy, got "
                                    + got.unwrapKey().map(ResourceKey::location).orElse(null));
                        }
                    }
                }
            }
        }
        helper.assertTrue(desertSamples > 0, "no sampler produced a vanilla desert to swap");

        // 分带映射：六个断点把 param 切七段，每段端点必须落在原版温度档的边界上，
        // 否则某一带会跨档，带里就冒出别的带的群系（比如热带刷出针叶林）
        helper.assertTrue(ChronoTemperature.bandTemperature(ChronoTemperature.POLAR_NIGHT) == -1.0D,
            "-0.8 must map to the frozen band floor");
        helper.assertTrue(ChronoTemperature.bandTemperature(ChronoTemperature.NIGHT_ZONE) == -0.45D,
            "-0.6 must map to the frozen band ceiling, which is where the night zone starts");
        helper.assertTrue(ChronoTemperature.bandTemperature(ChronoTemperature.COLD_ZONE) == -0.15D,
            "-0.3 must map to the cold band ceiling, which is where the temperate band starts");
        helper.assertTrue(ChronoTemperature.bandTemperature(ChronoTemperature.TROPIC_ZONE) == 0.20D,
            "0.3 must map to the temperate band ceiling, which is where the tropic band starts");
        helper.assertTrue(ChronoTemperature.bandTemperature(ChronoTemperature.DAY_ZONE) == 0.55D,
            "0.6 must map to the tropic band ceiling, which is where the day zone starts");
        helper.assertTrue(ChronoTemperature.bandTemperature(ChronoTemperature.POLAR_DAY) == 1.0D,
            "0.8 must map to the hot band ceiling");
        // 极带继续往外推：线端是 POLAR_TEMPERATURE，extra 那几条的跨度就从 ±1 起
        helper.assertTrue(ChronoTemperature.bandTemperature(1.0D) == ChronoTemperature.POLAR_TEMPERATURE,
            "param 1.0 must reach the polar day ceiling");
        helper.assertTrue(ChronoTemperature.bandTemperature(-1.0D) == -ChronoTemperature.POLAR_TEMPERATURE,
            "param -1.0 must reach the polar night floor");
        // 带内单调：沿着线走，温度不能反向
        for (double param = -0.79D; param < 0.79D; param += 0.01D) {
            helper.assertTrue(ChronoTemperature.bandTemperature(param)
                    <= ChronoTemperature.bandTemperature(param + 0.01D),
                "the band mapping must be monotonic at param " + param);
        }
        // 温带带内即使算上噪声也到不了沙漠与雪原的门槛（0.55 / -0.45）。交界抖动与 SPREAD 同时满幅，
        // 所以泄漏的上界是两者之和
        double seamReach = ChronoTemperature.SPREAD + ChronoTemperature.SEAM_DITHER;
        double temperateCeiling = ChronoTemperature.bandTemperature(ChronoTemperature.TROPIC_ZONE);
        double temperateFloor = ChronoTemperature.bandTemperature(ChronoTemperature.COLD_ZONE);
        helper.assertTrue(temperateCeiling + seamReach < 0.55D,
            "the temperate band must not reach the desert threshold even with noise, got "
                + (temperateCeiling + seamReach));
        helper.assertTrue(temperateFloor - seamReach > -0.45D,
            "the temperate band must not reach the snowy threshold even with noise, got "
                + (temperateFloor - seamReach));

        // 交界抖动：断点上满幅，离开 SEAM_WIDTH 归零，带心一点不动
        helper.assertTrue(ChronoTemperature.seamWeight(ChronoTemperature.TROPIC_ZONE) == 1.0D,
            "a breakpoint must get the full seam weight");
        helper.assertTrue(ChronoTemperature.seamWeight(ChronoTemperature.TROPIC_ZONE
                + ChronoTemperature.SEAM_WIDTH) == 0.0D,
            "the seam weight must be gone one SEAM_WIDTH away from the breakpoint");
        helper.assertTrue(ChronoTemperature.seamWeight(0.0D) == 0.0D,
            "the middle of the temperate band must stay free of seam jitter");
        helper.assertTrue(ChronoTemperature.seamDither(0.0D, 1234.0D, -5678.0D) == 0.0D,
            "band cores must not be jittered");
        // 极带边界（±0.8）也吃抖动，但极带内部即使满幅抖动也必须留在 extra 的 [1, 2]
        helper.assertTrue(ChronoTemperature.seamWeight(ChronoTemperature.POLAR_DAY) == 1.0D,
            "the polar seams must be jittered too, otherwise those two lines stay razor sharp");
        helper.assertTrue(ChronoTemperature.bandTemperature(0.9D) - ChronoTemperature.SEAM_DITHER > 1.0D,
            "the polar bands must stay inside the extra spans even at full seam jitter");

        // 断点两侧都得被推过温标：两侧都有正有负，交界才会两侧群系都出现，而不是一条线
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double x = -2000.0D; x <= 2000.0D; x += 37.0D) {
            for (double z = -2000.0D; z <= 2000.0D; z += 37.0D) {
                double dither = ChronoTemperature.seamDither(ChronoTemperature.TROPIC_ZONE, x, z);
                helper.assertTrue(Math.abs(dither) <= ChronoTemperature.SEAM_DITHER,
                    "the seam jitter must stay within SEAM_DITHER, got " + dither);
                min = Math.min(min, dither);
                max = Math.max(max, dither);
            }
        }
        helper.assertTrue(max > 0.02D && min < -0.02D,
            "the seam jitter must push both ways at a breakpoint, got " + min + " ~ " + max);

        // 连续：逐格跳 0.01 以上就会把交界切成格子状断层
        for (double z = -1500.0D; z < 1500.0D; z += 1.0D) {
            double a = ChronoTemperature.seamDither(ChronoTemperature.COLD_ZONE, 300.0D, z);
            double b = ChronoTemperature.seamDither(ChronoTemperature.COLD_ZONE, 300.0D, z + 1.0D);
            helper.assertTrue(Math.abs(a - b) < 0.01D,
                "the seam jitter must stay smooth at z " + z + ", got a step of " + Math.abs(a - b));
        }
        helper.succeed();
    }

    /**
     * 极带靠 {@code extra} 那几条自建点选群系：{@link ChronoTemperature} 把 {@code param} 推到 {@code ±1}
     * 时温度到 {@link ChronoTemperature#POLAR_TEMPERATURE}，正好落进温度跨度 {@code ±1 ~ ±2} 的那几条；
     * 极夜那条按湿度切成两半，干的一半是蓝沙沙漠。线中同一套采样器必须还是原版那一侧，否则说明加点的
     * 范围写宽了
     */
    @GameTest(template = "ddt_empty")
    public static void polarBandsUseTheExtraBiomes(GameTestHelper helper) {
        var access = helper.getLevel().getServer().registryAccess();
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        Registry<MultiNoiseBiomeSourceParameterList> presets =
            access.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        Holder<MultiNoiseBiomeSourceParameterList> overworld = presets.getHolderOrThrow(ResourceKey.create(
            Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, ResourceLocation.withDefaultNamespace("overworld")));
        HolderSet<Biome> palette = biomes.getTag(TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "palette_biomes"))).orElseThrow();

        // 与维度 JSON 里的 extra 同形：极昼一条，极夜按湿度切两条
        Climate.Parameter full = Climate.Parameter.span(-1.0F, 1.0F);
        Climate.Parameter dry = Climate.Parameter.span(-1.0F, -0.35F);
        Climate.Parameter wet = Climate.Parameter.span(-0.35F, 1.0F);
        ResourceKey<Biome> dawn = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "polar_dawn"));
        ResourceKey<Biome> dusk = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "polar_dusk"));
        ResourceKey<Biome> blueDesert = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "blue_desert"));
        // 湿度可调，用来验极夜那两条是按干湿分开的
        java.util.function.Function<Double, Climate.Sampler> sampler = humidity -> new Climate.Sampler(
            ChronoTemperature.wrap(DensityFunctions.constant(0.0D)),
            DensityFunctions.constant(humidity),
            DensityFunctions.constant(0.0D),
            DensityFunctions.constant(0.0D),
            DensityFunctions.constant(0.0D),
            DensityFunctions.constant(0.0D),
            List.of());
        List<PaletteBiomeSource.ExtraBiome> extra = List.of(
            new PaletteBiomeSource.ExtraBiome(Climate.Parameter.span(1.0F, 2.0F), full, full, full, full,
                biomes.getHolderOrThrow(dawn)),
            new PaletteBiomeSource.ExtraBiome(Climate.Parameter.span(-2.0F, -1.0F), wet, full, full, full,
                biomes.getHolderOrThrow(dusk)),
            new PaletteBiomeSource.ExtraBiome(Climate.Parameter.span(-2.0F, -1.0F), dry, full, full, full,
                biomes.getHolderOrThrow(blueDesert)));
        PaletteBiomeSource source = new PaletteBiomeSource(overworld, palette, extra);
        ChronoLine previous = ChronoLineState.hasServerLine() ? ChronoLineState.serverLine() : null;
        ChronoLineState.setServerLine(ChronoLine.fallback());
        try {
            // 温度包成和区块里一样的分段映射，极带的 ±2 就是这个包装算出来的
            // 25000 quart = 100000 格，param（blockX / maxDistance 5000）在这里被钳到 ±1
            helper.assertTrue(source.getNoiseBiome(25000, 16, 0, sampler.apply(0.0D)).is(dawn),
                "param 1.0 must land in the polar day biome");
            helper.assertTrue(source.getNoiseBiome(-25000, 16, 0, sampler.apply(0.5D)).is(dusk),
                "the wet half of the polar night must be the polar dusk plains");
            helper.assertTrue(source.getNoiseBiome(-25000, 16, 0, sampler.apply(-0.7D)).is(blueDesert),
                "the dry half of the polar night must be the blue sand desert");
            Holder<Biome> middle = source.getNoiseBiome(0, 16, 0, sampler.apply(0.0D));
            helper.assertTrue(!middle.is(dawn) && !middle.is(dusk) && !middle.is(blueDesert),
                "param 0 must stay on the vanilla climate table, got "
                    + middle.unwrapKey().map(ResourceKey::location).orElse(null));
        } finally {
            if (previous != null) {
                ChronoLineState.setServerLine(previous);
            }
        }
        helper.succeed();
    }

}
