package com.dawnduskterminal.client;

import com.dawnduskterminal.inventory.Experiment004Menu;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.recipebook.SmeltingRecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 试验物品 004 的界面：原版熔炉的 176x166 面板，火苗与箭头也是原版熔炉那两个 sprite
 *
 * <p>菜单是 {@link Experiment004Menu}，它继承自 {@link net.minecraft.world.inventory.AbstractFurnaceMenu}，
 * 所以这里只比原版 {@code FurnaceScreen} 少写一个参数：面板贴图与两个 sprite 的路径都照抄原版
 * {@code FurnaceScreen}（{@code textures/gui/container/furnace.png}、{@code container/furnace/lit_progress}、
 * {@code container/furnace/burn_progress}），不另画一张图
 */
@OnlyIn(Dist.CLIENT)
public class Experiment004Screen extends AbstractFurnaceScreen<Experiment004Menu> {
    private static final ResourceLocation LIT_PROGRESS =
        ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");

    private static final ResourceLocation BURN_PROGRESS =
        ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");

    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");

    public Experiment004Screen(Experiment004Menu menu, Inventory inventory, Component title) {
        super(menu, new SmeltingRecipeBookComponent(), inventory, title, TEXTURE, LIT_PROGRESS, BURN_PROGRESS);
    }
}
