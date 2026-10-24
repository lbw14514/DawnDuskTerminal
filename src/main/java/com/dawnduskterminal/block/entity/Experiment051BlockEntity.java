package com.dawnduskterminal.block.entity;

import com.dawnduskterminal.block.Experiment051Block;
import com.dawnduskterminal.crafting.UncraftingPlan;
import com.dawnduskterminal.crafting.UncraftingResolver;
import com.dawnduskterminal.inventory.Experiment051Menu;
import com.dawnduskterminal.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 试验物品 051 摆下来的那一块
 *
 * <p>左边是熔炉那套（一格原料、一格燃料、一把火、一支箭头），右边是拆解台的 3x3 出料格。
 * 与拆解台的不同点全在这里：
 *
 * <ol>
 *   <li><b>不收经验</b>：拆解台按配方 {@code cost} 扣等级，这里一分都不扣，所以 {@code ContainerData} 里
 *       只有燃烧与进度四个数
 *   <li><b>不预览、直接拆完出货</b>：拆解台把产物摆在 3x3 里等玩家点走，这里进度条走满就直接把产物并进出料格，
 *       并进去之后下一 tick 重新判断能不能再来一次，于是连续拆、产物自然堆叠
 *   <li><b>产物堵了就停</b>：只要有任意一格出料格放不下这一次的产物（不是同一个物品，或者并进去超过堆叠上限），
 *       整台机器停工、进度清零。见 {@link #canStore(UncraftingPlan)}
 *   <li><b>烧燃料</b>：一次加工的时间是 {@link UncraftingPlan#DEFAULT_COOK_TIME}，也就是熔炉配方的常规时间
 *       （原版 200 tick）；熔炼回退时取那条熔炼配方自己写的时间，同样不折算
 *   <li><b>走哪条配方</b>：{@link UncraftingResolver} 按拆解台的顺序取第 0 条，没有可拆配方就退成熔炉
 * </ol>
 *
 * <p>漏斗：顶面进原料、侧面进燃料、底面抽产物，见 {@link #getSlotsForFace(Direction)}
 */
public class Experiment051BlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int INPUT_SLOT = 0;
    public static final int FUEL_SLOT = 1;
    public static final int OUTPUT_FIRST = 2;
    public static final int OUTPUT_COUNT = UncraftingPlan.PRODUCT_SLOTS;
    public static final int CONTAINER_SIZE = OUTPUT_FIRST + OUTPUT_COUNT;

    public static final int DATA_LIT_TIME = 0;
    public static final int DATA_LIT_DURATION = 1;
    public static final int DATA_COOK_PROGRESS = 2;
    public static final int DATA_COOK_TOTAL = 3;
    public static final int DATA_COUNT = 4;

    /** 顶面只进原料 */
    private static final int[] TOP_SLOTS = {INPUT_SLOT};

    /** 侧面只进燃料 */
    private static final int[] SIDE_SLOTS = {FUEL_SLOT};

    /** 底面只抽产物 */
    private static final int[] BOTTOM_SLOTS = new int[OUTPUT_COUNT];

    static {
        for (int i = 0; i < OUTPUT_COUNT; i++) {
            BOTTOM_SLOTS[i] = OUTPUT_FIRST + i;
        }
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    /** 还能烧几 tick，大于 0 就是 {@code active=true} 的那一版 */
    private int litTime;

    /** 当前这份燃料一共能烧几 tick，客户端拿它算火焰高度 */
    private int litDuration;

    private int cookProgress;

    private int cookTotal = UncraftingPlan.DEFAULT_COOK_TIME;

    // 配方扫描比熔炉那套 CachedCheck 贵得多，只在原料变了、数据包重载了才重算，见 refreshPlan()
    @Nullable
    private RecipeManager cachedManager;

    private ItemStack cachedInput = ItemStack.EMPTY;

    private UncraftingPlan cachedPlan = UncraftingPlan.none();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                // 数据槽在网络上按 short 同步，超过 Short.MAX_VALUE 的燃料按比例缩一下，原版熔炉在 NeoForge 里也这么处理
                case DATA_LIT_TIME -> Experiment051BlockEntity.this.litDuration > Short.MAX_VALUE
                    ? Mth.floor((double) Experiment051BlockEntity.this.litTime
                        / (double) Experiment051BlockEntity.this.litDuration * Short.MAX_VALUE)
                    : Experiment051BlockEntity.this.litTime;
                case DATA_LIT_DURATION -> Math.min(Experiment051BlockEntity.this.litDuration, Short.MAX_VALUE);
                case DATA_COOK_PROGRESS -> Experiment051BlockEntity.this.cookProgress;
                case DATA_COOK_TOTAL -> Experiment051BlockEntity.this.cookTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_LIT_TIME -> Experiment051BlockEntity.this.litTime = value;
                case DATA_LIT_DURATION -> Experiment051BlockEntity.this.litDuration = value;
                case DATA_COOK_PROGRESS -> Experiment051BlockEntity.this.cookProgress = value;
                case DATA_COOK_TOTAL -> Experiment051BlockEntity.this.cookTotal = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public Experiment051BlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXPERIMENT_051.get(), pos, state);
    }

    public ContainerData dataAccess() {
        return this.dataAccess;
    }

    /**
     * 一个机器刻
     *
     * <p>骨架照原版熔炉的 {@code AbstractFurnaceBlockEntity#serverTick}：先判断有没有活可干，再决定烧不烧燃料、
     * 推不推进度、灭了火要不要把进度往回退。差别是「有没有活可干」由 3x3 出料格决定，
     * 而不是原版那一个结果格
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, Experiment051BlockEntity entity) {
        boolean wasLit = entity.isLit();
        boolean changed = false;
        if (entity.litTime > 0) {
            entity.litTime--;
        }

        entity.refreshPlan();
        UncraftingPlan plan = entity.cachedPlan;
        boolean canWork = entity.canWork(plan);

        // 没火就点一把；点不着（燃料格空着或者烧不动）就什么都不做
        if (!entity.isLit() && canWork && entity.consumeFuel()) {
            changed = true;
        }

        if (entity.isLit() && canWork) {
            entity.cookProgress++;
            if (entity.cookProgress >= entity.cookTotal) {
                entity.cookProgress = 0;
                entity.produce(plan);
                changed = true;
            }
        } else if (entity.isLit()) {
            // 有火但干不了活：原料不够或者出料格堵了，进度清零，燃料继续烧完
            entity.cookProgress = 0;
        } else if (entity.cookProgress > 0) {
            // 火灭了，进度像原版一样往回退
            entity.cookProgress = Mth.clamp(entity.cookProgress - 2, 0, Math.max(entity.cookTotal, 1));
        }

        if (wasLit != entity.isLit()) {
            changed = true;
            level.setBlock(pos, state.setValue(Experiment051Block.ACTIVE, entity.isLit()), Block.UPDATE_ALL);
        }
        if (changed) {
            setChanged(level, pos, state);
        }
    }

    /**
     * 重算配方，只在原料换了、数量变多了、不够当前配方吃了、或者数据包重载过时才真的扫一遍
     *
     * <p>数据包重载会换掉 {@code RecipeManager} 实例，所以比对象就够了，不需要另外挂重载监听
     */
    private void refreshPlan() {
        if (this.level == null) {
            return;
        }
        ItemStack input = this.items.get(INPUT_SLOT);
        RecipeManager manager = this.level.getRecipeManager();
        boolean itemChanged = !ItemStack.isSameItemSameComponents(input, this.cachedInput);
        boolean grew = input.getCount() > this.cachedInput.getCount();
        boolean starved = input.getCount() < this.cachedPlan.inputCount();
        if (manager == this.cachedManager && !itemChanged && !grew && !starved) {
            return;
        }
        UncraftingPlan previous = this.cachedPlan;
        this.cachedManager = manager;
        this.cachedInput = input.copy();
        this.cachedPlan = UncraftingResolver.resolve(this.level, input);
        // 漏斗续料只让数量变大时重扫结果通常还是同一条配方，这时进度必须保留，否则机器永远拆不完
        // 只有配方本身变了（换了物品、数据包重载、原料不够吃了）才清零进度并重算总时长
        if (!this.cachedPlan.equals(previous)) {
            this.cookProgress = 0;
            this.cookTotal = Math.max(1, this.cachedPlan.cookTime());
        }
    }

    /** 有配方、原料够、并且 3x3 出料格全都放得下 */
    private boolean canWork(UncraftingPlan plan) {
        if (plan.isNone() || !plan.producesAnything()) {
            return false;
        }
        if (this.items.get(INPUT_SLOT).getCount() < plan.inputCount()) {
            return false;
        }
        return canStore(plan);
    }

    /**
     * 这一次的产物能不能全塞进 3x3
     *
     * <p>逐格比：空格当然行；同物品同组件的要并得进去；其余一律算堵。不可堆叠的产物（堆叠上限 1）
     * 拆一次就把那格填满，第二次这里就过不去，于是自然停住——「存在不可堆叠的产物时配方堵塞」不用单独判
     */
    private boolean canStore(UncraftingPlan plan) {
        for (int i = 0; i < OUTPUT_COUNT; i++) {
            ItemStack product = plan.products().get(i);
            if (product.isEmpty()) {
                continue;
            }
            ItemStack stored = this.items.get(OUTPUT_FIRST + i);
            if (stored.isEmpty()) {
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(stored, product)) {
                return false;
            }
            if (stored.getCount() + product.getCount() > stored.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    /** 把产物并进 3x3，再从原料格扣掉这一次吃掉的量 */
    private void produce(UncraftingPlan plan) {
        for (int i = 0; i < OUTPUT_COUNT; i++) {
            ItemStack product = plan.products().get(i);
            if (product.isEmpty()) {
                continue;
            }
            ItemStack stored = this.items.get(OUTPUT_FIRST + i);
            if (stored.isEmpty()) {
                this.items.set(OUTPUT_FIRST + i, product.copy());
            } else {
                stored.grow(product.getCount());
            }
        }
        this.items.get(INPUT_SLOT).shrink(plan.inputCount());
    }

    /**
     * 烧一份燃料
     *
     * <p>燃料值走 NeoForge 的 {@code ItemStack#getBurnTime(RecipeType)}，熔炉能烧的这里都能烧。
     * 剩下容器的那类燃料（岩浆桶）按原版熔炉的写法把剩余物放回燃料格，而不是硬扣一个
     */
    private boolean consumeFuel() {
        ItemStack fuel = this.items.get(FUEL_SLOT);
        int burnTime = fuel.getBurnTime(RecipeType.SMELTING);
        if (burnTime <= 0) {
            return false;
        }
        this.litDuration = burnTime;
        this.litTime = burnTime;
        if (fuel.hasCraftingRemainingItem()) {
            this.items.set(FUEL_SLOT, fuel.getCraftingRemainingItem());
        } else {
            fuel.shrink(1);
            if (fuel.isEmpty()) {
                this.items.set(FUEL_SLOT, ItemStack.EMPTY);
            }
        }
        return true;
    }

    public boolean isLit() {
        return this.litTime > 0;
    }

    /** 看火焰那半边的进度，与熔炉菜单的 {@code getLitProgress} 同形 */
    public float litProgress() {
        int duration = this.litDuration == 0 ? 200 : this.litDuration;
        return Mth.clamp((float) this.litTime / (float) duration, 0.0F, 1.0F);
    }

    /** 箭头那半边的进度 */
    public float cookProgressRatio() {
        return this.cookTotal != 0 && this.cookProgress != 0
            ? Mth.clamp((float) this.cookProgress / (float) this.cookTotal, 0.0F, 1.0F)
            : 0.0F;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (side) {
            case UP -> TOP_SLOTS;
            case DOWN -> BOTTOM_SLOTS;
            default -> SIDE_SLOTS;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (side == null || !this.canPlaceItem(slot, stack)) {
            return false;
        }
        return switch (side) {
            case UP -> slot == INPUT_SLOT;
            case DOWN -> false;
            default -> slot == FUEL_SLOT;
        };
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != Direction.UP && slot >= OUTPUT_FIRST;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == FUEL_SLOT) {
            return stack.getBurnTime(RecipeType.SMELTING) > 0;
        }
        // 原料格什么都收：拆不了的东西摆着也不动，漏斗往里灌不会崩，拆得动就有进度
        return slot == INPUT_SLOT;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.dawnduskterminal.experiment_051");
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
        return new Experiment051Menu(containerId, inventory, this, this.dataAccess);
    }

    /**
     * 开界面时把自己的坐标带给客户端
     *
     * <p>1.21.1 原版的 {@code ClientboundOpenScreenPacket} 不带坐标，菜单那边靠这一段才找得到方块实体；
     * 这也是 NeoForge 的 {@code IMenuProviderExtension} 约定的扩展点
     */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.worldPosition);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
        this.litTime = tag.getInt("LitTime");
        this.litDuration = tag.getInt("LitDuration");
        this.cookProgress = tag.getInt("CookProgress");
        this.cookTotal = Math.max(1, tag.getInt("CookTotal"));
        // 读档后第一 tick 会重新扫配方，这里先把缓存清掉，免得拿上一份存档的残余去干活
        this.cachedManager = null;
        this.cachedInput = ItemStack.EMPTY;
        this.cachedPlan = UncraftingPlan.none();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
        tag.putInt("LitTime", this.litTime);
        tag.putInt("LitDuration", this.litDuration);
        tag.putInt("CookProgress", this.cookProgress);
        tag.putInt("CookTotal", this.cookTotal);
    }
}
