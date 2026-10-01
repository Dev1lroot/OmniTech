/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.suspension_insulator.SuspensionInsulatorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.suspension_insulator.SuspensionInsulatorBlockEntity;
import com.dev1lroot.mcmods.omnitech.util.ConductorMetals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the overhead lines strung between suspension insulators as a sagging
 * square cable tinted by its metal. Each span is stored at both ends but drawn
 * once, by the end with the lower block position.
 */
public class OverheadLineRenderer
        implements BlockEntityRenderer<SuspensionInsulatorBlockEntity, OverheadLineRenderState> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/entity/overhead_line.png");

    private static final int   SEGMENTS   = 16;
    private static final float HALF_WIDTH = 0.03f;
    /** Mid-span sag per block of span length. */
    private static final double SAG_PER_BLOCK = 0.035;

    public OverheadLineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public OverheadLineRenderState createRenderState() {
        return new OverheadLineRenderState();
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(SuspensionInsulatorBlockEntity be) {
        AABB box = new AABB(be.getBlockPos());
        for (SuspensionInsulatorBlockEntity.Link link : be.getLinks()) {
            box = box.minmax(new AABB(link.other()));
        }
        return box.inflate(0, SAG_PER_BLOCK * SuspensionInsulatorBlockEntity.MAX_SPAN + 1, 0);
    }

    @Override
    public void extractRenderState(SuspensionInsulatorBlockEntity be, OverheadLineRenderState state,
            float partialTicks, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPos, crumbling);
        state.spans.clear();
        Level level = be.getLevel();
        if (level == null) return;

        BlockPos self = be.getBlockPos();
        Vec3 origin = Vec3.atLowerCornerOf(self);
        Vec3 a = be.attachPoint();
        for (SuspensionInsulatorBlockEntity.Link link : be.getLinks()) {
            BlockPos other = link.other();
            if (self.compareTo(other) > 0) continue; // the other end draws it
            BlockState otherState = level.getBlockState(other);
            if (!(otherState.getBlock() instanceof SuspensionInsulatorBlock)) continue;

            Vec3 b = SuspensionInsulatorBlock.attachPoint(other, otherState);
            double sag = SAG_PER_BLOCK * a.distanceTo(b);
            Vec3[] points = new Vec3[SEGMENTS + 1];
            int[] lights = new int[SEGMENTS + 1];
            for (int i = 0; i <= SEGMENTS; i++) {
                double t = i / (double) SEGMENTS;
                Vec3 p = a.lerp(b, t).add(0, -sag * 4 * t * (1 - t), 0);
                points[i] = p.subtract(origin);
                lights[i] = LightCoordsUtil.getLightCoords(level, BlockPos.containing(p));
            }
            state.spans.add(new OverheadLineRenderState.Span(points, lights, ConductorMetals.color(link.metal())));
        }
    }

    @Override
    public void submit(OverheadLineRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodes, CameraRenderState camera) {
        if (state.spans.isEmpty()) return;
        nodes.submitCustomGeometry(poseStack, RenderTypes.entitySolid(TEXTURE), (pose, buf) -> {
            for (OverheadLineRenderState.Span span : state.spans) drawSpan(pose, buf, span);
        });
    }

    private static void drawSpan(PoseStack.Pose pose, VertexConsumer buf, OverheadLineRenderState.Span span) {
        Vec3[] p = span.points();
        Vec3 chord = p[p.length - 1].subtract(p[0]);
        Vec3 side = new Vec3(chord.z, 0, -chord.x);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();

        // Ring corners: side × up offsets around each point, "up" perpendicular to the local tangent.
        Vec3[][] rings = new Vec3[p.length][4];
        for (int i = 0; i < p.length; i++) {
            Vec3 tangent = p[Math.min(i + 1, p.length - 1)].subtract(p[Math.max(i - 1, 0)]);
            // Not flipped to +Y: the (side, up, tangent) frame keeps the quad winding outward.
            Vec3 up = side.cross(tangent).normalize();
            Vec3 s = side.scale(HALF_WIDTH), u = up.scale(HALF_WIDTH);
            rings[i][0] = p[i].add(s).add(u);
            rings[i][1] = p[i].subtract(s).add(u);
            rings[i][2] = p[i].subtract(s).subtract(u);
            rings[i][3] = p[i].add(s).subtract(u);
        }

        int color = span.color();
        for (int i = 0; i < p.length - 1; i++) {
            for (int f = 0; f < 4; f++) {
                int g = (f + 1) & 3;
                Vec3 n = rings[i][f].add(rings[i][g]).scale(0.5).subtract(p[i]).normalize();
                vertex(pose, buf, rings[i][f],     0f, 0f, color, span.lights()[i],     n);
                vertex(pose, buf, rings[i + 1][f], 0f, 1f, color, span.lights()[i + 1], n);
                vertex(pose, buf, rings[i + 1][g], 1f, 1f, color, span.lights()[i + 1], n);
                vertex(pose, buf, rings[i][g],     1f, 0f, color, span.lights()[i],     n);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buf, Vec3 v, float u, float w,
            int color, int light, Vec3 n) {
        buf.addVertex(pose, (float) v.x, (float) v.y, (float) v.z).setColor(color).setUv(u, w)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }
}
