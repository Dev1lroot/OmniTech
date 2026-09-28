/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client.spacemap;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Renders a {@link SpaceMapRenderState} into the GUI as real 3-D geometry — the same
 * picture-in-picture path as the inventory player model, with the orrery's look:
 * emissive textured boxes for bodies, line loops for orbits, flat textured galaxy discs
 * (geometry shared with the orrery through {@link SpaceScene}).
 *
 * <p>The base class sets up an orthographic projection, centres the pose on the
 * viewport and scales by GUI scale × {@code state.scale()}; the state's {@code view}
 * matrix then orbits the camera around the focused body.
 * {@code SpaceNavigationScreen} projects with the identical transform for picking.
 */
public class SpaceMapPipRenderer extends PictureInPictureRenderer<SpaceMapRenderState> {

    @Override
    public Class<SpaceMapRenderState> getRenderStateClass() {
        return SpaceMapRenderState.class;
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;   // centre, like the entity renderer
    }

    @Override
    protected String getTextureLabel() {
        return "space_map";
    }

    @Override
    protected void renderToTexture(SpaceMapRenderState state, PoseStack pose, SubmitNodeCollector nodes) {
        pose.mulPose(state.view());
        SpaceScene.submit(state.cubes(), state.rings(), state.planes(), pose, nodes, 1.5f);
    }
}
