package com.dawnduskterminal.item;

import com.dawnduskterminal.entity.GhastPaintingEntity;
import com.dawnduskterminal.registry.ModPaintingVariants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 恶魂画的物品形态，三幅画共用。摆放流程照抄原版 {@code HangingEntityItem}，只把「挑哪幅画」这一步
 * 换成「永远是注册时指定的这一幅」
 *
 * <p>不能继承 {@code HangingEntityItem}：那个类的 {@code useOn} 只认
 * {@code PAINTING} / {@code ITEM_FRAME} / {@code GLOW_ITEM_FRAME} 三种实体类型，
 * 别的类型一律走「什么都不生成，只返回成功」的分支
 *
 * <p>实体类型收的是 {@link DeferredHolder} 而不是现成的 {@code EntityType}：物品注册跑在实体类型之前，
 * 这个引用要等到真正摆放的那一刻才取值
 */
public class GhastPaintingItem extends Item {
    private final DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> entityType;
    private final ModPaintingVariants.Kind kind;

    public GhastPaintingItem(
            Properties properties,
            DeferredHolder<EntityType<?>, EntityType<GhastPaintingEntity>> entityType,
            ModPaintingVariants.Kind kind) {
        super(properties);
        this.entityType = entityType;
        this.kind = kind;
    }

    /** 这件物品对应的是哪一幅画 */
    public ModPaintingVariants.Kind kind() {
        return this.kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isVertical()) {
            return InteractionResult.FAIL;
        }

        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos anchor = context.getClickedPos().relative(face);
        if (player != null && !player.mayUseItemAt(anchor, face, stack)) {
            return InteractionResult.FAIL;
        }

        Level level = context.getLevel();
        GhastPaintingEntity painting = new GhastPaintingEntity(this.entityType.get(), level, this.kind, anchor, face);
        if (!painting.survives()) {
            return InteractionResult.CONSUME;
        }

        if (!level.isClientSide) {
            painting.playPlacementSound();
            level.gameEvent(player, GameEvent.ENTITY_PLACE, painting.position());
            level.addFreshEntity(painting);
        }

        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
