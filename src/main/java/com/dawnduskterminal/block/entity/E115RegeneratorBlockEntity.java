package com.dawnduskterminal.block.entity;

import com.dawnduskterminal.block.E115RegeneratorBlock;
import com.dawnduskterminal.inventory.E115RegeneratorMenu;
import com.dawnduskterminal.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 115 再生机的方块实体：一格产物格
 *
 * <p>两个条件（侧面红石信号、顶上完整的撒粉 115，判定在 {@link E115RegeneratorBlock}）都成立时，
 * 每 tick 掷一次骰子，掷中就产出一份试验物品 115。概率与原版随机刻完全一致：
 * 一个 16³ 区块段每 tick 抽 {@code randomTickSpeed}（默认 3）个方块位置，任意一格被抽中的机会是 3/4096；
 * 暮色森林的 115 在 {@code regenerate=true} 且没满片时被抽中一次就补一片、不再掷第二次骰子。
 * 于是「每 tick 掷 3/4096、掷中就产一份」与「115 自己补一片」是同一件事：随机、不可预期，
 * 期望间隔 4096/3 ≈ 1365.3 tick（20 tps 下约 68.3 秒）
 *
 * <p>产出一份时放一声顶上那块 115 的放置音效（115 是羊毛音效，就是 {@code block.wool.place}），
 * 与暮色森林给 115 补片时那一声同一个来源
 *
 * <p>漏斗：只有底面抽得走（{@link #getSlotsForFace}），其余五个面既不进也不出——这一格只装产出的，
 * 玩家想往里塞东西也塞不进（{@link #canPlaceItem} 一律 false）
 */
public class E115RegeneratorBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int OUTPUT_SLOT = 0;

    public static final int CONTAINER_SIZE = 1;

    /** 一 tick 抽几个方块位置，等于原版 {@code randomTickSpeed} 的默认值 3 */
    public static final int RANDOM_TICK_STEPS = 3;

    /** 一个区块段里有多少个方块位置：16×16×16，也就是随机刻的分母 */
    public static final int RANDOM_TICK_VOLUME = 16 * 16 * 16;

    private static final int[] SLOTS_FOR_DOWN = {OUTPUT_SLOT};

    private static final int[] NO_SLOTS = {};

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    public E115RegeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.E_115_REGENERATOR.get(), pos, state);
    }

    /**
     * 一个机器刻：先看两个条件成不成立，再掷一次骰子
     *
     * <p>没有进度可存，格子里有没有东西就是全部状态，所以只有真的产出一份时才 {@code setChanged}
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, E115RegeneratorBlockEntity entity) {
        if (!E115RegeneratorBlock.isSidePowered(level, pos)
            || !E115RegeneratorBlock.hasSprinkledFullSource(level, pos)
            || !entity.canAcceptSlice()) {
            return;
        }
        if (level.getRandom().nextInt(RANDOM_TICK_VOLUME) >= RANDOM_TICK_STEPS) {
            return;
        }
        entity.pushSlice();
        level.playSound(null, pos, E115RegeneratorBlock.sourcePlaceSound(level, pos), SoundSource.BLOCKS, 1.0F, 1.0F);
        setChanged(level, pos, state);
    }

    /** 产物格放不放得下下一份：空着，或者已经是同一种东西且没到堆叠上限 */
    private boolean canAcceptSlice() {
        ItemStack stored = this.items.get(OUTPUT_SLOT);
        if (stored.isEmpty()) {
            return true;
        }
        ItemStack slice = E115RegeneratorBlock.newSourceStack();
        return !slice.isEmpty()
            && ItemStack.isSameItemSameComponents(stored, slice)
            && stored.getCount() < stored.getMaxStackSize();
    }

    /** 产一份：并进产物格 */
    private void pushSlice() {
        ItemStack slice = E115RegeneratorBlock.newSourceStack();
        if (slice.isEmpty()) {
            return;
        }
        ItemStack stored = this.items.get(OUTPUT_SLOT);
        if (stored.isEmpty()) {
            this.items.set(OUTPUT_SLOT, slice);
        } else {
            stored.grow(slice.getCount());
        }
    }

    /** 只有底面抽得走，其余五个面一个槽都不开 */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? SLOTS_FOR_DOWN : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && slot == OUTPUT_SLOT;
    }

    /** 产物格只装机器自己产的，漏斗与界面都塞不进来 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.dawnduskterminal.e_115_regenerator");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new E115RegeneratorMenu(containerId, inventory, this);
    }

    /** 开界面时把自己的坐标带给客户端，菜单那边靠它把方块实体找回来，写法与 051 相同 */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.worldPosition);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
    }
}
