package com.dev1lroot.mcmods.omnitech.models;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.entities.RocketRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class RocketModel extends EntityModel<RocketRenderState> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "rocket"), "main");

    private final ModelPart bb_main;

    public RocketModel(ModelPart root) {
        super(root);
        this.bb_main = root.getChild("bb_main");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bb_main = partdefinition.addOrReplaceChild("bb_main",
                CubeListBuilder.create()
                        .texOffs(160, 144).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(-24.0F, -112.0F, -24.0F, 48.0F, 96.0F, 48.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 144).addBox(-16.0F, -144.0F, -16.0F, 32.0F, 32.0F, 32.0F, new CubeDeformation(0.0F))
                        .texOffs(160, 176).addBox(-8.0F, -160.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F))
                        .texOffs(128, 144).addBox(0.0F, -64.0F, 24.0F, 0.0F, 64.0F, 16.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        bb_main.addOrReplaceChild("cube_r1",
                CubeListBuilder.create().texOffs(128, 144).addBox(1.0F, -64.0F, -1.0F, 0.0F, 64.0F, 16.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(25.0F, 0.0F, 4.0F, 0.0F, 1.5708F, 0.0F));

        bb_main.addOrReplaceChild("cube_r2",
                CubeListBuilder.create().texOffs(128, 144).addBox(1.0F, -64.0F, -1.0F, 0.0F, 64.0F, 16.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-25.0F, 0.0F, 4.0F, 0.0F, -1.5708F, 0.0F));

        bb_main.addOrReplaceChild("cube_r3",
                CubeListBuilder.create().texOffs(128, 144).addBox(1.0F, -64.0F, -1.0F, 0.0F, 64.0F, 16.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.0F, 0.0F, -25.0F, 0.0F, 3.1416F, 0.0F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public void setupAnim(RocketRenderState state) {
        // No animation needed
    }
}
