/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Renders an expanding white sphere for each active nuclear explosion.
 * Phase 1 (3s): radius 0→32, fully opaque.
 * Phase 2 (1s): radius held at 32, fully opaque.
 * Phase 3 (5s): radius 32→96, fades out to transparent.
 * Registered on the NeoForge event bus (client-only).
 */
//@OnlyIn(Dist.CLIENT)
public final class NuclearExplosionRenderer {

    private static final int STACKS = 16;
    private static final int SLICES = 24;

    private NuclearExplosionRenderer() {}

    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        if (NuclearExplosionEffect.ACTIVE.isEmpty()) return;

        long now = System.currentTimeMillis();
        NuclearExplosionEffect.ACTIVE.removeIf(e -> now - e.startMs() >= NuclearExplosionEffect.DURATION_MS);
        if (NuclearExplosionEffect.ACTIVE.isEmpty()) return;

        Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;

        TextureAtlasSprite sprite = net.minecraft.client.Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(Identifier.withDefaultNamespace("block/white_concrete"));
        float umid = (sprite.getU0() + sprite.getU1()) * 0.5f;
        float vmid = (sprite.getV0() + sprite.getV1()) * 0.5f;

        PoseStack pose = event.getPoseStack();
        SubmitNodeCollector nodes = event.getSubmitNodeCollector();

        for (NuclearExplosionEffect.ActiveEffect effect : NuclearExplosionEffect.ACTIVE) {
            long elapsed = now - effect.startMs();
            if (elapsed >= NuclearExplosionEffect.DURATION_MS) continue;

            float radius;
            int alpha;
            long p1 = NuclearExplosionEffect.PHASE1_MS;
            long p2 = NuclearExplosionEffect.PHASE2_MS;
            long p3 = NuclearExplosionEffect.PHASE3_MS;
            float r1 = NuclearExplosionEffect.PHASE1_MAX_RADIUS;
            float rMax = NuclearExplosionEffect.MAX_RADIUS;

            if (elapsed < p1) {
                float t = elapsed / (float) p1;
                radius = t * r1;
                alpha = 220;
            } else if (elapsed < p1 + p2) {
                radius = r1;
                alpha = 220;
            } else {
                float t = (elapsed - p1 - p2) / (float) p3;
                radius = r1 + t * (rMax - r1);
                alpha = Math.max(0, (int)(220 * (1f - t)));
            }
            if (radius < 0.01f) continue;
            int color = ARGB.color(alpha, 255, 255, 255);

            Vec3 center = effect.center();
            double dx = center.x - cam.x;
            double dy = center.y - cam.y;
            double dz = center.z - cam.z;

            pose.pushPose();
            pose.translate(dx, dy, dz);
            final float r = radius;
            final int c = color;
            final float u = umid, v = vmid;
            nodes.submitCustomGeometry(pose, RenderTypes.eyes(sprite.atlasLocation()),
                    (p, buf) -> renderSphere(p, buf, r, c, u, v));
            pose.popPose();
        }
    }

    private static void renderSphere(PoseStack.Pose pose, VertexConsumer buf,
                                      float radius, int color, float u, float v) {
        for (int i = 0; i < STACKS; i++) {
            double phi0 = Math.PI * i / STACKS - Math.PI / 2.0;
            double phi1 = Math.PI * (i + 1) / STACKS - Math.PI / 2.0;
            double cosPhi0 = Math.cos(phi0), sinPhi0 = Math.sin(phi0);
            double cosPhi1 = Math.cos(phi1), sinPhi1 = Math.sin(phi1);

            for (int j = 0; j < SLICES; j++) {
                double theta0 = 2.0 * Math.PI * j / SLICES;
                double theta1 = 2.0 * Math.PI * (j + 1) / SLICES;
                double cosT0 = Math.cos(theta0), sinT0 = Math.sin(theta0);
                double cosT1 = Math.cos(theta1), sinT1 = Math.sin(theta1);

                float x00 = (float)(radius * cosPhi0 * cosT0);
                float y00 = (float)(radius * sinPhi0);
                float z00 = (float)(radius * cosPhi0 * sinT0);

                float x10 = (float)(radius * cosPhi1 * cosT0);
                float y10 = (float)(radius * sinPhi1);
                float z10 = (float)(radius * cosPhi1 * sinT0);

                float x11 = (float)(radius * cosPhi1 * cosT1);
                float y11 = (float)(radius * sinPhi1);
                float z11 = (float)(radius * cosPhi1 * sinT1);

                float x01 = (float)(radius * cosPhi0 * cosT1);
                float y01 = (float)(radius * sinPhi0);
                float z01 = (float)(radius * cosPhi0 * sinT1);

                float nx = (float)(cosPhi0 * cosT0);
                float ny = (float) sinPhi0;
                float nz = (float)(cosPhi0 * sinT0);

                vertex(pose, buf, x00, y00, z00, u, v, color, nx, ny, nz);
                vertex(pose, buf, x10, y10, z10, u, v, color, nx, ny, nz);
                vertex(pose, buf, x11, y11, z11, u, v, color, nx, ny, nz);
                vertex(pose, buf, x01, y01, z01, u, v, color, nx, ny, nz);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buf,
                                float x, float y, float z,
                                float u, float v, int color,
                                float nx, float ny, float nz) {
        buf.addVertex(pose, x, y, z)
           .setColor(color)
           .setUv(u, v)
           .setOverlay(OverlayTexture.NO_OVERLAY)
           .setLight(LightCoordsUtil.FULL_BRIGHT)
           .setNormal(pose, nx, ny, nz);
    }
}
