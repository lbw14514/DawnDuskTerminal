package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModExperimentItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * 试验物品 011 摆下来的那只盘子
 *
 * <p>它是一张 14 像素见方、1 像素厚的薄片，碰撞箱与外形一致（{@code 14/16 x 1/16 x 14/16}，见
 * {@code ModEntities#EXPERIMENT_011_PLATE}）。不算生物、不走重力、不会动：位置在摆下来那一刻就由
 * {@link com.dawnduskterminal.item.Experiment011Item} 按脚下那块方块的碰撞箱算好了，之后一辈子不挪
 *
 * <p>盘子上能摆一件东西，只占一格：手里的物品右键放上去，空手右键拿回来，潜行空手右键把盘子本身收走，
 * 左键砸掉会把盘子与上面的东西一起掉出来。展示物存在同步数据里（{@link #DATA_DISPLAYED}），
 * 存档里存在 {@value #DISPLAYED_TAG} 这一项，客户端画成方块还是面片由
 * {@code client/Experiment011PlateRenderer} 决定
 */
public class Experiment011PlateEntity extends Entity {
    /** 盘子上摆着的那一件。空手摆下来的盘子这里就是 {@link ItemStack#EMPTY} */
    private static final EntityDataAccessor<ItemStack> DATA_DISPLAYED =
            SynchedEntityData.defineId(Experiment011PlateEntity.class, EntityDataSerializers.ITEM_STACK);

    /** 存档里的键：一件物品，最长也就一格的量 */
    private static final String DISPLAYED_TAG = "displayed";

    public Experiment011PlateEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DISPLAYED, ItemStack.EMPTY);
    }

    /** 盘子上摆着的那一件，没摆东西时是 {@link ItemStack#EMPTY} */
    public ItemStack displayed() {
        return this.entityData.get(DATA_DISPLAYED);
    }

    /**
     * 换掉盘子上摆着的东西
     *
     * <p>只留一个：传进来的那一栈先截成 1，免得把数量带进展示里；空栈归一成
     * {@link ItemStack#EMPTY}，读档时那一项缺着也是这条路径
     */
    public void setDisplayed(ItemStack stack) {
        this.entityData.set(DATA_DISPLAYED, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    /**
     * 右键：手上有东西就摆上去，空手就把东西拿回来，潜行空手则把盘子本身收走
     *
     * <p>潜行优先于「拿回东西」：潜行空手永远是收盘子本身，盘子上有东西就先把它掉在脚下，
     * 否则盘子上摆了东西时潜行分支永远到不了（原来是先判 displayed 非空）
     *
     * <p>客户端只回 {@link InteractionResult#SUCCESS} 让手抬一下，真正的判断全在服务端；
     * 摆不进去（盘子上已经有东西、手是空的且没潜行）时回 {@link InteractionResult#PASS}，
     * 让这次右键继续往下走，不至于把别的交互吃掉
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        ItemStack displayed = this.displayed();
        if (held.isEmpty()) {
            if (player.isSecondaryUseActive()) {
                if (!displayed.isEmpty()) {
                    this.spawnAtLocation(displayed);
                }
                this.playSound(SoundEvents.ITEM_FRAME_BREAK, 0.8F, 1.0F);
                this.give(player, new ItemStack(ModExperimentItems.EXPERIMENT_011.get()));
                this.discard();
                return InteractionResult.CONSUME;
            }
            if (!displayed.isEmpty()) {
                this.setDisplayed(ItemStack.EMPTY);
                this.give(player, displayed);
                this.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.8F, 1.0F);
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        // 一次只摆一件：盘子上已经有东西时先拿走再放
        if (!displayed.isEmpty()) {
            return InteractionResult.PASS;
        }
        this.setDisplayed(held);
        held.shrink(1);
        this.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.8F, 1.0F);
        return InteractionResult.CONSUME;
    }

    /**
     * 左键砸掉：盘子与上面的东西一起掉出来
     *
     * <p>创造模式的玩家砸掉什么都不掉，与原版画、物品展示框一致；{@code doEntityDrops} 关掉时也不掉。
     * 非玩家砸掉的（箭、爆炸）照常掉
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        boolean drops = this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)
                && !(source.getEntity() instanceof Player player && player.hasInfiniteMaterials());
        if (drops) {
            ItemStack displayed = this.displayed();
            if (!displayed.isEmpty()) {
                this.spawnAtLocation(displayed);
            }
            this.spawnAtLocation(new ItemStack(ModExperimentItems.EXPERIMENT_011.get()));
        }
        this.playSound(SoundEvents.ITEM_FRAME_BREAK, 0.8F, 1.0F);
        this.discard();
        return true;
    }

    /** 进背包：塞得下就进背包，塞不下就掉在脚下 */
    private void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setDisplayed(ItemStack.parseOptional(this.registryAccess(), tag.getCompound(DISPLAYED_TAG)));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ItemStack displayed = this.displayed();
        if (!displayed.isEmpty()) {
            tag.put(DISPLAYED_TAG, displayed.saveOptional(this.registryAccess()));
        }
    }

    /** 打得着：{@code isPickable} 为假的话左键会直接穿过去，{@link #hurt} 永远收不到 */
    @Override
    public boolean isPickable() {
        return true;
    }

    /** 推不动：它不是被推着走的实体，把它挤开只会让盘子与脚下的方块错位 */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModExperimentItems.EXPERIMENT_011.get());
    }
}
