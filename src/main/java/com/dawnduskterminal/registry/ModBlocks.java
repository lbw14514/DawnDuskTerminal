package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.block.CarminiteFireBlock;
import com.dawnduskterminal.block.Experiment004Block;
import com.dawnduskterminal.block.Experiment019Block;
import com.dawnduskterminal.block.Experiment051Block;
import com.dawnduskterminal.block.Experiment052Block;
import com.dawnduskterminal.block.Experiment065Block;
import com.dawnduskterminal.block.Experiment069Block;
import com.dawnduskterminal.block.Experiment114Block;
import com.dawnduskterminal.portal.PortalBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DawnDuskTerminal.MOD_ID);

    public static final DeferredBlock<PortalBlock> PORTAL_FLUID = BLOCKS.registerBlock(
        "portal_fluid",
        props -> new PortalBlock(ModFluids.PORTAL_FLUID.get(), props),
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .replaceable()
            .noCollission()
            .randomTicks()
            .strength(100.0F)
            .lightLevel(state -> 15)
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY));

    public static final DeferredBlock<Block> CHRONO_CRUST = BLOCKS.registerSimpleBlock(
        "chrono_crust",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(-1.0F, 3600000.0F)
            .noLootTable()
            .lightLevel(state -> 3));

    /**
     * 能当合成台用的方块
     *
     * <p>原版 {@code CraftingMenu#stillValid} 把「玩家开着的这台还在不在」硬编码成
     * {@code level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)}，所以光让方块继承
     * {@link CraftingTableBlock} 是不够的：界面会打开，下一秒就被服务端以「方块不是工作台」关掉。
     * 这里用方块标签接管那条判定，{@code mixin/CraftingMenuMixin} 读的就是它。
     * 标签文件在 {@code data/dawnduskterminal/tags/block/crafting_tables.json}，原版工作台也在里面
     */
    public static final TagKey<Block> CRAFTING_TABLES =
        TagKey.create(Registries.BLOCK, DawnDuskTerminal.id("crafting_tables"));

    /**
     * 试验物品 019 摆下来的样子：一丛植物，行为在 {@link Experiment019Block}
     *
     * <p>属性照原版花与草那一套：{@code PLANT} 地图色、{@code instabreak}（空手一下就掉）、{@code GRASS} 音效、
     * {@code noCollission}、随机 XZ 偏移、{@code DESTROY} 推挤反应。掉自身走
     * {@code data/dawnduskterminal/loot_table/blocks/experiment_019.json}
     */
    public static final DeferredBlock<Experiment019Block> EXPERIMENT_019 = BLOCKS.registerBlock(
        "experiment_019",
        Experiment019Block::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollission()
            .instabreak()
            .sound(SoundType.GRASS)
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .pushReaction(PushReaction.DESTROY));

    /**
     * 试验物品 001：属性和工作台完全一样（{@link net.minecraft.world.level.block.Blocks#CRAFTING_TABLE}
     * 的构造参数逐字照抄），只是没有配方，只能创造栏或命令拿到
     *
     * <p>掉自身的战利品表不走 {@code ofFullCopy}，走本方块自己的
     * {@code data/dawnduskterminal/loot_table/blocks/experiment_001.json}
     */
    public static final DeferredBlock<CraftingTableBlock> EXPERIMENT_001 = BLOCKS.registerBlock(
        "experiment_001",
        CraftingTableBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .instrument(NoteBlockInstrument.BASS)
            .strength(2.5F)
            .sound(SoundType.WOOD)
            .ignitedByLava());

    /**
     * 试验物品 004：照熔炉工作的那一台
     *
     * <p>属性逐条照抄原版熔炉（{@code Blocks.FURNACE}）的注册参数：{@code mapColor(STONE)}、
     * {@code requiresCorrectToolForDrops()}、3.5 硬度、石头音效，烧着时 13 级光
     * （原版 {@code litBlockEmission(13)} 写死了 {@code BlockStateProperties.LIT}，这里用的正是
     * {@link AbstractFurnaceBlock#LIT}，所以直接写一行 lambda）
     *
     * <p>方块模型、方块实体与界面见 {@link Experiment004Block}、
     * {@code Experiment004BlockEntity}、{@code Experiment004Menu}；掉自身走
     * {@code data/dawnduskterminal/loot_table/blocks/experiment_004.json}
     */
    public static final DeferredBlock<Experiment004Block> EXPERIMENT_004 = BLOCKS.registerBlock(
        "experiment_004",
        Experiment004Block::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(3.5F)
            .sound(SoundType.STONE)
            .lightLevel(state -> state.getValue(AbstractFurnaceBlock.LIT) ? 13 : 0));

    /**
     * 试验物品 114 摆下来的那一块：整块照抄暮色森林的试验物品 115 方块
     *
     * <p>方块属性逐条照搬 115 的注册参数（{@code noLootTable}、{@code pushReaction(DESTROY)}、
     * {@code strength(0.5)}、{@code WOOL}），只有 {@code randomTicks()} 没跟：115 的随机刻只用于给
     * {@code regenerate} 那版补片，我们那版不补片而是收到红石信号响一声，所以没有随机刻要做的事
     */
    public static final DeferredBlock<Experiment114Block> EXPERIMENT_114 = BLOCKS.registerBlock(
        "experiment_114",
        Experiment114Block::new,
        BlockBehaviour.Properties.of()
            .noLootTable()
            .pushReaction(PushReaction.DESTROY)
            .sound(SoundType.WOOL)
            .strength(0.5F));

    /**
     * 试验物品 051：拆解台那台机器
     *
     * <p>方块属性逐条照版暮色森林的 {@code twilightforest:uncrafting_table} 的注册参数
     * （{@code ignitedByLava}、{@code mapColor(FIRE)}、{@code sound(WOOD)}、{@code strength(2.5)}），
     * 也一起进 {@code #minecraft:mineable/axe}。与拆解台的功能差别见
     * {@link com.dawnduskterminal.block.entity.Experiment051BlockEntity} 的类注释
     *
     * <p>掉自身走 {@code data/dawnduskterminal/loot_table/blocks/experiment_051.json}；
     * 里面 11 格的内容物由方块自己的 {@code onRemove} 撒出来
     */
    public static final DeferredBlock<Experiment051Block> EXPERIMENT_051 = BLOCKS.registerBlock(
        "experiment_051",
        Experiment051Block::new,
        BlockBehaviour.Properties.of()
            .ignitedByLava()
            .mapColor(MapColor.FIRE)
            .sound(SoundType.WOOD)
            .strength(2.5F)
            // 烧起来时发 13 级光，数字与原版熔炉的 litBlockEmission(13) 一致；
            // 原版那个辅助方法写死了 BlockStateProperties.LIT，我们用的是 active，只能自己写一行
            .lightLevel(state -> state.getValue(Experiment051Block.ACTIVE) ? 13 : 0));

    /**
     * 试验物品 052：照砷铅铁反应堆做的那颗方雷，行为在 {@link Experiment052Block}
     *
     * <p>方块属性的取值没有源 mod 可以照抄（这一件是本模组自己的设计），按「塔木机器」那一档给：
     * 3.5 硬度、石头音效、红这一档地图色，挖它要镐子（进 {@code #minecraft:mineable/pickaxe}）。
     * 光只跟着 {@code active} 走：通着电、还没炸的那 3 秒是 15 级
     *
     * <p>掉自身走 {@code data/dawnduskterminal/loot_table/blocks/experiment_052.json}
     */
    public static final DeferredBlock<Experiment052Block> EXPERIMENT_052 = BLOCKS.registerBlock(
        "experiment_052",
        Experiment052Block::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .requiresCorrectToolForDrops()
            .strength(3.5F)
            .sound(SoundType.STONE)
            .lightLevel(state -> state.getValue(Experiment052Block.ACTIVE) ? 15 : 0));

    /**
     * 试验物品 065：反建器那一档里更高级的一台，压出一张 17×17×17 的「打不出伤害」场
     *
     * <p>方块属性照 052 那一档的塔木机器给：3.5 硬度、石头音效、红这一档地图色，挖它要镐子
     * （进 {@code #minecraft:mineable/pickaxe}）。方块自己发 15 级光，贴图只有一张、直接读满亮；
     * 场有多大由 {@code world/CubeFieldMarker} 那圈立方框粒子画出来（客户端画，服务端不挂 ticker）
     *
     * <p>场没有状态，方块实体只用来登记场心，见 {@link Experiment065Block} 与
     * {@code world/Experiment065Field}；掉自身走 {@code data/dawnduskterminal/loot_table/blocks/experiment_065.json}
     */
    public static final DeferredBlock<Experiment065Block> EXPERIMENT_065 = BLOCKS.registerBlock(
        "experiment_065",
        Experiment065Block::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .requiresCorrectToolForDrops()
            .strength(3.5F)
            .sound(SoundType.STONE)
            .lightLevel(state -> 15));

    /**
     * 试验物品 069：反建器那一档里更高级的第二台，压出一张 17×17×17 的「更新不处理」场
     *
     * <p>方块属性与 065 同档（3.5 硬度、石头音效、红这一档地图色、要镐子、常亮 15 级光），
     * 地图色用 {@code TERRACOTTA_ORANGE}：比 065 那个 {@code COLOR_ORANGE} 偏棕一档，与 069 那张更暗的贴图
     * 对得上，两台摆在一起在地图上分得清
     *
     * <p>场不扫方块，服务端也不挂 ticker，方块实体只用来登记场心，见 {@link Experiment069Block} 与
     * {@code world/Experiment069Field}；场有多大由 {@code world/CubeFieldMarker} 那圈立方框粒子画出来
     * （客户端画）。贴图只有一张，方块自己发 15 级光
     *
     * <p>掉自身走 {@code data/dawnduskterminal/loot_table/blocks/experiment_069.json}
     */
    public static final DeferredBlock<Experiment069Block> EXPERIMENT_069 = BLOCKS.registerBlock(
        "experiment_069",
        Experiment069Block::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_ORANGE)
            .requiresCorrectToolForDrops()
            .strength(3.5F)
            .sound(SoundType.STONE)
            .lightLevel(state -> 15));

    /**
     * 砷铅铁火焰：试验物品 052 炸出来、碎片落地补上的那种火，行为在 {@link CarminiteFireBlock}
     *
     * <p>属性照原版火焰那一套（{@code replaceable}、{@code noCollission}、{@code instabreak}、
     * {@code WOOL} 音效、{@code DESTROY} 推挤反应），只有两条不同：光是 10 级不是 15 级，
     * 以及没有战利品表（敲掉什么都不掉，也没有对应的物品）
     */
    public static final DeferredBlock<CarminiteFireBlock> CARMINITE_FIRE = BLOCKS.registerBlock(
        "carminite_fire",
        CarminiteFireBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.FIRE)
            .replaceable()
            .noCollission()
            .instabreak()
            .lightLevel(state -> 10)
            .sound(SoundType.WOOL)
            .pushReaction(PushReaction.DESTROY)
            .noLootTable());

    private ModBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
