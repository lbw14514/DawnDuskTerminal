package com.dawnduskterminal.entity;

import com.dawnduskterminal.registry.ModPaintingVariants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * 恶魂画：十幅画共用这一个类，每幅画自己的种类由 {@link ModPaintingVariants.Kind} 带进来
 *
 * <p>尺寸从 1x2 到 6x4 都有（{@code painting_ghast_2_sub} 最小，{@code painting_feuer} 最大），
 * 各占一个实体类型、各配一件物品；尺寸本身只写在
 * {@code data/dawnduskterminal/painting_variant/} 的 JSON 里
 *
 * <p>和 {@code minecraft:painting} 是两条互不相干的实体类型，互换的三种路子都堵死了：
 * <ul>
 *   <li>原版画物品只从 {@code #minecraft:painting_variant/placeable} 里挑变体（见
 *       {@code Painting#create}），我们的变体不在那个标签里，原版物品放不出这几幅画</li>
 *   <li>我们的物品只 {@code new} 自己那一个实体类型，放不出原版的任何一幅画</li>
 *   <li>存档 NBT 与 {@code /summon} 这条侧门由下面的 {@link #setVariant} 与
 *       {@code PaintingVariantGuardMixin} 两头堵：本实体强制用自己的变体，原版画实体不许用我们的变体</li>
 * </ul>
 *
 * <p>敲碎掉落与中键选取拿到的都是这一幅自己那件物品，三种之间也互不相通
 */
public class GhastPaintingEntity extends Painting {
    /** 这一幅画：变体键与它唯一对应的物品，构造时定下，之后不再变 */
    private final ModPaintingVariants.Kind kind;

    public GhastPaintingEntity(EntityType<? extends Painting> type, Level level, ModPaintingVariants.Kind kind) {
        super(type, level);
        this.kind = kind;
        this.setVariant(ModPaintingVariants.holder(level.registryAccess(), kind));
    }

    /**
     * 摆放用。原版 {@code Painting} 的带坐标构造写死了 {@code EntityType.PAINTING}，
     * 没法拿模组实体类型走那条路，所以照 {@code HangingEntity} 的写法自己接：
     * {@code pos} 是 {@code BlockAttachedEntity} 的 protected 字段，
     * {@code setDirection} 会以它为锚算包围盒并把实体挪到画面上
     *
     * <p>顺序照抄原版 {@code Painting#create} 与那个带变体的构造：先把变体写进去，
     * 再算方向 —— {@code calculateBoundingBox} 要从变体上读宽高
     */
    public GhastPaintingEntity(
            EntityType<? extends Painting> type,
            Level level,
            ModPaintingVariants.Kind kind,
            BlockPos pos,
            Direction direction) {
        this(type, level, kind);
        this.pos = pos;
        this.setDirection(direction);
    }

    /** 这一幅画自己的种类 */
    public ModPaintingVariants.Kind kind() {
        return this.kind;
    }

    /**
     * 变体固定成这一幅：构造、读档、数据同步带来的任何值都会被这里换掉，
     * 于是「这幅画只对应唯一物品」在实体层面也成立
     */
    @Override
    public void setVariant(Holder<PaintingVariant> variant) {
        super.setVariant(ModPaintingVariants.holder(this.registryAccess(), this.kind));
    }

    @Override
    public void dropItem(Entity breaker) {
        if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
            if (breaker instanceof Player player && player.hasInfiniteMaterials()) {
                return;
            }

            this.spawnAtLocation(this.kind.item().get());
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(this.kind.item().get());
    }
}
