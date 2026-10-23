package com.dawnduskterminal.item;

import com.dawnduskterminal.registry.ModParticles;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 试验物品 027：按住右键，视线指到哪块方块的哪一面，就在那个落点每 tick 画一个红石点
 *
 * <p>「触及范围」用玩家自己的 {@code block_interaction_range} 属性（默认 4.5 格），所以别的模组改过属性也照走；
 * 射线取 {@link net.minecraft.world.entity.Entity#pick(double, float, boolean)}，它走方块轮廓，
 * 打中什么就返回那个面的确切落点。没看中方块（看空、看太远）时什么都不发
 *
 * <p>落点沿方块面法线往外让 {@link #SURFACE_OFFSET}，不让粒子贴脸渲染时半边陷进方块里
 *
 * <p>粒子在服务端发（{@code ServerLevel#sendParticles}），周围 32 格内的玩家都看得见；粒子本体在
 * {@link com.dawnduskterminal.client.Experiment027Particle}：静止、原版红石粒子的一半大小、每个存在 60 秒
 */
public class Experiment027Item extends Item {
    /** 一直按着也不主动结束，和原版弓的 72000 一样 */
    private static final int USE_DURATION = 72000;

    /** 落点沿方块面法线往外让出的距离 */
    public static final double SURFACE_OFFSET = 0.02D;

    public Experiment027Item(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 客户端也要走这一句：右键那一刻本地玩家就已经在「使用中」，不用等服务端回包
        player.startUsingItem(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** 每 tick 一个粒子，全在服务端发，客户端只负责显示 */
    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !(living instanceof Player player)) {
            return;
        }
        Vec3 point = surfacePoint(level, player);
        if (point == null || !(level instanceof ServerLevel server)) {
            return;
        }
        // count=1、速度 0：客户端拿到一个静止的点
        server.sendParticles(ModParticles.EXPERIMENT_027.get(),
            point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /**
     * 视线在触及范围内打中的方块面落点，沿法线让出 {@link #SURFACE_OFFSET}；没打中方块就是 {@code null}
     *
     * <p>抽成静态方法好让 GameTest 直接验射线：摆一块石头、把假玩家摆到正对它的位置，落点应当正好在那一面上
     */
    @Nullable
    public static Vec3 surfacePoint(Level level, Player player) {
        double reach = player.blockInteractionRange();
        if (reach <= 0.0D) {
            return null;
        }
        HitResult hit = player.pick(reach, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        Direction face = blockHit.getDirection();
        return blockHit.getLocation().add(
            face.getStepX() * SURFACE_OFFSET,
            face.getStepY() * SURFACE_OFFSET,
            face.getStepZ() * SURFACE_OFFSET);
    }
}
