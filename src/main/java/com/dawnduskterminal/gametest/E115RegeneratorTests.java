package com.dawnduskterminal.gametest;

import com.dawnduskterminal.block.E115RegeneratorBlock;
import com.dawnduskterminal.block.entity.E115RegeneratorBlockEntity;
import com.dawnduskterminal.registry.ModBlockEntities;
import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 115 再生机的规格
 *
 * <p>钉住的是「照 115 的随机刻随机产」与「两个条件缺一不可」：
 *
 * <ol>
 *   <li>概率就是随机刻那一档：分母 {@value E115RegeneratorBlockEntity#RANDOM_TICK_VOLUME}、
 *       每 tick 抽 {@value E115RegeneratorBlockEntity#RANDOM_TICK_STEPS} 个位置，与 115 被抽中一次补一片同一件事
 *   <li>条件满足时一定会产：几十万分之一以下的漏空概率之外，跑 {@value #TICKS} tick 必出东西
 *   <li>顶面那块的信号不算侧面信号：满片撒粉的 115 自己是信号源，它往下发 15 级，只看水平四面才立得住
 *   <li>顶上必须完整且撒过粉：吃到一半 / 没撒粉 / 空着都不产
 *   <li>产物格满了就停
 *   <li>漏斗只有底面抽得走，其余五个面既不进也不出
 *   <li>玩家拿着 115 点过来时本方块不吞交互（这一下留给物品自己往顶面摆），别的物品照默认走
 * </ol>
 *
 * <p>机器刻不靠等方块实体自然刻，直接调 {@link E115RegeneratorBlockEntity#serverTick}，
 * 十几万 tick 在一次测试里跑完
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class E115RegeneratorTests {
    private static final String SUBJECT = "dawnduskterminal:e_115_regenerator";

    /** 机器摆哪：模板里靠里的空格，正上方与东边都还空着 */
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);

    /**
     * 掷骰子要跑多少 tick
     *
     * <p>出货概率 3/4096 每 tick，跑这么多 tick 一条也不出的概率是
     * {@code (1 - 3/4096)^50000 ≈ 1e-16}，可以当「必出」用
     */
    private static final int TICKS = 50_000;

    @GameTest(template = "ddt_empty")
    public static void blockAndItemAreWiredUp(GameTestHelper helper) {
        Item item = ModItems.E_115_REGENERATOR.get();
        helper.assertTrue(item instanceof BlockItem blockItem && blockItem.getBlock() == ModBlocks.E_115_REGENERATOR.get(),
            SUBJECT + " must be the item form of its own block, got " + item);
        helper.assertTrue(item.getDescriptionId().equals("block.dawnduskterminal.e_115_regenerator"),
            "a block item must take its name from the block, got " + item.getDescriptionId());

        BlockState state = ModBlocks.E_115_REGENERATOR.get().defaultBlockState();
        helper.assertTrue(state.getProperties().isEmpty(),
            SUBJECT + " must stay stateless, got " + state.getProperties());
        helper.assertTrue(ModBlockEntities.E_115_REGENERATOR.get().isValid(state),
            SUBJECT + " must be a valid block for its own block entity type");
        helper.assertTrue(ModMenus.E_115_REGENERATOR.get() != null,
            SUBJECT + " must have its own menu type so the screen opens");

        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MACHINE);
        E115RegeneratorBlockEntity machine = placeMachine(level, pos);
        helper.assertTrue(machine.getContainerSize() == E115RegeneratorBlockEntity.CONTAINER_SIZE,
            SUBJECT + " has " + E115RegeneratorBlockEntity.CONTAINER_SIZE + " slot, got " + machine.getContainerSize());
        helper.assertTrue(machine.getItem(0).isEmpty(),
            SUBJECT + " must start with an empty output slot, got " + machine.getItem(0));
        helper.succeed();
    }

    /** 骰子就是随机刻那一档：分母 16³、每 tick 3 个位置 */
    @GameTest(template = "ddt_empty")
    public static void rollMatchesExperiment115RandomTick(GameTestHelper helper) {
        helper.assertTrue(E115RegeneratorBlockEntity.RANDOM_TICK_VOLUME == 16 * 16 * 16,
            "the roll denominator must be a 16x16x16 section, got "
                + E115RegeneratorBlockEntity.RANDOM_TICK_VOLUME);
        helper.assertTrue(E115RegeneratorBlockEntity.RANDOM_TICK_STEPS == 3,
            "the roll must draw the vanilla randomTickSpeed of 3 positions per tick, got "
                + E115RegeneratorBlockEntity.RANDOM_TICK_STEPS);
        helper.succeed();
    }

    /** 侧面通红石、顶上完整的撒粉 115：跑够长一定产，而且产的是 115 物品 */
    @GameTest(template = "ddt_empty")
    public static void producesWhileSidePoweredWithSprinkledSource(GameTestHelper helper) {
        if (!sourceAvailable(helper)) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MACHINE);
        E115RegeneratorBlockEntity machine = placeMachine(level, pos);
        level.setBlockAndUpdate(pos.above(), sourceState(0, true));
        level.setBlockAndUpdate(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.assertTrue(E115RegeneratorBlock.isSidePowered(level, pos),
            "a redstone block next to the machine must count as a side signal");
        helper.assertTrue(E115RegeneratorBlock.hasSprinkledFullSource(level, pos),
            "a full experiment_115 with redstone dust above must be accepted as the source");

        runTicks(level, pos, machine, TICKS);
        ItemStack produced = machine.getItem(0);
        helper.assertTrue(!produced.isEmpty(),
            SUBJECT + " must produce something within " + TICKS + " ticks, got " + produced);
        helper.assertTrue(produced.is(E115RegeneratorBlock.sourceBlock().asItem()),
            SUBJECT + " must produce twilightforest:experiment_115, got " + produced);
        helper.succeed();
    }

    /** 顶上那块 115 自己发的信号不算侧面信号：一个红石都不给，跑再久也不许产 */
    @GameTest(template = "ddt_empty")
    public static void sourceAboveDoesNotPowerTheMachine(GameTestHelper helper) {
        if (!sourceAvailable(helper)) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MACHINE);
        E115RegeneratorBlockEntity machine = placeMachine(level, pos);
        level.setBlockAndUpdate(pos.above(), sourceState(0, true));

        helper.assertTrue(level.getSignal(pos.above(), Direction.DOWN) > 0,
            "a full sprinkled experiment_115 must emit a signal downwards, otherwise this test proves nothing");
        helper.assertTrue(!E115RegeneratorBlock.isSidePowered(level, pos),
            SUBJECT + " must ignore the signal the source above emits downwards");

        runTicks(level, pos, machine, TICKS);
        helper.assertTrue(machine.getItem(0).isEmpty(),
            SUBJECT + " must do nothing without a side signal, got " + machine.getItem(0));
        helper.succeed();
    }

    /** 顶上少一条都不行：吃到一半的不行，没撒粉的也不行，空的更不行 */
    @GameTest(template = "ddt_empty")
    public static void sourceMustBeFullAndSprinkled(GameTestHelper helper) {
        if (!sourceAvailable(helper)) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MACHINE);
        E115RegeneratorBlockEntity machine = placeMachine(level, pos);
        level.setBlockAndUpdate(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());

        // 吃到一半（omnomnom=1）
        level.setBlockAndUpdate(pos.above(), sourceState(1, true));
        helper.assertFalse(E115RegeneratorBlock.hasSprinkledFullSource(level, pos),
            "a half eaten experiment_115 must not count as the source");
        runTicks(level, pos, machine, TICKS);
        helper.assertTrue(machine.getItem(0).isEmpty(),
            SUBJECT + " must not produce for a half eaten source, got " + machine.getItem(0));

        // 满片、但没撒粉
        level.setBlockAndUpdate(pos.above(), sourceState(0, false));
        helper.assertFalse(E115RegeneratorBlock.hasSprinkledFullSource(level, pos),
            "a plain experiment_115 must not count as the source");
        runTicks(level, pos, machine, TICKS);
        helper.assertTrue(machine.getItem(0).isEmpty(),
            SUBJECT + " must not produce for an unsprinkled source, got " + machine.getItem(0));

        // 顶上什么都不放
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        helper.assertFalse(E115RegeneratorBlock.hasSprinkledFullSource(level, pos),
            "an empty block above must not count as the source");
        runTicks(level, pos, machine, TICKS);
        helper.assertTrue(machine.getItem(0).isEmpty(),
            SUBJECT + " must not produce without a source above, got " + machine.getItem(0));
        helper.succeed();
    }

    /** 产物格满了就停，不会再往里塞 */
    @GameTest(template = "ddt_empty")
    public static void fullOutputStopsTheMachine(GameTestHelper helper) {
        if (!sourceAvailable(helper)) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MACHINE);
        E115RegeneratorBlockEntity machine = placeMachine(level, pos);
        level.setBlockAndUpdate(pos.above(), sourceState(0, true));
        level.setBlockAndUpdate(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());

        machine.setItem(0, E115RegeneratorBlock.newSourceStack().copyWithCount(64));
        runTicks(level, pos, machine, TICKS);
        helper.assertTrue(machine.getItem(0).getCount() == 64,
            SUBJECT + " must not overfill its output slot, got " + machine.getItem(0).getCount());
        helper.succeed();
    }

    /** 漏斗只从底面抽，其余五个面一个槽都不开，往里也塞不进 */
    @GameTest(template = "ddt_empty")
    public static void hopperOnlyTakesFromTheBottom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MACHINE);
        E115RegeneratorBlockEntity machine = placeMachine(level, pos);

        helper.assertTrue(machine.getSlotsForFace(Direction.DOWN).length == 1
                && machine.getSlotsForFace(Direction.DOWN)[0] == E115RegeneratorBlockEntity.OUTPUT_SLOT,
            SUBJECT + " must expose its output slot downwards");
        for (Direction side : new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.EAST, Direction.WEST}) {
            helper.assertTrue(machine.getSlotsForFace(side).length == 0,
                SUBJECT + " must expose no slot to " + side);
            helper.assertTrue(!machine.canPlaceItemThroughFace(E115RegeneratorBlockEntity.OUTPUT_SLOT,
                    new ItemStack(ModItems.E_115_REGENERATOR.get()), side),
                SUBJECT + " must not accept items through " + side);
        }
        helper.assertTrue(machine.canTakeItemThroughFace(E115RegeneratorBlockEntity.OUTPUT_SLOT,
                ItemStack.EMPTY, Direction.DOWN), SUBJECT + " must let a hopper below take the output");
        helper.assertTrue(!machine.canPlaceItem(E115RegeneratorBlockEntity.OUTPUT_SLOT,
                new ItemStack(ModItems.E_115_REGENERATOR.get())),
            SUBJECT + " must never accept items into its output slot");
        helper.succeed();
    }

    /**
     * 玩家拿着 115 点过来时本方块不吞交互，这一下留给物品自己往顶面摆
     *
     * <p>问的是方块那一层的 {@code useItemOn}（{@code BlockState#useItemOn}）：拿 115 时必须是
     * {@code SKIP_DEFAULT_BLOCK_INTERACTION}，不然「摆不下」那一下会顺势把界面开出来；
     * 拿别的物品照默认的 {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} 走
     */
    @GameTest(template = "ddt_empty")
    public static void sourceItemClickIsLeftToTheItem(GameTestHelper helper) {
        if (!sourceAvailable(helper)) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MACHINE);
        placeMachine(level, pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0.0D, 0.5D, 0.0D), Direction.UP, pos, false);

        ItemInteractionResult withSource = level.getBlockState(pos)
            .useItemOn(E115RegeneratorBlock.newSourceStack(), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(withSource == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION,
            SUBJECT + " must leave the click with experiment_115 to the item itself, got " + withSource);

        ItemInteractionResult withOther = level.getBlockState(pos)
            .useItemOn(new ItemStack(Items.STICK), level, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(withOther == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
            SUBJECT + " must keep the default block interaction for other items, got " + withOther);
        helper.succeed();
    }

    /** 产出时放的是顶上那块 115 的放置音效：115 是羊毛音效 */
    @GameTest(template = "ddt_empty")
    public static void productionSoundIsTheSourcePlaceSound(GameTestHelper helper) {
        if (!sourceAvailable(helper)) {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MACHINE);
        placeMachine(level, pos);
        level.setBlockAndUpdate(pos.above(), sourceState(0, true));
        helper.assertTrue(E115RegeneratorBlock.sourcePlaceSound(level, pos) == SoundType.WOOL.getPlaceSound(),
            "experiment_115 is a wool sounding block, so producing must play block.wool.place, got "
                + E115RegeneratorBlock.sourcePlaceSound(level, pos));
        helper.succeed();
    }

    private static E115RegeneratorBlockEntity placeMachine(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, ModBlocks.E_115_REGENERATOR.get().defaultBlockState());
        E115RegeneratorBlockEntity machine = (E115RegeneratorBlockEntity) level.getBlockEntity(pos);
        // 结构里的方块实体不一定已经挂上 level，这里显式补一次
        machine.setLevel(level);
        return machine;
    }

    private static void runTicks(ServerLevel level, BlockPos pos, E115RegeneratorBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) {
            E115RegeneratorBlockEntity.serverTick(level, pos, level.getBlockState(pos), machine);
        }
    }

    /**
     * 顶上那块的某个状态，按字符串找属性名，与 {@link E115RegeneratorBlock} 那边同一套规矩
     *
     * <p>{@code bites} 是「咬了几口」，0 是满的 8 片；{@code regenerate} 是撒没撒过红石粉
     */
    private static BlockState sourceState(int bites, boolean regenerate) {
        BlockState state = E115RegeneratorBlock.sourceBlock().defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integer
                && integer.getName().equals(E115RegeneratorBlock.SOURCE_BITES_PROPERTY)) {
                state = state.setValue(integer, bites);
            } else if (property instanceof BooleanProperty bool
                && bool.getName().equals(E115RegeneratorBlock.SOURCE_REGENERATE_PROPERTY)) {
                state = state.setValue(bool, regenerate);
            }
        }
        return state;
    }

    /** 没装暮色森林时这几条测不了：判过，别让缺 jar 的运行炸掉整个测试集 */
    private static boolean sourceAvailable(GameTestHelper helper) {
        if (E115RegeneratorBlock.sourceBlock() != Blocks.AIR && !E115RegeneratorBlock.newSourceStack().isEmpty()) {
            return true;
        }
        helper.succeed();
        return false;
    }

    private E115RegeneratorTests() {}
}
