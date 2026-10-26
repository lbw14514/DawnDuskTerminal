package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.registry.ModBlockEntities;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.world.CubeField;
import com.dawnduskterminal.world.CubeFieldMarker;
import com.dawnduskterminal.world.Experiment069Field;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 069 的规格：占位号让出、物品是那个方块的 {@link BlockItem}、方块没有状态且常亮 15 级光、
 * 场是 17×17×17，<b>场里的方块更新一律不处理</b>——邻居更新、形状更新、计划刻、随机刻、流体刻五条
 * 各钉一条，每条都配一个场外的对照：同样的摆法放在场外必须照常发生，否则「压住了」和「这游戏本来就这样」
 * 分不开
 *
 * <p>场心那一格摆在 (4,3,4)，场是 x/z 各 -4..12（结构只有 16 宽），所以对照都摆在相对 x = 10 或 11：
 * |dx| = 10 > 8，确定在场外
 *
 * <p><b>每条测试各占一个 batch。</b>场是 17 格、结构只有 16 格，所以一台机器压的场必然伸到自己的结构外面去；
 * GameTest 同一个 batch 里的测试是<b>并行</b>跑的（阵位之间只隔 5 格），两个 batch 之间才是串行。
 * 全塞进 defaultBatch 的话，隔壁那条测试摆的机器会给这一条的对照方块压更新，反过来也一样——之前
 * 052 的连锁测试与这里的落沙对照就是这么被压红的。一条测试一个 batch，跑的时候就它自己；
 * 每条结束时再把机器挖掉（{@link #discardMachine}），别把场留给后面的 batch 与下一轮跑
 *
 * <p>随机刻那一条不等随机刻自然轮到（几率为零）：直接调 {@code BlockState#randomTick}，要的是入场口本身；
 * 冰的两侧各放一块萤石给光，好让「会不会化」只取决于刻有没有被执行
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment069Tests {
    private static final String SUBJECT = "dawnduskterminal:experiment_069";

    /** 场心那一格 */
    private static final BlockPos CENTRE = new BlockPos(4, 3, 4);

    /** 相对中心 x 推 10 格：8 格外，确定在场外 */
    private static final int OUTSIDE_X = 10;

    /** 随机刻那条最多等多少拍让光引擎把萤石的光算出来 */
    private static final int MAX_LIGHT_POLL_TICKS = 40;

    @GameTest(template = "ddt_empty", batch = "ddt069_registration")
    public static void blockReplacesThePlaceholder(GameTestHelper helper) {
        helper.assertFalse(ModExperimentItems.placeholders().containsKey(69),
            "number 69 must not be a placeholder anymore, it has a block now");
        Item item = ModItems.EXPERIMENT_069.get();
        helper.assertTrue(item instanceof BlockItem blockItem && blockItem.getBlock() == ModBlocks.EXPERIMENT_069.get(),
            SUBJECT + " must be the item form of its own block, got " + item);
        // 方块物品的名字取自方块，所以语言文件的键是 block.dawnduskterminal.experiment_069
        helper.assertTrue(item.getDescriptionId().equals("block.dawnduskterminal.experiment_069"),
            "a block item must take its name from the block, got " + item.getDescriptionId());

        BlockState state = ModBlocks.EXPERIMENT_069.get().defaultBlockState();
        helper.assertTrue(state.getProperties().isEmpty(),
            SUBJECT + " must carry no state, it works as soon as it is placed, got " + state.getProperties());
        helper.assertTrue(state.getLightEmission(helper.getLevel(), helper.absolutePos(CENTRE)) == 15,
            SUBJECT + " must emit 15 light levels, it is its own light source and carries no glow layer, got "
                + state.getLightEmission(helper.getLevel(), helper.absolutePos(CENTRE)));
        helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE),
            SUBJECT + " must stay in #minecraft:mineable/pickaxe, otherwise a pickaxe cannot drop it");
        helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(DawnDuskTerminal.id("experiment_069")),
            "dawnduskterminal:experiment_069 must be registered as a block entity type, the field needs one");
        // 场的大小就写在这里：17×17×17，中心那一格往每个方向各 8 格
        helper.assertTrue(CubeField.SIZE == 17 && CubeField.RADIUS == 8,
            "the field must stay 17x17x17, got " + CubeField.SIZE + " (radius " + CubeField.RADIUS + ")");
        helper.succeed();
    }

    /**
     * 邻居更新：场里的红石灯通了电也收不到更新，场外那盏照常亮
     *
     * <p>红石灯点亮是 {@code neighborChanged} 里当场做的（{@code setBlock(LIT, 2)}），
     * 所以「灯亮没亮」就是「这一条邻居更新有没有送到」最干净的读数
     */
    @GameTest(template = "ddt_empty", batch = "ddt069_neighbor")
    public static void neighborUpdatesAreNotDelivered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeMachine(level, helper.absolutePos(CENTRE));

        BlockPos lampInside = helper.absolutePos(CENTRE.offset(2, 0, 0));
        BlockPos lampOutside = helper.absolutePos(CENTRE.offset(OUTSIDE_X, 0, 0));
        level.setBlockAndUpdate(lampInside, Blocks.REDSTONE_LAMP.defaultBlockState());
        level.setBlockAndUpdate(lampOutside, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.assertFalse(level.getBlockState(lampInside).getValue(RedstoneLampBlock.LIT),
            "a freshly placed redstone lamp starts unlit");

        // 红石块贴上灯的一侧：这一步会广播邻居更新
        level.setBlockAndUpdate(lampInside.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(lampOutside.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.assertFalse(level.getBlockState(lampInside).getValue(RedstoneLampBlock.LIT),
            "a lamp inside the field must not receive the neighbor update, it has power next to it but must stay unlit");
        helper.assertTrue(level.getBlockState(lampOutside).getValue(RedstoneLampBlock.LIT),
            "a lamp outside the field must light up like vanilla, otherwise the check above proves nothing");
        discardMachine(level, helper.absolutePos(CENTRE));
        helper.succeed();
    }

    /**
     * 形状更新：场里的栅栏不会因为旁边多了方块而连上去，场外那根照常连
     *
     * <p>形状更新是「邻居换了，我该不该改外形」那一支，栅栏的 SOUTH 位就是它的读数
     */
    @GameTest(template = "ddt_empty", batch = "ddt069_shape")
    public static void shapeUpdatesAreNotDelivered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeMachine(level, helper.absolutePos(CENTRE));

        BlockPos fenceInside = helper.absolutePos(CENTRE.offset(2, 0, 2));
        BlockPos fenceOutside = helper.absolutePos(CENTRE.offset(OUTSIDE_X, 0, 2));
        level.setBlockAndUpdate(fenceInside, Blocks.OAK_FENCE.defaultBlockState());
        level.setBlockAndUpdate(fenceOutside, Blocks.OAK_FENCE.defaultBlockState());
        helper.assertFalse(level.getBlockState(fenceInside).getValue(FenceBlock.SOUTH),
            "the fence is placed before anything stands next to it, it must not connect yet");
        helper.assertFalse(level.getBlockState(fenceOutside).getValue(FenceBlock.SOUTH),
            "the control fence is placed before anything stands next to it, it must not connect yet");

        // 各放一块石头在南边：这一步会给栅栏排一条形状更新
        level.setBlockAndUpdate(fenceInside.south(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(fenceOutside.south(), Blocks.STONE.defaultBlockState());

        helper.assertFalse(level.getBlockState(fenceInside).getValue(FenceBlock.SOUTH),
            "the fence inside the field must not reshape, its shape update was suppressed");
        helper.assertTrue(level.getBlockState(fenceOutside).getValue(FenceBlock.SOUTH),
            "the fence outside the field must connect to the stone like vanilla, otherwise the check above proves nothing");
        discardMachine(level, helper.absolutePos(CENTRE));
        helper.succeed();
    }

    /**
     * 计划刻：场里的沙子排了落沙刻也不掉，场外那块照常掉
     *
     * <p>沙子摆下自己会排一个 {@code getDelayAfterPlace()} tick 的刻，落不落就是「计划刻有没有被执行」
     */
    @GameTest(template = "ddt_empty", batch = "ddt069_scheduled")
    public static void scheduledTicksAreNotExecuted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeMachine(level, helper.absolutePos(CENTRE));

        BlockPos sandInside = helper.absolutePos(CENTRE.offset(0, 0, 2));
        BlockPos sandOutside = helper.absolutePos(CENTRE.offset(OUTSIDE_X, 0, 2));
        level.setBlockAndUpdate(sandInside, Blocks.SAND.defaultBlockState());
        level.setBlockAndUpdate(sandOutside, Blocks.SAND.defaultBlockState());
        helper.assertTrue(level.getBlockTicks().hasScheduledTick(sandInside, Blocks.SAND),
            "placing sand must schedule its own falling tick, otherwise this test proves nothing");
        helper.assertTrue(level.getBlockTicks().hasScheduledTick(sandOutside, Blocks.SAND),
            "placing sand outside the field must schedule its falling tick too");

        helper.runAfterDelay(8, () -> {
            helper.assertTrue(level.getBlockState(sandInside).is(Blocks.SAND),
                "sand inside the field must not fall, its scheduled tick was suppressed");
            helper.assertTrue(level.getBlockState(sandOutside).isAir(),
                "sand outside the field must fall like vanilla, otherwise the check above proves nothing");
            discardMachine(level, helper.absolutePos(CENTRE));
            helper.succeed();
        });
    }

    /**
     * 随机刻：场里的冰不化，场外那块照常化
     *
     * <p>两块冰旁边各摆一块萤石给足方块光，再直接调 {@code BlockState#randomTick}：要钉的是「刻有没有被执行」，
     * 不等随机刻自然轮到（那几率在测试里等于零）。萤石只发光、不靠方块更新，场内场外一样亮
     *
     * <p>等的是「光引擎把光算出来」而不是固定几拍：光按 tick 结算，服务端忙的时候两三拍也未必算完，
     * 写死等待会让这条测试时不时红在对照那半边。{@link #whenIceIsLit} 逐拍问到两块冰的方块光都过阈值为止
     */
    @GameTest(template = "ddt_empty", batch = "ddt069_random")
    public static void randomTicksAreNotExecuted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeMachine(level, helper.absolutePos(CENTRE));

        BlockPos iceInside = helper.absolutePos(CENTRE.offset(-2, 0, 2));
        BlockPos iceOutside = helper.absolutePos(CENTRE.offset(OUTSIDE_X, 0, 2));
        level.setBlockAndUpdate(iceInside.east(), Blocks.GLOWSTONE.defaultBlockState());
        level.setBlockAndUpdate(iceOutside.east(), Blocks.GLOWSTONE.defaultBlockState());
        level.setBlockAndUpdate(iceInside, Blocks.ICE.defaultBlockState());
        level.setBlockAndUpdate(iceOutside, Blocks.ICE.defaultBlockState());
        whenIceIsLit(helper, iceInside, iceOutside, 0);
    }

    /** 等到两块冰的方块光都足够化冰（最多等 {@value #MAX_LIGHT_POLL_TICKS} 拍），再把两种情形都试一遍 */
    private static void whenIceIsLit(GameTestHelper helper, BlockPos iceInside, BlockPos iceOutside, int attempt) {
        ServerLevel level = helper.getLevel();
        BlockState ice = level.getBlockState(iceOutside);
        int threshold = 11 - ice.getLightBlock(level, iceOutside);
        boolean lit = level.getBrightness(LightLayer.BLOCK, iceInside) > threshold
            && level.getBrightness(LightLayer.BLOCK, iceOutside) > threshold;
        if (!lit && attempt < MAX_LIGHT_POLL_TICKS) {
            helper.runAfterDelay(1, () -> whenIceIsLit(helper, iceInside, iceOutside, attempt + 1));
            return;
        }
        helper.assertTrue(lit,
            "the light engine never lit both ice blocks (threshold " + threshold + ", inside "
                + level.getBrightness(LightLayer.BLOCK, iceInside) + ", outside "
                + level.getBrightness(LightLayer.BLOCK, iceOutside) + ")");

        level.getBlockState(iceInside).randomTick(level, iceInside, level.getRandom());
        helper.assertTrue(level.getBlockState(iceInside).is(Blocks.ICE),
            "ice inside the field must not melt, its random tick was suppressed");

        level.getBlockState(iceOutside).randomTick(level, iceOutside, level.getRandom());
        helper.assertTrue(level.getBlockState(iceOutside).is(Blocks.WATER),
            "ice outside the field must melt under the same light, otherwise the check above proves nothing");
        discardMachine(level, helper.absolutePos(CENTRE));
        helper.succeed();
    }

    /**
     * 流体刻：场里的水不往下流，场外那桶照常流
     *
     * <p>水与岩浆的流动走的是另一条队列（{@code ServerLevel#tickFluid} → {@code FluidState#tick}），
     * 不在方块刻那条路上，所以单独钉一条
     */
    @GameTest(template = "ddt_empty", batch = "ddt069_fluid")
    public static void fluidTicksAreNotExecuted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        placeMachine(level, helper.absolutePos(CENTRE));

        BlockPos waterInside = helper.absolutePos(CENTRE.offset(2, 0, -2));
        BlockPos waterOutside = helper.absolutePos(CENTRE.offset(OUTSIDE_X, 0, -2));
        level.setBlockAndUpdate(waterInside, Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(waterOutside, Blocks.WATER.defaultBlockState());
        level.removeBlock(waterInside.below(), false);
        level.removeBlock(waterOutside.below(), false);

        helper.assertTrue(Experiment069Field.suppressed(level, waterInside),
            "the machine must already cover its own field in the same tick it was placed");
        helper.assertFalse(Experiment069Field.suppressed(level, waterOutside),
            "the control water must sit outside every field");

        level.getFluidState(waterInside).tick(level, waterInside);
        level.getFluidState(waterOutside).tick(level, waterOutside);

        helper.assertTrue(level.getFluidState(waterInside.below()).isEmpty(),
            "water inside the field must not flow down, its fluid tick was suppressed");
        helper.assertTrue(!level.getFluidState(waterOutside.below()).isEmpty(),
            "water outside the field must flow down like vanilla, otherwise the check above proves nothing");
        discardMachine(level, helper.absolutePos(CENTRE));
        helper.succeed();
    }

    /**
     * 挖掉就停：场在的时候那盏灯怎么给电都不亮，机器一没、再给一次更新就亮了
     */
    @GameTest(template = "ddt_empty", batch = "ddt069_teardown")
    public static void breakingTheBlockTurnsTheFieldOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(CENTRE);
        placeMachine(level, centre);

        BlockPos lamp = helper.absolutePos(CENTRE.offset(2, 0, 0));
        level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        level.setBlockAndUpdate(lamp.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.assertFalse(level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT),
            "the field must already work in the same tick the block was placed");

        level.setBlockAndUpdate(centre, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(lamp.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.assertTrue(level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT),
            "breaking the block must turn the field off, the next neighbor update has to land again");
        helper.succeed();
    }

    /**
     * 资源那一套：方块状态一个变体、单层 {@code cube_all} 模型（方块自己发光，不再叠发光层）、
     * 物品模型指向方块模型、战利品表掉自己，本体贴图在 jar 里
     *
     * <p>assets 下的模型服务端不加载，构建也不会因为路径写错而失败，所以这里直接读 classpath 上的文件本身
     */
    @GameTest(template = "ddt_empty", batch = "ddt069_assets")
    public static void assetsMatchTheBlock(GameTestHelper helper) {
        JsonObject states = readJson(helper, "/assets/dawnduskterminal/blockstates/experiment_069.json");
        JsonObject variants = states.getAsJsonObject("variants");
        helper.assertTrue(variants.size() == 1,
            SUBJECT + " has no state, its blockstates file must carry exactly one variant, got " + variants.size());
        helper.assertTrue(variants.getAsJsonObject("").get("model").getAsString()
                .equals("dawnduskterminal:block/experiment_069"),
            "the only variant must point at block/experiment_069, got " + variants.getAsJsonObject(""));

        JsonObject model = readJson(helper, "/assets/dawnduskterminal/models/block/experiment_069.json");
        helper.assertTrue(model.get("parent").getAsString().equals("minecraft:block/cube_all"),
            "block/experiment_069 must stay a plain cube_all model, got " + model.get("parent"));
        JsonObject textures = model.getAsJsonObject("textures");
        helper.assertTrue(textures.entrySet().size() == 1
                && textures.get("all").getAsString().equals("dawnduskterminal:block/experiment_069"),
            "the block is a light source, one texture is enough, got " + textures);
        helper.assertFalse(model.has("elements"),
            "one texture needs no hand written elements, the cube comes from cube_all, got " + model.get("elements"));

        JsonObject item = readJson(helper, "/assets/dawnduskterminal/models/item/experiment_069.json");
        helper.assertTrue(item.get("parent").getAsString().equals("dawnduskterminal:block/experiment_069"),
            "the item model must point at the block model, got " + item.get("parent"));

        JsonObject loot = readJson(helper, "/data/dawnduskterminal/loot_table/blocks/experiment_069.json");
        helper.assertTrue(loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")
                .get(0).getAsJsonObject().get("name").getAsString().equals(SUBJECT),
            "the block must drop itself, got " + loot.getAsJsonArray("pools").get(0));

        for (String texture : new String[] {"experiment_069"}) {
            String path = "/assets/dawnduskterminal/textures/block/" + texture + ".png";
            try (InputStream stream = Experiment069Tests.class.getResourceAsStream(path)) {
                helper.assertTrue(stream != null, path + " must ship inside the mod resources");
            } catch (IOException e) {
                helper.assertTrue(false, "failed to read " + path + ": " + e);
            }
        }
        helper.succeed();
    }

    /**
     * 场的范围由反建器那一套框线粒子画出来：框住的必须正好是那 17 格方块
     *
     * <p>框线的十二条棱逐条核：两个端点都落在场最外层方块的外侧面上（中心外各 8.5 格 = 那一格往外的
     * {@code -8} 与 {@code +9}），每条棱只差一个轴（都是棱，不是斜线），三条轴各四条。
     * 粒子自身是客户端每 tick 一颗粉尘、每十次挑一次连画三组框线，且只在 16 格内有活着的玩家时画；
     * 服务端那颗 ticker 必须是空的
     */
    @GameTest(template = "ddt_empty", batch = "ddt069_frame")
    public static void theFieldFrameIsDrawnOnTheClientOnly(GameTestHelper helper) {
        helper.assertTrue(CubeFieldMarker.halfExtent() == CubeField.RADIUS + 0.5D,
            "the frame must sit on the outer face of the outermost field blocks, got " + CubeFieldMarker.halfExtent());
        helper.assertTrue(CubeFieldMarker.outlineCount() == 12,
            "the anti-builder traces twelve outlines, got " + CubeFieldMarker.outlineCount());

        BlockPos centre = helper.absolutePos(CENTRE);
        double low = -CubeField.RADIUS;
        double high = CubeField.RADIUS + 1;
        int top = 0;
        int bottom = 0;
        int vertical = 0;
        for (int outline = 0; outline < CubeFieldMarker.outlineCount(); outline++) {
            double[] edge = CubeFieldMarker.edge(centre, outline);
            helper.assertTrue(edge.length == 6, "outline " + outline + " must be one edge, got " + edge.length);
            for (int end = 0; end < 2; end++) {
                double x = edge[end * 3] - centre.getX();
                double y = edge[end * 3 + 1] - centre.getY();
                double z = edge[end * 3 + 2] - centre.getZ();
                for (double axis : new double[] {x, y, z}) {
                    helper.assertTrue(axis == low || axis == high,
                        "every end of every edge must sit on the field boundary (" + low + " or " + high
                            + "), outline " + outline + " has " + axis);
                }
            }
            int differing = 0;
            for (int axis = 0; axis < 3; axis++) {
                if (edge[axis] != edge[axis + 3]) {
                    differing++;
                }
            }
            helper.assertTrue(differing == 1, "outline " + outline + " must be an edge, it varies along "
                + differing + " axes");
            if (edge[4] != edge[1]) {
                vertical++;
            } else if (edge[1] - centre.getY() == high) {
                top++;
            } else {
                bottom++;
            }
        }
        helper.assertTrue(top == 4 && bottom == 4 && vertical == 4,
            "twelve outlines split into four on top, four on the bottom and four vertical, got "
                + top + " / " + bottom + " / " + vertical);

        BlockState state = ModBlocks.EXPERIMENT_069.get().defaultBlockState();
        helper.assertTrue(ModBlocks.EXPERIMENT_069.get().getTicker(helper.getLevel(), state,
                ModBlockEntities.EXPERIMENT_069.get()) == null,
            "the field frame is drawn on the client only, the server must get no ticker at all");
        helper.succeed();
    }

    /** 摆一台 069，场心是 {@code pos} */
    private static void placeMachine(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, ModBlocks.EXPERIMENT_069.get().defaultBlockState());
    }

    /** 挖掉那一台：场是全局的，留着会压到后面 batch 与下一轮跑的测试 */
    private static void discardMachine(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    /** 读一个资源（路径以 / 开头），失败时直接把断言打红 */
    private static JsonObject readJson(GameTestHelper helper, String resource) {
        try (InputStream stream = Experiment069Tests.class.getResourceAsStream(resource)) {
            helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (IOException e) {
            helper.assertTrue(false, "failed to read " + resource + ": " + e);
            return new JsonObject();
        }
    }

    private Experiment069Tests() {}
}
