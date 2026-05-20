/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui.tooltip;

import com.dev1lroot.mcmods.omnitech.FluidPhysicsRegistry;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Common-side tooltip data carrier for the phase-diagram mini-graph.
 * Passed via {@code Optional<TooltipComponent>} to
 * {@code GuiGraphicsExtractor.setTooltipForNextFrame} and converted to
 * {@link PhaseDiagramClientTooltipComponent} on the client by the factory
 * registered in {@code OmniTechClient}.
 */
public record PhaseDiagramTooltipData(
        FluidPhysicsRegistry.PhaseDiagram diagram,
        int currentTempC,
        int currentPressureKPa
) implements TooltipComponent {}
