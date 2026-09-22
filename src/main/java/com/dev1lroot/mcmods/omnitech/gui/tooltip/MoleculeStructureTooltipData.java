/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui.tooltip;

import com.dev1lroot.mcmods.omnitech.chemistry.Molecule;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Common-side tooltip data carrier for a shift-held structure-formula diagram: a molecule already
 * run through {@link com.dev1lroot.mcmods.omnitech.chemistry.StructureLayout#layout} so it has
 * real (x, y) positions. Converted to {@link MoleculeStructureClientTooltipComponent} on the
 * client by the factory registered in {@code OmniTechClient}.
 */
public record MoleculeStructureTooltipData(Molecule molecule) implements TooltipComponent {}
