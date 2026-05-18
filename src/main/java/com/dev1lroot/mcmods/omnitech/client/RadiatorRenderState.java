/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class RadiatorRenderState extends BlockEntityRenderState {
    /** Packed ARGB color for the glow overlay (0 = no glow). */
    public int argb = 0;
    /** Packed sky+block light for the radiator's block position. */
    public int lightCoords = 0;
}
