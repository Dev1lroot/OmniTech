/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public class ReactorCellBERRenderState extends BlockEntityRenderState {
    public enum RodType { NONE, CONTROL, FUEL }

    /** Which rod is present. NONE means nothing to render. */
    public RodType rodType = RodType.NONE;

    /** 0–100 insertion percentage (only meaningful for CONTROL). */
    public int controlInsertion = 0;

    public int light = 0xF000F0;
}
