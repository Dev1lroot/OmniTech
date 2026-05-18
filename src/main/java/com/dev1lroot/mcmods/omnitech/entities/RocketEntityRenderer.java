/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.entities;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.models.RocketModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class RocketEntityRenderer extends EntityRenderer<RocketEntity, RocketRenderState> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/entity/rocket.png");

    private final RocketModel model;

    public RocketEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.5f;
        this.model = new RocketModel(context.bakeLayer(RocketModel.LAYER_LOCATION));
    }

    @Override
    public RocketRenderState createRenderState() {
        return new RocketRenderState();
    }

    @Override
    public void extractRenderState(RocketEntity entity, RocketRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.fuelAmount = entity.getFuelAmount();
    }

    @Override
    public void submit(RocketRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        super.submit(state, poseStack, submitNodeCollector, camera);

        poseStack.pushPose();
        // Align the nozzle bottom (model Y=24 after offset) to entity feet,
        // then flip axes to match Minecraft's model coordinate convention.
        poseStack.translate(0.0f, 1.5f, 0.0f);
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        submitNodeCollector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        poseStack.popPose();
    }
}
