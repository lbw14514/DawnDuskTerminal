package com.dawnduskterminal.inventory;

import com.dawnduskterminal.block.entity.Experiment051BlockEntity;
import com.dawnduskterminal.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 试验物品 051 的界面：左半边熔炉、右半边拆解台
 *
 * <p>格子是「原料、燃料、3x3 出料」共 11 格，加玩家背包 36 格。原料与燃料的合法性不在 {@link Slot} 上另写一套，
 * 直接吃方块实体那份 {@link Container#canPlaceItem(int, ItemStack)}，于是界面里塞不进去的东西漏斗也塞不进去，
 * 两处不会走岔
 *
 * <p>没有配方书、没有「换配方」按钮，也没有让玩家选配方的字段——出什么完全由方块实体那边的
 * {@code UncraftingResolver} 定死
 *
 * <p>坐标常量都与 {@code textures/gui/experiment_051.png} 里的面板对齐：面板 186x166，
 * 火焰与箭头直接借原版熔炉的两个 sprite（{@code container/furnace/lit_progress} 与
 * {@code container/furnace/burn_progress}），所以那两个位置必须与贴图里画好的空框严丝合缝
 */
public class Experiment051Menu extends AbstractContainerMenu {
    /** 面板宽：比原版熔炉的 176 宽一圈，才放得下右半边的 3x3 */
    public static final int IMAGE_WIDTH = 186;

    /** 面板高：与原版熔炉一致 */
    public static final int IMAGE_HEIGHT = 166;

    public static final int INPUT_SLOT = Experiment051BlockEntity.INPUT_SLOT;
    public static final int FUEL_SLOT = Experiment051BlockEntity.FUEL_SLOT;
    public static final int OUTPUT_FIRST = Experiment051BlockEntity.OUTPUT_FIRST;
    public static final int OUTPUT_COUNT = Experiment051BlockEntity.OUTPUT_COUNT;
    public static final int SLOT_COUNT = Experiment051BlockEntity.CONTAINER_SIZE;

    /** 玩家背包的 36 格紧跟在机器那 11 格后面 */
    public static final int INVENTORY_START = SLOT_COUNT;
    public static final int INVENTORY_END = INVENTORY_START + 27;
    public static final int HOTBAR_END = INVENTORY_END + 9;

    // 格子位置（16x16 物品格的左上角），与贴图里的凹槽一一对应
    public static final int INPUT_X = 24;
    public static final int INPUT_Y = 17;
    public static final int FUEL_X = 24;
    public static final int FUEL_Y = 53;
    public static final int OUTPUT_X = 96;
    public static final int OUTPUT_Y = 17;
    public static final int OUTPUT_STRIDE = 18;
    public static final int INVENTORY_X = 12;
    public static final int INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    /** 火焰 sprite 的左上角，14x14 */
    public static final int FLAME_X = 24;
    public static final int FLAME_Y = 36;

    /** 箭头 sprite 的左上角，24x16 */
    public static final int ARROW_X = 47;
    public static final int ARROW_Y = 34;

    private final Container container;
    private final ContainerData data;
    protected final Level level;

    /**
     * 客户端用：服务端把方块坐标写在开界面的包里，这里按坐标把方块实体找回来
     *
     * <p>{@code ModMenus} 注册的工厂就是这个静态方法
     */
    public static Experiment051Menu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        if (inventory.player.level().getBlockEntity(pos) instanceof Experiment051BlockEntity entity) {
            return new Experiment051Menu(containerId, inventory, entity, entity.dataAccess());
        }
        // 区块没加载之类的情况：给一份假容器，界面照样开得起来，只是没有内容
        return new Experiment051Menu(containerId, inventory,
            new SimpleContainer(SLOT_COUNT), new SimpleContainerData(Experiment051BlockEntity.DATA_COUNT));
    }

    public Experiment051Menu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenus.EXPERIMENT_051.get(), containerId);
        checkContainerSize(container, SLOT_COUNT);
        checkContainerDataCount(data, Experiment051BlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.level = inventory.player.level();

        this.addSlot(new MachineSlot(container, INPUT_SLOT, INPUT_X, INPUT_Y));
        this.addSlot(new MachineSlot(container, FUEL_SLOT, FUEL_X, FUEL_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new OutputSlot(container,
                    OUTPUT_FIRST + row * 3 + col,
                    OUTPUT_X + col * OUTPUT_STRIDE,
                    OUTPUT_Y + row * OUTPUT_STRIDE));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9,
                    INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }

        this.addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /**
     * Shift 点击的动线：机器那 11 格往背包搬，背包往原料格塞，塞不进再往燃料格
     *
     * <p>骨架照原版熔炉菜单，只是把「结果格」换成了 3x3 那九格
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
            // 机器 → 背包
            if (!this.moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包 → 原料，塞不进再试燃料
            if (!this.moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)
                && !this.moveItemStackTo(stack, FUEL_SLOT, FUEL_SLOT + 1, false)) {
                // 两格都不收，就在背包里上下换行
                if (index < INVENTORY_END) {
                    if (!this.moveItemStackTo(stack, INVENTORY_END, HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
                    return ItemStack.EMPTY;
                }
            }
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

    public boolean isLit() {
        return this.data.get(Experiment051BlockEntity.DATA_LIT_TIME) > 0;
    }

    /** 火焰画多高，与熔炉菜单的 {@code getLitProgress} 同形 */
    public float litProgress() {
        int duration = this.data.get(Experiment051BlockEntity.DATA_LIT_DURATION);
        if (duration == 0) {
            duration = 200;
        }
        return Mth.clamp((float) this.data.get(Experiment051BlockEntity.DATA_LIT_TIME) / (float) duration, 0.0F, 1.0F);
    }

    /** 箭头画多长，与熔炉菜单的 {@code getBurnProgress} 同形 */
    public float cookProgress() {
        int total = this.data.get(Experiment051BlockEntity.DATA_COOK_TOTAL);
        int progress = this.data.get(Experiment051BlockEntity.DATA_COOK_PROGRESS);
        return total != 0 && progress != 0 ? Mth.clamp((float) progress / (float) total, 0.0F, 1.0F) : 0.0F;
    }

    /** 原料与燃料格都直接问容器，界面与漏斗共用同一套 canPlaceItem 判定 */
    private static class MachineSlot extends Slot {
        MachineSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.container.canPlaceItem(this.getContainerSlot(), stack);
        }
    }

    /** 出料格：能拿不能放 */
    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
