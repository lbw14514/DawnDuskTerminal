package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.block.CarminiteFireBlock;
import com.dawnduskterminal.block.Experiment052Block;
import com.dawnduskterminal.entity.CarminiteShrapnelEntity;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.world.CarminiteBlast;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 试验物品 052 的规格
 *
 * <p>钉住的是这一整条链：占位号让出去了、物品是那个方块的 {@link BlockItem}、状态只有 {@code active}
 * 一个且点亮时是 15 级光、<b>一个</b>红石信号就点、3 秒后方块自己没了、砷铅铁火焰不蔓延不点燃只掉 2 点血
 * 还会按原版火焰那套年龄机制自己熄灭、碎片打出的是爆炸伤害且落地会补火、
 * 方块状态与模型是原版火焰那套 multipart
 *
 * <p>引信那一条真的跑满 {@value Experiment052Block#FUSE_TICKS} tick（{@code runAfterDelay}），
 * 爆炸是 {@code ServerLevel#explode}、会真的炸出一片空地，所以那条测试给了 200 tick 的超时
 *
 * <p>碎片与火焰的判定不等方块自然刻：碎片的 {@code tick()} 与火焰方块的
 * {@code BlockState#tick(...)} 都是一拍一拍手动推的，测试跑得快也不会因为元素没 tick 而红
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment052Tests {
    private static final String SUBJECT = "dawnduskterminal:experiment_052";

    /** 猪的满血：{@code EntityType.PIG} 的 {@code max_health} 是 10 */
    private static final float PIG_HEALTH = 10.0F;

    /** 测试结构是 16x16x16 的空房间，中间这片随便摆 */
    private static final BlockPos CENTRE = new BlockPos(4, 3, 4);

    /** 原版火焰那套模型文件名，方块状态里这十个都要用到 */
    private static final List<String> FIRE_MODELS = List.of(
        "carminite_fire_floor0", "carminite_fire_floor1",
        "carminite_fire_side0", "carminite_fire_side1",
        "carminite_fire_side_alt0", "carminite_fire_side_alt1",
        "carminite_fire_up0", "carminite_fire_up1",
        "carminite_fire_up_alt0", "carminite_fire_up_alt1");

    @GameTest(template = "ddt_empty")
    public static void blockReplacesThePlaceholder(GameTestHelper helper) {
        helper.assertFalse(ModExperimentItems.placeholders().containsKey(52),
            "number 52 must not be a placeholder anymore, it has a block now");
        Item item = ModItems.EXPERIMENT_052.get();
        helper.assertTrue(item instanceof BlockItem blockItem && blockItem.getBlock() == ModBlocks.EXPERIMENT_052.get(),
            SUBJECT + " must be the item form of its own block, got " + item);
        // 方块物品的名字取自方块，所以语言文件的键是 block.dawnduskterminal.experiment_052
        helper.assertTrue(item.getDescriptionId().equals("block.dawnduskterminal.experiment_052"),
            "a block item must take its name from the block, got " + item.getDescriptionId());
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(DawnDuskTerminal.id("carminite_shrapnel")),
            "dawnduskterminal:carminite_shrapnel must be registered, the explosion needs something to throw");
        helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(DawnDuskTerminal.id("experiment_052_ignite")),
            "dawnduskterminal:experiment_052_ignite must be registered, igniting plays it");
        helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(DawnDuskTerminal.id("experiment_052_explode")),
            "dawnduskterminal:experiment_052_explode must be registered, the blast plays it");
        // 爆炸用自己的那一种粒子（同一套贴图、更慢的帧），反应场那一种不许被改
        helper.assertTrue(BuiltInRegistries.PARTICLE_TYPE.containsKey(DawnDuskTerminal.id("particle_carminite_blast")),
            "dawnduskterminal:particle_carminite_blast must be registered, the blast uses it");
        helper.assertTrue(BuiltInRegistries.PARTICLE_TYPE.containsKey(DawnDuskTerminal.id("particle_carminite_effect")),
            "dawnduskterminal:particle_carminite_effect must stay registered for the field");
        helper.assertTrue(BuiltInRegistries.PARTICLE_TYPE.containsKey(DawnDuskTerminal.id("particle_carminite_ember")),
            "dawnduskterminal:particle_carminite_ember must be registered, the fire and the shrapnel use it");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void activeIsTheOnlyStateAndLightsFifteen(GameTestHelper helper) {
        BlockState state = ModBlocks.EXPERIMENT_052.get().defaultBlockState();
        helper.assertFalse(state.getValue(Experiment052Block.ACTIVE),
            "a placed " + SUBJECT + " must start inactive");
        helper.assertTrue(Experiment052Block.ACTIVE.getName().equals("active"),
            "the lit property must be named active, got " + Experiment052Block.ACTIVE.getName());
        helper.assertTrue(state.getProperties().size() == 1,
            SUBJECT + " must carry exactly one state, got " + state.getProperties());

        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CENTRE);
        BlockState lit = state.setValue(Experiment052Block.ACTIVE, true);
        helper.assertTrue(state.getLightEmission(level, pos) == 0,
            "an inactive " + SUBJECT + " must not emit light, got " + state.getLightEmission(level, pos));
        helper.assertTrue(lit.getLightEmission(level, pos) == 15,
            "an armed " + SUBJECT + " must emit 15 light levels, got " + lit.getLightEmission(level, pos));
        helper.succeed();
    }

    /**
     * 一个红石信号就点火，然后 {@value Experiment052Block#FUSE_TICKS} tick 之后炸掉
     *
     * <p>红石块贴在旁边，一面就够：规格是「任意一个红石信号」，不是六面全通
     */
    @GameTest(template = "ddt_empty", timeoutTicks = 200)
    public static void oneSignalIgnitesAndTheFuseRunsOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(CENTRE);
        level.setBlockAndUpdate(centre, ModBlocks.EXPERIMENT_052.get().defaultBlockState());
        helper.assertFalse(level.getBlockState(centre).getValue(Experiment052Block.ACTIVE),
            "an unpowered " + SUBJECT + " must stay inactive");

        level.setBlockAndUpdate(centre.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.assertTrue(level.getBlockState(centre).getValue(Experiment052Block.ACTIVE),
            "a single redstone signal must arm " + SUBJECT);
        helper.assertTrue(level.getBlockTicks().hasScheduledTick(centre, ModBlocks.EXPERIMENT_052.get()),
            "arming " + SUBJECT + " must schedule the " + Experiment052Block.FUSE_TICKS + " tick fuse");

        helper.runAfterDelay(Experiment052Block.FUSE_TICKS + 10, () -> {
            helper.assertFalse(level.getBlockState(centre).is(ModBlocks.EXPERIMENT_052.get()),
                SUBJECT + " must be gone after the fuse runs out");
            // 爆炸同时会把附近刷成 dawnduskterminal:old_growth_birch_forest
            helper.assertTrue(level.getBiome(centre).is(CarminiteBlast.BLAST_BIOME),
                "the blast must repaint the biome around it, got " + level.getBiome(centre));
            helper.succeed();
        });
    }

    /**
     * 连锁：范围内没点着的同类方块不走进点火那套，爆炸当时就被点亮并排上一个 1~10 tick 的随机计划刻
     *
     * <p>三个方块摆成一行，只给中间那个红石信号；两侧在爆炸之前不许点亮，之后要在十几 tick 内炸掉。
     * 爆炸会真的拆掉一片地形，所以给了 {@code FUSE_TICKS + 30} 的等待与 260 tick 的超时
     */
    @GameTest(template = "ddt_empty", timeoutTicks = 260)
    public static void neighbouringBlocksChainDetonateWithoutIgniting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(CENTRE);
        BlockPos west = centre.west(2);
        BlockPos east = centre.east(2);
        level.setBlockAndUpdate(west, ModBlocks.EXPERIMENT_052.get().defaultBlockState());
        level.setBlockAndUpdate(centre, ModBlocks.EXPERIMENT_052.get().defaultBlockState());
        level.setBlockAndUpdate(east, ModBlocks.EXPERIMENT_052.get().defaultBlockState());
        level.setBlockAndUpdate(centre.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.assertTrue(level.getBlockState(centre).getValue(Experiment052Block.ACTIVE),
            "the centre block must arm on the single redstone signal");
        helper.assertFalse(level.getBlockState(west).getValue(Experiment052Block.ACTIVE),
            "the neighbouring block must not arm before the blast happens");

        helper.runAfterDelay(Experiment052Block.FUSE_TICKS + 30, () -> {
            helper.assertFalse(level.getBlockState(centre).is(ModBlocks.EXPERIMENT_052.get()),
                "the centre block must be gone after its fuse");
            helper.assertFalse(level.getBlockState(west).is(ModBlocks.EXPERIMENT_052.get()),
                "a block inside the blast radius must chain-detonate instead of being destroyed");
            helper.assertFalse(level.getBlockState(east).is(ModBlocks.EXPERIMENT_052.get()),
                "a block inside the blast radius must chain-detonate instead of being destroyed");
            helper.succeed();
        });
    }

    /**
     * 一次爆炸最多连带引爆五个最近的同类方块；范围内没被连锁到的同类方块<b>不会被这次爆炸拆掉</b>
     *
     * <p>七个方块围爆心摆成一圈（距离 1~1.5 格，全部落在这颗威力 6 的爆炸的破坏名单里）。
     * 直接调 {@link CarminiteBlast#large}，同一 tick 里数：
     * <ul>
     *   <li>还站着的必须是 7 个——整类都被从破坏名单里摘掉了，一颗都不该被炸毁
     *   <li>亮着的必须正好 5 个——只有离爆心最近的五个进了连锁，引信是 1~10 tick 的随机值，这一瞬间还没到点
     * </ul>
     *
     * <p>再等 30 tick：连锁会把剩下那两个也卷进去，七个最终都不在
     */
    @GameTest(template = "ddt_empty", timeoutTicks = 200)
    public static void onlyTheFiveNearestBlocksAreChained(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(CENTRE);
        BlockPos[] ring = {
            new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1),
            new BlockPos(1, 0, 1), new BlockPos(-1, 0, -1), new BlockPos(1, 0, -1)};
        for (BlockPos offset : ring) {
            level.setBlockAndUpdate(centre.offset(offset), ModBlocks.EXPERIMENT_052.get().defaultBlockState());
        }

        CarminiteBlast.large(level, centre.getX() + 0.5D, centre.getY() + 0.5D, centre.getZ() + 0.5D);

        int lit = 0;
        int standing = 0;
        for (BlockPos offset : ring) {
            BlockState state = level.getBlockState(centre.offset(offset));
            if (!state.is(ModBlocks.EXPERIMENT_052.get())) {
                continue;
            }
            standing++;
            if (state.getValue(Experiment052Block.ACTIVE)) {
                lit++;
            }
        }
        helper.assertTrue(standing == ring.length,
            "no experiment_052 inside the blast may be destroyed by it, " + standing + " of " + ring.length
                + " still stand");
        helper.assertTrue(lit == 5,
            "the blast must chain only the five nearest blocks, chained " + lit + " of " + standing + " standing");

        helper.runAfterDelay(30, () -> {
            for (BlockPos offset : ring) {
                helper.assertFalse(level.getBlockState(centre.offset(offset)).is(ModBlocks.EXPERIMENT_052.get()),
                    "the chain must eventually consume the block at " + offset);
            }
            helper.succeed();
        });
    }

    /**
     * 砷铅铁火焰：不是原版那条会蔓延的火，接触掉 2 点血，且不把人点着
     *
     * <p>直接调 {@code BlockState#entityInside}：这是原版「实体待在方块里」那一刻的入口
     */
    @GameTest(template = "ddt_empty")
    public static void fireHurtsButDoesNotIgniteOrSpread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CENTRE);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, ModBlocks.CARMINITE_FIRE.get().defaultBlockState());

        // 变量声明成 Block：直接拿 CarminiteFireBlock 去 instanceof FireBlock，两个类已知无关，
        // javac 会当成编译错误而不是「恒为 false」
        Block fireBlock = ModBlocks.CARMINITE_FIRE.get();
        helper.assertFalse(fireBlock instanceof FireBlock,
            "carminite fire must not extend the spreading FireBlock");
        BlockState fire = level.getBlockState(pos);
        helper.assertTrue(fire.is(ModBlocks.CARMINITE_FIRE.get()),
            "the fire must be placeable on a sturdy face");
        helper.assertTrue(fire.getValue(CarminiteFireBlock.UP),
            "a block above the fire must switch the up plane on, got " + fire.getValue(CarminiteFireBlock.UP));

        Pig pig = helper.spawn(EntityType.PIG, 5, 2, 4);
        // 猪没被点着时 remainingFireTicks 是负数（-getFireImmuneTicks()，猪是 -1），不是 0
        int fireTicksBefore = pig.getRemainingFireTicks();
        fire.entityInside(level, pos, pig);
        helper.assertTrue(Math.abs(pig.getHealth() - (PIG_HEALTH - 2.0F)) < 1.0E-4F,
            "carminite fire must deal exactly 2 damage on contact, the pig has " + pig.getHealth());
        helper.assertTrue(pig.getRemainingFireTicks() == fireTicksBefore,
            "carminite fire must never set an entity on fire, fire ticks went from " + fireTicksBefore
                + " to " + pig.getRemainingFireTicks());
        helper.succeed();
    }

    /**
     * 只靠自己熄灭：与压在石头上的原版火焰一样，{@code AGE} 超过 3 就没了
     *
     * <p>{@code BlockState#tick} 就是计划刻到了以后原版走的入口，一拍一拍推过去不靠等十来秒
     */
    @GameTest(template = "ddt_empty")
    public static void fireBurnsOutByItself(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CENTRE);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, ModBlocks.CARMINITE_FIRE.get().defaultBlockState());

        int ticks = 0;
        for (; ticks <= 30; ticks++) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModBlocks.CARMINITE_FIRE.get())) {
                break;
            }
            state.tick(level, pos, level.getRandom());
        }
        helper.assertFalse(level.getBlockState(pos).is(ModBlocks.CARMINITE_FIRE.get()),
            "carminite fire must burn out on its own within a handful of ageing ticks, still there after " + ticks);
        helper.succeed();
    }

    /**
     * 碎片打生物：1~10 点随机、伤害类型是原版爆炸、不点着火
     *
     * <p>猪摆在碎片正前方，手动推 tick 让弹体一路飞过去。走廊先清成空气：GameTest 会给结构
     * 一圈 {@code minecraft:barrier} 围边（{@code StructureUtils#encaseStructure}），
     * 别的测试单元留下的围边有可能正好落在这一格里，碎片会在第一 tick 撞上它
     */
    @GameTest(template = "ddt_empty")
    public static void shrapnelDealsExplosionDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int x = 0; x <= 7; x++) {
            for (int dy = 0; dy <= 2; dy++) {
                level.removeBlock(origin.offset(x, 2 + dy, 2), false);
            }
        }
        Pig pig = helper.spawn(EntityType.PIG, 5, 2, 2);

        CarminiteShrapnelEntity shrapnel = new CarminiteShrapnelEntity(ModEntities.CARMINITE_SHRAPNEL.get(), level);
        shrapnel.setPos(pig.getX() - 3.0D, pig.getY() + 0.5D, pig.getZ());
        shrapnel.shoot(1.0D, 0.0D, 0.0D, 0.9F, 0.0F);
        level.addFreshEntity(shrapnel);

        for (int tick = 0; tick < 40 && pig.getHealth() == PIG_HEALTH && !shrapnel.isRemoved(); tick++) {
            shrapnel.tick();
        }

        float damage = PIG_HEALTH - pig.getHealth();
        helper.assertTrue(damage > 0.0F,
            "the shrapnel must hurt the pig it flies into, the pig still has " + pig.getHealth()
                + " (shrapnel at " + shrapnel.position() + ", removed " + shrapnel.isRemoved()
                + ", pig box " + pig.getBoundingBox() + ")");
        helper.assertTrue(damage <= 10.0F,
            "the shrapnel may deal at most 10 damage, it dealt " + damage);
        DamageSource source = pig.getLastDamageSource();
        helper.assertTrue(source != null && source.is(DamageTypes.EXPLOSION),
            "the shrapnel must deal explosion damage, got " + source);
        helper.assertTrue(pig.getRemainingFireTicks() <= 0,
            "the shrapnel must not set the pig on fire, got " + pig.getRemainingFireTicks() + " fire ticks");
        helper.succeed();
    }

    /**
     * 碎片落地：砸在实心顶面上就消失，并在附近地面补几团砷铅铁火焰
     *
     * <p>竖直朝下打一块石头；补火带随机偏移，所以按一小片范围找，而不是钉死一格。下落的两格先清空，
     * 理由与打猪那条一样：别的测试单元留下的 barrier 围边会挡在中间
     */
    @GameTest(template = "ddt_empty")
    public static void shrapnelLeavesFireWhereItLands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(4, 1, 12));
        level.removeBlock(floor.above(2), false);
        level.removeBlock(floor.above(), false);
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());

        CarminiteShrapnelEntity shrapnel = new CarminiteShrapnelEntity(ModEntities.CARMINITE_SHRAPNEL.get(), level);
        shrapnel.setPos(floor.getX() + 0.5D, floor.getY() + 2.5D, floor.getZ() + 0.5D);
        shrapnel.shoot(0.0D, -1.0D, 0.0D, 0.6F, 0.0F);
        level.addFreshEntity(shrapnel);

        for (int tick = 0; tick < 60 && !shrapnel.isRemoved(); tick++) {
            shrapnel.tick();
        }

        helper.assertTrue(shrapnel.isRemoved(), "the shrapnel must disappear once it lands");
        List<BlockPos> fires = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos candidate = floor.offset(dx, dy, dz);
                    if (level.getBlockState(candidate).is(ModBlocks.CARMINITE_FIRE.get())) {
                        fires.add(candidate);
                    }
                }
            }
        }
        helper.assertFalse(fires.isEmpty(),
            "a landed shrapnel must leave carminite fire behind, none in the 5x5 columns around " + floor);
        helper.succeed();
    }

    /**
     * 资源那一套：方块状态、两张方块模型、物品模型、火焰那十张模型与音效表
     *
     * <p>assets 下的模型与音效表服务端不加载，构建也不会因为写错路径而失败，所以这里直接读
     * classpath 上的文件本身：模型名与贴图名对不上、{@code render_type} 漏写、音效事件漏写，
     * 都只有进游戏才看得出来
     */
    @GameTest(template = "ddt_empty")
    public static void assetsMatchTheBlockAndItsStates(GameTestHelper helper) {
        JsonObject states = readJson(helper, "/assets/dawnduskterminal/blockstates/experiment_052.json");
        JsonObject variants = states.getAsJsonObject("variants");
        helper.assertTrue(variants.getAsJsonObject("active=false").get("model").getAsString()
                .equals("dawnduskterminal:block/experiment_052"),
            "the idle variant must point at block/experiment_052, got " + variants.getAsJsonObject("active=false"));
        helper.assertTrue(variants.getAsJsonObject("active=true").get("model").getAsString()
                .equals("dawnduskterminal:block/experiment_052_glow"),
            "the armed variant must point at block/experiment_052_glow, got "
                + variants.getAsJsonObject("active=true"));

        assertCubeAll(helper, "block/experiment_052", "dawnduskterminal:block/experiment_052");
        assertCubeAll(helper, "block/experiment_052_glow", "dawnduskterminal:block/experiment_052_glow");
        JsonObject item = readJson(helper, "/assets/dawnduskterminal/models/item/experiment_052.json");
        helper.assertTrue(item.get("parent").getAsString().equals("dawnduskterminal:block/experiment_052"),
            "the item model must point at the block model, got " + item.get("parent"));

        // 火焰：方块状态与 minecraft:fire 同构，六个 multipart 段，十个模型都要在里面
        JsonObject fireStates = readJson(helper, "/assets/dawnduskterminal/blockstates/carminite_fire.json");
        JsonArray multipart = fireStates.getAsJsonArray("multipart");
        helper.assertTrue(multipart.size() == 6,
            "carminite fire must mirror the six vanilla fire multipart parts, got " + multipart.size());
        List<String> used = new ArrayList<>();
        for (int part = 0; part < multipart.size(); part++) {
            JsonArray apply = multipart.get(part).getAsJsonObject().getAsJsonArray("apply");
            for (int i = 0; i < apply.size(); i++) {
                used.add(apply.get(i).getAsJsonObject().get("model").getAsString()
                    .replace("dawnduskterminal:block/", ""));
            }
        }
        for (String model : FIRE_MODELS) {
            helper.assertTrue(used.contains(model),
                "blockstates/carminite_fire.json must use " + model + ", it uses " + used);
        }
        for (String model : FIRE_MODELS) {
            JsonObject json = readJson(helper, "/assets/dawnduskterminal/models/block/" + model + ".json");
            String parent = json.get("parent").getAsString();
            helper.assertTrue(parent.startsWith("minecraft:block/template_fire_"),
                model + " must reuse a vanilla fire template, got " + parent);
            helper.assertTrue(json.has("render_type")
                    && json.get("render_type").getAsString().equals("minecraft:cutout"),
                model + " must render as cutout, otherwise its transparent pixels turn black");
            String texture = json.getAsJsonObject("textures").get("fire").getAsString();
            helper.assertTrue(texture.startsWith("dawnduskterminal:block/carminite_fire_"),
                model + " must use a carminite fire texture, got " + texture);
        }

        JsonObject sounds = readJson(helper, "/assets/dawnduskterminal/sounds.json");
        helper.assertTrue(sounds.has("experiment_052_ignite"),
            "sounds.json must declare experiment_052_ignite, otherwise the sound event has nothing to play");
        helper.assertTrue(sounds.has("experiment_052_explode"),
            "sounds.json must declare experiment_052_explode, otherwise the blast is silent");
        helper.succeed();
    }

    /** 读一个资源（路径以 / 开头），失败时直接把断言打红 */
    private static JsonObject readJson(GameTestHelper helper, String resource) {
        try (InputStream stream = Experiment052Tests.class.getResourceAsStream(resource)) {
            helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (IOException e) {
            helper.assertTrue(false, "failed to read " + resource + ": " + e);
            return new JsonObject();
        }
    }

    private static void assertCubeAll(GameTestHelper helper, String modelPath, String texture) {
        JsonObject model = readJson(helper, "/assets/dawnduskterminal/models/" + modelPath + ".json");
        helper.assertTrue(model.get("parent").getAsString().equals("minecraft:block/cube_all"),
            modelPath + " must stay a cube_all model, got " + model.get("parent"));
        helper.assertTrue(model.getAsJsonObject("textures").get("all").getAsString().equals(texture),
            modelPath + " must use " + texture + ", got " + model.getAsJsonObject("textures"));
    }

    /**
     * 爆炸粒子的贴图表必须与反应场那一份是同一批图：新开的只是帧序，不是新贴图
     */
    @GameTest(template = "ddt_empty")
    public static void blastParticlesShareTheEffectSprites(GameTestHelper helper) {
        JsonArray effect = readJson(helper,
            "/assets/dawnduskterminal/particles/particle_carminite_effect.json").getAsJsonArray("textures");
        JsonArray blast = readJson(helper,
            "/assets/dawnduskterminal/particles/particle_carminite_blast.json").getAsJsonArray("textures");
        helper.assertTrue(effect.size() == blast.size() && effect.size() == 8,
            "both particle sheets must carry the same eight sprites, got " + effect.size() + " and " + blast.size());
        for (int i = 0; i < effect.size(); i++) {
            helper.assertTrue(effect.get(i).getAsString().equals(blast.get(i).getAsString()),
                "blast sprite #" + i + " must reuse the effect sprite, got " + blast.get(i)
                    + " against " + effect.get(i));
        }
        helper.succeed();
    }

    /**
     * 余烬粒子的贴图表要的就是原版红石粒子那组 {@code minecraft:generic_0..7}
     */
    @GameTest(template = "ddt_empty")
    public static void emberParticleUsesTheVanillaDustSprites(GameTestHelper helper) {
        JsonArray ember = readJson(helper,
            "/assets/dawnduskterminal/particles/particle_carminite_ember.json").getAsJsonArray("textures");
        helper.assertTrue(ember.size() == 8,
            "the ember sheet must carry the eight vanilla dust sprites, got " + ember.size());
        for (int i = 0; i < ember.size(); i++) {
            String sprite = ember.get(i).getAsString();
            helper.assertTrue(sprite.startsWith("minecraft:generic_"),
                "ember sprite #" + i + " must be a vanilla dust sprite, got " + sprite);
        }
        helper.succeed();
    }

    private Experiment052Tests() {}
}
