package com.dawnduskterminal.server;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.network.ChronoLinePayload;
import com.dawnduskterminal.registry.ModFluids;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import com.dawnduskterminal.portal.PortalBlock;
import com.dawnduskterminal.portal.PortalTrigger;
import com.dawnduskterminal.progression.BossProgressTracker;
import com.dawnduskterminal.structure.StructureExclusions;
import com.dawnduskterminal.world.ChronoLineState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class DdtServerEvents {
    private static final double ITEM_SCAN_RADIUS = 12.0D;

    private DdtServerEvents() {}

    /**
     * 铁砧：创造模式玩家本来可以无视 {@code supportsEnchantment} 直接合上任意附魔，
     * 权杖每发都要消耗弹药，无限对它没有意义，所以在结果算出来之前就拦掉
     */
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!event.getLeft().is(ModItems.CARMINITE_MOLYBDENUM_REACTOR_STAFF.get())) {
            return;
        }
        for (Holder<Enchantment> enchantment : EnchantmentHelper.getEnchantmentsForCrafting(event.getRight()).keySet()) {
            if (enchantment.is(Enchantments.INFINITY)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null) {
            DawnDuskTerminal.LOGGER.warn("DawnDuskTerminal overworld is not available, the chrono line stays at its fallback value");
            return;
        }
        ChronoLineState state = ChronoLineState.get(overworld);
        ChronoLineState.setServerLine(state.line());
        DawnDuskTerminal.LOGGER.info("DawnDuskTerminal chrono line origin=({}, {}) normal=({}, {})",
            state.line().originX(), state.line().originZ(), state.line().normalX(), state.line().normalZ());
        if (net.neoforged.fml.ModList.get().isLoaded("c2me")) {
            DawnDuskTerminal.LOGGER.info(
                "DawnDuskTerminal c2me is active (bundled; external copies are deduplicated by JarJar)");
        }

        Registry<Structure> structures = event.getServer().registryAccess().registryOrThrow(Registries.STRUCTURE);
        StructureExclusions.validate(structures);

        com.dawnduskterminal.debug.ChronoParityProbe.runIfRequested(event.getServer());
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ensureLine(player.server);
            PacketDistributor.sendToPlayer(player, new ChronoLinePayload(ChronoLineState.serverLine()));
        }
    }

    private static void ensureLine(MinecraftServer server) {
        if (ChronoLineState.hasServerLine()) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        ChronoLineState.setServerLine(ChronoLineState.get(overworld).line());
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 所有玩家的 tickCount 同相位，直接取模会让全服在同一刻一起扫 12 格
        // 用实体 id 错开相位，平均开销不变，但不再形成每 10 tick 一次的尖峰
        if ((player.tickCount + player.getId()) % 10 == 0) {
            scanFuelItems(player);
        }
        if (PortalTrigger.isChrono(player)) {
            if (ChronoLineState.hasServerLine() && player.tickCount % 20 == 0) {
                double param = ChronoLineState.serverLine().param(player.getX(), player.getZ());
                com.dawnduskterminal.advancement.ModCriteria.lineParam().trigger(player, param);
            }
        }
    }

    private static void scanFuelItems(ServerPlayer player) {
        List<ItemEntity> items = player.level().getEntitiesOfClass(
            ItemEntity.class,
            player.getBoundingBox().inflate(ITEM_SCAN_RADIUS),
            item -> PortalBlock.isCatalyst(item.getItem()));
        for (ItemEntity item : items) {
            BlockPos pos = item.blockPosition();
            if (item.level().getBlockState(pos).is(Blocks.WATER)) {
                PortalBlock.tryIgnite(item.level(), pos, item);
            }
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.getKillCredit() instanceof Player killer)) {
            return;
        }
        BossProgressTracker.onBossKilled(dead, killer);
    }

    public static void onSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!SpawnRules.allow(level, event.getPos(), event.getEntityType())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    /**
     * 原版 {@code minecraft:bucket} 不能舀 portal_fluid 与 enthu_fluid
     *
     * 尝试去舀不会拾起流体：交互被取消，播工具损坏音效，销毁一个桶。这两种流体交给苍穹罐处理
     * （见 {@link com.dawnduskterminal.item.CanocanItem}）
     */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.BUCKET)) {
            return;
        }
        Level level = event.getLevel();
        BlockHitResult hit = Item.getPlayerPOVHitResult(level, event.getEntity(), ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        FluidState fluidState = level.getFluidState(pos);
        if (!fluidState.isSource()
            || (fluidState.getType() != ModFluids.PORTAL_FLUID.get()
                && fluidState.getType() != ModScorchingTwilight.SOURCE.get())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (!level.isClientSide()) {
            level.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
            stack.shrink(1);
        }
    }
}
