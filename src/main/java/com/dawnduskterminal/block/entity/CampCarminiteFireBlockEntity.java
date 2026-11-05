package com.dawnduskterminal.block.entity;

import com.dawnduskterminal.registry.ModBlockEntities;
import com.dawnduskterminal.world.CampCarminiteFireAura;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * 红篝火的方块实体：四格食材、烧烤进度、冷却、配方查询，整套照抄原版 {@code CampfireBlockEntity}，
 * 只把方块实体类型换成自己的，另外挂上气场登记
 *
 * <p>为什么不直接继承原版那个类：它的构造器把类型写死成 {@code BlockEntityType.CAMPFIRE}，
 * 而 {@code BlockEntity} 构造时就校验「这个类型认不认这一格的方块」，本方块不在原版篝火那个类型的
 * 名单里，于是直接抛 {@code Invalid block entity minecraft:campfire state at ...}。
 * 所以这里自带一份同样的数据与同样的两个静态 tick，方块那边照原版同一套接上去，
 * 烤出来的东西、放食材的规则、熄灭后的冷却与原版篝火逐条一致
 *
 * <p>气场那边只要位置：区块载入时 {@link #onLoad} 登记，区块卸载时 {@link #setRemoved} 撤掉；
 * 摆下与挖掉那两次由方块的 {@code onPlace} / {@code onRemove} 当场做掉，所以方块落地就生效。
 * 登记的是位置、不管点着没点着：气场判的是「那一格现在是不是点亮着的红篝火」
 */
public class CampCarminiteFireBlockEntity extends BlockEntity implements Clearable {
    /** 四格食材 */
    private static final int NUM_SLOTS = 4;

    /** 熄灭之后每 tick 退两格进度，与 {@code CampfireBlockEntity} 同名同值 */
    private static final int BURN_COOL_SPEED = 2;

    private final NonNullList<ItemStack> items = NonNullList.withSize(NUM_SLOTS, ItemStack.EMPTY);
    private final int[] cookingProgress = new int[NUM_SLOTS];
    private final int[] cookingTime = new int[NUM_SLOTS];
    private final RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> quickCheck =
            RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);

    public CampCarminiteFireBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMP_CARMINITE_FIRE.get(), pos, state);
    }

    /** 点着时每 tick 推进一格进度，到点就把成品吐在方块脚下（原版 {@code CampfireBlockEntity#cookTick}） */
    public static void cookTick(Level level, BlockPos pos, BlockState state, CampCarminiteFireBlockEntity campfire) {
        boolean changed = false;
        for (int slot = 0; slot < campfire.items.size(); slot++) {
            ItemStack stack = campfire.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            changed = true;
            campfire.cookingProgress[slot]++;
            if (campfire.cookingProgress[slot] < campfire.cookingTime[slot]) {
                continue;
            }
            SingleRecipeInput input = new SingleRecipeInput(stack);
            ItemStack cooked = campfire.quickCheck
                    .getRecipeFor(input, level)
                    .map(holder -> holder.value().assemble(input, level.registryAccess()))
                    .orElse(stack);
            if (!cooked.isItemEnabled(level.enabledFeatures())) {
                continue;
            }
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), cooked);
            campfire.items.set(slot, ItemStack.EMPTY);
            level.sendBlockUpdated(pos, state, state, 3);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
        }
        if (changed) {
            setChanged(level, pos, state);
        }
    }

    /** 熄灭之后慢慢退进度，重新点着时接着烤（原版 {@code CampfireBlockEntity#cooldownTick}） */
    public static void cooldownTick(
            Level level, BlockPos pos, BlockState state, CampCarminiteFireBlockEntity campfire) {
        boolean changed = false;
        for (int slot = 0; slot < campfire.items.size(); slot++) {
            if (campfire.cookingProgress[slot] <= 0) {
                continue;
            }
            changed = true;
            campfire.cookingProgress[slot] =
                    Mth.clamp(campfire.cookingProgress[slot] - BURN_COOL_SPEED, 0, campfire.cookingTime[slot]);
        }
        if (changed) {
            setChanged(level, pos, state);
        }
    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    /** 这一份食材能不能烤：四格都占着就不能再放，与 {@code CampfireBlockEntity#getCookableRecipe} 同一条 */
    public Optional<RecipeHolder<CampfireCookingRecipe>> getCookableRecipe(ItemStack stack) {
        return this.items.stream().noneMatch(ItemStack::isEmpty)
                ? Optional.empty()
                : this.quickCheck.getRecipeFor(new SingleRecipeInput(stack), this.level);
    }

    /** 放一份食材进空的格子，返回有没有放进去（原版 {@code CampfireBlockEntity#placeFood}） */
    public boolean placeFood(@Nullable LivingEntity placer, ItemStack stack, int cookingTicks) {
        for (int slot = 0; slot < this.items.size(); slot++) {
            if (!this.items.get(slot).isEmpty()) {
                continue;
            }
            this.cookingTime[slot] = cookingTicks;
            this.cookingProgress[slot] = 0;
            this.items.set(slot, stack.consumeAndReturn(1, placer));
            this.level.gameEvent(
                    GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.Context.of(placer, this.getBlockState()));
            this.markUpdated();
            return true;
        }
        return false;
    }

    private void markUpdated() {
        this.setChanged();
        this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    /** 泡水或铲子熄灭时叫一声，让客户端知道这一格的食材该重画了（原版同一条） */
    public void dowse() {
        if (this.level != null) {
            this.markUpdated();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        CampCarminiteFireAura.track(this.level, this.worldPosition);
    }

    @Override
    public void setRemoved() {
        CampCarminiteFireAura.untrack(this.level, this.worldPosition);
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items.clear();
        ContainerHelper.loadAllItems(tag, this.items, registries);
        if (tag.contains("CookingTimes", 11)) {
            int[] progress = tag.getIntArray("CookingTimes");
            System.arraycopy(progress, 0, this.cookingProgress, 0, Math.min(this.cookingTime.length, progress.length));
        }
        if (tag.contains("CookingTotalTimes", 11)) {
            int[] total = tag.getIntArray("CookingTotalTimes");
            System.arraycopy(total, 0, this.cookingTime, 0, Math.min(this.cookingTime.length, total.length));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, true, registries);
        tag.putIntArray("CookingTimes", this.cookingProgress);
        tag.putIntArray("CookingTotalTimes", this.cookingTime);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, this.items, true, registries);
        return tag;
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        input.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .copyInto(this.getItems());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Items");
    }
}
