package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModBlockEntities;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.world.Experiment076Reaction;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import twilightforest.init.TFBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 076 的规格：占位号让出、物品是那个方块的 {@link BlockItem}、方块挂了一个空方块实体当渲染落点、
 * 方块自己与两边都不挂 ticker
 *
 * <p>外观那一半是纯客户端的（{@code client/Experiment076Renderer} 每 tick 给六个面各抽一张贴图），
 * 服务端测不到，所以这里钉的是它依赖的前提：方块得真的是 {@link EntityBlock} 且方块实体登记得上，
 * 否则渲染器没有落点，方块就永远只剩那层 netherrack 兜底
 *
 * <p>资源那一套照 {@link Experiment069Tests} 的老规矩核：blockstates 一个变体、方块模型是 cube_all、
 * 物品模型把四张候选贴图都摆出来、战利品表掉自己、镐子能更快挖
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment076Tests {
    private static final String SUBJECT = "dawnduskterminal:experiment_076";

    /** 摆块方块的位置 */
    private static final BlockPos SUBJECT_POS = new BlockPos(2, 2, 2);

    /** 反应那一套用的位置：模板只有 3 格宽，摆在正中间，塌陷半径 1 的壳才不会伸出模板 */
    private static final BlockPos REACTION_POS = new BlockPos(1, 8, 8);

    @GameTest(template = "ddt_empty")
    public static void blockReplacesThePlaceholder(GameTestHelper helper) {
        helper.assertFalse(ModExperimentItems.placeholders().containsKey(76),
            "number 76 must not be a placeholder anymore, it has a block now");
        Item item = ModItems.EXPERIMENT_076.get();
        helper.assertTrue(item instanceof BlockItem blockItem && blockItem.getBlock() == ModBlocks.EXPERIMENT_076.get(),
            SUBJECT + " must be the item form of its own block, got " + item);
        helper.succeed();
    }

    /**
     * 渲染落点：方块是 {@link EntityBlock}，摆下来就有一个自己的方块实体
     *
     * <p>没有它渲染器画不了任何一个面，方块会一直是那层兜底的 netherrack
     */
    @GameTest(template = "ddt_empty")
    public static void placingItCreatesItsBlockEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(SUBJECT_POS);
        helper.setBlock(SUBJECT_POS, ModBlocks.EXPERIMENT_076.get());

        BlockEntity blockEntity = level.getBlockEntity(pos);
        helper.assertTrue(blockEntity != null, SUBJECT + " must carry a block entity, otherwise nothing can draw it");
        helper.assertTrue(blockEntity.getType() == ModBlockEntities.EXPERIMENT_076.get(),
            "the block entity must be the one registered for " + SUBJECT + ", got " + blockEntity.getType());

        // 外观每 tick 现算，不靠 ticker；服务端这边更不能有东西要跑
        BlockState state = ModBlocks.EXPERIMENT_076.get().defaultBlockState();
        helper.assertTrue(ModBlocks.EXPERIMENT_076.get().getTicker(level, state,
                ModBlockEntities.EXPERIMENT_076.get()) == null,
            "the appearance is computed in the renderer, neither side needs a ticker");

        // 挖掉它一定会起一场反应，那是全局的：清场并掐掉，别让它跑到后面的测试里去
        helper.setBlock(SUBJECT_POS, Blocks.AIR);
        Experiment076Reaction.clear();
        helper.succeed();
    }

    /**
     * 资源那一套：方块状态一个变体、方块模型是 cube_all 且拿 netherrack 当兜底、物品模型走
     * {@code builtin/entity} 并自带方块那套姿势、战利品表掉自己、镐子挖得更快
     *
     * <p>assets 下的模型服务端不加载，构建也不会因为路径写错而失败，所以这里直接读 classpath 上的文件本身
     */
    @GameTest(template = "ddt_empty")
    public static void assetsMatchTheBlock(GameTestHelper helper) {
        JsonObject states = readJson(helper, "/assets/dawnduskterminal/blockstates/experiment_076.json");
        JsonObject variants = states.getAsJsonObject("variants");
        helper.assertTrue(variants.size() == 1,
            SUBJECT + " has no state, its blockstates file must carry exactly one variant, got " + variants.size());
        helper.assertTrue(variants.getAsJsonObject("").get("model").getAsString()
                .equals("dawnduskterminal:block/experiment_076"),
            "the only variant must point at block/experiment_076, got " + variants.getAsJsonObject(""));

        JsonObject model = readJson(helper, "/assets/dawnduskterminal/models/block/experiment_076.json");
        helper.assertTrue(model.get("parent").getAsString().equals("minecraft:block/cube_all"),
            "block/experiment_076 must stay a plain cube_all, it is the fallback layer, got " + model.get("parent"));
        helper.assertTrue(model.getAsJsonObject("textures").get("all").getAsString()
                .equals("minecraft:block/netherrack"),
            "the fallback layer must be one of the four candidates, got " + model.getAsJsonObject("textures"));

        JsonObject item = readJson(helper, "/assets/dawnduskterminal/models/item/experiment_076.json");
        helper.assertTrue(item.get("parent").getAsString().equals("minecraft:builtin/entity"),
            "the item must go through the custom renderer, a baked model cannot change its texture every tick, got "
                + item.get("parent"));
        helper.assertTrue(item.get("gui_light").getAsString().equals("side"),
            "an item that is a cube needs side lighting in the inventory, got " + item.get("gui_light"));
        JsonObject display = item.getAsJsonObject("display");
        for (String context : new String[] {"gui", "ground", "fixed", "thirdperson_righthand",
                "firstperson_righthand", "firstperson_lefthand"}) {
            helper.assertTrue(display.has(context),
                "builtin/entity models carry no transforms of their own, the block poses must be written out, missing "
                    + context);
        }

        JsonObject loot = readJson(helper, "/data/dawnduskterminal/loot_table/blocks/experiment_076.json");
        helper.assertTrue(loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")
                .get(0).getAsJsonObject().get("name").getAsString().equals(SUBJECT),
            "the block must drop itself, got " + loot.getAsJsonArray("pools").get(0));

        helper.assertTrue(ModBlocks.EXPERIMENT_076.get().defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
            SUBJECT + " must be in #minecraft:mineable/pickaxe");
        helper.succeed();
    }

    /**
     * 它是光源方块：15 级光，摆在哪里都亮
     *
     * <p>外观那一侧也一律按满亮画（{@code Experiment076Cube} 里六个面都不吃光图），两头对齐。
     * 这一条是钉死「不发光的 076 不是 076」的——之前那一版不是光源，在洞里/夜里整块就是一片黑
     */
    @GameTest(template = "ddt_empty")
    public static void itLightsTheRoom(GameTestHelper helper) {
        BlockState state = ModBlocks.EXPERIMENT_076.get().defaultBlockState();
        BlockPos pos = helper.absolutePos(SUBJECT_POS);
        ServerLevel level = helper.getLevel();
        // 0 参的那个 getLightEmission 被 NeoForge 标了废弃，用带 level/pos 的版本
        int emission = state.getLightEmission(level, pos);
        helper.assertTrue(emission == 15,
            SUBJECT + " must be a light source, so its own faces are lit wherever it stands, got " + emission);
        helper.succeed();
    }

    /**
     * 被<b>玩家挖掉</b>才跑那场砷铅铁反应堆的反应，而且<b>不摆假金块与假钻石块</b>
     *
     * <p>三件事各钉一条：
     * <ol>
     *   <li>换成别的方块（也就是被破坏而不是被挖）<b>不</b>起反应——原版只有 {@code playerWillDestroy}
     *       这一条路算「玩家挖」</li>
     *   <li>计数 5 那一拍反应堆会往 3x3x3 摆一圈假金块与假钻石块，我们这里一格都不该有</li>
     *   <li>推到计数 120，塌陷已经把暮色森林的反应堆碎片灌进壳里了，说明后面几段接上了</li>
     * </ol>
     *
     * <p>反应是全局的（{@code ServerTickEvent} 推进），跑完必须 {@code clear()} 并清场，
     * 否则它会在后面的测试里继续塌陷、最后炸一发
     */
    @GameTest(template = "ddt_empty")
    public static void miningItRunsTheReactorReactionWithoutFakeBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(REACTION_POS);

        // 一：不是挖掉的，不起反应
        helper.setBlock(REACTION_POS, ModBlocks.EXPERIMENT_076.get());
        helper.setBlock(REACTION_POS, Blocks.AIR);
        helper.assertTrue(Experiment076Reaction.activeCount() == 0,
            "only mining may start the reaction, replacing the block must not, got "
                + Experiment076Reaction.activeCount());

        // 二：玩家挖。服务端就是先调 playerWillDestroy，再把方块移除
        helper.setBlock(REACTION_POS, ModBlocks.EXPERIMENT_076.get());
        BlockState state = level.getBlockState(pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        state.getBlock().playerWillDestroy(level, pos, state, player);
        helper.setBlock(REACTION_POS, Blocks.AIR);
        helper.assertTrue(Experiment076Reaction.activeCount() == 1,
            "mining " + SUBJECT + " must start the reactor reaction, got " + Experiment076Reaction.activeCount());

        for (int tick = 0; tick < 5; tick++) {
            Experiment076Reaction.tickAll();
        }
        helper.assertTrue(count(level, pos, 1, TFBlocks.FAKE_GOLD.get(), TFBlocks.FAKE_DIAMOND.get()) == 0,
            "the fake gold and fake diamond blocks are the stage we skip, none may appear around the block");

        for (int tick = 5; tick < 120; tick++) {
            Experiment076Reaction.tickAll();
        }
        helper.assertTrue(count(level, pos, 1, TFBlocks.REACTOR_DEBRIS.get()) > 0,
            "after the collapse starts the shell must be filled with twilightforest:reactor_debris");

        Experiment076Reaction.clear();
        clearCube(level, pos, 2);
        helper.succeed();
    }

    /** 数一数以 {@code centre} 为心、半径 {@code radius} 的立方里，方块是 {@code blocks} 之一的几个 */
    private static int count(ServerLevel level, BlockPos centre, int radius, Block... blocks) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius),
                centre.offset(radius, radius, radius))) {
            Block state = level.getBlockState(pos).getBlock();
            for (Block candidate : blocks) {
                if (state == candidate) {
                    found++;
                    break;
                }
            }
        }
        return found;
    }

    /** 把一个立方清成空气，别把块留给后面的测试 */
    private static void clearCube(ServerLevel level, BlockPos centre, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius),
                centre.offset(radius, radius, radius))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** 读一个资源（路径以 / 开头），失败时直接把断言打红 */
    private static JsonObject readJson(GameTestHelper helper, String resource) {
        try (InputStream stream = Experiment076Tests.class.getResourceAsStream(resource)) {
            helper.assertTrue(stream != null, resource + " must ship inside the mod resources");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (IOException e) {
            helper.assertTrue(false, "failed to read " + resource + ": " + e);
            return new JsonObject();
        }
    }

    private Experiment076Tests() {}
}
