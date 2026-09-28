/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client.spacemap;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

/**
 * One frame of the 3-D space map, rendered off-screen by {@link SpaceMapPipRenderer}
 * the same way the GUI draws the player model (picture-in-picture).
 *
 * <p>Scene units are the map's orbit units; {@code view} is the camera rotation +
 * translation to the focused body (applied after the renderer's screen scale), and
 * {@code scale} is GUI pixels per scene unit.
 */
public record SpaceMapRenderState(
        int x0, int y0, int x1, int y1,
        float scale,
        Matrix4f view,
        List<Cube> cubes,
        List<Ring> rings,
        List<Plane> planes,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {

    /**
     * Box like the orrery's bodies: a standalone {@code texture}, else a block-atlas {@code sprite}
     * (e.g. stone for belt particles), else white; {@code spin} rotates textured boxes about Y.
     */
    public record Cube(float x, float y, float z, float half, @Nullable Identifier texture,
                       @Nullable Identifier sprite, int color, float spin) {}

    /** Circular orbit line around (cx, cy, cz) with the body's inclination / ascending node. */
    public record Ring(float cx, float cy, float cz, float r, float inclination, float node, int color) {}

    /** Flat textured square on the XZ plane (galaxy discs). */
    public record Plane(float x, float y, float z, float half, Identifier texture, int color) {}

    public SpaceMapRenderState(int x0, int y0, int x1, int y1, float scale, Matrix4f view,
                               List<Cube> cubes, List<Ring> rings, List<Plane> planes,
                               @Nullable ScreenRectangle scissorArea) {
        this(x0, y0, x1, y1, scale, view, cubes, rings, planes, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}
