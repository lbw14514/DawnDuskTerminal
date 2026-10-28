package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.model.CountertopModel;
import com.dawnduskterminal.client.model.ExperimentArmorModel;
import com.dawnduskterminal.client.model.MountedTowerWoodModel;
import com.dawnduskterminal.client.model.ScarletMolybdenumArmorModel;
import com.dawnduskterminal.client.model.WroughtIronGlassModel;
import com.dawnduskterminal.registry.ModBlockEntities;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModExperimentItems;
import com.dawnduskterminal.registry.ModFluids;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModMenus;
import com.dawnduskterminal.registry.ModParticles;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.client.model.HumanoidModel;
import com.dawnduskterminal.world.ChronoLineState;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.GrassColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

public final class DdtClient {
    private DdtClient() {}

    public static void init(IEventBus modBus) {
        // 只在断开连接时清：换维度仍属同一存档 群系注册表与晨昏线都没变 清了反而会短暂退回兜底值
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ChronoFoliageColors.clearCache();
            ChronoLineState.resetClientLine();
        });
        modBus.addListener(DdtClient::onClientSetup);
        modBus.addListener(DdtClient::registerFluidExtensions);
        modBus.addListener(DdtClient::registerItemExtensions);
        modBus.addListener(ScarletMolybdenumArmorModel::registerLayerDefinitions);
        modBus.addListener(ExperimentArmorModel::registerLayerDefinitions);
        modBus.addListener(DdtClient::registerRenderBuffers);
        modBus.addListener(DdtClient::registerItemProperties);
        modBus.addListener(DdtClient::registerBlockColors);
        modBus.addListener(DdtClient::registerItemColors);
        modBus.addListener(DdtClient::onModifyBakingResult);
        modBus.addListener(TowerGhastRenderer::onRegisterRenderers);
        modBus.addListener(StringRenderer::onRegisterRenderers);
        modBus.addListener(DdtClient::onRegisterEntityRenderers);
        modBus.addListener(DdtClient::onRegisterParticleProviders);
        modBus.addListener(DdtClient::registerMenuScreens);
    }

    /**
     * 绯色钼护甲的穿戴模型
     *
     * <p>四个槽位的专用几何在 {@link ScarletMolybdenumArmorModel}，贴图用
     * {@code ScarletMolybdArmorItem#getArmorTexture} 指到的 {@code textures/entities/carmarmort1.png}；
     * 这里只把「哪件物品换哪套模型」挂上去
     */
    private static final IClientItemExtensions SCARLET_MOLYBDENUM_ARMOR = new IClientItemExtensions() {
        @Override
        public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                                                      EquipmentSlot slot, HumanoidModel<?> original) {
            return ScarletMolybdenumArmorModel.model(slot);
        }
    };

    /**
     * 试验物品 047~050 那套护甲的穿戴模型：四个槽位的几何在 {@link ExperimentArmorModel}，
     * 贴图由 {@code ExperimentArmorItem#getArmorTexture} 指到 {@code textures/entities/experiment_armor.png}；
     * 这里同样只挂「哪件物品换哪套模型」
     */
    private static final IClientItemExtensions EXPERIMENT_ARMOR = new IClientItemExtensions() {
        @Override
        public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                                                      EquipmentSlot slot, HumanoidModel<?> original) {
            return ExperimentArmorModel.model(slot);
        }
    };

    /**
     * 试验物品 045 的盾面渲染器：原版盾的物品渲染写死在 {@code BlockEntityWithoutLevelRenderer} 里，
     * 判据是 {@code stack.is(Items.SHIELD)}，别的盾一律走不到那条分支；模型挂 {@code builtin/entity}
     * 之后再从这里取自定义渲染器，才是原版给模组留的口子（{@code IClientItemExtensions#getCustomRenderer}）
     *
     * <p>渲染器懒创建：事件触发时 {@code Minecraft} 实例在，但第一帧之前没必要先烘一遍盾模型
     *
     * <p>绯色钼护甲也在这个事件里挂：护甲层通过 {@code IClientItemExtensions#getGenericArmorModel}
     * 取每件物品的穿戴模型，与物品渲染器是同一张扩展表
     */
    private static void registerItemExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Nullable
            private Experiment045ShieldRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new Experiment045ShieldRenderer();
                }
                return renderer;
            }
        }, ModExperimentItems.EXPERIMENT_045.get());
        // 试验物品 076 的物品图标与手持：静态烘焙模型换不了贴图，整个交给 BEWLR 每帧现画，
        // 见 Experiment076ItemRenderer；世界里的方块那边是 Experiment076Renderer
        event.registerItem(new IClientItemExtensions() {
            @Nullable
            private Experiment076ItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new Experiment076ItemRenderer();
                }
                return renderer;
            }
        }, ModItems.EXPERIMENT_076.get());
        event.registerItem(SCARLET_MOLYBDENUM_ARMOR,
            ModItems.SCARLET_MOLYBD_HELMET.get(),
            ModItems.SCARLET_MOLYBD_CHESTPLATE.get(),
            ModItems.SCARLET_MOLYBD_LEGGINGS.get(),
            ModItems.SCARLET_MOLYBD_BOOTS.get());
        event.registerItem(EXPERIMENT_ARMOR,
            ModExperimentItems.EXPERIMENT_047.get(),
            ModExperimentItems.EXPERIMENT_048.get(),
            ModExperimentItems.EXPERIMENT_049.get(),
            ModExperimentItems.EXPERIMENT_050.get());
    }

    /**
     * 盾面发光层单独占一条固定渲染缓冲
     *
     * <p>固定缓冲按注册顺序冲刷，原版那些（{@code entitySolid}、{@code entityCutout}、shield 图集……）都在
     * 这个事件之前登记好，所以这里加进来的类型排在最后、最后冲刷——木盾本体与旗面图案先落地，发光层再叠上去
     *
     * <p>不注册的话它会掉进共享缓冲，在下一类几何切进来时就被冲刷，反而排在本体之前、被本体盖掉
     */
    private static void registerRenderBuffers(RegisterRenderBuffersEvent event) {
        event.registerRenderBuffer(Experiment045ShieldRenderer.GLOW_LAYER);
    }

    /**
     * 举盾姿势的物品属性
     *
     * <p>原版只把 {@code blocking} 谓词注册给 {@code Items.SHIELD}（{@code ItemProperties} 的静态块），
     * 别的物品模型里写这个谓词永远命中不了，举盾时的第一/第三人称变换也就换不过去。判据照抄原版：
     * 正在用、并且用的就是这一栈
     *
     * <p>属性表是静态的，必须在客户端 setup 里补，不能放到注册期——那时 {@code ItemProperties} 还没意义
     */
    private static void registerItemProperties(FMLClientSetupEvent event) {
        ItemProperties.register(ModExperimentItems.EXPERIMENT_045.get(),
            ResourceLocation.withDefaultNamespace("blocking"),
            (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                ? 1.0F : 0.0F);
    }

    /**
     * 试验物品 051 的界面与 115 再生机的界面：菜单类型在 {@code ModMenus}，屏幕在这里挂上去
     *
     * <p>只有客户端才认这个事件，所以挂在 {@link #init} 里——那一层已经由主类按 {@code FMLEnvironment.dist}
     * 挡住，服务端不会走到这里
     */
    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.EXPERIMENT_004.get(), Experiment004Screen::new);
        event.register(ModMenus.EXPERIMENT_051.get(), Experiment051Screen::new);
        event.register(ModMenus.E_115_REGENERATOR.get(), E115RegeneratorScreen::new);
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        wrapCtm(event, "mounted_towerwood");
        wrapCtm(event, "encased_towerbeam");
        wrapCtm(event, "chiseled_towerbeam");
        wrapCtm(event, "warning_towerwood");
        wrapCountertop(event, "dark_log_countertop");
        wrapCountertop(event, "stripped_dark_log_countertop");
        wrapGlassCtm(event, "wrought_iron_glass");
        wrapGlassTrapdoorCtm(event, "wrought_iron_glass_trapdoor");
    }

    private static void wrapCtm(ModelEvent.ModifyBakingResult event, String name) {
        ModelResourceLocation blockLoc = new ModelResourceLocation(DawnDuskTerminal.id(name), "");
        BakedModel original = event.getModels().get(blockLoc);
        if (original != null) {
            event.getModels().put(blockLoc, new MountedTowerWoodModel(original));
        }
    }

    /**
     * 黑木台面的顶面连接纹理：只换方块那一处，键是 {@code #}（无变体）
     *
     * <p>物品图标不走这条：{@code models/item/<id>.json} 是它自己的 {@code cube_bottom_top}，顶面贴的是
     * 16x16 的单图 {@code block/countertop_top}，没有 8x8 图集可裁
     */
    private static void wrapCountertop(ModelEvent.ModifyBakingResult event, String name) {
        ModelResourceLocation blockLoc = new ModelResourceLocation(DawnDuskTerminal.id(name), "");
        BakedModel original = event.getModels().get(blockLoc);
        if (original != null) {
            event.getModels().put(blockLoc, new CountertopModel(original));
        }
    }

    /**
     * 「锻铁玻璃」竖直连接纹理的模型替换：只换方块那一处，键是 {@code #}（无变体）
     *
     * <p>物品图标不走这条：{@code models/item/wrought_iron_glass.json} 是它自己的
     * {@code cube_bottom_top}，上下贴拆出来的 {@code block/wrought_iron_glass_top}、
     * 四面贴 {@code block/wrought_iron_glass_particle}，<b>完全不碰 48x48 九宫图</b>，
     * 所以这里也不能再去包 {@code #inventory}——那份模型没有九宫格可裁
     *
     * <p>包装器本身按「这个面是哪一面」选格子：上下两面取顶/底那一格、四个侧面按上下邻居接段
     */
    private static void wrapGlassCtm(ModelEvent.ModifyBakingResult event, String name) {
        ModelResourceLocation blockLoc = new ModelResourceLocation(DawnDuskTerminal.id(name), "");
        BakedModel original = event.getModels().get(blockLoc);
        if (original != null) {
            event.getModels().put(blockLoc, new WroughtIronGlassModel(original));
        }
    }

    /**
     * 锻铁玻璃活板门的模型替换：按 id 把<b>所有</b>键都包一遍
     *
     * <p>活板门是按 blockstate 变体烘的（{@code facing} × {@code half} × {@code open} 共 16 个键，
     * 形如 {@code wrought_iron_glass_trapdoor#facing=north,open=false,half=bottom}），不像锻铁玻璃那样只有
     * 一个空变体，所以不能拿固定键去取，得把 id 对得上的键全换掉——物品那个 {@code #inventory} 也在其中
     *
     * <p>规则与方块同一个：两块 16x16 的大面用九宫图，由包装器按「这个面是哪一面」裁格
     * （关着时那两块是顶底面 → 顶/底那一格；打开时是正反面 → 侧面那一格，再按上下邻居接段），
     * 四条薄边贴的是单面图 {@code block/wrought_iron_glass_particle}，包装器按 sprite 名认出来、不裁格
     *
     * <p>物品图标不在这条链上：{@code models/item/wrought_iron_glass_trapdoor.json} 是它自己的薄板模型，
     * 大面贴拆出来的 {@code block/wrought_iron_glass_top}、薄边贴 {@code block/wrought_iron_glass_particle}，
     * 一个像素都不来自 48x48 九宫图
     */
    private static void wrapGlassTrapdoorCtm(ModelEvent.ModifyBakingResult event, String name) {
        ResourceLocation id = DawnDuskTerminal.id(name);
        // 物品图标那一份（{@code #inventory}）不包：它自己贴的是拆出来的两张 16x16，没有九宫格可裁
        event.getModels().replaceAll((location, model) ->
            location.id().equals(id) && !location.variant().equals(ModelResourceLocation.INVENTORY_VARIANT)
                ? new WroughtIronGlassModel(model)
                : model);
    }




    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return -1;
            }
            if (level != null && pos != null) {
                return BiomeColors.getAverageFoliageColor(level, pos);
            }
            return 0x48B518;
        }, ModTerrainBlocks.SKYGATE_PINE_LEAVES.get());
        // 原版按方块逐个注册草色（BlockColors.createDefault 里的 GRASS_BLOCK、FERN 等），模组方块完全不
        // 上色，tintindex 0 的面会直接渲染成灰白贴图。这里用与原版相同的取色器，包含物品渲染器用的
        // level 为空时的兜底
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return -1;
            }
            return level != null && pos != null
                ? BiomeColors.getAverageGrassColor(level, pos)
                : GrassColor.getDefaultColor();
        }, ModTerrainBlocks.ROSY_GRASS.get());
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // 原版 ItemColors 会以 level 为空把方块物品的颜色转回方块取色器（ItemColors.createDefault），
        // 缺了这条注册，模组草方块在背包和手里就是灰的。物品渲染的 level 恒为空，所以这里落到默认草色
        event.register((stack, tintIndex) -> tintIndex == 0
            ? GrassColor.getDefaultColor()
            : -1,
            ModTerrainBlocks.ROSY_GRASS_ITEM.get());
        // 树叶在背包和手里渲染时 level 为空，所以落到默认树叶色（与上面方块取色器用的 0x48B518 兜底一致）
        event.register((stack, tintIndex) -> tintIndex == 0 ? 0x48B518 : -1,
            ModTerrainBlocks.SKYGATE_PINE_LEAVES_ITEM.get());
        // 药水瓶的紫色不来自贴图：瓶子模型 {@code item/potion} 的 layer0 是那张白色液体遮罩 potion_overlay，
        // 颜色靠物品取色器往 tintindex 0 上刷。原版 ItemColors.createDefault 只给 Items.POTION /
        // SPLASH_POTION / LINGERING_POTION / TIPPED_ARROW 注册了这条（按 potion_contents 取色），
        // 模组药水瓶不在那张表里，不补就渲染成纯白
        event.register((stack, tintIndex) -> tintIndex != 0
                ? -1
                : FastColor.ARGB32.opaque(
                    stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor()),
            ModItems.STABILITY_POTION.get());
    }

    /**
     * 传送门流体的流动参数已经照抄了原版水（见 {@link ModFluids}），
     * 这里是同一套照抄的渲染侧
     *
     * <p>原版把 {@code Fluids.WATER} 与 {@code Fluids.FLOWING_WATER} 以 {@link RenderType#translucent()}
     * 放进 {@code ItemBlockRenderTypes.TYPE_BY_FLUID}（在 {@code ItemBlockRenderTypes} 的静态初始化里），
     * {@code SectionCompiler} 再查这张表决定流体表面属于哪个区块渲染层
     *
     * <p>不注册就会落到实体层，而实体层的着色器忽略 alpha 通道：{@code portal_fluid_still.png} 与
     * {@code portal_fluid_flow.png} 的 alpha 统一是 230/255，这正是它们看起来半透明的原因，落到实体层
     * 就会完全不透明
     *
     * <p>{@code setRenderLayer(Fluid, RenderType)} 只在客户端模组加载期（{@code ClientModLoader.isLoading()}）
     * 允许调用，所以这里是客户端 setup 监听，而不是更晚的客户端事件
     */
    private static void onClientSetup(FMLClientSetupEvent event) {
        ItemBlockRenderTypes.setRenderLayer(ModFluids.PORTAL_FLUID.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(ModFluids.PORTAL_FLOWING.get(), RenderType.translucent());
        // 加载 native 并跑启动探针 不要在第一次渲染高塔恶魂时才做文件 IO
        NativeMesh.preload();
    }

    /**
     * 权杖弹体用原版的投掷物渲染器：它会把物品（也就是那个球）摆成朝镜头的面片，正是需要的效果。
     * 源 mod 为此写了模型类与实体渲染器，这里不需要
     *
     * <p>反应场没有模型，但必须注册渲染器：{@code EntityRenderDispatcher} 对没注册的实体类型
     * 拿到 null 后直接空指针崩客户端
     *
     * <p>原版画的渲染器认的是 Painting 而不是具体实体类型，3x2 的贴图切分、按面光源着色、背板
     * 全是它算的，直接复用；它从 PaintingTextureManager（atlas minecraft:paintings）取
     * asset_id 对应的 sprite，所以贴图必须放 textures/painting/ 下
     */
    private static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CARMINITE_BALL.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.CARMINITE_FIELD.get(), NoopRenderer::new);
        // 试验物品 052 的碎片不画贴图，外观只有每 tick 那几颗红石粒子，见 CarminiteShrapnelEntity
        event.registerEntityRenderer(ModEntities.CARMINITE_SHRAPNEL.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.GHAST_PAINTING_1.get(), PaintingRenderer::new);
        // 试验物品 076 的外观整个在这一边：每个面每 tick 抽一张贴图，见 Experiment076Renderer
        event.registerBlockEntityRenderer(ModBlockEntities.EXPERIMENT_076.get(), Experiment076Renderer::new);
    }

    /** 三种粒子共用一支实现，只差参数，见 {@link CarminiteParticle}；027 的红石点与 043 的大火焰各一支 */
    private static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.CARMINITE_BALL.get(), CarminiteParticle::ballProvider);
        event.registerSpriteSet(ModParticles.CARMINITE_TRACE.get(), CarminiteParticle::traceProvider);
        event.registerSpriteSet(ModParticles.CARMINITE_EFFECT.get(), CarminiteParticle::effectProvider);
        // 爆炸专用：同一套贴图、更慢的帧序，见 CarminiteParticle#blastProvider
        event.registerSpriteSet(ModParticles.CARMINITE_BLAST.get(), CarminiteParticle::blastProvider);
        event.registerSpriteSet(ModParticles.EXPERIMENT_027.get(), Experiment027Particle::provider);
        // 砷铅铁余烬：火焰向上飘的星火与碎片拖的红线，见 CarminiteEmberParticle
        event.registerSpriteSet(ModParticles.CARMINITE_EMBER.get(), CarminiteEmberParticle::provider);
        event.registerSpriteSet(ModParticles.JET_FLAME.get(), JetFlameParticle::provider);
    }

    private static void registerFluidExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(PortalFluidExtensions.INSTANCE, ModFluids.PORTAL_FLUID_TYPE.get());
        event.registerFluidType(ScorchingTwilightFluidExtensions.INSTANCE,
            ModScorchingTwilight.TYPE.get());
    }
}
