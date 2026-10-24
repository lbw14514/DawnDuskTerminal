package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.model.MountedTowerWoodModel;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModFluids;
import com.dawnduskterminal.registry.ModMenus;
import com.dawnduskterminal.registry.ModParticles;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import com.dawnduskterminal.world.ChronoLineState;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.GrassColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

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
        modBus.addListener(DdtClient::registerBlockColors);
        modBus.addListener(DdtClient::registerItemColors);
        modBus.addListener(DdtClient::onModifyBakingResult);
        modBus.addListener(TowerGhastRenderer::onRegisterRenderers);
        modBus.addListener(DdtClient::onRegisterEntityRenderers);
        modBus.addListener(DdtClient::onRegisterParticleProviders);
        modBus.addListener(DdtClient::registerMenuScreens);
    }

    /**
     * 试验物品 051 的界面：菜单类型在 {@code ModMenus}，屏幕在这里挂上去
     *
     * <p>只有客户端才认这个事件，所以挂在 {@link #init} 里——那一层已经由主类按 {@code FMLEnvironment.dist}
     * 挡住，服务端不会走到这里
     */
    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.EXPERIMENT_004.get(), Experiment004Screen::new);
        event.register(ModMenus.EXPERIMENT_051.get(), Experiment051Screen::new);
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        wrapCtm(event, "mounted_towerwood");
        wrapCtm(event, "encased_towerbeam");
    }

    private static void wrapCtm(ModelEvent.ModifyBakingResult event, String name) {
        ModelResourceLocation blockLoc = new ModelResourceLocation(DawnDuskTerminal.id(name), "");
        BakedModel original = event.getModels().get(blockLoc);
        if (original != null) {
            event.getModels().put(blockLoc, new MountedTowerWoodModel(original));
        }
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
     */
    private static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CARMINITE_BALL.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.CARMINITE_FIELD.get(), NoopRenderer::new);
        // 原版画的渲染器认的是 Painting 而不是具体实体类型，3x2 的贴图切分、按面光源着色、背板
        // 全是它算的，直接复用；它从 PaintingTextureManager（atlas minecraft:paintings）取
        // asset_id 对应的 sprite，所以贴图必须放 textures/painting/ 下
        event.registerEntityRenderer(ModEntities.GHAST_PAINTING_1.get(), PaintingRenderer::new);
    }

    /** 三种粒子共用一支实现，只差参数，见 {@link CarminiteParticle}；027 的红石点单独一支，见 {@link Experiment027Particle} */
    private static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.CARMINITE_BALL.get(), CarminiteParticle::ballProvider);
        event.registerSpriteSet(ModParticles.CARMINITE_TRACE.get(), CarminiteParticle::traceProvider);
        event.registerSpriteSet(ModParticles.CARMINITE_EFFECT.get(), CarminiteParticle::effectProvider);
        event.registerSpriteSet(ModParticles.EXPERIMENT_027.get(), Experiment027Particle::provider);
    }

    private static void registerFluidExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(PortalFluidExtensions.INSTANCE, ModFluids.PORTAL_FLUID_TYPE.get());
        event.registerFluidType(ScorchingTwilightFluidExtensions.INSTANCE,
            ModScorchingTwilight.TYPE.get());
    }
}
