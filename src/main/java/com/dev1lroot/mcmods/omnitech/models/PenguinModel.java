/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.models;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class PenguinModel extends EntityModel<LivingEntityRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "penguin"), "main");

    private final ModelPart bb_main;
    private final ModelPart wingLeft;
    private final ModelPart wingRight;

    public PenguinModel(ModelPart root) {
        super(root);
        this.bb_main   = root.getChild("bb_main");
        this.wingLeft  = bb_main.getChild("arm_left_r1");
        this.wingRight = bb_main.getChild("arm_left_r2");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition bbMain = root.addOrReplaceChild("bb_main",
                CubeListBuilder.create()
                        .texOffs(24, 24).addBox(-3.0F, -1.0F, -3.0F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(0,  27).addBox( 1.0F, -1.0F, -3.0F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(0,   0).addBox(-4.0F, -9.0F, -3.0F, 8.0F, 8.0F, 6.0F, new CubeDeformation(0.0F))
                        .texOffs(0,  14).addBox(-3.0F,-16.0F, -3.0F, 6.0F, 7.0F, 6.0F, new CubeDeformation(0.0F))
                        .texOffs(12, 27).addBox(-2.0F,-13.0F, -5.0F, 4.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        bbMain.addOrReplaceChild("arm_left_r1",
                CubeListBuilder.create()
                        .texOffs(24, 19).addBox(-6.0F, -1.0F, -2.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-3.0F, -11.0F, 0.0F, 0.0F, 0.0F, -1.2217F));

        bbMain.addOrReplaceChild("arm_left_r2",
                CubeListBuilder.create()
                        .texOffs(24, 14).addBox(0.0F, -1.0F, -2.0F, 6.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(3.0F, -11.0F, 0.0F, 0.0F, 0.0F, 1.2217F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * state.walkAnimationSpeed;
        // Flippers flap more actively when swimming, gently when waddling on land
        float amplitude = state.isInWater ? 0.55F : 0.18F;
        wingLeft.zRot  = -1.2217F + swing * amplitude;
        wingRight.zRot =  1.2217F - swing * amplitude;
    }
}
