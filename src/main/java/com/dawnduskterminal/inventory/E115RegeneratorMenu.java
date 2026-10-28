package com.dawnduskterminal.inventory;

import com.dawnduskterminal.block.entity.E115RegeneratorBlockEntity;
import com.dawnduskterminal.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 115 再生机的界面：一格产物格加玩家背包，没有别的读数
 *
 * <p>格子位置与玩家背包那 36 格全部照原版 3x3 容器（{@code DispenserMenu}）的坐标：
 * 机器那 9 个位置是 (62 + 18c, 17 + 18r)，我们只用正中间那一格 (80, 35)；
 * 背包 (8, 84) 起、快捷栏 (8, 142)。面板是本模组那张
 * {@code textures/gui/e_115_regenerator.png}（256x256 画布，176x166 的面板），
 * 凹槽就是按这几个数画的，改坐标要连图一起改
 *
 * <p>产物格能拿不能放：{@code mayPlace} 直接吃方块实体那份 {@link Container#canPlaceItem(int, ItemStack)}，
 * 于是界面里塞不进去的东西漏斗也塞不进去
 *
 * <p>开界面靠坐标找方块实体，做法与 051 相同：{@code ModMenus} 注册的工厂是 {@link #fromNetwork}
 */
public class E115RegeneratorMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 176;

    public static final int IMAGE_HEIGHT = 166;

    public static final int SLOT_COUNT = E115RegeneratorBlockEntity.CONTAINER_SIZE;

    /** 玩家背包的 36 格紧跟在机器那一格后面 */
    public static final int INVENTORY_START = SLOT_COUNT;
    public static final int INVENTORY_END = INVENTORY_START + 27;
    public static final int HOTBAR_END = INVENTORY_END + 9;

    /** 产物格：原版 3x3 的正中那一格（62 + 18, 17 + 18） */
    public static final int OUTPUT_X = 80;
    public static final int OUTPUT_Y = 35;

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    private final Container container;
    protected final Level level;

    /** 客户端用：服务端把方块坐标写在开界面的包里，这里按坐标把方块实体找回来 */
    public static E115RegeneratorMenu fromNetwork(int containerId, Inventory inventory,
                                                  RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        if (inventory.player.level().getBlockEntity(pos) instanceof E115RegeneratorBlockEntity entity) {
            return new E115RegeneratorMenu(containerId, inventory, entity);
        }
        // 区块没加载之类的情况：给一份假容器，界面照样开得起来，只是没有内容
        return new E115RegeneratorMenu(containerId, inventory, new SimpleContainer(SLOT_COUNT));
    }

    public E115RegeneratorMenu(int containerId, Inventory inventory, Container container) {
        super(ModMenus.E_115_REGENERATOR.get(), containerId);
        checkContainerSize(container, SLOT_COUNT);
        this.container = container;
        this.level = inventory.player.level();

        this.addSlot(new OutputSlot(container, E115RegeneratorBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9,
                    INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /**
     * Shift 点击的动线：产物格往背包搬；背包里没有能塞回机器的格子（那一格只出不进），
     * 所以在背包与快捷栏之间上下换行
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        if (index < SLOT_COUNT) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < INVENTORY_END) {
            if (!this.moveItemStackTo(stack, INVENTORY_END, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == moved.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return moved;
    }

    /** 产物格：能拿不能放，合法性问容器本身 */
    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.container.canPlaceItem(this.getContainerSlot(), stack);
        }
    }
}
