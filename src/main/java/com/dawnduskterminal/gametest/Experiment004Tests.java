package com.dawnduskterminal.gametest;

import com.dawnduskterminal.block.entity.Experiment004BlockEntity;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 试验物品 004 的规格
 *
 * <p>钉住三条：方块属性照抄原版熔炉（3.5 硬度、石头音效、进镐头标签、烧着发 13 级光）、状态就是熔炉那对
 * {@code facing} 与 {@code lit}、以及「像熔炉一样烧、食物快一倍」——200 tick 的土豆→烤土豆在这里是 100 tick，
 * 粗铁→铁锭仍是 200
 *
 * <p>粒子不在这条测试里：{@code animateTick} 只在客户端跑，GameTestServer 上没有客户端；方块类也压根没写
 * {@code addParticle}，只补了火响那一声
 *
 * <p>机器刻不靠等方块实体自然刻，直接调 {@link Experiment004BlockEntity#serverTick}，一百多 tick 的进度在
 * 一次测试里跑完，与 051 那套测试同一个写法
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class Experiment004Tests {
    private static final String SUBJECT = "dawnduskterminal:experiment_004";

    @GameTest(template = "ddt_empty")
    @SuppressWarnings("deprecation") // 爆炸抗性只有这个没带 Explosion 的取值入口，NeoForge 把它标了废弃
    public static void propertiesCopyTheFurnace(GameTestHelper helper) {
        Block ours = ModBlocks.EXPERIMENT_004.get();
        helper.assertTrue(ours.defaultDestroyTime() == 3.5F,
            SUBJECT + " destroy time must be the furnace's 3.5, got " + ours.defaultDestroyTime());
        helper.assertTrue(ours.getExplosionResistance() == 3.5F,
            SUBJECT + " explosion resistance must be 3.5, got " + ours.getExplosionResistance());
        helper.assertTrue(ours.defaultMapColor() == MapColor.STONE,
            SUBJECT + " map color must be STONE, got " + ours.defaultMapColor());
        helper.assertTrue(ours.defaultBlockState().getSoundType() == SoundType.STONE,
            SUBJECT + " must sound like stone, got " + ours.defaultBlockState().getSoundType());
        helper.assertTrue(ours.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE),
            SUBJECT + " must stay in #minecraft:mineable/pickaxe, exactly like the furnace");
        helper.assertTrue(ours.defaultBlockState().requiresCorrectToolForDrops(),
            SUBJECT + " must require the correct tool, exactly like the furnace");
        helper.assertTrue(new ItemStack(ModItems.EXPERIMENT_004.get()).is(ours.asItem()),
            SUBJECT + " must have a block item registered under the same id");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void stateSetMatchesTheFurnace(GameTestHelper helper) {
        BlockState state = ModBlocks.EXPERIMENT_004.get().defaultBlockState();
        helper.assertFalse(state.getValue(AbstractFurnaceBlock.LIT),
            "a placed " + SUBJECT + " must start unlit");
        helper.assertTrue(state.getValue(AbstractFurnaceBlock.FACING).equals(Direction.NORTH),
            "the default facing must be north, got " + state.getValue(AbstractFurnaceBlock.FACING));
        helper.assertTrue(AbstractFurnaceBlock.FACING.getName().equals("facing"),
            "the facing property must keep the furnace's name, got " + AbstractFurnaceBlock.FACING.getName());
        helper.assertTrue(AbstractFurnaceBlock.LIT.getName().equals("lit"),
            "the burning property must keep the furnace's name lit, got " + AbstractFurnaceBlock.LIT.getName());
        helper.assertTrue(state.getProperties().size() == 2,
            SUBJECT + " must carry exactly facing and lit, got " + state.getProperties());
        helper.assertTrue(AbstractFurnaceBlock.FACING.getPossibleValues().size() == 4,
            "facing must run over four horizontal directions, got "
                + AbstractFurnaceBlock.FACING.getPossibleValues());
        // 光只跟着 lit 走，13 级与原版熔炉一致
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        ServerLevel level = helper.getLevel();
        BlockState lit = state.setValue(AbstractFurnaceBlock.LIT, true);
        helper.assertTrue(state.getLightEmission(level, pos) == 0,
            "an unlit " + SUBJECT + " must not emit light, got " + state.getLightEmission(level, pos));
        helper.assertTrue(lit.getLightEmission(level, pos) == 13,
            "a burning " + SUBJECT + " must emit the furnace's 13 light levels, got "
                + lit.getLightEmission(level, pos));
        helper.succeed();
    }

    /**
     * 土豆→烤土豆：原版配方 200 tick，产物是食物，所以这里只要 100 tick
     *
     * <p>第 100 tick 出锅，99 tick 时产物格必须还是空的——差一 tick 就说明翻倍没生效或者提前出锅
     */
    @GameTest(template = "ddt_empty")
    public static void potatoSmeltsInHalfTheUsualTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Experiment004BlockEntity machine = placeMachine(level, pos);
        machine.setItem(Experiment004BlockEntity.INPUT_SLOT, new ItemStack(Items.POTATO));
        machine.setItem(Experiment004BlockEntity.FUEL_SLOT, new ItemStack(Items.COAL));

        helper.assertTrue(machine.dataAccess().get(Experiment004BlockEntity.DATA_COOK_TOTAL) == 100,
            "baked potato must take half the vanilla 200 ticks, got "
                + machine.dataAccess().get(Experiment004BlockEntity.DATA_COOK_TOTAL));

        runTicks(level, pos, machine, 99);
        helper.assertTrue(machine.getItem(Experiment004BlockEntity.RESULT_SLOT).isEmpty(),
            "the potato must still be in the furnace at tick 99, got "
                + machine.getItem(Experiment004BlockEntity.RESULT_SLOT));

        runTicks(level, pos, machine, 1);
        helper.assertTrue(machine.getItem(Experiment004BlockEntity.RESULT_SLOT).is(Items.BAKED_POTATO),
            "the potato must come out as a baked potato at tick 100, got "
                + machine.getItem(Experiment004BlockEntity.RESULT_SLOT));
        helper.assertTrue(machine.getItem(Experiment004BlockEntity.INPUT_SLOT).isEmpty(),
            "smelting one potato must eat exactly one potato, got "
                + machine.getItem(Experiment004BlockEntity.INPUT_SLOT));
        helper.assertTrue(machine.isLit(), "coal burns for 1600 ticks, the machine must still be lit");
        helper.assertTrue(level.getBlockState(pos).getValue(AbstractFurnaceBlock.LIT),
            "a burning " + SUBJECT + " must switch to the active model");
        helper.succeed();
    }

    /** 粗铁→铁锭的产物不是食物，所以仍是原版的 200 tick */
    @GameTest(template = "ddt_empty")
    public static void rawIronKeepsTheFullCookingTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Experiment004BlockEntity machine = placeMachine(level, pos);
        machine.setItem(Experiment004BlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON));
        machine.setItem(Experiment004BlockEntity.FUEL_SLOT, new ItemStack(Items.COAL));

        helper.assertTrue(machine.dataAccess().get(Experiment004BlockEntity.DATA_COOK_TOTAL) == 200,
            "an iron ingot must keep the vanilla 200 ticks, got "
                + machine.dataAccess().get(Experiment004BlockEntity.DATA_COOK_TOTAL));

        runTicks(level, pos, machine, 199);
        helper.assertTrue(machine.getItem(Experiment004BlockEntity.RESULT_SLOT).isEmpty(),
            "raw iron must not smelt before tick 200, got "
                + machine.getItem(Experiment004BlockEntity.RESULT_SLOT));

        runTicks(level, pos, machine, 1);
        helper.assertTrue(machine.getItem(Experiment004BlockEntity.RESULT_SLOT).is(Items.IRON_INGOT),
            "raw iron must come out as an iron ingot at tick 200, got "
                + machine.getItem(Experiment004BlockEntity.RESULT_SLOT));
        helper.succeed();
    }

    /** 换原料：进度清零，总时间按新配方重算（粗铁 200 → 土豆 100） */
    @GameTest(template = "ddt_empty")
    public static void switchedInputResetsProgressAndRecomputesTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Experiment004BlockEntity machine = placeMachine(level, pos);
        machine.setItem(Experiment004BlockEntity.INPUT_SLOT, new ItemStack(Items.RAW_IRON));
        machine.setItem(Experiment004BlockEntity.FUEL_SLOT, new ItemStack(Items.COAL));

        runTicks(level, pos, machine, 10);
        helper.assertTrue(machine.dataAccess().get(Experiment004BlockEntity.DATA_COOK_PROGRESS) == 10,
            "ten ticks of coal must advance the raw iron by ten, got "
                + machine.dataAccess().get(Experiment004BlockEntity.DATA_COOK_PROGRESS));

        machine.setItem(Experiment004BlockEntity.INPUT_SLOT, new ItemStack(Items.POTATO));
        helper.assertTrue(machine.dataAccess().get(Experiment004BlockEntity.DATA_COOK_PROGRESS) == 0,
            "swapping the input must reset the progress, got "
                + machine.dataAccess().get(Experiment004BlockEntity.DATA_COOK_PROGRESS));
        helper.assertTrue(machine.dataAccess().get(Experiment004BlockEntity.DATA_COOK_TOTAL) == 100,
            "a potato must be cooked in 100 ticks, got "
                + machine.dataAccess().get(Experiment004BlockEntity.DATA_COOK_TOTAL));
        helper.succeed();
    }

    /** 漏斗那三条（顶进原料、侧进燃料、底抽产物）直接问 {@code WorldlyContainer} 的接口，不用真摆三个漏斗 */
    @GameTest(template = "ddt_empty")
    public static void hopperFacesMatchTheFurnace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Experiment004BlockEntity machine = placeMachine(level, pos);

        helper.assertTrue(machine.getSlotsForFace(Direction.UP).length == 1
                && machine.getSlotsForFace(Direction.UP)[0] == Experiment004BlockEntity.INPUT_SLOT,
            "the top face must only feed the input slot");
        helper.assertTrue(machine.getSlotsForFace(Direction.NORTH).length == 1
                && machine.getSlotsForFace(Direction.NORTH)[0] == Experiment004BlockEntity.FUEL_SLOT,
            "the side faces must only feed the fuel slot");
        helper.assertTrue(machine.getSlotsForFace(Direction.DOWN).length == 2,
            "the bottom face must expose the result and the fuel slot, like the furnace");

        helper.assertTrue(machine.canPlaceItem(Experiment004BlockEntity.INPUT_SLOT, new ItemStack(Items.POTATO)),
            "raw food must go into the input slot");
        helper.assertFalse(machine.canPlaceItem(Experiment004BlockEntity.RESULT_SLOT, new ItemStack(Items.POTATO)),
            "the result slot must never take items in");
        helper.assertTrue(machine.canPlaceItem(Experiment004BlockEntity.FUEL_SLOT, new ItemStack(Items.COAL)),
            "coal must go into the fuel slot");
        helper.assertFalse(machine.canPlaceItem(Experiment004BlockEntity.FUEL_SLOT, new ItemStack(Items.STONE)),
            "stone is not fuel, the fuel slot must refuse it");
        helper.assertTrue(machine.canTakeItemThroughFace(Experiment004BlockEntity.RESULT_SLOT,
                new ItemStack(Items.BAKED_POTATO), Direction.DOWN),
            "the result slot must be pullable from the bottom");
        helper.succeed();
    }

    /**
     * 界面：菜单类型必须是本模组注册的那一个，格子数与熔炉一致，产物格能拿不能放
     *
     * <p>原版 {@code FurnaceMenu} 把 {@code MenuType.FURNACE} 写死在构造参数里，所以菜单类必须自己把类型传进
     * {@code AbstractFurnaceMenu}；如果哪天改回继承 {@code FurnaceMenu}，客户端会按原版熔炉界面开，
     * 这条断言先红
     */
    @GameTest(template = "ddt_empty")
    public static void menuUsesTheModMenuTypeAndFloatsTheResultSlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        Experiment004BlockEntity machine = placeMachine(level, pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        AbstractContainerMenu menu = machine.createMenu(0, player.getInventory(), player);
        helper.assertTrue(menu != null, SUBJECT + " must open a menu");
        helper.assertTrue(menu.getType() == ModMenus.EXPERIMENT_004.get(),
            "the menu must use dawnduskterminal:experiment_004, got " + menu.getType());
        // 三格机器加玩家背包 36 格
        helper.assertTrue(menu.slots.size() == 39,
            "the furnace menu must hold 3 + 36 slots, got " + menu.slots.size());
        helper.assertFalse(menu.getSlot(Experiment004BlockEntity.RESULT_SLOT)
                .mayPlace(new ItemStack(Items.BAKED_POTATO)),
            "the result slot must never take items in");
        helper.succeed();
    }

    private static Experiment004BlockEntity placeMachine(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, ModBlocks.EXPERIMENT_004.get().defaultBlockState());
        Experiment004BlockEntity machine = (Experiment004BlockEntity) level.getBlockEntity(pos);
        // 假玩家与结构里的方块实体不一定已经挂上 level，这里显式补一次，机器刻才有配方表可读
        machine.setLevel(level);
        return machine;
    }

    private static void runTicks(ServerLevel level, BlockPos pos, Experiment004BlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) {
            Experiment004BlockEntity.serverTick(level, pos, level.getBlockState(pos), machine);
        }
    }

    private Experiment004Tests() {}
}
