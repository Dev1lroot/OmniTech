/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.recipes.GlassBlowingRecipe;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Screen for the Glass Blowing Station — same recipe-grid/scroller layout and interaction as
 * vanilla's stonecutter (borrowing its sprites and background directly, pending dedicated art),
 * plus a heat readout under the title showing the current temperature against whatever recipe
 * is selected.
 */
public class GlassBlowingStationScreen extends AbstractContainerScreen<GlassBlowingStationMenu> {

    private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/scroller");
    private static final Identifier SCROLLER_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/scroller_disabled");
    private static final Identifier RECIPE_SELECTED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");
    private static final Identifier RECIPE_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");
    private static final Identifier RECIPE_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe");
    private static final Identifier BG_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/stonecutter.png");

    private float scrollOffs;
    private boolean scrolling;
    private int startIndex;
    private boolean displayRecipes;

    public GlassBlowingStationScreen(GlassBlowingStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.titleLabelY--;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // broadcastChanges() (where the menu would normally catch an input-slot change) is only
        // ever invoked server-side — see GlassBlowingStationMenu for why — so the client's own
        // copy of the recipe list needs this poked directly, every tick, to actually populate.
        this.menu.refreshRecipeListIfInputChanged();
        this.displayRecipes = this.menu.hasInputItem();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int xo = this.leftPos;
        int yo = this.topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BG_LOCATION, xo, yo, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        int sy = (int) (41.0F * this.scrollOffs);
        Identifier sprite = isScrollBarActive() ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
        int scrollerXStart = xo + 119;
        int scrollerYStart = yo + 15;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, scrollerXStart, scrollerYStart + sy, 12, 15);
        if (mouseX >= scrollerXStart && mouseY >= scrollerYStart && mouseX < scrollerXStart + 12 && mouseY < scrollerYStart + 54) {
            if (isScrollBarActive()) {
                graphics.requestCursor(this.scrolling ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
            } else {
                graphics.requestCursor(CursorTypes.NOT_ALLOWED);
            }
        }

        int x = this.leftPos + 52;
        int y = this.topPos + 14;
        int endIndex = this.startIndex + 12;
        extractButtons(graphics, mouseX, mouseY, x, y, endIndex);
        extractRecipes(graphics, x, y, endIndex);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        int temp = menu.getTemperature();
        int maxTemp = menu.getMaxTemperature();
        GlassBlowingRecipe selected = selectedRecipe();

        String tempLine = temp + "°C" + (maxTemp > 0 ? " / " + maxTemp + "°C max" : "");
        graphics.text(this.font, tempLine, 8, 58, 0xFF888888, false);

        if (selected != null) {
            int required = selected.getRequiredMinimalTemperature();
            boolean hot = temp >= required;
            String status = hot ? "Ready (" + required + "°C)" : "Needs " + required + "°C";
            graphics.text(this.font, status, 8, 68, hot ? 0xFF55FF55 : 0xFFFF5555, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (!this.displayRecipes) return;

        int edgeLeft = this.leftPos + 52;
        int edgeTop = this.topPos + 14;
        int endIndex = this.startIndex + 12;
        List<GlassBlowingRecipe> visible = menu.getVisibleRecipes();

        for (int index = this.startIndex; index < endIndex && index < visible.size(); index++) {
            int posIndex = index - this.startIndex;
            int itemLeft = edgeLeft + posIndex % 4 * 16;
            int itemTop = edgeTop + posIndex / 4 * 18 + 2;
            if (mouseX >= itemLeft && mouseX < itemLeft + 16 && mouseY >= itemTop && mouseY < itemTop + 18) {
                GlassBlowingRecipe recipe = visible.get(index);
                List<Component> lines = List.of(
                        recipe.getResult().getHoverName(),
                        Component.literal(recipe.getRequiredMinimalTemperature() + "°C required")
                                .withStyle(ChatFormatting.GRAY));
                graphics.setTooltipForNextFrame(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private void extractButtons(GuiGraphicsExtractor graphics, int xm, int ym, int x, int y, int endIndex) {
        for (int index = this.startIndex; index < endIndex && index < menu.getNumberOfVisibleRecipes(); index++) {
            int posIndex = index - this.startIndex;
            int posX = x + posIndex % 4 * 16;
            int row = posIndex / 4;
            int posY = y + row * 18 + 2;
            Identifier sprite;
            if (index == menu.getSelectedRecipeIndex()) {
                sprite = RECIPE_SELECTED_SPRITE;
            } else if (xm >= posX && ym >= posY && xm < posX + 16 && ym < posY + 18) {
                sprite = RECIPE_HIGHLIGHTED_SPRITE;
            } else {
                sprite = RECIPE_SPRITE;
            }

            int textureY = posY - 1;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, posX, textureY, 16, 18);
            if (xm >= posX && ym >= textureY && xm < posX + 16 && ym < textureY + 18) {
                graphics.requestCursor(CursorTypes.POINTING_HAND);
            }
        }
    }

    private void extractRecipes(GuiGraphicsExtractor graphics, int x, int y, int endIndex) {
        List<GlassBlowingRecipe> visible = menu.getVisibleRecipes();
        for (int index = this.startIndex; index < endIndex && index < visible.size(); index++) {
            int posIndex = index - this.startIndex;
            int posX = x + posIndex % 4 * 16;
            int row = posIndex / 4;
            int posY = y + row * 18 + 2;
            graphics.item(visible.get(index).getResult(), posX, posY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.displayRecipes) {
            int xo = this.leftPos + 52;
            int yo = this.topPos + 14;
            int endIndex = this.startIndex + 12;

            for (int index = this.startIndex; index < endIndex; index++) {
                int posIndex = index - this.startIndex;
                double xx = event.x() - (xo + posIndex % 4 * 16);
                double yy = event.y() - (yo + posIndex / 4 * 18);
                if (xx >= 0.0 && yy >= 0.0 && xx < 16.0 && yy < 18.0 && menu.clickMenuButton(this.minecraft.player, index)) {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, index);
                    return true;
                }
            }

            xo = this.leftPos + 119;
            yo = this.topPos + 9;
            if (event.x() >= xo && event.x() < xo + 12 && event.y() >= yo && event.y() < yo + 54) {
                this.scrolling = true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.scrolling && isScrollBarActive()) {
            int yscr = this.topPos + 14;
            int yscr2 = yscr + 54;
            this.scrollOffs = ((float) event.y() - yscr - 7.5F) / (yscr2 - yscr - 15.0F);
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
            this.startIndex = (int) (this.scrollOffs * getOffscreenRows() + 0.5) * 4;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.scrolling = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) return true;

        if (isScrollBarActive()) {
            int offscreenRows = getOffscreenRows();
            float scrolledDelta = (float) scrollY / offscreenRows;
            this.scrollOffs = Mth.clamp(this.scrollOffs - scrolledDelta, 0.0F, 1.0F);
            this.startIndex = (int) (this.scrollOffs * offscreenRows + 0.5) * 4;
        }
        return true;
    }

    private boolean isScrollBarActive() {
        return this.displayRecipes && menu.getNumberOfVisibleRecipes() > 12;
    }

    private int getOffscreenRows() {
        return (menu.getNumberOfVisibleRecipes() + 4 - 1) / 4 - 3;
    }

    private GlassBlowingRecipe selectedRecipe() {
        int index = menu.getSelectedRecipeIndex();
        List<GlassBlowingRecipe> visible = menu.getVisibleRecipes();
        return (index >= 0 && index < visible.size()) ? visible.get(index) : null;
    }
}
