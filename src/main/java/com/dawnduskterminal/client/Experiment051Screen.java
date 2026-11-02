package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.inventory.Experiment051Menu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 试验物品 051 的界面：一张自画的 186x166 面板，火焰与箭头借原版熔炉的两个 sprite
 *
 * <p>1.21.1 把熔炉的火与箭头从 {@code gui/container/furnace.png} 里拆成了两个独立 sprite
 * （{@code container/furnace/lit_progress} 与 {@code container/furnace/burn_progress}），
 * 用 {@link GuiGraphics#blitSprite} 裁一段贴上去，所以火苗随燃料上涨、箭头随进度变长的那段照抄原版
 * {@code AbstractFurnaceScreen#renderBg} 就够了，不需要自己再画一套
 *
 * <p>面板里的凹槽位置见 {@link Experiment051Menu} 的坐标常量，两处必须一致
 */
@OnlyIn(Dist.CLIENT)
public class Experiment051Screen extends AbstractContainerScreen<Experiment051Menu> {
    private static final ResourceLocation TEXTURE = DawnDuskTerminal.id("textures/gui/experiment_051.png");

    /** 原版熔炉的火苗 sprite（14x14，从下往上亮） */
    private static final ResourceLocation LIT_PROGRESS =
            ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");

    /** 原版熔炉的箭头 sprite（24x16，从左往右长） */
    private static final ResourceLocation COOK_PROGRESS =
            ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");

    private static final int FLAME_SIZE = 14;
    private static final int ARROW_HEIGHT = 16;
    private static final int ARROW_WIDTH = 24;

    public Experiment051Screen(Experiment051Menu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = Experiment051Menu.IMAGE_WIDTH;
        this.imageHeight = Experiment051Menu.IMAGE_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        if (this.menu.isLit()) {
            int lit = Mth.ceil(this.menu.litProgress() * (FLAME_SIZE - 1)) + 1;
            graphics.blitSprite(
                    LIT_PROGRESS,
                    FLAME_SIZE,
                    FLAME_SIZE,
                    0,
                    FLAME_SIZE - lit,
                    x + Experiment051Menu.FLAME_X,
                    y + Experiment051Menu.FLAME_Y + FLAME_SIZE - lit,
                    FLAME_SIZE,
                    lit);
        }

        int progress = Mth.ceil(this.menu.cookProgress() * ARROW_WIDTH);
        graphics.blitSprite(
                COOK_PROGRESS,
                ARROW_WIDTH,
                ARROW_HEIGHT,
                0,
                0,
                x + Experiment051Menu.ARROW_X,
                y + Experiment051Menu.ARROW_Y,
                progress,
                ARROW_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(
                this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}
