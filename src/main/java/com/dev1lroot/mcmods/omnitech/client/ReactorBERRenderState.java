/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.ArrayList;
import java.util.List;

public class ReactorBERRenderState extends BlockEntityRenderState {

    /** One entry per control rod currently inserted in the reactor. */
    public record RodEntry(int dx, int dy, int dz, int control, int light) {}

    public final List<RodEntry> rods = new ArrayList<>();

    // ── Coolant fluid visualization ───────────────────────────────────────────

    /** Still-texture sprite of the coolant fluid; null = no coolant / no structure. */
    public TextureAtlasSprite coolantSprite   = null;
    /** ARGB tint from the fluid's FluidTintSource (-1 = white, no tint). */
    public int                coolantTintARGB = -1;
    /** Fraction filled [0..1]: 1.0 means 5 inner blocks tall. */
    public float              coolantFill     = 0f;
    /** Inner XZ dimensions of the cavity (structure.width/depth − 2). */
    public int                innerWidth      = 0;
    public int                innerDepth      = 0;
    /** Packed light coords for the coolant geometry. */
    public int                coolantLight    = 0;
    /** True for water-like fluids that want the translucent render pass. */
    public boolean            coolantTranslucent = true;

    // ── Cherenkov radiation visualization ─────────────────────────────────────

    /** BER-local X coordinate for each reactor cell. null = no data. */
    public int[] cherenkovLX      = null;
    /** BER-local Z coordinate for each reactor cell. */
    public int[] cherenkovLZ      = null;
    /** Neutron flux percent [0..100] per cell, same index as lX/lZ arrays. */
    public int[] cherenkovFluxPct = null;
}
