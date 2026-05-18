/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.thermal.thermal_conductor.ThermalConductorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.thermal_conductor.ThermalConductorBlockEntity;
import com.dev1lroot.mcmods.omnitech.io.IThermalNode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a sin()-pulsing translucent color overlay on thermal conductor pipes to
 * visualize live heat (red) or cold (blue) flow. The overlay sits slightly outside
 * the block model geometry to avoid Z-fighting.
 */
public class ThermalConductorBER
        implements BlockEntityRenderer<ThermalConductorBlockEntity, ThermalConductorRenderState> {

    // Model geometry (in block units, 0.0–1.0) — matches thermal_conductor_core/arm models
    // Arm half-width: 4 px / 16 = 0.25; center = 0.5 → arms span [0.375, 0.625]
    private static final float C0 = 6f / 16f;   // 0.375
    private static final float C1 = 10f / 16f;  // 0.625

    // Small outward offset to avoid Z-fighting with the block model
    private static final float EP = 0.002f;

    public ThermalConductorBER(BlockEntityRendererProvider.Context context) {}

    // ── RenderState ───────────────────────────────────────────────────────────

    @Override
    public ThermalConductorRenderState createRenderState() {
        return new ThermalConductorRenderState();
    }

    @Override
    public void extractRenderState(
            ThermalConductorBlockEntity entity,
            ThermalConductorRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {

        BlockEntityRenderer.super.extractRenderState(
                entity, state, partialTicks, cameraPosition, breakProgress);

        float deviation = entity.getTemperature() - IThermalNode.AMBIENT_TEMP;

        if (Math.abs(deviation) < 1f) {
            state.argb = 0;
        } else {
            // Static tint: neutral at 0 deviation, full red/blue at ±273 °C.
            float frac = Math.min(1f, Math.abs(deviation) / 273f);
            int alpha = (int)(0xAF * frac);
            int r, g, b;
            if (deviation > 0) {
                r = 255; g = 0; b = 0;
            } else {
                r = 0; g = 0; b = 255;
            }
            state.argb = ARGB.color(alpha, r, g, b);
        }

        BlockState bs = entity.getBlockState();
        state.north = bs.getValue(ThermalConductorBlock.NORTH);
        state.south = bs.getValue(ThermalConductorBlock.SOUTH);
        state.east  = bs.getValue(ThermalConductorBlock.EAST);
        state.west  = bs.getValue(ThermalConductorBlock.WEST);
        state.up    = bs.getValue(ThermalConductorBlock.UP);
        state.down  = bs.getValue(ThermalConductorBlock.DOWN);

        if (entity.getLevel() instanceof ClientLevel clientLevel) {
            state.lightCoords = LevelRenderer.getLightCoords(clientLevel, entity.getBlockPos());
        } else {
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        }
    }

    // ── Submission ────────────────────────────────────────────────────────────

    @Override
    public void submit(ThermalConductorRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {

        if (ARGB.alpha(state.argb) == 0) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(Identifier.withDefaultNamespace("block/white_wool"));

        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        int color = state.argb;
        int light = LightCoordsUtil.FULL_BRIGHT; // glow effect, independent of block lighting

        // sprite.atlasLocation() = "minecraft:textures/atlas/blocks.png" — the actual PNG path
        // AtlasIds.BLOCKS = "minecraft:blocks" — only the atlas lookup key, not the texture path
        submitNodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.eyes(sprite.atlasLocation()), (pose, buf) -> {

            // Core cube — always present
            box(pose, buf, C0 - EP, C0 - EP, C0 - EP, C1 + EP, C1 + EP, C1 + EP,
                    u0, v0, u1, v1, color, light);

            // Arms — only where the conductor connects
            if (state.north) box(pose, buf, C0 - EP, C0 - EP, -EP,      C1 + EP, C1 + EP, C0 + EP,
                    u0, v0, u1, v1, color, light);
            if (state.south) box(pose, buf, C0 - EP, C0 - EP, C1 - EP,  C1 + EP, C1 + EP, 1f + EP,
                    u0, v0, u1, v1, color, light);
            if (state.west)  box(pose, buf, -EP,     C0 - EP, C0 - EP,  C0 + EP, C1 + EP, C1 + EP,
                    u0, v0, u1, v1, color, light);
            if (state.east)  box(pose, buf, C1 - EP, C0 - EP, C0 - EP,  1f + EP, C1 + EP, C1 + EP,
                    u0, v0, u1, v1, color, light);
            if (state.down)  box(pose, buf, C0 - EP, -EP,     C0 - EP,  C1 + EP, C0 + EP, C1 + EP,
                    u0, v0, u1, v1, color, light);
            if (state.up)    box(pose, buf, C0 - EP, C1 - EP, C0 - EP,  C1 + EP, 1f + EP, C1 + EP,
                    u0, v0, u1, v1, color, light);
        });
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /** Renders all 6 faces of an axis-aligned box. */
    private static void box(PoseStack.Pose pose, VertexConsumer buf,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float u0, float v0, float u1, float v1, int color, int light) {

        // +Y
        quad(pose, buf, x0,y1,z0, u0,v0, x0,y1,z1, u0,v1, x1,y1,z1, u1,v1, x1,y1,z0, u1,v0, color, light, 0,1,0);
        // -Y
        quad(pose, buf, x1,y0,z0, u0,v0, x1,y0,z1, u0,v1, x0,y0,z1, u1,v1, x0,y0,z0, u1,v0, color, light, 0,-1,0);
        // -Z (north)
        quad(pose, buf, x0,y1,z0, u0,v0, x1,y1,z0, u1,v0, x1,y0,z0, u1,v1, x0,y0,z0, u0,v1, color, light, 0,0,-1);
        // +Z (south)
        quad(pose, buf, x1,y1,z1, u0,v0, x0,y1,z1, u1,v0, x0,y0,z1, u1,v1, x1,y0,z1, u0,v1, color, light, 0,0,1);
        // -X (west)
        quad(pose, buf, x0,y1,z1, u0,v0, x0,y1,z0, u1,v0, x0,y0,z0, u1,v1, x0,y0,z1, u0,v1, color, light, -1,0,0);
        // +X (east)
        quad(pose, buf, x1,y1,z0, u0,v0, x1,y1,z1, u1,v0, x1,y0,z1, u1,v1, x1,y0,z0, u0,v1, color, light, 1,0,0);
    }

    private static void quad(
            PoseStack.Pose pose, VertexConsumer buf,
            float x0, float y0, float z0, float u0a, float v0a,
            float x1, float y1, float z1, float u1a, float v1a,
            float x2, float y2, float z2, float u2a, float v2a,
            float x3, float y3, float z3, float u3a, float v3a,
            int color, int light, float nx, float ny, float nz) {

        buf.addVertex(pose, x0, y0, z0).setColor(color)
                .setUv(u0a, v0a).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x1, y1, z1).setColor(color)
                .setUv(u1a, v1a).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x2, y2, z2).setColor(color)
                .setUv(u2a, v2a).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x3, y3, z3).setColor(color)
                .setUv(u3a, v3a).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }
}
