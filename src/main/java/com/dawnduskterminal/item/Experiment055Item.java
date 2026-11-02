package com.dawnduskterminal.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 试验物品 055：按住右键持续外泄的那个场
 *
 * <p>按住期间半径与伤害系数从 0 一路涨到 {@value BeetrootProcess#CHARGE_TICKS} tick（5 秒）时的
 * 满值 {@value BeetrootProcess#MAX_RADIUS} 格与 {@value BeetrootProcess#MAX_DAMAGE_FACTOR}，圈内除自己以外的
 * 活物每 tick 挨一次 {@code 系数 × (半径 - 距离)} 的无击退伤害，每一下都在目标身上炸开一片红石粒子；
 * 充满之后连自己每秒也在掉同类型的血。每秒扣 1 点耐久，共 {@value #DURABILITY} 点，并且每秒把影响半径内
 * 不是 {@code dawnduskterminal:old_growth_birch_forest} 的群系刷成它
 *
 * <p>整段在 {@link BeetrootProcess} 里，挂在服务端 tick 上推进（与 043、066、095 同一个做法）：
 * 这一支要按充能进度切换三支音效（charge → full → geiger），而循环音只能由客户端自己的
 * {@code SoundInstance} 维持，所以每次切换与结束都得有服务端发话，写在下钩子里收不齐
 */
public class Experiment055Item extends Item {
    /** 300 点耐久 = 按住 300 秒 */
    public static final int DURABILITY = 300;
    /** 一直按着也不主动结束，和原版弓的 72000 一样 */
    public static final int USE_DURATION = 72000;

    public Experiment055Item(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 客户端也要走这一句：右键那一刻本地玩家就已经在「使用中」
        player.startUsingItem(hand);
        // 两端都会走到 use，真正起过程的只有服务端；已经在跑就不再叠第二段
        if (level instanceof ServerLevel server && !BeetrootProcess.isActive(player)) {
            BeetrootProcess.start(server, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
