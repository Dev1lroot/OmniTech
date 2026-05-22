/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class OrreryRenderState extends BlockEntityRenderState {

    /** One entry per body to render (star + planets + visible moons). */
    public final List<BodyEntry> bodies = new ArrayList<>();
    /** One orbit ring entry per planet. */
    public final List<OrbitEntry> orbits = new ArrayList<>();
    /** Pre-computed scene scale so max orbital radius fits in 0.35 block units. */
    public float sceneScale = 1f;
    /** Accumulated game time (ticks + partial tick) for smooth animation. */
    public float animTime = 0f;

    public void clear() {
        bodies.clear();
        orbits.clear();
    }

    /**
     * A single body to render as a cube in the orrery.
     *
     * @param spriteId  celestials-atlas sprite (may be null → use color only)
     * @param x         body X in scene units (heliocentric or parent-centric)
     * @param y         body Y
     * @param z         body Z
     * @param halfSize  half-width of the cube in scene units
     * @param color     ARGB fallback color (used when sprite is unavailable)
     */
    public record BodyEntry(Identifier spriteId, float x, float y, float z,
                            float halfSize, int color) {}

    /**
     * A single orbit ring to render as dotted circles.
     *
     * @param r              orbital radius in scene units
     * @param inclinationDeg orbital inclination
     * @param ascendingNodeDeg ascending node longitude
     * @param color          ARGB color of the ring
     */
    public record OrbitEntry(float r, float inclinationDeg, float ascendingNodeDeg, int color) {}
}
