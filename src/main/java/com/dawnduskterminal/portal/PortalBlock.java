package com.dawnduskterminal.portal;

import com.dawnduskterminal.config.DdtConfig;
import com.dawnduskterminal.registry.ModEffects;
import com.dawnduskterminal.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
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
    public PortalBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!com.dawnduskterminal.portal.PortalShape.isCompletePortalPart(level, pos)) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            return;
        }
        com.dawnduskterminal.world.FluidReactions.handle(level, pos);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide) {
            return;
        }
        if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
            if (DdtConfig.entityDebuff()) {
                living.addEffect(new MobEffectInstance(ModEffects.PORTAL_SICKNESS, 200, 0, false, true));
            }
            return;
        }
        if (entity instanceof Player player) {
            PortalTeleporter.tickCooldown(player);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                PortalTeleporter.tick(serverPlayer);
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
        serverLevel.playSound(null, pos, com.dawnduskterminal.registry.ModSounds.PORTAL_OPEN.get(),
            net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
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
        return stack.is(ModItems.CHRONO_CORE.get());
    }
}
