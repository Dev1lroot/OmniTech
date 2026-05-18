/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.entities;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Direction;

public class CokeOvenRenderState extends EntityRenderState {
    public boolean lit;
    public float progressFraction;
    public Direction facing = Direction.NORTH;
    public final BlockModelRenderState displayModel = new BlockModelRenderState();
}
