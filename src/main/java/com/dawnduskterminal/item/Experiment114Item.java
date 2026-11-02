package com.dawnduskterminal.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * 试验物品 114：既能吃又能摆的方块物品，物品行为照抄暮色森林的试验物品 115
 *
 * <p>吃一份的效果按下 8 片试验物品 115 算：暮色森林那件自带的一份是 4 点营养、饱和度系数 0.3，
 * 8 份就是 {@link #NUTRITION} 与 {@link #SATURATION_MODIFIER}
 *
 * <p>{@link #useOn} 逐行照 115：点在自己那一块上不动手（把交互让给方块，方块自己补片或撒粉），
 * 不是潜行就摆，摆不下才转成吃
 *
 * <p>与 115 的差别：咽下去那一下随机甩一声恶魂受伤，见 {@link #playGhastHurt}
 */
public class Experiment114Item extends BlockItem {
    /** 8 份试验物品 115 的营养 */
    public static final int NUTRITION = 32;

    /** 8 份试验物品 115 的饱和度系数 */
    public static final float SATURATION_MODIFIER = 0.3F;

    private static final float MIN_PITCH = 0.7F;
    private static final float MAX_PITCH = 1.3F;
    private static final float MIN_VOLUME = 0.8F;
    private static final float MAX_VOLUME = 1.2F;

    public Experiment114Item(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().getBlockState(context.getClickedPos()).is(this.getBlock())) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (player != null && !player.isShiftKeyDown()) {
            InteractionResult result = this.place(new BlockPlaceContext(context));
            return !result.consumesAction() && context.getItemInHand().get(DataComponents.FOOD) != null
                    ? this.use(context.getLevel(), player, context.getHand()).getResult()
                    : result;
        }
        return InteractionResult.PASS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // 只在服务端放：服务端的音效会广播给附近所有人，两边都放就响两次
        if (!level.isClientSide) {
            playGhastHurt(level, entity.blockPosition());
        }
        return super.finishUsingItem(stack, level, entity);
    }

    /**
     * 随机音高音量的恶魂受伤声
     *
     * <p>两处共用：吃下 114 的那一下，以及方块吃掉一片、{@code regenerate} 版收到一次红石信号的那一下
     */
    public static void playGhastHurt(Level level, BlockPos pos) {
        RandomSource random = level.getRandom();
        float pitch = MIN_PITCH + random.nextFloat() * (MAX_PITCH - MIN_PITCH);
        float volume = MIN_VOLUME + random.nextFloat() * (MAX_VOLUME - MIN_VOLUME);
        // 恶魂自己受伤走的是 HOSTILE 声道，字幕才会显示成「恶魂：受伤」
        level.playSound(null, pos, SoundEvents.GHAST_HURT, SoundSource.HOSTILE, volume, pitch);
    }
}
