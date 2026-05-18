/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a 1×5×1 block rod above each ReactorCell that contains a control rod.
 * The rod slides vertically with insertion percentage:
 *   0%  inserted → rod fully above cell  (yBottom = 1.0, yTop = 6.0)
 *   100% inserted → rod fully inside cell (yBottom = -4.0, yTop = 1.0)
 */
public class ReactorBER implements BlockEntityRenderer<ReactorBlockEntity, ReactorBERRenderState> {

    // 1×5×1 full blocks — fills the entire cell face in X/Z
    private static final float X0 = 0.0f;
    private static final float X1 = 1.0f;
    private static final float Z0 = 0.0f;
    private static final float Z1 = 1.0f;
    private static final float ROD_H = 5.0f;

    // Steel-gray graphite color (fully opaque)
    private static final int COLOR = ARGB.color(0xFF, 0x50, 0x58, 0x60);

    public ReactorBER(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public ReactorBERRenderState createRenderState() { return new ReactorBERRenderState(); }

    @Override
    public void extractRenderState(ReactorBlockEntity entity, ReactorBERRenderState state,
                                   float partialTicks, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPos, breakProgress);
        // Control-rod rendering is now handled per-cell by ReactorCellBER.
        state.rods.clear();
    }

    @Override
    public void submit(ReactorBERRenderState state, PoseStack pose,
                       SubmitNodeCollector nodes, CameraRenderState camera) {
        if (state.rods.isEmpty()) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(Identifier.withDefaultNamespace("block/white_wool"));
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        for (ReactorBERRenderState.RodEntry rod : state.rods) {
            float t       = rod.control() / 100.0f;
            // 0% → yBottom=1.0 (resting above cell); 100% → yBottom=-4.0 (fully inside)
            float yBottom = 1.0f - ROD_H * t;
            float yTop    = yBottom + ROD_H;

            pose.pushPose();
            pose.translate(rod.dx(), rod.dy(), rod.dz());

            nodes.submitCustomGeometry(pose, RenderTypes.eyes(sprite.atlasLocation()),
                    (p, buf) -> box(p, buf,
                            X0, yBottom, Z0, X1, yTop, Z1,
                            u0, v0, u1, v1, COLOR, rod.light()));

            pose.popPose();
        }
    }

    // ── Geometry helpers (copied from ThermalConductorBER pattern) ────────────

    private static void box(PoseStack.Pose pose, VertexConsumer buf,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float u0, float v0, float u1, float v1,
                             int color, int light) {
        // +Y
        quad(pose, buf, x0,y1,z0, u0,v0, x0,y1,z1, u0,v1, x1,y1,z1, u1,v1, x1,y1,z0, u1,v0, color, light, 0,1,0);
        // -Y
        quad(pose, buf, x1,y0,z0, u0,v0, x1,y0,z1, u0,v1, x0,y0,z1, u1,v1, x0,y0,z0, u1,v0, color, light, 0,-1,0);
        // -Z north
        quad(pose, buf, x0,y1,z0, u0,v0, x1,y1,z0, u1,v0, x1,y0,z0, u1,v1, x0,y0,z0, u0,v1, color, light, 0,0,-1);
        // +Z south
        quad(pose, buf, x1,y1,z1, u0,v0, x0,y1,z1, u1,v0, x0,y0,z1, u1,v1, x1,y0,z1, u0,v1, color, light, 0,0,1);
        // -X west
        quad(pose, buf, x0,y1,z1, u0,v0, x0,y1,z0, u1,v0, x0,y0,z0, u1,v1, x0,y0,z1, u0,v1, color, light, -1,0,0);
        // +X east
        quad(pose, buf, x1,y1,z0, u0,v0, x1,y1,z1, u1,v0, x1,y0,z1, u1,v1, x1,y0,z0, u0,v1, color, light, 1,0,0);
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
