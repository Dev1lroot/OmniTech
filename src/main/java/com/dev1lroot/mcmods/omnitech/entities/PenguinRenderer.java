/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.entities;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.models.PenguinModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class PenguinRenderer extends MobRenderer<PenguinEntity, PenguinRenderState, PenguinModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/entity/penguin.png");

    public PenguinRenderer(EntityRendererProvider.Context context) {
        super(context, new PenguinModel(context.bakeLayer(PenguinModel.LAYER_LOCATION)), 0.25f);
    }

    @Override
    public Identifier getTextureLocation(PenguinRenderState state) {
        return TEXTURE;
    }

    @Override
    public PenguinRenderState createRenderState() {
        return new PenguinRenderState();
    }
}
