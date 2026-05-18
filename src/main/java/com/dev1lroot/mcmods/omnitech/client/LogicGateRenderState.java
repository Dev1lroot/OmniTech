/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class LogicGateRenderState extends BlockEntityRenderState {
    public @Nullable ItemStackRenderState templateItemState = null;
    public int lightCoords = 0;
    public Direction facing = Direction.NORTH;
}
