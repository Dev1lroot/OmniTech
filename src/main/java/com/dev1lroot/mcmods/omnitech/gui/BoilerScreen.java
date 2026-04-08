package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class BoilerScreen extends AbstractContainerScreen<BoilerMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    // Water bar — left side
    private static final int WATER_X = 8,  WATER_Y = 17, WATER_W = 16, WATER_H = 52;

    // Steam bar — right side
    private static final int STEAM_X = 153, STEAM_Y = 17, STEAM_W = 16, STEAM_H = 52;

    // Progress arrow — center
    private static final int ARROW_X = 68, ARROW_Y = 50, ARROW_W = 36;

    // Output slot — drawn by engine, just need the slot frame
    private static final int OUT_SLOT_X = BoilerMenu.OUTPUT_SLOT_X;
    private static final int OUT_SLOT_Y = BoilerMenu.OUTPUT_SLOT_Y;

    public BoilerScreen(BoilerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        // Background
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0f, 0f, this.imageWidth, this.imageHeight, 256, 256);

        // Water bar (frame + fill)
        GuiUtil.renderFrame(graphics, x + WATER_X, y + WATER_Y, WATER_W, WATER_H);
        GuiUtil.renderFluidBar(graphics,
                menu.getWaterFluid(), menu.getWaterAmount(), menu.getCapacity(),
                x + WATER_X, y + WATER_Y, WATER_W, WATER_H);

        // Steam bar (frame + fill)
        GuiUtil.renderFrame(graphics, x + STEAM_X, y + STEAM_Y, STEAM_W, STEAM_H);
        GuiUtil.renderFluidBar(graphics,
                menu.getSteamFluid(), menu.getSteamAmount(), menu.getCapacity(),
                x + STEAM_X, y + STEAM_Y, STEAM_W, STEAM_H);

        // Progress arrow
        GuiUtil.renderProgressBar(graphics, x + ARROW_X, y + ARROW_Y, ARROW_W, menu.getCookProgressScaled());

        // Output slot frame
        GuiUtil.renderSlot(graphics, x + OUT_SLOT_X, y + OUT_SLOT_Y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        int currentTemp  = menu.getTemperature();
        int requiredTemp = menu.getRequiredTemperature();

        HudWriter writer = new HudWriter(graphics, this.font, 28, 8, 10, false);

        int curColor = getTempColor(currentTemp, requiredTemp);
        writer.setColor(curColor).write(currentTemp + "°C");

        if (requiredTemp != 0) {
            boolean conditionMet = requiredTemp >= 0
                    ? currentTemp >= requiredTemp   // hot: need enough heat
                    : currentTemp <= requiredTemp;  // cold: need cold enough
            int reqColor = conditionMet ? 0xFF00AA00 : 0xFFAA0000;
            writer.setColor(0xFF404040).write(" / ")
                  .setColor(reqColor).write(requiredTemp + "°C");
        }

        // Water info below water bar
        int waterAmt = menu.getWaterAmount();
        if (waterAmt > 0) {
            writer.write("\n").setColor(0xFF4488FF)
                  .write(waterAmt + " mb");
        }
    }

    private int getTempColor(int current, int required) {
        // Cold recipe colours
        if (required < 0) {
            if (current <= required) return 0xFF00AAFF;  // cold enough – blue
            if (current < 0)        return 0xFF88CCFF;  // getting there – light blue
            return 0xFFAAAAAA;                           // still warm – grey
        }
        // Hot recipe colours
        if (required > 0 && current >= required) return 0xFFFF6600;
        if (current > 200) return 0xFFFFAA00;
        if (current > 100) return 0xFFFFFF00;
        return 0xFFAAAAAA;
    }
}
