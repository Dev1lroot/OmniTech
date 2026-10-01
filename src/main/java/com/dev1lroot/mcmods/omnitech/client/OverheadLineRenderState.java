/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class OverheadLineRenderState extends BlockEntityRenderState {
    /**
     * One span drawn from this insulator. {@code points} are the catenary
     * vertices relative to the block origin, {@code lights} the packed light at each.
     */
    public record Span(Vec3[] points, int[] lights, int color) {}

    public final List<Span> spans = new ArrayList<>();
}
