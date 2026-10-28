package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.inventory.E115RegeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 115 再生机的界面：面板是 {@code textures/gui/e_115_regenerator.png}（256x256 画布，
 * 176x166 的面板画在左上角），不另注册 sprite
 *
 * <p>面板上只有一格机器格加玩家背包那 36 格，位置与 {@link E115RegeneratorMenu} 的常量一一对应：
 * 机器格 (80, 35) 正好是原版 3x3 容器正中那一格，背包 (8, 84) 起、快捷栏 (8, 142)，
 * 这几个数是从原版 {@code DispenserMenu} 的坐标来的，与面板上画好的凹槽严丝合缝
 */
@OnlyIn(Dist.CLIENT)
public class E115RegeneratorScreen extends AbstractContainerScreen<E115RegeneratorMenu> {
    private static final ResourceLocation TEXTURE = DawnDuskTerminal.id("textures/gui/e_115_regenerator.png");

    public E115RegeneratorScreen(E115RegeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = E115RegeneratorMenu.IMAGE_WIDTH;
        this.imageHeight = E115RegeneratorMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle,
            this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}
