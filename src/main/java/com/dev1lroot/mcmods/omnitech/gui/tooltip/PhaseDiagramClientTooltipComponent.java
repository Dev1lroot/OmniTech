/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui.tooltip;

import com.dev1lroot.mcmods.omnitech.FluidPhase;
import com.dev1lroot.mcmods.omnitech.FluidPhaseUtil;
import com.dev1lroot.mcmods.omnitech.FluidPhysicsRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders an 80x40 phase-diagram mini-graph inside a fluid tooltip.
 *
 * <p>X axis = temperature (°C, linear, auto-ranged to cover melting–critical
 * and the current fluid temperature).  Y axis = pressure (kPa, log scale,
 * 1 kPa at the bottom to 3× critical pressure at the top).
 *
 * <p>Phase region colours:
 * <ul>
 *   <li>SOLID        — blue  {@code 0xFF4466CC}</li>
 *   <li>LIQUID       — green {@code 0xFF22AA44}</li>
 *   <li>VAPOUR       — gray  {@code 0xFF888888}</li>
 *   <li>GAS          — yellow {@code 0xFFCCCC00}</li>
 *   <li>SUPERCRITICAL — red  {@code 0xFFCC3333}</li>
 *   <li>PLASMA       — magenta {@code 0xFFCC44CC}</li>
 * </ul>
 *
 * <p>A 3×3 white dot marks the fluid's current (T, P) on the graph.
 */
//@OnlyIn(Dist.CLIENT)
public class PhaseDiagramClientTooltipComponent implements ClientTooltipComponent {

    private static final int W = 80;
    private static final int H = 40;

    private final FluidPhysicsRegistry.PhaseDiagram diagram;
    private final int currentTempC;
    private final int currentPressureKPa;

    // Temperature axis bounds (°C, linear)
    private final int tMin;
    private final int tMax;

    // Pressure axis bounds (log(kPa))
    private final double logPMin;
    private final double logPMax;

    public PhaseDiagramClientTooltipComponent(PhaseDiagramTooltipData data) {
        this.diagram             = data.diagram();
        this.currentTempC        = data.currentTempC();
        this.currentPressureKPa  = data.currentPressureKPa();

        // Temperature range: span from slightly before melting to slightly after critical,
        // guaranteed to include the current temperature.
        int span   = Math.max(100, diagram.criticalTempC() - diagram.meltingPointC() + 50);
        int center = (diagram.meltingPointC() + diagram.criticalTempC()) / 2;
        int rawMin = center - span / 2;
        int rawMax = center + span / 2;
        this.tMin = Math.min(rawMin, currentTempC - 20);
        this.tMax = Math.max(rawMax, currentTempC + 20);

        // Pressure range: log scale 1 kPa → 3× critical, expanded for current pressure.
        double pMin = 1.0;
        double pMax = Math.max(1000.0, diagram.criticalPressureKPa() * 3.0);
        double logCurP = Math.log(Math.max(1.0, currentPressureKPa));
        if (logCurP < Math.log(pMin + 0.5)) pMin = Math.max(0.1, currentPressureKPa / 10.0);
        if (logCurP > Math.log(pMax - 1.0)) pMax = currentPressureKPa * 3.0;
        this.logPMin = Math.log(pMin);
        this.logPMax = Math.log(pMax);
    }

    @Override
    public int getWidth(Font font) { return W; }

    @Override
    public int getHeight(Font font) { return H; }

    @Override
    public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
        int tRange = tMax - tMin;

        // Phase regions: row by row, run-length encoded by colour
        for (int row = 0; row < H; row++) {
            // row 0 = top = highest pressure; row H-1 = bottom = lowest pressure
            double logP      = logPMax - (double) row / (H - 1) * (logPMax - logPMin);
            int pressureKPa  = (int) Math.round(Math.exp(logP));

            for (int col = 0; col < W; ) {
                int tempC = (tRange == 0) ? tMin : tMin + col * tRange / (W - 1);
                int color = phaseColor(tempC, pressureKPa);
                int runEnd = col + 1;
                while (runEnd < W) {
                    int nextTemp = (tRange == 0) ? tMin : tMin + runEnd * tRange / (W - 1);
                    if (phaseColor(nextTemp, pressureKPa) != color) break;
                    runEnd++;
                }
                graphics.fill(x + col, y + row, x + runEnd, y + row + 1, color);
                col = runEnd;
            }
        }

        // White 3x3 dot at current (T, P)
        int dotX = (tRange == 0) ? W / 2
                 : (int) Math.round((double)(currentTempC - tMin) / tRange * (W - 1));
        double logCurP = Math.log(Math.max(1.0, currentPressureKPa));
        double logRange = logPMax - logPMin;
        int dotY = (logRange <= 0.0) ? H / 2
                 : (int) Math.round((logPMax - logCurP) / logRange * (H - 1));

        dotX = Math.max(0, Math.min(W - 1, dotX));
        dotY = Math.max(0, Math.min(H - 1, dotY));

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int px = dotX + dx, py = dotY + dy;
                if (px >= 0 && px < W && py >= 0 && py < H)
                    graphics.fill(x + px, y + py, x + px + 1, y + py + 1, 0xFFFFFFFF);
            }
        }
    }

    private int phaseColor(int tempC, int pressureKPa) {
        FluidPhase phase = FluidPhaseUtil.getPhase(tempC, pressureKPa, diagram);
        if (phase == null) return 0xFF222222;
        return switch (phase) {
            case SOLID         -> 0xFF4466CC;
            case LIQUID        -> 0xFF22AA44;
            case VAPOUR        -> 0xFF888888;
            case GAS           -> 0xFFCCCC00;
            case SUPERCRITICAL -> 0xFFCC3333;
            case PLASMA        -> 0xFFCC44CC;
        };
    }
}
