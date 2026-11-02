package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.decoration.PaintingVariant;

/**
 * 画的变体是数据包注册表（{@code Registries.PAINTING_VARIANT}，数据在
 * {@code data/dawnduskterminal/painting_variant/}），没有 DeferredRegister 可挂，
 * 这里只放指向那个条目的键和取值入口
 *
 * <p>尺寸 3x2 只写在 JSON 里，Java 侧不再抄一份：{@code PaintingVariant} 是 record，
 * 包围盒与渲染都从它身上读，重复一份反而会走岔
 */
public final class ModPaintingVariants {
    /** 3 格宽 2 格高，贴图 assets/dawnduskterminal/textures/painting/painting_ghast_1.png */
    public static final ResourceKey<PaintingVariant> GHAST_1 =
            ResourceKey.create(Registries.PAINTING_VARIANT, DawnDuskTerminal.id("painting_ghast_1"));

    /** 服务端与客户端都能调：变体跟着数据包同步，两边查同一张表 */
    public static Holder<PaintingVariant> holder(RegistryAccess registries) {
        return registries.registryOrThrow(Registries.PAINTING_VARIANT).getHolderOrThrow(GHAST_1);
    }

    private ModPaintingVariants() {}
}
