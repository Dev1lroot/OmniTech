/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;

public class FoundryRenderState extends BlockEntityRenderState {
    /** Full item render state for the template slot; null when empty. */
    public @Nullable ItemStackRenderState templateItemState = null;
    /** Fluid still-texture sprite; null when no fluid is present. */
    public @Nullable TextureAtlasSprite fluidSprite    = null;
    /** Packed ARGB tint for the fluid layer (-1 = white / no tint). */
    public int fluidTintARGB                           = -1;
    /** True when the fluid renders on the translucent chunk layer (e.g. water). */
    public boolean fluidTranslucent                    = false;
    /** Craft progress 0.0→1.0; controls fluid top-face from y=8 to y=9 (block px). */
    public float fluidProgress                         = 0f;
    /** Full item render state for the 200 ms output flash; null when not flashing. */
    public @Nullable ItemStackRenderState outputItemState = null;
    /** Packed sky+block light coords for this block position. */
    public int lightCoords                             = 0;
}
