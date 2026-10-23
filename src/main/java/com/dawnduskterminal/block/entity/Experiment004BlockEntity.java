package com.dawnduskterminal.block.entity;

import com.dawnduskterminal.inventory.Experiment004Menu;
import com.dawnduskterminal.registry.ModBlockEntities;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 试验物品 004：照熔炉工作的那一台
 *
 * <p>骨架逐段照抄原版 {@code AbstractFurnaceBlockEntity#serverTick}：一格原料、一格燃料、一格产物，
 * 数据槽同样是「剩余燃烧时间、这份燃料能烧多久、进度、总时间」四个数，索引与原版完全一致，所以界面那边
 * 直接复用 {@code AbstractFurnaceMenu} 的火焰与箭头，方块实体这边不用另写一套界面逻辑
 *
 * <p>不能直接继承 {@code AbstractFurnaceBlockEntity}：它的 {@code serverTick} 是静态方法，烧制时间也由
 * 它自己按配方现算（私有的 {@code getTotalCookTime}），两处都没有留给子类的口子，而「烧食物快一倍」
 * 恰恰要改这两处。于是这里抄一份骨架、把时间换成 {@link #cookTotalFor}：产物带
 * {@link DataComponents#FOOD} 的按配方时间的一半算（原版 10 秒的熔炼配方就是 5 秒），其余照配方原值
 *
 * <p>粒子不产生：原版熔炉的烟与火是 {@code FurnaceBlock#animateTick} 写的，本方块根本没抄那一段；
 * 火响（{@code block.furnace.fire_crackle}）照原版以约一成概率放，见方块类的 {@code animateTick}
 *
 * <p>漏斗：顶面进原料、侧面进燃料、底面抽产物，与熔炉一致；合法性统一走 {@link #canPlaceItem(int, ItemStack)}
 *
 * <p>经验按原版熔炉那套在取产物时结算：每烧成一次记一条 {@code recipesUsed}，玩家从产物格拿走东西时由
 * {@link Experiment004Menu} 的产物格调 {@link #awardExperience}，方块被敲掉时按
 * {@link #popExperienceIntoWorld} 撒在方块中心
 */
public class Experiment004BlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, StackedContentsCompatible {
    public static final int INPUT_SLOT = 0;
    public static final int FUEL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    public static final int CONTAINER_SIZE = 3;

    public static final int DATA_LIT_TIME = 0;
    public static final int DATA_LIT_DURATION = 1;
    public static final int DATA_COOK_PROGRESS = 2;
    public static final int DATA_COOK_TOTAL = 3;
    public static final int DATA_COUNT = 4;

    /** 原版熔炉一锅的常规时间，也是 {@code cookTotal} 读档时缺省的那个值 */
    public static final int DEFAULT_COOK_TOTAL = 200;

    private static final int[] SLOTS_FOR_UP = {INPUT_SLOT};
    private static final int[] SLOTS_FOR_DOWN = {RESULT_SLOT, FUEL_SLOT};
    private static final int[] SLOTS_FOR_SIDES = {FUEL_SLOT};

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    /** 还能烧几 tick，大于 0 就是 {@code lit=true} 的那一版 */
    private int litTime;

    /** 当前这份燃料一共能烧几 tick，客户端拿它算火焰高度 */
    private int litDuration;

    private int cookProgress;

    private int cookTotal = DEFAULT_COOK_TOTAL;

    /** 这一锅烧成了几次，按配方 ID 计数；取产物或敲方块时换成经验 */
    private final Object2IntOpenHashMap<ResourceLocation> recipesUsed = new Object2IntOpenHashMap<>();

    // 配方每 tick 都要查，缓存按「配方表实例 + 原料物品与组件」失效：数据包重载换掉 RecipeManager，原料换了缓存也就用不上
    @Nullable
    private RecipeManager cachedManager;

    private ItemStack cachedRecipeInput = ItemStack.EMPTY;

    @Nullable
    private RecipeHolder<SmeltingRecipe> cachedRecipe;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                // 数据槽在网络上按 short 同步，超过 Short.MAX_VALUE 的燃料按比例缩一下，原版熔炉也这么处理
                case DATA_LIT_TIME -> Experiment004BlockEntity.this.litDuration > Short.MAX_VALUE
                    ? Mth.floor((double) Experiment004BlockEntity.this.litTime
                        / (double) Experiment004BlockEntity.this.litDuration * Short.MAX_VALUE)
                    : Experiment004BlockEntity.this.litTime;
                case DATA_LIT_DURATION -> Math.min(Experiment004BlockEntity.this.litDuration, Short.MAX_VALUE);
                case DATA_COOK_PROGRESS -> Experiment004BlockEntity.this.cookProgress;
                case DATA_COOK_TOTAL -> Experiment004BlockEntity.this.cookTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_LIT_TIME -> Experiment004BlockEntity.this.litTime = value;
                case DATA_LIT_DURATION -> Experiment004BlockEntity.this.litDuration = value;
                case DATA_COOK_PROGRESS -> Experiment004BlockEntity.this.cookProgress = value;
                case DATA_COOK_TOTAL -> Experiment004BlockEntity.this.cookTotal = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public Experiment004BlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXPERIMENT_004.get(), pos, state);
    }

    public ContainerData dataAccess() {
        return this.dataAccess;
    }

    /**
     * 一个机器刻
     *
     * <p>骨架照原版熔炉：先看有没有活可干，再点燃料、推进度、灭了火把进度往回退。差别只有「总时间」的来源，
     * 见 {@link #cookTotalFor}
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, Experiment004BlockEntity entity) {
        boolean wasLit = entity.isLit();
        boolean changed = false;
        if (entity.litTime > 0) {
            entity.litTime--;
        }

        ItemStack input = entity.items.get(INPUT_SLOT);
        ItemStack fuel = entity.items.get(FUEL_SLOT);
        RecipeHolder<SmeltingRecipe> recipe = entity.recipeFor(input);
        ItemStack result = recipe == null ? ItemStack.EMPTY : entity.resultFor(recipe, input);
        boolean canCook = recipe != null && entity.canAcceptResult(result);

        if (entity.isLit() || (!fuel.isEmpty() && canCook)) {
            // 没火就点一把；燃料格空着或者烧不动就什么都不做
            if (!entity.isLit() && canCook) {
                int burnTime = fuel.getBurnTime(RecipeType.SMELTING);
                if (burnTime > 0) {
                    entity.litTime = burnTime;
                    entity.litDuration = burnTime;
                    ItemStack remaining = fuel.getCraftingRemainingItem();
                    fuel.shrink(1);
                    if (fuel.isEmpty()) {
                        entity.items.set(FUEL_SLOT, remaining);
                    }
                    changed = true;
                }
            }

            if (entity.isLit() && canCook) {
                entity.cookProgress++;
                if (entity.cookProgress >= entity.cookTotal) {
                    entity.cookProgress = 0;
                    entity.produce(recipe, result);
                    // 与熔炉一样在出锅这一刻重算下一锅的总时间
                    entity.cookTotal = entity.cookTotalFor(recipe, result);
                    changed = true;
                }
            } else {
                // 有火但干不了活：原料没了、没有配方，或者产物格放不下，进度清零
                entity.cookProgress = 0;
            }
        } else if (!entity.isLit() && entity.cookProgress > 0) {
            // 火灭了，进度像原版一样往回退
            entity.cookProgress = Mth.clamp(entity.cookProgress - 2, 0, entity.cookTotal);
        }

        if (wasLit != entity.isLit()) {
            changed = true;
            level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, entity.isLit()), Block.UPDATE_ALL);
        }
        if (changed) {
            setChanged(level, pos, state);
        }
    }

    /**
     * 当前原料对应的熔炼配方，查不到就是 {@code null}
     *
     * <p>结果按「配方表实例 + 原料物品与组件」缓存：数量变了不算换原料（熔炼只吃一个），
     * 数据包重载换掉 {@code RecipeManager} 实例时缓存自动失效
     */
    @Nullable
    private RecipeHolder<SmeltingRecipe> recipeFor(ItemStack input) {
        if (this.level == null || input.isEmpty()) {
            this.cachedManager = null;
            this.cachedRecipeInput = ItemStack.EMPTY;
            this.cachedRecipe = null;
            return null;
        }
        RecipeManager manager = this.level.getRecipeManager();
        if (manager == this.cachedManager && ItemStack.isSameItemSameComponents(this.cachedRecipeInput, input)) {
            return this.cachedRecipe;
        }
        this.cachedManager = manager;
        this.cachedRecipeInput = input.copy();
        this.cachedRecipe = manager
            .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), this.level)
            .orElse(null);
        return this.cachedRecipe;
    }

    private ItemStack resultFor(RecipeHolder<SmeltingRecipe> recipe, ItemStack input) {
        return recipe.value().assemble(new SingleRecipeInput(input), this.level.registryAccess());
    }

    /**
     * 这一锅要烧几 tick
     *
     * <p>产物带食物组件就按配方时间的一半算，所以原版 200 tick 的土豆→烤土豆是 100 tick，
     * 粗铁→铁锭仍是 200；判断看产物而不是原料，海带→干海带这种「原料不是食物、产物是」的也算
     */
    private int cookTotalFor(RecipeHolder<SmeltingRecipe> recipe, ItemStack result) {
        int base = Math.max(1, recipe.value().getCookingTime());
        return isFood(result) ? Math.max(1, base / 2) : base;
    }

    private static boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.get(DataComponents.FOOD) != null;
    }

    /** 原料换了就按新配方重算总时间，读档与摆放后的第一刻也靠它 */
    private void refreshCookTotal() {
        ItemStack input = this.items.get(INPUT_SLOT);
        RecipeHolder<SmeltingRecipe> recipe = recipeFor(input);
        this.cookTotal = recipe == null ? DEFAULT_COOK_TOTAL : this.cookTotalFor(recipe, this.resultFor(recipe, input));
    }

    /** 产物格放不放得下这一次的产量，与原版熔炉的 {@code canBurn} 后半段同形 */
    private boolean canAcceptResult(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        ItemStack stored = this.items.get(RESULT_SLOT);
        if (stored.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(stored, result)) {
            return false;
        }
        int total = stored.getCount() + result.getCount();
        return total <= this.getMaxStackSize() && total <= stored.getMaxStackSize();
    }

    /**
     * 出锅：产物并进产物格、原料扣一个、记一条配方
     *
     * <p>湿海绵配空桶那一手照原版熔炉：烧干湿海绵时燃料格里的空桶换成水桶
     */
    private void produce(RecipeHolder<SmeltingRecipe> recipe, ItemStack result) {
        ItemStack stored = this.items.get(RESULT_SLOT);
        if (stored.isEmpty()) {
            this.items.set(RESULT_SLOT, result.copy());
        } else {
            stored.grow(result.getCount());
        }

        ItemStack input = this.items.get(INPUT_SLOT);
        if (input.is(Items.WET_SPONGE) && this.items.get(FUEL_SLOT).is(Items.BUCKET)) {
            this.items.set(FUEL_SLOT, new ItemStack(Items.WATER_BUCKET));
        }
        input.shrink(1);

        if (recipe != null) {
            this.recipesUsed.addTo(recipe.id(), 1);
        }
    }

    public boolean isLit() {
        return this.litTime > 0;
    }

    /**
     * 玩家从产物格拿走东西时结算经验，逐行照原版
     * {@code AbstractFurnaceBlockEntity#awardUsedRecipesAndPopExperience}：按每条配方烧成的次数乘它的经验值
     * 在玩家脚下撒球，再把配方记进统计与进度，最后清空计数
     */
    public void awardExperience(ServerPlayer player) {
        if (this.level == null || this.recipesUsed.isEmpty()) {
            return;
        }
        List<RecipeHolder<?>> awarded = new ArrayList<>();
        for (Object2IntMap.Entry<ResourceLocation> entry : this.recipesUsed.object2IntEntrySet()) {
            this.level.getRecipeManager().byKey(entry.getKey()).ifPresent(holder -> {
                awarded.add(holder);
                createExperience(player.serverLevel(), player.position(), entry.getIntValue(),
                    ((AbstractCookingRecipe) holder.value()).getExperience());
            });
        }
        player.awardRecipes(awarded);
        for (RecipeHolder<?> holder : awarded) {
            player.triggerRecipeCrafted(holder, this.items);
        }
        this.recipesUsed.clear();
    }

    /** 方块被敲掉时把还没结算的经验撒在方块中心，与原版熔炉拆掉时一样 */
    public void popExperienceIntoWorld(ServerLevel level, Vec3 pos) {
        for (Object2IntMap.Entry<ResourceLocation> entry : this.recipesUsed.object2IntEntrySet()) {
            level.getRecipeManager().byKey(entry.getKey()).ifPresent(holder ->
                createExperience(level, pos, entry.getIntValue(),
                    ((AbstractCookingRecipe) holder.value()).getExperience()));
        }
        this.recipesUsed.clear();
    }

    private static void createExperience(ServerLevel level, Vec3 pos, int count, float experience) {
        int amount = Mth.floor((float) count * experience);
        float fraction = Mth.frac((float) count * experience);
        if (fraction != 0.0F && Math.random() < (double) fraction) {
            amount++;
        }
        ExperienceOrb.award(level, pos, amount);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (side) {
            case UP -> SLOTS_FOR_UP;
            case DOWN -> SLOTS_FOR_DOWN;
            default -> SLOTS_FOR_SIDES;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side != Direction.DOWN || slot != FUEL_SLOT
            || stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == RESULT_SLOT) {
            return false;
        }
        if (slot == FUEL_SLOT) {
            ItemStack stored = this.items.get(FUEL_SLOT);
            return stack.getBurnTime(RecipeType.SMELTING) > 0
                || stack.is(Items.BUCKET) && !stored.is(Items.BUCKET);
        }
        return slot == INPUT_SLOT;
    }

    /**
     * 原料换了就把进度清零、总时间重算
     *
     * <p>原版熔炉在 {@code setItem} 里做同一件事：只有物品或组件真的变了才算换，往同一格塞同一种东西
     * （数量变多）不清进度。总时间要按配方重算，所以这里必须覆盖掉基类那个只写格子的实现
     */
    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack previous = this.items.get(slot);
        boolean same = !stack.isEmpty() && ItemStack.isSameItemSameComponents(previous, stack);
        super.setItem(slot, stack);
        if (slot == INPUT_SLOT && !same) {
            this.cookProgress = 0;
            this.refreshCookTotal();
        }
    }

    @Override
    public void fillStackedContents(StackedContents contents) {
        for (ItemStack stack : this.items) {
            contents.accountStack(stack);
        }
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.dawnduskterminal.experiment_004");
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
        return new Experiment004Menu(containerId, inventory, this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
        this.litTime = tag.getInt("BurnTime");
        this.litDuration = tag.getInt("BurnDuration");
        this.cookProgress = tag.getInt("CookTime");
        this.cookTotal = Math.max(1, tag.getInt("CookTimeTotal"));
        this.recipesUsed.clear();
        CompoundTag used = tag.getCompound("RecipesUsed");
        for (String key : used.getAllKeys()) {
            this.recipesUsed.put(ResourceLocation.parse(key), used.getInt(key));
        }
        // 读档后重新查配方，不拿上一份存档的缓存去干活
        this.cachedManager = null;
        this.cachedRecipeInput = ItemStack.EMPTY;
        this.cachedRecipe = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
        tag.putInt("BurnTime", this.litTime);
        tag.putInt("BurnDuration", this.litDuration);
        tag.putInt("CookTime", this.cookProgress);
        tag.putInt("CookTimeTotal", this.cookTotal);
        CompoundTag used = new CompoundTag();
        this.recipesUsed.forEach((id, count) -> used.putInt(id.toString(), count));
        tag.put("RecipesUsed", used);
    }
}
