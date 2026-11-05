package com.dawnduskterminal.item;

import com.dawnduskterminal.entity.Experiment011PlateEntity;
import com.dawnduskterminal.registry.ModEntities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 试验物品 011：右键摆下一只盘子（{@link Experiment011PlateEntity}）
 *
 * <p>三条摆法：
 * <ul>
 *   <li><b>只能向上</b>。点到的是别的面就 {@link InteractionResult#FAIL}，手都不抬——
 *       原版方块摆不下去时也是这个返回值</li>
 *   <li><b>高度由下方的碰撞箱定</b>。取的是所点方块碰撞箱朝上那一面的高度，于是半砖上摆在 0.5、
 *       台阶上摆在 1.0、地毯那种 0 高度的薄板上就贴着地；不是「按方块坐标算」也不是「按形状算」，
 *       就是要跟脚下的碰撞箱对齐</li>
 *   <li><b>放不下就不放</b>。盘子只有 1 像素厚，顶上有方块压着时 {@code Level#noCollision} 会否掉这一次</li>
 * </ul>
 *
 * <p>盘子上摆什么、怎么取走，全在 {@link Experiment011PlateEntity#interact}，这件物品只管「摆一只空盘」
 */
public class Experiment011Item extends Item {
    public Experiment011Item(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // 只认朝上的那一面：对着侧面、底面点都不摆
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        BlockPos support = context.getClickedPos();
        VoxelShape shape = level.getBlockState(support).getCollisionShape(level, support);
        if (shape.isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = support.above();
        Experiment011PlateEntity plate = new Experiment011PlateEntity(ModEntities.EXPERIMENT_011_PLATE.get(), level);
        plate.moveTo(pos.getX() + 0.5D, support.getY() + shape.bounds().maxY, pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.noCollision(plate)) {
            return InteractionResult.FAIL;
        }

        level.addFreshEntity(plate);
        level.playSound(null, plate.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
        Player player = context.getPlayer();
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.dawnduskterminal.experiment_011.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
