package com.dawnduskterminal.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 试验物品 033 与 034：按住右键架起来的长矛
 *
 * <p>使用动作是 {@link UseAnim#BOW}：第一人称按住期间由原版播拉弓那套物品动画（手臂底位 + 拉满的位移
 * 与拉伸），第三人称由 {@code PlayerRenderer#getArmPose} 摆成 {@code BOW_AND_ARROW}。
 * 物品模型得配同一套 display：这两张贴图是按弓的朝向画的，所以模型照原版 {@code item/bow} 的
 * thirdperson 变换来，不能挂 {@code item/generated} 什么变换都不给，也不能挂 {@code item/handheld}
 *
 * <p>视野不会缩：原版压 FOV 的条件是 {@code AbstractClientPlayer#getFieldOfViewModifier} 里的
 * {@code getUseItem().is(Items.BOW)}，查的是物品本体而不是使用动作
 *
 * <p>按住期间每 10 tick（半秒）对选中的生物造成 1 点伤害，伤害类型直接用原版的 {@code minecraft:sweet_berry_bush}：
 * 它是普通伤害（护甲照减），而击退与死亡信息都省事——这个类型本来就在原版 {@code minecraft:no_knockback}
 * 标签里，{@code LivingEntity#hurt} 里那整段击退会被跳过，所以只有伤害没有击退；死亡信息是原版自带的那句，
 * 本模组不需要再加一条
 *
 * <p>「选中」= 触及范围内、视野内、离视线最近的活生物，见 {@link #findTarget(Player)}
 */
public class ThrustSpearItem extends Item {
    /** 突刺间隔，10 tick 正好 0.5 秒 */
    private static final int THRUST_INTERVAL = 10;
    /** 一次 1 点，也就是半颗心 */
    private static final float THRUST_DAMAGE = 1.0F;
    /** 长按多久都不主动结束，和原版弓的 72000 一样 */
    private static final int USE_DURATION = 72000;
    /** 视野：以视线为轴、两侧各 30 度的锥体 */
    private static final double VIEW_CONE_DEGREES = 30.0;

    public ThrustSpearItem(Properties properties) {
        super(properties);
    }

    /** 拉弓的姿势：按住期间物品一直被拉满 */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 客户端也要走这一句：右键那一刻本地玩家就已经在「使用中」，动画不用等服务端回包
        player.startUsingItem(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !(living instanceof Player player)) {
            return;
        }
        // 用掉的时间从 0 开始数：第一次 onUseTick 拿到的是完整时长，那一拍不算
        int elapsed = USE_DURATION - remainingUseDuration;
        if (elapsed <= 0 || elapsed % THRUST_INTERVAL != 0) {
            return;
        }
        LivingEntity target = findTarget(player);
        if (target == null) {
            return;
        }
        // 无敌帧交给原版自己算，这里只负责掉血
        if (target.hurt(level.damageSources().sweetBerryBush(), THRUST_DAMAGE)) {
            // 浆果丛刺伤的那声，原版玩家撞进甜浆果丛时放的就是它
            level.playSound(
                    null,
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH,
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }
    }

    /**
     * 触及范围内、视野内、离视线最近的生物；没有就是 null
     *
     * <p>「触及范围」用玩家自己的 {@code entity_interaction_range} 属性（默认 3 格），所以属性被别的
     * 模组改过时也跟着走
     *
     * <p>排序按「到视线的垂距」而不是按距离：要的是准心附近那只，不是脚边那只
     */
    private static LivingEntity findTarget(Player player) {
        double reach = player.entityInteractionRange();
        if (reach <= 0.0) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double lookLength = look.length();
        if (lookLength < 1.0E-4) {
            return null;
        }
        Vec3 direction = look.scale(1.0 / lookLength);
        double maxTangent = Math.tan(Math.toRadians(VIEW_CONE_DEGREES));

        AABB searchBox = player.getBoundingBox().inflate(reach);
        LivingEntity best = null;
        double bestOffset = Double.MAX_VALUE;
        double bestAlong = Double.MAX_VALUE;
        for (LivingEntity candidate : player.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        searchBox,
                        other -> other != player
                                && other.isAlive()
                                && !other.isSpectator()
                                && other.isAttackable()
                                && player.canAttack(other))) {
            if (candidate.getBoundingBox().distanceToSqr(eye) > reach * reach) {
                continue;
            }
            Vec3 toTarget = candidate.getBoundingBox().getCenter().subtract(eye);
            double along = toTarget.dot(direction);
            if (along <= 0.0) {
                // 视线背后
                continue;
            }
            double offset = toTarget.subtract(direction.scale(along)).length();
            if (offset > along * maxTangent) {
                // 锥体之外
                continue;
            }
            if (!player.hasLineOfSight(candidate)) {
                // 隔墙不刺
                continue;
            }
            if (offset < bestOffset - 1.0E-6 || (Math.abs(offset - bestOffset) <= 1.0E-6 && along < bestAlong)) {
                best = candidate;
                bestOffset = offset;
                bestAlong = along;
            }
        }
        return best;
    }
}
