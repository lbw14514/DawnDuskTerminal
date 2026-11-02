package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 狄拉克石是高硬度陷阱方块：用低于钻石级的镐挖不会掉落，而是把矿石「传送」走——末影人传送音效、传送门
 * 粒子，方块原地变成传送门流体
 */
public class DiracOreBlock extends Block {
    public DiracOreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (tool.getItem() instanceof PickaxeItem && !tool.isCorrectToolForDrops(state)) {
            level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        ParticleTypes.PORTAL,
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        24,
                        0.4,
                        0.4,
                        0.4,
                        0.05);
            }
            level.setBlock(pos, ModFluids.PORTAL_FLUID.get().defaultFluidState().createLegacyBlock(), 3);
        }
    }
}
