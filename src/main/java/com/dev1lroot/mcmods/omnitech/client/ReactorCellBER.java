/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorCellBlockEntity;
import com.dev1lroot.mcmods.omnitech.items.ReactorControlRodItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorFuelRodItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the rod geometry for a single ReactorCell.
 * Control rods slide in from the top; fuel rods sit permanently at full insertion.
 * Both use a 4×80×4 pixel rod (0.25×5×0.25 blocks) centred in the cell block.
 */
public class ReactorCellBER implements BlockEntityRenderer<ReactorCellBlockEntity, ReactorCellBERRenderState> {

    // Rod is 4/16 = 0.25 blocks wide, centred in the 1-block XZ space
    private static final float ROD_MIN = 0.375f;   // 0.5 - 0.125
    private static final float ROD_MAX = 0.625f;   // 0.5 + 0.125
    private static final float ROD_H   = 5.0f;     // 5 blocks tall

    // Texture layout: 16×128.  Cap occupies rows 0–3 (4×4), side rows 4–83 (4×80).
    private static final float CAP_U0  = 0f,       CAP_V0  = 0f;
    private static final float CAP_U1  = 4f / 16f, CAP_V1  = 4f / 128f;   // 0.25, 0.03125
    private static final float SIDE_U0 = 0f,       SIDE_V0 = 4f / 128f;   // 0.0,  0.03125
    private static final float SIDE_U1 = 4f / 16f, SIDE_V1 = 84f / 128f;  // 0.25, 0.65625

    private static final Identifier TEXTURE_CONTROL =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/entity/reactor_control_rod.png");
    private static final Identifier TEXTURE_FUEL =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/entity/reactor_fuel_rod.png");

    public ReactorCellBER(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public ReactorCellBERRenderState createRenderState() { return new ReactorCellBERRenderState(); }

    @Override
    public void extractRenderState(ReactorCellBlockEntity entity, ReactorCellBERRenderState state,
                                   float partialTicks, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPos, breakProgress);
        state.rodType = ReactorCellBERRenderState.RodType.NONE;

        ItemStack stack = entity.getItem(0);

        if (stack.getItem() instanceof ReactorControlRodItem) {
            Integer control = stack.get(OmniTechDataComponents.ROD_CONTROL.get());
            if (control == null) return;
            state.rodType          = ReactorCellBERRenderState.RodType.CONTROL;
            state.controlInsertion = control;
        } else if (stack.getItem() instanceof ReactorFuelRodItem) {
            state.rodType          = ReactorCellBERRenderState.RodType.FUEL;
            state.controlInsertion = 100; // fuel rods always fully inserted
        } else {
            return;
        }

        ClientLevel level = entity.getLevel() instanceof ClientLevel cl ? cl : null;
        BlockPos pos = entity.getBlockPos();
        state.light = level != null
                ? LightCoordsUtil.getLightCoords(level, pos.above())
                : LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    public void submit(ReactorCellBERRenderState state, PoseStack pose,
                       SubmitNodeCollector nodes, CameraRenderState camera) {
        if (state.rodType == ReactorCellBERRenderState.RodType.NONE) return;

        Identifier texture = state.rodType == ReactorCellBERRenderState.RodType.FUEL
                ? TEXTURE_FUEL : TEXTURE_CONTROL;

        float t       = state.controlInsertion / 100.0f;
        float yBottom = 1.0f - ROD_H * t;
        float yTop    = yBottom + ROD_H;

        nodes.submitCustomGeometry(pose, RenderTypes.entitySolid(texture),
                (p, buf) -> box(p, buf,
                        ROD_MIN, yBottom, ROD_MIN,
                        ROD_MAX, yTop,    ROD_MAX,
                        state.light));
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buf,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             int light) {
        int color = 0xFFFFFFFF;
        // +Y (top cap)   u→x, v→z  (4×4)
        quad(pose, buf, x0,y1,z0, CAP_U0,CAP_V0, x0,y1,z1, CAP_U0,CAP_V1, x1,y1,z1, CAP_U1,CAP_V1, x1,y1,z0, CAP_U1,CAP_V0, color, light,  0, 1, 0);
        // -Y (bottom cap) u→-x, v→z  (4×4 — mirrored but cap is symmetric)
        quad(pose, buf, x1,y0,z0, CAP_U0,CAP_V0, x1,y0,z1, CAP_U0,CAP_V1, x0,y0,z1, CAP_U1,CAP_V1, x0,y0,z0, CAP_U1,CAP_V0, color, light,  0,-1, 0);
        // -Z (north side) u→x, v→y top-to-bottom  (4×80)
        quad(pose, buf, x0,y1,z0, SIDE_U0,SIDE_V0, x1,y1,z0, SIDE_U1,SIDE_V0, x1,y0,z0, SIDE_U1,SIDE_V1, x0,y0,z0, SIDE_U0,SIDE_V1, color, light,  0, 0,-1);
        // +Z (south side) u→-x, v→y top-to-bottom  (4×80)
        quad(pose, buf, x1,y1,z1, SIDE_U0,SIDE_V0, x0,y1,z1, SIDE_U1,SIDE_V0, x0,y0,z1, SIDE_U1,SIDE_V1, x1,y0,z1, SIDE_U0,SIDE_V1, color, light,  0, 0, 1);
        // -X (west side)  u→-z, v→y top-to-bottom  (4×80)
        quad(pose, buf, x0,y1,z1, SIDE_U0,SIDE_V0, x0,y1,z0, SIDE_U1,SIDE_V0, x0,y0,z0, SIDE_U1,SIDE_V1, x0,y0,z1, SIDE_U0,SIDE_V1, color, light, -1, 0, 0);
        // +X (east side)  u→z, v→y top-to-bottom   (4×80)
        quad(pose, buf, x1,y1,z0, SIDE_U0,SIDE_V0, x1,y1,z1, SIDE_U1,SIDE_V0, x1,y0,z1, SIDE_U1,SIDE_V1, x1,y0,z0, SIDE_U0,SIDE_V1, color, light,  1, 0, 0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buf,
                              float x0, float y0, float z0, float u0a, float v0a,
                              float x1, float y1, float z1, float u1a, float v1a,
                              float x2, float y2, float z2, float u2a, float v2a,
                              float x3, float y3, float z3, float u3a, float v3a,
                              int color, int light, float nx, float ny, float nz) {
        buf.addVertex(pose, x0, y0, z0).setColor(color).setUv(u0a, v0a)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x1, y1, z1).setColor(color).setUv(u1a, v1a)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x2, y2, z2).setColor(color).setUv(u2a, v2a)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x3, y3, z3).setColor(color).setUv(u3a, v3a)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
