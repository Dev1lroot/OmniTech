package com.dev1lroot.mcmods.omnitech.entities;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class RocketEntityRenderer extends EntityRenderer<RocketEntity, RocketRenderState> {

    // Half-extent in X and Z; entity is 3×3×3 blocks
    private static final float W = 1.5f;
    // Full height of the rocket body
    private static final float H = 3.0f;

    // Steel-gray color, slightly transparent
    private static final int CR = 80, CG = 80, CB = 100, CA = 230;

    public RocketEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.5f;
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

        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
            float x1 = -W, y1 = 0f, z1 = -W;
            float x2 =  W, y2 =  H, z2 =  W;

            // Bottom face (y-)
            buffer.addVertex(pose, x1, y1, z2).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x2, y1, z2).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x2, y1, z1).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x1, y1, z1).setColor(CR, CG, CB, CA);

            // Top face (y+)
            buffer.addVertex(pose, x1, y2, z1).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x2, y2, z1).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x2, y2, z2).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x1, y2, z2).setColor(CR, CG, CB, CA);

            // North face (z-)
            buffer.addVertex(pose, x2, y1, z1).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x2, y2, z1).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x1, y2, z1).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x1, y1, z1).setColor(CR, CG, CB, CA);

            // South face (z+)
            buffer.addVertex(pose, x1, y1, z2).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x1, y2, z2).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x2, y2, z2).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x2, y1, z2).setColor(CR, CG, CB, CA);

            // West face (x-)
            buffer.addVertex(pose, x1, y1, z1).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x1, y2, z1).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x1, y2, z2).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x1, y1, z2).setColor(CR, CG, CB, CA);

            // East face (x+)
            buffer.addVertex(pose, x2, y1, z2).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x2, y2, z2).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x2, y2, z1).setColor(CR, CG, CB, CA);
            buffer.addVertex(pose, x2, y1, z1).setColor(CR, CG, CB, CA);
        });
    }
}
