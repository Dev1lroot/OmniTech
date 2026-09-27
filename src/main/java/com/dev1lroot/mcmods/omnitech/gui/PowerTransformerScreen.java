/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.electrical.power_transformer.PowerTransformerBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import com.dev1lroot.mcmods.omnitech.util.ElectricUnits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Screen for the Power Transformer.
 *
 * <p>Layout (panel-relative y):
 * <ul>
 *   <li>y=6  – title</li>
 *   <li>y=18 – "Output voltage" label, ON/OFF toggle (right)</li>
 *   <li>y=28 – six ± buttons with the voltage setpoint between them</li>
 *   <li>y=45 – "Power limit" label</li>
 *   <li>y=55 – six ± buttons with the power limit between them</li>
 *   <li>y=73 – In:  U · I · P</li>
 *   <li>y=84 – Out: U · I · P</li>
 *   <li>y=95 – turns ratio · efficiency · losses</li>
 * </ul>
 * Holding Shift multiplies the voltage steps ×100 and the power steps ×10.
 * Hovering the readouts shows the full electrical tooltip.
 */
public class PowerTransformerScreen extends AbstractContainerScreen<PowerTransformerMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("power_transformer");

    private static final int VOLTAGE_ROW_Y = 28;
    private static final int LIMIT_ROW_Y   = 55;
    private static final int BTN_H         = 12;
    /** x and width of the six step buttons; the value is drawn in the gap between index 2 and 3. */
    private static final int[] BTN_X = {6, 31, 52, 108, 125, 146};
    private static final int[] BTN_W = {24, 20, 16, 16, 20, 24};
    private static final int VALUE_X = 69;
    private static final int VALUE_W = 38;

    private static final int IN_Y    = 73;
    private static final int OUT_Y   = 84;
    private static final int STATS_Y = 95;

    private final List<Button> voltageButtons = new ArrayList<>();
    private final List<Button> limitButtons   = new ArrayList<>();
    private Button toggleButton;
    private GuiDataContext dataCtx;

    public PowerTransformerScreen(PowerTransformerMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;

        this.dataCtx = new GuiDataContext()
                .value("energy_stored", menu::getBuffer)
                .value("energy_max",    menu::getBufferMax)
                .value("voltage",       menu::getOutputVoltage)
                .value("input_voltage", menu::getInputVoltage)
                .value("power_in",      menu::getInputWatts)
                .value("power_out",     menu::getOutputWatts)
                .value("power_loss",    menu::getLossWatts)
                .value("rated_power",   menu::getLimitWatts);

        voltageButtons.clear();
        limitButtons.clear();
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            voltageButtons.add(addRenderableWidget(Button.builder(Component.empty(),
                            b -> click(shift() ? PowerTransformerBlockEntity.BTN_VOLTAGE_SHIFT + idx
                                               : PowerTransformerBlockEntity.BTN_VOLTAGE + idx))
                    .bounds(leftPos + BTN_X[i], topPos + VOLTAGE_ROW_Y, BTN_W[i], BTN_H).build()));
            limitButtons.add(addRenderableWidget(Button.builder(Component.empty(),
                            b -> click(shift() ? PowerTransformerBlockEntity.BTN_LIMIT_SHIFT + idx
                                               : PowerTransformerBlockEntity.BTN_LIMIT + idx))
                    .bounds(leftPos + BTN_X[i], topPos + LIMIT_ROW_Y, BTN_W[i], BTN_H).build()));
        }
        toggleButton = addRenderableWidget(Button.builder(Component.empty(),
                        b -> click(PowerTransformerBlockEntity.BTN_TOGGLE))
                .bounds(leftPos + LAYOUT.width - 6 - 36, topPos + 16, 36, BTN_H).build());
        updateButtonLabels();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtonLabels();
    }

    /** Button captions follow the Shift key so the player sees the current step size. */
    private void updateButtonLabels() {
        boolean shift = shift();
        for (int i = 0; i < 6; i++) {
            int v = PowerTransformerBlockEntity.VOLTAGE_STEPS[i]
                    * (shift ? PowerTransformerBlockEntity.VOLTAGE_SHIFT_MULT : 1);
            int l = PowerTransformerBlockEntity.LIMIT_STEPS[i]
                    * (shift ? PowerTransformerBlockEntity.LIMIT_SHIFT_MULT : 1);
            voltageButtons.get(i).setMessage(Component.literal(stepLabel(v)));
            limitButtons.get(i).setMessage(Component.literal(stepLabel(l)));
        }
        toggleButton.setMessage(Component.translatable(menu.isEnabled()
                ? "gui.omnitech.power_transformer.on" : "gui.omnitech.power_transformer.off"));
    }

    private static String stepLabel(int step) {
        String sign = step > 0 ? "+" : "-";
        int a = Math.abs(step);
        return sign + (a >= 1000 ? (a / 1000) + "k" : String.valueOf(a));
    }

    private void click(int id) {
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private static boolean shift() {
        return Minecraft.getInstance().hasShiftDown();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayoutRenderer.renderBackground(graphics, LAYOUT, dataCtx,
                this.leftPos, this.topPos, LAYOUT.width, LAYOUT.height, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        graphics.text(font, Component.translatable("gui.omnitech.power_transformer.output_voltage"),
                6, 18, 0xFFAAAAAA, false);
        centered(graphics, ElectricUnits.formatVoltage(menu.getOutputVoltageSetpoint()),
                VALUE_X, VALUE_W, VOLTAGE_ROW_Y + 2, 0xFFFFDD44);

        graphics.text(font, Component.translatable("gui.omnitech.power_transformer.power_limit"),
                6, 45, 0xFFAAAAAA, false);
        centered(graphics, ElectricUnits.formatPower(menu.getLimitWatts()),
                VALUE_X, VALUE_W, LIMIT_ROW_Y + 2, 0xFFFFDD44);

        String in = GuiLayoutRenderer.powerLineText("gui.omnitech.electric.input",
                menu.getInputVoltage(), menu.getInputWatts());
        graphics.text(font, in, 6, IN_Y, menu.getInputWatts() > 0f ? 0xFF55FF55 : 0xFF888888, false);

        String out = menu.isEnabled()
                ? GuiLayoutRenderer.powerLineText("gui.omnitech.electric.output",
                        menu.getOutputVoltageSetpoint(), menu.getOutputWatts())
                : Component.translatable("gui.omnitech.electric.output").getString() + " "
                        + Component.translatable("gui.omnitech.power_transformer.disabled").getString();
        graphics.text(font, out, 6, OUT_Y, menu.getOutputWatts() > 0f ? 0xFF44AAFF : 0xFF888888, false);

        graphics.text(font, statsLine(), 6, STATS_Y, 0xFF888888, false);
    }

    /** "1 : 27.5 · η 99.1% · 180 W loss" (step-up) or "27.5 : 1 · …" (step-down). */
    private String statsLine() {
        float ratio = menu.getTurnsRatio();
        String r;
        if (ratio <= 0f)       r = "– : –";
        else if (ratio >= 1f)  r = String.format(Locale.ROOT, "%.3g : 1", ratio);
        else                   r = String.format(Locale.ROOT, "1 : %.3g", 1f / ratio);
        float eta = menu.getEfficiency();
        String e = eta > 0f ? String.format(Locale.ROOT, "η %.1f%%", eta * 100f) : "η –";
        return r + " · " + e + " · " + ElectricUnits.formatPower(menu.getLossWatts()) + " "
                + Component.translatable("gui.omnitech.power_transformer.loss").getString();
    }

    private void centered(GuiGraphicsExtractor graphics, String text, int x, int w, int y, int color) {
        graphics.text(font, text, x + (w - font.width(text)) / 2, y, color, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int rx = mouseX - leftPos;
        int ry = mouseY - topPos;
        if (hoveredSlot == null && rx >= 0 && rx < LAYOUT.width
                && ry >= IN_Y - 1 && ry < STATS_Y + 9) {
            graphics.setTooltipForNextFrame(font,
                    GuiLayoutRenderer.buildElectricTooltip(dataCtx), Optional.empty(),
                    mouseX, mouseY);
        }
    }
}
