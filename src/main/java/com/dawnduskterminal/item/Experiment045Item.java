package com.dawnduskterminal.item;

import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.Item.Properties;

/**
 * 试验物品 045：盾
 *
 * <p>格挡那一套整份继承原版 {@code ShieldItem}，一点不改；物品形态的盾面渲染走
 * {@code client/Experiment045ShieldRenderer}（模型挂 {@code minecraft:builtin/entity}），
 * 渲染器要靠 {@code IClientItemExtensions#getCustomRenderer} 才挂得上
 */

public final class Experiment045Item extends ShieldItem {
    public static final int DURABILITY = 336;

    public Experiment045Item(Properties properties) {
        super(properties);
    }
}
