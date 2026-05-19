/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class ElectricEngineRenderState extends BlockEntityRenderState {
    public float statorAngle = 0f;
    public Direction facing = Direction.NORTH;
    public @Nullable MovingBlockRenderState statorModel;
}
