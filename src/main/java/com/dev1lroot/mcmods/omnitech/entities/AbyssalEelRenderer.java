package com.dev1lroot.mcmods.omnitech.entities;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SalmonRenderer;
import net.minecraft.client.renderer.entity.state.SalmonRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders {@link AbyssalEelEntity} using Salmon's model and animation rig,
 * but with a custom dark deep-sea texture and 2× visual scale.
 *
 * <p>The entity's hitbox is already 2× medium salmon via {@code getSalmonScale()}.
 * Since the LARGE salmon model is 1.5× the medium model, the renderer applies
 * an additional (2.0 / 1.5) ≈ 1.333× scale factor so the visual matches the hitbox.
 */
public class AbyssalEelRenderer extends SalmonRenderer {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/entity/abyssal_eel.png");

    /** Scale factor to bump the LARGE salmon model (1.5×) up to 2× medium salmon. */
    private static final float MODEL_SCALE = 2.0f / 1.5f;

    public AbyssalEelRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0f;
    }

    @Override
    public Identifier getTextureLocation(SalmonRenderState state) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(SalmonRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        // Compensate: LARGE model is 1.5× base; we want 2× base.
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }
}
