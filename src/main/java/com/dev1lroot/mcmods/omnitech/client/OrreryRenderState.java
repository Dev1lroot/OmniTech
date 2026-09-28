/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.client.spacemap.SpaceScene;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jetbrains.annotations.Nullable;

/** Orrery hologram frame: the shared star-system scene and its block-space scale. */
public class OrreryRenderState extends BlockEntityRenderState {

    /** Star system as built for the space map ({@link SpaceScene#buildSystem}); null = nothing to show. */
    public @Nullable SpaceScene.Scene scene;
    /** Scene units → blocks, so the outermost orbit spans the display radius. */
    public float sceneScale = 1f;
}
