package com.dawnduskterminal.inventory;

import com.dawnduskterminal.block.entity.Experiment004BlockEntity;
import com.dawnduskterminal.registry.ModMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.event.EventHooks;

/**
 * 试验物品 004 的界面：原版熔炉那套，一格原料、一格燃料、一格产物，加火焰与箭头
 *
 * <p>直接继承 {@link AbstractFurnaceMenu}，格子位置、Shift 点击动线、配方书、火焰与箭头的进度换算
 * 全走原版那份，本类只做两件事：
 *
 * <ol>
 *   <li>菜单类型换成 {@code dawnduskterminal:experiment_004}（原版 {@code FurnaceMenu} 把
 *       {@code MenuType.FURNACE} 写死在构造参数里，继承它的话 {@code getType()} 还是熔炉，客户端会按熔炉
 *       界面开，本模组的菜单类型与屏幕就白注册了）
 *   <li>把产物格换成 {@link ResultSlot}：原版 {@code FurnaceResultSlot} 只在容器是
 *       {@code AbstractFurnaceBlockEntity} 时才结算经验，本模组的方块实体不是那一支，所以照抄一份、
 *       改成问 {@link Experiment004BlockEntity#awardExperience}
 * </ol>
 *
 * <p>方块坐标不往客户端传：原版熔炉界面本来就是「客户端拿一份空容器、内容靠槽位同步」，
 * 配方书与进度条都只读 {@code ContainerData}，不需要方块实体本体
 */
public class Experiment004Menu extends AbstractFurnaceMenu {
    /** 产物格坐标：与原版 {@code AbstractFurnaceMenu} 里的 (116, 35) 一致，换槽位时位置不能动 */
    public static final int RESULT_X = 116;
    public static final int RESULT_Y = 35;

    /** 客户端用：原版 {@code AbstractFurnaceMenu} 会给一份空容器与四个数据槽，内容随后同步下来 */
    public Experiment004Menu(int containerId, Inventory inventory) {
        super(ModMenus.EXPERIMENT_004.get(), RecipeType.SMELTING, RecipeBookType.FURNACE,
            containerId, inventory);
    }

    /** 服务端用：方块实体自己当容器与数据源 */
    public Experiment004Menu(int containerId, Inventory inventory, Experiment004BlockEntity entity) {
        super(ModMenus.EXPERIMENT_004.get(), RecipeType.SMELTING, RecipeBookType.FURNACE,
            containerId, inventory, entity, entity.dataAccess());
        this.slots.set(RESULT_SLOT, new ResultSlot(inventory.player, entity, RESULT_SLOT, RESULT_X, RESULT_Y));
    }

    /**
     * 产物格：能拿不能放，拿走时结算经验
     *
     * <p>{@code removeCount} 的累计与 {@code onQuickCraft} 那条路照抄 {@code FurnaceResultSlot}，
     * 这样 Shift 连拿多份时经验只结算一次、数量也对
     */
    private static class ResultSlot extends Slot {
        private final Player player;

        private int removeCount;

        ResultSlot(Player player, Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.player = player;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public ItemStack remove(int amount) {
            if (this.hasItem()) {
                this.removeCount += Math.min(amount, this.getItem().getCount());
            }
            return super.remove(amount);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            this.checkTakeAchievements(stack);
            super.onTake(player, stack);
        }

        @Override
        protected void onQuickCraft(ItemStack stack, int amount) {
            this.removeCount += amount;
            this.checkTakeAchievements(stack);
        }

        @Override
        protected void checkTakeAchievements(ItemStack stack) {
            stack.onCraftedBy(this.player.level(), this.player, this.removeCount);
            if (this.player instanceof ServerPlayer serverPlayer
                && this.container instanceof Experiment004BlockEntity entity) {
                entity.awardExperience(serverPlayer);
            }
            this.removeCount = 0;
            EventHooks.firePlayerSmeltedEvent(this.player, stack);
        }
    }
}
