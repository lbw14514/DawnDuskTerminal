package com.dawnduskterminal.portal;

import com.dawnduskterminal.registry.ModDamageTypes;
import com.dawnduskterminal.registry.ModEffects;
import com.dawnduskterminal.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

public class PortalBlock extends LiquidBlock {
    /**
     * 传送后给实体留的同一刻防重入标记。玩家的跨维度传送在同一 tick 里不会被移除，包围盒覆盖的
     * 其余传送门方块还会继续走 {@code entityInside}；到达维度如果恰好也有门，会立刻被送回原维度
     */
    private static final int REENTRY_GUARD_TICKS = 1;

    public PortalBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void randomTick(
            BlockState state,
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            net.minecraft.util.RandomSource random) {
        if (com.dawnduskterminal.portal.PortalShape.countNeighbors(level, pos) == 0) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide) {
            return;
        }
        // 只认本方块刚写下的标记，不去管原版下界门留下的 300 tick 冷却
        if (entity.getPortalCooldown() == REENTRY_GUARD_TICKS) {
            return;
        }
        if (entity instanceof LivingEntity living) {
            boolean teleports = living instanceof Player
                    ? !living.hasEffect(ModEffects.STABILITY)
                    : living.hasEffect(ModEffects.STABILITY);
            if (teleports) {
                // 完整门与游泳豁免原来只在 PortalTrigger 里，但没有调用方；不接上的话
                // 单格传送门流体和游泳状态也会直接过门
                if (!PortalTrigger.shouldTeleport(living, pos)) {
                    return;
                }
                PortalTeleporter.teleportEntity(living);
                living.setPortalCooldown(REENTRY_GUARD_TICKS);
            } else if (living.tickCount % 40 == 0
                    && living.hurt(living.damageSources().source(ModDamageTypes.STABILITY), 2.0F)) {
                level.playSound(
                        null,
                        living.getX(),
                        living.getY(),
                        living.getZ(),
                        com.dawnduskterminal.registry.ModSounds.PORTAL_HURT.get(),
                        living.getSoundSource(),
                        1.0F,
                        0.8F + living.getRandom().nextFloat() * 0.4F);
            }
            return;
        }
        if (entity instanceof ItemEntity item) {
            tryIgnite(level, pos, item);
        }
    }

    public static void tryIgnite(Level level, BlockPos pos, ItemEntity item) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack stack = item.getItem();
        if (!isCatalyst(stack)) {
            return;
        }
        Entity owner = item.getOwner();
        Player player = owner instanceof Player p ? p : null;
        if (!PortalBuilder.build(serverLevel, pos)) {
            return;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            item.discard();
        } else {
            item.setItem(stack);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            awardGate(serverPlayer);
        }
        causeLightning(serverLevel, pos);
        emitParticles(serverLevel, pos);
        serverLevel.playSound(
                null,
                pos,
                com.dawnduskterminal.registry.ModSounds.PORTAL_OPEN.get(),
                net.minecraft.sounds.SoundSource.BLOCKS,
                1.0F,
                1.0F);
    }

    private static void causeLightning(ServerLevel level, BlockPos pos) {
        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        bolt.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        level.addFreshEntity(bolt);
    }

    private static void emitParticles(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.0D;
        double z = pos.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 160, 1.6D, 1.2D, 1.6D, 0.15D);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 48, 1.2D, 1.0D, 1.2D, 0.08D);
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 64, 1.0D, 0.8D, 1.0D, 0.25D);
    }

    private static void awardGate(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.advancements.AdvancementHolder holder =
                player.server.getAdvancements().get(com.dawnduskterminal.DawnDuskTerminal.id("build_gate"));
        if (holder != null) {
            player.getAdvancements().award(holder, "built");
        }
    }

    public static boolean isCatalyst(ItemStack stack) {
        return stack.is(ModItems.ENCASED_DUST.get());
    }
}
