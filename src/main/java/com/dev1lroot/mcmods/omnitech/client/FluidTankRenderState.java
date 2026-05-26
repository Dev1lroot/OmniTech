/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.FluidPhase;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

public class FluidTankRenderState extends BlockEntityRenderState {
    /** Proportion of tank that is filled (0.0 = empty, 1.0 = full). */
    public float fillLevel = 0f;
    /**
     * Thermodynamic phase of the stored fluid at its current T/P.
     * Null when the fluid has no phase diagram (falls back to per-fluid texture + liquid behaviour).
     */
    public @Nullable FluidPhase phase = null;
    /** Phase-selected still-texture sprite; null when the tank is empty. */
    public @Nullable TextureAtlasSprite stillSprite = null;
    /** Packed ARGB tint from the fluid's FluidTintSource (-1 = white/no tint). */
    public int tintARGB = -1;
    /** True when the fluid's chunk-section layer is TRANSLUCENT (e.g. water). */
    public boolean isTranslucent = false;
    /** Packed sky+block light for the tank's block position. */
    public int lightCoords = 0;
}
