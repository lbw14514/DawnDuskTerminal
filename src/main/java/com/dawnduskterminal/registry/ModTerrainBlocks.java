package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.block.CountertopBlock;
import com.dawnduskterminal.block.WroughtIronGlassBlock;
import com.dawnduskterminal.block.WroughtIronGlassTrapdoorBlock;
import com.dawnduskterminal.world.CanopyFrostLayerBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;

public final class ModTerrainBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DawnDuskTerminal.MOD_ID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DawnDuskTerminal.MOD_ID);

    public static final DeferredBlock<SnowyDirtBlock> ROSY_GRASS = BLOCKS.registerBlock(
        "rosy_grass", SnowyDirtBlock::new, grassBlock());

    public static final DeferredBlock<Block> ROSY_DIRT = BLOCKS.registerSimpleBlock(
        "rosy_dirt", dirt());

    public static final DeferredBlock<Block> CANOPY_STONE = BLOCKS.registerSimpleBlock(
        "canopy_stone", stone(MapColor.STONE, 2.5F));

    public static final DeferredBlock<Block> OCHRE = BLOCKS.registerSimpleBlock(
        "ochre_block", stone(MapColor.DIRT, 2.5F));

    public static final DeferredBlock<Block> GREENSCHIST = BLOCKS.registerSimpleBlock(
        "greenschist", stone(MapColor.QUARTZ, 2.5F));

    public static final DeferredBlock<Block> ECLOGITE = BLOCKS.registerSimpleBlock(
        "eclogite", stone(MapColor.STONE, 5.0F));

    public static final DeferredBlock<Block> CHARNOCKITE = BLOCKS.registerSimpleBlock(
        "charnockite", stone(MapColor.DEEPSLATE, 5.0F));

    public static final DeferredBlock<Block> TWILIGHT_STONE = BLOCKS.registerSimpleBlock(
        "twilight_stone", stone(MapColor.TERRACOTTA_GRAY, 2.5F));

    public static final DeferredBlock<Block> ROAD_SAND = BLOCKS.registerSimpleBlock(
        "road_sand", copied(Blocks.SAND));

    public static final DeferredBlock<Block> UNCONSCIOUS_SAND = BLOCKS.registerSimpleBlock(
        "unconscious_sand", copied(Blocks.RED_SAND));

    public static final DeferredBlock<Block> SILTSTONE = BLOCKS.registerSimpleBlock(
        "siltstone", stone(MapColor.TERRACOTTA_WHITE, 1.5F));

    // 用 HalfTransparentBlock，也就是原版冰和原版玻璃共同继承的那个类
    // （IceBlock 继承 HalfTransparentBlock，TransparentBlock 继承 HalfTransparentBlock，玻璃是
    // TransparentBlock）。它存在只为一个原因：Block.shouldRenderFace 先问
    // `state.skipRendering(neighbourState, side)`，而 HalfTransparentBlock 在邻居就是同一种方块时回答
    // 「跳过」，正好剔掉两个相邻半透明立方体之间的内部面
    //
    // 普通 Block 没别的地方能拿到这个剔除：Block.shouldRenderFace 再往下的体素形状分支只有邻居
    // `canOcclude()` 时才会走到，而半透明整方块必须是 noOcclusion（见下面 FRAGILE_CANOPY 的说明），
    // 所以两个相邻苍穹方块之间的每个面都会被画出来，一整块苍穹的内部会像幽灵网格
    public static final DeferredBlock<HalfTransparentBlock> FROZEN_CANOPY = BLOCKS.registerBlock(
        "frozen_canopy", HalfTransparentBlock::new, copied(Blocks.ICE));

    // 它的贴图（fragile_canopy.png，alpha 统一 204/255）和方块模型
    // （models/block/fragile_canopy.json，"render_type": "minecraft:translucent"）把它放进了半透明区块
    // 层，所以它必须像原版其它半透明整方块（玻璃 冰 蜂蜜 史莱姆）一样 noOcclusion：会遮挡的方块会剔除
    // 邻居的面，而没画出来的面就是一个洞，能直接看穿半透明方块
    public static final DeferredBlock<HalfTransparentBlock> FRAGILE_CANOPY = BLOCKS.registerBlock(
        "fragile_canopy", HalfTransparentBlock::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE)
            .strength(0.4F)
            .sound(SoundType.GLASS)
            .noOcclusion());

    // 不用 SnowLayerBlock 本身：原版雪层只要本格方块光照高于 11 就会自己融化掉，而本维度的海洋 含水层
    // 泉都是光照 15 的 portal_fluid，水边的雪层会全部蒸发。CanopyFrostLayerBlock 只砍掉这个融化
    public static final DeferredBlock<CanopyFrostLayerBlock> CANOPY_FROST = BLOCKS.registerBlock(
        "canopy_frost", CanopyFrostLayerBlock::new, snowLayer());

    public static final DeferredBlock<Block> CANOPY_FROST_BLOCK = BLOCKS.registerSimpleBlock(
        "canopy_frost_block", BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW)
            .strength(0.5F, 3.0F)
            .sound(SoundType.SNOW));

    public static final DeferredBlock<Block> CHAOS_STONE = BLOCKS.registerSimpleBlock(
        "chaos_stone", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .requiresCorrectToolForDrops()
            .strength(25.0F, 1200.0F)
            .sound(SoundType.STONE));

    public static final DeferredBlock<Block> BLAZING_BLOCK = BLOCKS.registerSimpleBlock(
        "blazing_block", BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .requiresCorrectToolForDrops()
            .strength(3.0F, 9.0F)
            .lightLevel(state -> 15)
            .sound(SoundType.BASALT));

    public static final DeferredItem<BlockItem> ROSY_GRASS_ITEM = ITEMS.registerSimpleBlockItem(ROSY_GRASS);
    public static final DeferredItem<BlockItem> ROSY_DIRT_ITEM = ITEMS.registerSimpleBlockItem(ROSY_DIRT);
    public static final DeferredItem<BlockItem> CANOPY_STONE_ITEM = ITEMS.registerSimpleBlockItem(CANOPY_STONE);
    public static final DeferredItem<BlockItem> OCHRE_ITEM = ITEMS.registerSimpleBlockItem(OCHRE);
    public static final DeferredItem<BlockItem> GREENSCHIST_ITEM = ITEMS.registerSimpleBlockItem(GREENSCHIST);
    public static final DeferredItem<BlockItem> ECLOGITE_ITEM = ITEMS.registerSimpleBlockItem(ECLOGITE);
    public static final DeferredItem<BlockItem> CHARNOCKITE_ITEM = ITEMS.registerSimpleBlockItem(CHARNOCKITE);
    public static final DeferredItem<BlockItem> TWILIGHT_STONE_ITEM = ITEMS.registerSimpleBlockItem(TWILIGHT_STONE);
    public static final DeferredItem<BlockItem> ROAD_SAND_ITEM = ITEMS.registerSimpleBlockItem(ROAD_SAND);
    public static final DeferredItem<BlockItem> UNCONSCIOUS_SAND_ITEM = ITEMS.registerSimpleBlockItem(UNCONSCIOUS_SAND);
    public static final DeferredItem<BlockItem> SILTSTONE_ITEM = ITEMS.registerSimpleBlockItem(SILTSTONE);
    public static final DeferredItem<BlockItem> FROZEN_CANOPY_ITEM = ITEMS.registerSimpleBlockItem(FROZEN_CANOPY);
    public static final DeferredItem<BlockItem> FRAGILE_CANOPY_ITEM = ITEMS.registerSimpleBlockItem(FRAGILE_CANOPY);
    public static final DeferredItem<BlockItem> CANOPY_FROST_ITEM = ITEMS.registerSimpleBlockItem(CANOPY_FROST);
    public static final DeferredItem<BlockItem> CANOPY_FROST_BLOCK_ITEM =
        ITEMS.registerSimpleBlockItem(CANOPY_FROST_BLOCK);
    public static final DeferredItem<BlockItem> CHAOS_STONE_ITEM = ITEMS.register(
        "chaos_stone",
        () -> new BlockItem(CHAOS_STONE.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                tooltip.add(Component.translatable("item.dawnduskterminal.chaos_stone.lore").withStyle(ChatFormatting.GRAY));
            }
        });
    public static final DeferredItem<BlockItem> BLAZING_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(BLAZING_BLOCK);

    // 暮色森林塔木镶边。实心整方块，连接纹理边框由 MountedTowerWoodModel 在客户端绘制，方块本身是普通
    // Block。硬度 3，爆炸抗性 9，徒手可挖（掉落自身），挂进 mineable/axe 标签以便用斧更快
    public static final DeferredBlock<Block> MOUNTED_TOWERWOOD = BLOCKS.registerSimpleBlock(
        "mounted_towerwood", BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(3.0F, 9.0F)
            .sound(SoundType.WOOD));

    public static final DeferredItem<BlockItem> MOUNTED_TOWERWOOD_ITEM =
        ITEMS.registerSimpleBlockItem(MOUNTED_TOWERWOOD);

    // 暮色森林塔木梁。连接纹理与 mounted_towerwood 一样用 47 格图集
    public static final DeferredBlock<Block> ENCASED_TOWERBEAM = BLOCKS.registerSimpleBlock(
        "encased_towerbeam", BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(3.0F, 9.0F)
            .sound(SoundType.WOOD));

    public static final DeferredItem<BlockItem> ENCASED_TOWERBEAM_ITEM =
        ITEMS.registerSimpleBlockItem(ENCASED_TOWERBEAM);

    // 錾制塔木梁与警示塔木。跟封装塔木梁一个路子：整方块、六个面全走 47 格图集，只是各换一张图集，所以依旧套
    // MountedTowerWoodModel；图集互不相干，故两者都只跟同种方块接
    public static final DeferredBlock<Block> CHISELED_TOWERBEAM = BLOCKS.registerSimpleBlock(
        "chiseled_towerbeam", BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(3.0F, 9.0F)
            .sound(SoundType.WOOD));

    public static final DeferredItem<BlockItem> CHISELED_TOWERBEAM_ITEM =
        ITEMS.registerSimpleBlockItem(CHISELED_TOWERBEAM);

    public static final DeferredBlock<Block> WARNING_TOWERWOOD = BLOCKS.registerSimpleBlock(
        "warning_towerwood", BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(3.0F, 9.0F)
            .sound(SoundType.WOOD));

    public static final DeferredItem<BlockItem> WARNING_TOWERWOOD_ITEM =
        ITEMS.registerSimpleBlockItem(WARNING_TOWERWOOD);

    // 黑木台面与去皮黑木台面。两块共用同一张顶面：顶面贴的是封装塔木梁那张 8x8 图集，连接纹理由
    // CountertopModel 在客户端按「邻居是不是台面」裁格，方块本身不存连接状态；侧面与底面各是两张
    // 16x16 单图。属性同原木一档：硬度 2、木头音效、徒手可挖（掉落自身），挂进 mineable/axe 以便用斧更快
    public static final DeferredBlock<CountertopBlock> DARK_LOG_COUNTERTOP = BLOCKS.registerBlock(
        "dark_log_countertop", CountertopBlock::new, countertop());

    public static final DeferredBlock<CountertopBlock> STRIPPED_DARK_LOG_COUNTERTOP = BLOCKS.registerBlock(
        "stripped_dark_log_countertop", CountertopBlock::new, countertop());

    public static final DeferredItem<BlockItem> DARK_LOG_COUNTERTOP_ITEM =
        ITEMS.registerSimpleBlockItem(DARK_LOG_COUNTERTOP);
    public static final DeferredItem<BlockItem> STRIPPED_DARK_LOG_COUNTERTOP_ITEM =
        ITEMS.registerSimpleBlockItem(STRIPPED_DARK_LOG_COUNTERTOP);

    // 锻铁玻璃。是一种玻璃，所以方块类直接用原版玻璃那一支（TransparentBlock：视觉形状为空、天光直穿、
    // 亮度按满亮给），属性逐条照抄原版 Blocks.GLASS 的注册参数——0.3 硬度、GLASS 音效、HAT 乐器，
    // 四条 never 分别关掉刷怪 红石导通 窒息 遮挡视线，noOcclusion 则是半透明整方块的硬要求
    // （会遮挡的方块会剔除邻居的面，没画出来的面就是一个洞）
    //
    // 竖直方向的连接纹理由 WroughtIronGlassModel 在客户端画，方块本身不存连接状态；
    // 同种方块之间的内部面由 HalfTransparentBlock 的 skipRendering 剔掉，柱子只留最外一圈
    public static final DeferredBlock<TransparentBlock> WROUGHT_IRON_GLASS = BLOCKS.registerBlock(
        "wrought_iron_glass",
        WroughtIronGlassBlock::new,
        BlockBehaviour.Properties.of()
            .instrument(NoteBlockInstrument.HAT)
            .strength(0.3F)
            .sound(SoundType.GLASS)
            .noOcclusion()
            .isValidSpawn(Blocks::never)
            // 原版玻璃这三条写的是 Blocks::never，但 3 参的那个 never 在 Blocks 里是 private，
            // 只有 4 参的刷怪版本是 public，所以红石导通 窒息 遮挡视线三条只能自己写 lambda
            .isRedstoneConductor((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false));

    public static final DeferredItem<BlockItem> WROUGHT_IRON_GLASS_ITEM =
        ITEMS.registerSimpleBlockItem(WROUGHT_IRON_GLASS);

    // 锻铁玻璃的活板门。状态与开合逻辑全走原版 TrapDoorBlock（facing/half/open/powered/waterlogged），
    // 方块组是 BlockSetType.IRON，所以空手点不开、只吃红石，开合声是铁那一档；
    // 方块属性照上一件玻璃给：0.3 硬度、GLASS 音效、HAT 乐器、三条 never 与 noOcclusion。
    // 纹理分两套：两块 16x16 的大面用整张九宫图（由 WroughtIronGlassModel 按竖直连接裁格），
    // 四条薄边用拆出来的单面图 block/wrought_iron_glass_single 最前几行像素，见
    // WroughtIronGlassTrapdoorBlock 的类注释
    public static final DeferredBlock<WroughtIronGlassTrapdoorBlock> WROUGHT_IRON_GLASS_TRAPDOOR =
        BLOCKS.registerBlock(
            "wrought_iron_glass_trapdoor",
            WroughtIronGlassTrapdoorBlock::new,
            BlockBehaviour.Properties.of()
                .instrument(NoteBlockInstrument.HAT)
                .strength(0.3F)
                .sound(SoundType.GLASS)
                .noOcclusion()
                .isValidSpawn(Blocks::never)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false));

    public static final DeferredItem<BlockItem> WROUGHT_IRON_GLASS_TRAPDOOR_ITEM =
        ITEMS.registerSimpleBlockItem(WROUGHT_IRON_GLASS_TRAPDOOR);

    public static final DeferredBlock<Block> SCARLET_MOLYBDENUM_ORE = BLOCKS.registerSimpleBlock(
        "scarlet_molybdenum_ore", stone(MapColor.COLOR_RED, 2.5F));

    public static final DeferredItem<BlockItem> SCARLET_MOLYBDENUM_ORE_ITEM =
        ITEMS.registerSimpleBlockItem(SCARLET_MOLYBDENUM_ORE);

    public static final DeferredBlock<RotatedPillarBlock> SKYGATE_PINE_LOG =
        BLOCKS.registerBlock("skygate_pine_log", RotatedPillarBlock::new, log(MapColor.COLOR_GREEN));

    // 去皮原木。方块本身只换了贴图，原木到去皮原木的映射不写在代码里：NeoForge 用数据包
    // data/neoforge/data_maps/block/strippables.json 顶掉了原版 AxeItem 的静态表
    // （NeoForgeDataMaps.STRIPPABLES），所以这里既不用 mixin 也不用反射去改 AxeItem。
    // 注意路径的命名空间必须是 neoforge 而不是 dawnduskterminal：加载器按「命名空间:文件名」找数据图类型，
    // 写成 dawnduskterminal 会去找一个叫 dawnduskterminal:strippables 的不存在类型然后静默跳过
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_SKYGATE_PINE_LOG =
        BLOCKS.registerBlock("stripped_skygate_pine_log", RotatedPillarBlock::new, log(MapColor.COLOR_GREEN));

    public static final DeferredBlock<LeavesBlock> SKYGATE_PINE_LEAVES =
        BLOCKS.registerBlock("skygate_pine_leaves", LeavesBlock::new, leaves());

    public static final DeferredBlock<SaplingBlock> SKYGATE_PINE_SAPLING = BLOCKS.registerBlock(
        "skygate_pine_sapling",
        properties -> new SaplingBlock(new TreeGrower(
            "dawnduskterminal:skygate_pine",
            Optional.empty(),
            Optional.of(ResourceKey.create(Registries.CONFIGURED_FEATURE, DawnDuskTerminal.id("tree/skygate_pine"))),
            Optional.empty()
        ), properties),
        sapling());

    public static final DeferredItem<BlockItem> SKYGATE_PINE_LOG_ITEM =
        ITEMS.registerSimpleBlockItem(SKYGATE_PINE_LOG);
    public static final DeferredItem<BlockItem> STRIPPED_SKYGATE_PINE_LOG_ITEM =
        ITEMS.registerSimpleBlockItem(STRIPPED_SKYGATE_PINE_LOG);
    public static final DeferredItem<BlockItem> SKYGATE_PINE_LEAVES_ITEM =
        ITEMS.registerSimpleBlockItem(SKYGATE_PINE_LEAVES);
    public static final DeferredItem<BlockItem> SKYGATE_PINE_SAPLING_ITEM =
        ITEMS.registerSimpleBlockItem(SKYGATE_PINE_SAPLING);

    private ModTerrainBlocks() {}


    /**
     * 原样复用某个原版方块的 {@link BlockBehaviour.Properties}，让晨昏线方块在维度里替换谁就像谁
     *
     * <p>注意 {@code ofFullCopy} 会连源方块的战利品表引用一起带过来。如果源方块构建时没调过
     * {@code noLootTable()} 或 {@code dropsLike(...)}，那个引用就是 {@code null}，副本会回退到本方块
     * 自己的 {@code data/dawnduskterminal/loot_table/blocks/<id>.json}，这个文件本模组随包提供
     */
    private static BlockBehaviour.Properties copied(BlockBehaviour source) {
        return BlockBehaviour.Properties.ofFullCopy(source);
    }

    /** 石头类方块的共同属性：全部用锡、爆炸抗性 9、石头音效，只有硬度与地图色不同 */
    private static BlockBehaviour.Properties stone(MapColor color, float hardness) {
        return BlockBehaviour.Properties.of()
            .mapColor(color)
            .requiresCorrectToolForDrops()
            .strength(hardness, 9.0F)
            .sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties log(MapColor color) {
        return BlockBehaviour.Properties.of()
            .mapColor(color)
            .strength(2.0F)
            .sound(SoundType.WOOD);
    }

    /** 台面的属性：跟原木一档，徒手可挖、吃斧更快 */
    private static BlockBehaviour.Properties countertop() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(2.0F)
            .sound(SoundType.WOOD);
    }

    private static BlockBehaviour.Properties leaves() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .strength(0.2F)
            .randomTicks()
            .sound(SoundType.GRASS)
            .noOcclusion()
            .ignitedByLava()
            .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties sapling() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollission()
            .randomTicks()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY);
    }

    // 原版 Blocks.GRASS_BLOCK 的属性，逐字照拄 1.21.1
    // 行为来自 SnowyDirtBlock（原版 PODZOL 用的那个类）：snowy 状态加放置/更新形状，不带原版
    // GrassBlock/SpreadingSnowyDirtBlock 那些写死逻辑（它会退化成 Blocks.DIRT 并传播
    // Blocks.GRASS_BLOCK）
    private static BlockBehaviour.Properties grassBlock() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.GRASS)
            .randomTicks()
            .strength(0.6F)
            .sound(SoundType.GRASS);
    }

    // 原版 Blocks.DIRT 的属性，逐字照拄 1.21.1
    private static BlockBehaviour.Properties dirt() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIRT)
            .strength(0.5F)
            .sound(SoundType.GRAVEL);
    }

    // 原版 Blocks.SNOW（net.minecraft.world.level.block.SnowLayerBlock）的属性，逐字照拄 1.21.1
    private static BlockBehaviour.Properties snowLayer() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW)
            .replaceable()
            .forceSolidOff()
            .randomTicks()
            .strength(0.1F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.SNOW)
            .isViewBlocking((state, level, pos) -> state.getValue(SnowLayerBlock.LAYERS) >= 8)
            .pushReaction(PushReaction.DESTROY);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
