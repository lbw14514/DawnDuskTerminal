package com.dawnduskterminal.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 试验物品 095：镇魂曲
 *
 * <p>按住右键不放就在原地奏一支曲子：每 0.5 秒朝四周甩出一圈音符，曲子本身循环播放
 * （周围 32 格内都听得见，每次按住随机挑一支，见 {@link RequiemProcess} 与 {@code ModSounds#REQUIEM_TRACKS}），
 * 同时 32 格内除自己以外的活物每 tick 挨一次 {@code 0.5 × (32 - 距离)} 的伤害。每按住 1 秒扣 1 点耐久，
 * 共 {@value #DURABILITY} 点——从头按到尾正好 5 分钟
 *
 * <p>伤害不绕无敌帧：过程每 tick 都调一次 {@code hurt}，原版那 0.5 秒的无敌帧自然把节拍排出来，
 * 与 066 那本恶魂陷阱书同一个做法（043 相反，它要逐拍都打出去，所以进了 {@code bypasses_cooldown}）
 *
 * <p>用满 {@code USE_DURATION} 也不主动结束，与 032/033 两支长矛、原版弓的 72000 一样：
 * 一直按住就一直奏，直到耐久烧光。没有使用动作，所以物品模型照 {@code item/generated} 拿在手里即可，
 * 不需要配 {@code item/bow} 那套变换
 */
public class Experiment095Item extends Item {
    /** 300 点耐久 = 按住 300 秒 */
    public static final int DURABILITY = 300;
    /** 一直按着也不主动结束，和原版弓的 72000 一样 */
    public static final int USE_DURATION = 72000;

    public Experiment095Item(Properties properties) {
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
        // 两端都会走到 use，真正起过程的只有服务端；已经在奏就不再叠第二段
        if (level instanceof ServerLevel server && !RequiemProcess.isActive(player)) {
            RequiemProcess.start(server, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(
            ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.dawnduskterminal.experiment_095.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
