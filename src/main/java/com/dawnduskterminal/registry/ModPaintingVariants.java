package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * 画的变体是数据包注册表（{@code Registries.PAINTING_VARIANT}，数据在
 * {@code data/dawnduskterminal/painting_variant/}），没有 DeferredRegister 可挂，
 * 这里只放指向那些条目的键与「一幅画配一件物品」的对应关系
 *
 * <p>尺寸只写在 JSON 里，Java 侧不再抄一份：{@code PaintingVariant} 是 record，
 * 包围盒与渲染都从它身上读，重复一份反而会走岔
 *
 * <p>{@link Kind} 把变体键与它对应的那一件物品绑在一起：实体靠它决定自己画什么、掉什么，
 * 物品靠它决定自己放哪一幅，三处读的是同一个对象，不会各写一份而走岔
 */
public final class ModPaintingVariants {
    /** 3 格宽 2 格高，贴图 assets/dawnduskterminal/textures/painting/painting_ghast_1.png */
    public static final Kind GHAST_1 = new Kind("painting_ghast_1", ModItems.PAINTING_GHAST_1);

    /** 2 格宽 3 格高，贴图 assets/dawnduskterminal/textures/painting/painting_ghast_2.png */
    public static final Kind GHAST_2 = new Kind("painting_ghast_2", ModItems.PAINTING_GHAST_2);

    /** 1 格宽 2 格高，贴图 assets/dawnduskterminal/textures/painting/painting_ghast_2_sub.png */
    public static final Kind GHAST_2_SUB = new Kind("painting_ghast_2_sub", ModItems.PAINTING_GHAST_2_SUB);

    /** 3 格宽 2 格高，贴图 assets/dawnduskterminal/textures/painting/painting_ghast_3.png，名字是 Drop */
    public static final Kind GHAST_3 = new Kind("painting_ghast_3", ModItems.PAINTING_GHAST_3);

    /** 2 格宽 3 格高，贴图 assets/dawnduskterminal/textures/painting/painting_ghast_4.png，名字是 Ragdoll */
    public static final Kind GHAST_4 = new Kind("painting_ghast_4", ModItems.PAINTING_GHAST_4);

    /** 2 格宽 3 格高，贴图 assets/dawnduskterminal/textures/painting/painting_ghast_5.png，名字是 Bakery */
    public static final Kind GHAST_5 = new Kind("painting_ghast_5", ModItems.PAINTING_GHAST_5);

    /** 2 格宽 3 格高，贴图 assets/dawnduskterminal/textures/painting/painting_ghast_6.png，名字是 Butchess */
    public static final Kind GHAST_6 = new Kind("painting_ghast_6", ModItems.PAINTING_GHAST_6);

    /** 2 格宽 3 格高，贴图 assets/dawnduskterminal/textures/painting/painting_ghast_7.png，名字是 Maze */
    public static final Kind GHAST_7 = new Kind("painting_ghast_7", ModItems.PAINTING_GHAST_7);

    /** 2 格宽 3 格高，贴图 assets/dawnduskterminal/textures/painting/painting_ghast_8.png，名字是 Onesama */
    public static final Kind GHAST_8 = new Kind("painting_ghast_8", ModItems.PAINTING_GHAST_8);

    /** 6 格宽 4 格高，贴图 assets/dawnduskterminal/textures/painting/painting_ghast_9.png，名字是 Feuer */
    public static final Kind GHAST_9 = new Kind("painting_ghast_9", ModItems.PAINTING_GHAST_9);

    /** 全部十幅，{@code PaintingVariantGuardMixin} 与测试照这张表走 */
    public static final List<Kind> ALL =
            List.of(GHAST_1, GHAST_2, GHAST_2_SUB, GHAST_3, GHAST_4, GHAST_5, GHAST_6, GHAST_7, GHAST_8, GHAST_9);

    /** 服务端与客户端都能调：变体跟着数据包同步，两边查同一张表 */
    public static Holder<PaintingVariant> holder(RegistryAccess registries, Kind kind) {
        return registries.registryOrThrow(Registries.PAINTING_VARIANT).getHolderOrThrow(kind.variant());
    }

    /** 这个键是不是本模组的画之一：原版画实体靠它把外来变体拒之门外 */
    public static boolean isModVariant(ResourceKey<PaintingVariant> key) {
        return ALL.stream().anyMatch(kind -> kind.variant().equals(key));
    }

    /** 一幅画：变体键 + 与它唯一对应的那件物品 */
    public record Kind(ResourceKey<PaintingVariant> variant, DeferredItem<? extends Item> item) {
        private Kind(String path, DeferredItem<? extends Item> item) {
            this(ResourceKey.create(Registries.PAINTING_VARIANT, DawnDuskTerminal.id(path)), item);
        }
    }

    private ModPaintingVariants() {}
}
