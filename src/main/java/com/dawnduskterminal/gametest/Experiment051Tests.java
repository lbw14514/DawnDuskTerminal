package com.dawnduskterminal.gametest;

import com.dawnduskterminal.block.Experiment051Block;
import com.dawnduskterminal.block.entity.Experiment051BlockEntity;
import com.dawnduskterminal.crafting.UncraftingPlan;
import com.dawnduskterminal.crafting.UncraftingResolver;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModExperimentItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import twilightforest.init.TFItems;
import twilightforest.init.TFBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 051 的规格
 *
 * <p>钉住的是「与拆解台同一份产物」和那五条差别：
 *
 * <ol>
 *   <li>方块属性照抄 {@code twilightforest:uncrafting_table}（2.5 硬度、木头声音、FIRE 地图色、进斧头标签）
 *   <li>产物与拆解台同源：反查合成配方取第一条，3x3 的位置也照铺；没有反查配方就退成熔炼
 *   <li>不收经验：本方块压根没有扣等级的代码，这里只钉进度与产量
 *   <li>时间是熔炉配方的常规时间：拆解与反查 200 tick（原版烧一个东西的时间），熔炼回退取熔炼配方自己写的时间
 *   <li>产物堵了就停：出料格满 64 或者装不下同一种物品时不再推进度。不可堆叠的产物走的是同一条判定
 *       （堆叠上限 1），所以这条判死就等于「不可堆叠产物堵塞」也判死
 * </ol>
 *
 * <p>漏斗那三条（顶进原料、侧进燃料、底抽产物）直接问 {@code WorldlyContainer} 的接口，
 * 不用真摆三个漏斗
 *
 * <p>机器刻不靠等方块实体自然刻，直接调 {@link Experiment051BlockEntity#serverTick}，一百来 tick 的进度在
 * 一次测试里跑完，不依赖 GameTest 的等待时机
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment051Tests {
    private static final String SUBJECT = "dawnduskterminal:experiment_051";

    @GameTest(template = "ddt_empty")
    @SuppressWarnings("deprecation") // 爆炸抗性只有这个没带 Explosion 的取值入口，NeoForge 把它标了废弃
    public static void propertiesCopyUncraftingTable(GameTestHelper helper) {
        Block ours = ModBlocks.EXPERIMENT_051.get();
        helper.assertTrue(ours.defaultDestroyTime() == 2.5F,
            SUBJECT + " destroy time must be the uncrafting table's 2.5, got " + ours.defaultDestroyTime());
        helper.assertTrue(ours.getExplosionResistance() == 2.5F,
            SUBJECT + " explosion resistance must be 2.5, got " + ours.getExplosionResistance());
        helper.assertTrue(ours.defaultMapColor() == MapColor.FIRE,
            SUBJECT + " map color must be FIRE, got " + ours.defaultMapColor());
        helper.assertTrue(ours.defaultBlockState().getSoundType() == SoundType.WOOD,
            SUBJECT + " must sound like wood, got " + ours.defaultBlockState().getSoundType());
        helper.assertTrue(ours.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE),
            SUBJECT + " must stay in #minecraft:mineable/axe, exactly like the uncrafting table");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void onlyActiveIsAState(GameTestHelper helper) {
        BlockState state = ModBlocks.EXPERIMENT_051.get().defaultBlockState();
        helper.assertFalse(state.getValue(Experiment051Block.ACTIVE),
            "a placed " + SUBJECT + " must start inactive");
        helper.assertTrue(Experiment051Block.ACTIVE.getName().equals("active"),
            "the lit property must be named active to match the _active textures, got "
                + Experiment051Block.ACTIVE.getName());
        helper.assertTrue(state.getProperties().size() == 1,
            SUBJECT + " must carry exactly one state, got " + state.getProperties());
        // 光只跟着 active 走，13 级与原版熔炉一致
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        ServerLevel level = helper.getLevel();
        BlockState lit = state.setValue(Experiment051Block.ACTIVE, true);
        helper.assertTrue(state.getLightEmission(level, pos) == 0,
            "an inactive " + SUBJECT + " must not emit light, got " + state.getLightEmission(level, pos));
        helper.assertTrue(lit.getLightEmission(level, pos) == 13,
            "a burning " + SUBJECT + " must emit the furnace's 13 light levels, got "
                + lit.getLightEmission(level, pos));
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void reversesShapedRecipesInPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UncraftingPlan plan = UncraftingResolver.resolve(level, new ItemStack(Items.CRAFTING_TABLE));
        helper.assertFalse(plan.isNone(), "a crafting table must have something to uncraft");
        helper.assertTrue(plan.inputCount() == 1,
            "one crafting table must be eaten per craft, got " + plan.inputCount());
        helper.assertTrue(plan.cookTime() == UncraftingPlan.DEFAULT_COOK_TIME,
            "uncrafting must take the usual furnace time (" + UncraftingPlan.DEFAULT_COOK_TIME
                + " ticks), got " + plan.cookTime());
        // 工作台是 2x2 的四个木板，位置照拆解台铺在左上 2x2
        for (int slot : new int[] {0, 1, 3, 4}) {
            helper.assertTrue(plan.products().get(slot).is(ItemTags.PLANKS),
                "crafting table ingredient #" + slot + " must land on output slot " + slot
                    + ", got " + plan.products().get(slot));
        }
        for (int slot : new int[] {2, 5, 6, 7, 8}) {
            helper.assertTrue(plan.products().get(slot).isEmpty(),
                "output slot " + slot + " must stay empty for a 2x2 recipe, got " + plan.products().get(slot));
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void fallsBackToSmelting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UncraftingPlan plan = UncraftingResolver.resolve(level, new ItemStack(Items.RAW_IRON));
        helper.assertFalse(plan.isNone(), "raw iron has no crafting recipe, so it must fall back to smelting");
        helper.assertTrue(plan.products().get(0).is(Items.IRON_INGOT),
            "smelting raw iron must yield an iron ingot in the first output slot, got " + plan.products().get(0));
        helper.assertTrue(plan.cookTime() == 200,
            "smelting must take that recipe's own 200 tick cooking time, got " + plan.cookTime());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void fuelDrivesTheCraftAndFillsTheGrid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Experiment051BlockEntity machine = placeMachine(level, pos);
        machine.setItem(Experiment051BlockEntity.INPUT_SLOT, new ItemStack(Items.CRAFTING_TABLE, 2));
        machine.setItem(Experiment051BlockEntity.FUEL_SLOT, new ItemStack(Items.COAL));

        runTicks(level, pos, machine, UncraftingPlan.DEFAULT_COOK_TIME + 5);

        helper.assertTrue(machine.getItem(Experiment051BlockEntity.INPUT_SLOT).getCount() == 1,
            "one craft must eat one crafting table, 2 -> 1, got "
                + machine.getItem(Experiment051BlockEntity.INPUT_SLOT).getCount());
        helper.assertTrue(machine.getItem(Experiment051BlockEntity.OUTPUT_FIRST).is(ItemTags.PLANKS),
            "the four planks must land in the output grid, got "
                + machine.getItem(Experiment051BlockEntity.OUTPUT_FIRST));
        helper.assertTrue(machine.isLit(),
            "coal burns for 1600 ticks, the machine must still be lit after " + UncraftingPlan.DEFAULT_COOK_TIME);
        helper.assertTrue(level.getBlockState(pos).getValue(Experiment051Block.ACTIVE),
            "a burning " + SUBJECT + " must switch to the active model");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void fullOutputSlotStopsTheMachine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Experiment051BlockEntity machine = placeMachine(level, pos);
        machine.setItem(Experiment051BlockEntity.INPUT_SLOT, new ItemStack(Items.CRAFTING_TABLE));
        machine.setItem(Experiment051BlockEntity.FUEL_SLOT, new ItemStack(Items.COAL));
        // 第一格已经堆满木板，再一次拆就超 64
        ItemStack full = new ItemStack(Items.OAK_PLANKS, 64);
        machine.setItem(Experiment051BlockEntity.OUTPUT_FIRST, full);

        runTicks(level, pos, machine, UncraftingPlan.DEFAULT_COOK_TIME * 2);

        helper.assertTrue(machine.getItem(Experiment051BlockEntity.INPUT_SLOT).getCount() == 1,
            "a blocked machine must not eat the input, got "
                + machine.getItem(Experiment051BlockEntity.INPUT_SLOT).getCount());
        helper.assertFalse(machine.isLit(),
            "a blocked machine must not spend fuel either");
        helper.assertFalse(level.getBlockState(pos).getValue(Experiment051Block.ACTIVE),
            "a blocked machine must stay inactive");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void comparatorReadsTheContainerFill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Experiment051BlockEntity machine = placeMachine(level, pos);
        BlockState state = level.getBlockState(pos);

        helper.assertTrue(state.getAnalogOutputSignal(level, pos) == 0,
            "an empty " + SUBJECT + " must read 0 on a comparator, got " + state.getAnalogOutputSignal(level, pos));
        machine.setItem(Experiment051BlockEntity.INPUT_SLOT, new ItemStack(Items.CRAFTING_TABLE, 64));
        helper.assertTrue(state.getAnalogOutputSignal(level, pos) > 0,
            "a full input slot must drive the comparator, got " + state.getAnalogOutputSignal(level, pos));
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void hopperFacesSplitInputFuelAndOutput(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Experiment051BlockEntity machine = placeMachine(level, pos);

        helper.assertTrue(machine.getSlotsForFace(Direction.UP).length == 1
                && machine.getSlotsForFace(Direction.UP)[0] == Experiment051BlockEntity.INPUT_SLOT,
            "the top face must only feed the input slot");
        helper.assertTrue(machine.getSlotsForFace(Direction.NORTH).length == 1
                && machine.getSlotsForFace(Direction.NORTH)[0] == Experiment051BlockEntity.FUEL_SLOT,
            "the side faces must only feed the fuel slot");
        helper.assertTrue(machine.getSlotsForFace(Direction.DOWN).length == Experiment051BlockEntity.OUTPUT_COUNT,
            "the bottom face must expose all nine output slots");

        helper.assertTrue(machine.canPlaceItemThroughFace(Experiment051BlockEntity.INPUT_SLOT,
                new ItemStack(Items.CRAFTING_TABLE), Direction.UP),
            "crafting tables must go in through the top");
        helper.assertFalse(machine.canPlaceItemThroughFace(Experiment051BlockEntity.INPUT_SLOT,
                new ItemStack(Items.CRAFTING_TABLE), Direction.NORTH),
            "crafting tables must not go in through the side");
        helper.assertTrue(machine.canPlaceItemThroughFace(Experiment051BlockEntity.FUEL_SLOT,
                new ItemStack(Items.COAL), Direction.NORTH),
            "coal must go in through the side");
        helper.assertFalse(machine.canPlaceItemThroughFace(Experiment051BlockEntity.FUEL_SLOT,
                new ItemStack(Items.STONE), Direction.NORTH),
            "stone is not fuel, the fuel slot must refuse it");
        helper.assertFalse(machine.canPlaceItemThroughFace(Experiment051BlockEntity.OUTPUT_FIRST,
                new ItemStack(Items.STONE), Direction.DOWN),
            "output slots must never take items in");
        helper.assertTrue(machine.canTakeItemThroughFace(Experiment051BlockEntity.OUTPUT_FIRST,
                new ItemStack(Items.OAK_PLANKS), Direction.DOWN),
            "output slots must be pullable from the bottom");
        helper.succeed();
    }

    /**
     * 试验物品 017 只能拆、不能合：拆一次吃 1 个，吐四个钢叶加一片黑树树叶
     *
     * <p>产物位置照拆解台的 3x3：右上、中左、中中、下中四个钢叶（{@code twilightforest:steeleaf_ingot}），
     * 左下那片是黑树树叶（{@code twilightforest:dark_leaves}），其余四格空着
     *
     * <p>数据在 {@code data/dawnduskterminal/recipe/experiment_017_uncrafting.json}，类型是暮色森林的
     * {@code twilightforest:uncrafting}（本模组的拆解台与它共用同一份配方表）。同时钉住「不可合成」：
     * 哪天有人给它补了合成配方，反查那段就会抢在拆解配方前面，这条断言先红
     */
    @GameTest(template = "ddt_empty")
    public static void experiment017UncraftsIntoTwilightMaterials(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean craftable = level.getRecipeManager().getRecipes().stream()
            .anyMatch(holder -> holder.value().getResultItem(level.registryAccess())
                .is(ModExperimentItems.EXPERIMENT_017.get()));
        helper.assertFalse(craftable,
            "experiment_017 must stay uncraftable, but some crafting recipe now yields it");

        UncraftingPlan plan = UncraftingResolver.resolve(level,
            new ItemStack(ModExperimentItems.EXPERIMENT_017.get()));
        helper.assertFalse(plan.isNone(), "experiment_017 must uncraft into its twilight materials");
        helper.assertTrue(plan.inputCount() == 1,
            "one experiment_017 must be eaten per uncraft, got " + plan.inputCount());

        ItemStack steelLeaf = new ItemStack(TFItems.STEELEAF_INGOT.get());
        ItemStack darkLeaves = new ItemStack(TFBlocks.DARK_LEAVES.get().asItem());
        for (int slot : new int[] {2, 3, 4, 7}) {
            helper.assertTrue(plan.products().get(slot).is(steelLeaf.getItem()),
                "output slot " + slot + " must be a steeleaf ingot, got " + plan.products().get(slot));
        }
        helper.assertTrue(plan.products().get(6).is(darkLeaves.getItem()),
            "output slot 6 must be dark leaves, got " + plan.products().get(6));
        for (int slot : new int[] {0, 1, 5, 8}) {
            helper.assertTrue(plan.products().get(slot).isEmpty(),
                "output slot " + slot + " must stay empty, got " + plan.products().get(slot));
        }
        helper.succeed();
    }

    /** 别的模组的物品，只用字符串 ID 取；取不到就是空栈，让断言自己把这件事报出来 */
    private static ItemStack foreign(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    private static Experiment051BlockEntity placeMachine(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, ModBlocks.EXPERIMENT_051.get().defaultBlockState());
        Experiment051BlockEntity machine = (Experiment051BlockEntity) level.getBlockEntity(pos);
        // 假玩家与结构里的方块实体不一定已经挂上 level，这里显式补一次，机器刻才有配方表可读
        machine.setLevel(level);
        return machine;
    }

    private static void runTicks(ServerLevel level, BlockPos pos, Experiment051BlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) {
            Experiment051BlockEntity.serverTick(level, pos, level.getBlockState(pos), machine);
        }
    }

    private Experiment051Tests() {}
}
