/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client.spacemap;

import com.dev1lroot.mcmods.omnitech.space.SolarSystemScene;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;

/**
 * Renders a {@link SpaceMapRenderState} into the GUI as real 3-D geometry — the same
 * picture-in-picture path as the inventory player model, with the orrery's look:
 * emissive textured boxes for bodies, line loops for orbits, flat textured galaxy discs.
 *
 * <p>The base class sets up an orthographic projection, centres the pose on the
 * viewport and scales by GUI scale × {@code state.scale()}; the state's {@code view}
 * matrix then orbits the camera around the focused body.
 * {@code SpaceNavigationScreen} projects with the identical transform for picking.
 */
public class SpaceMapPipRenderer extends PictureInPictureRenderer<SpaceMapRenderState> {

    private static final int RING_SEGS = 128;
    private static final int LIGHT = LightCoordsUtil.FULL_BRIGHT;

    @Override
    public Class<SpaceMapRenderState> getRenderStateClass() {
        return SpaceMapRenderState.class;
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;   // centre, like the entity renderer
    }

    @Override
    protected String getTextureLabel() {
        return "space_map";
    }

    @Override
    protected void renderToTexture(SpaceMapRenderState state, PoseStack pose, SubmitNodeCollector nodes) {
        pose.mulPose(state.view());

        nodes.submitCustomGeometry(pose, RenderTypes.LINES, (p, buf) -> {
            for (SpaceMapRenderState.Ring ring : state.rings()) {
                for (int seg = 0; seg < RING_SEGS; seg++) {
                    Vector3f a = SolarSystemScene.cartesian(ring.r(), (float) (2 * Math.PI * seg / RING_SEGS), ring.inclination(), ring.node());
                    Vector3f b = SolarSystemScene.cartesian(ring.r(), (float) (2 * Math.PI * (seg + 1) / RING_SEGS), ring.inclination(), ring.node());
                    a.add(ring.cx(), ring.cy(), ring.cz());
                    b.add(ring.cx(), ring.cy(), ring.cz());
                    Vector3f d = new Vector3f(b).sub(a);
                    if (d.lengthSquared() > 1e-12f) d.normalize();
                    buf.addVertex(p, a.x, a.y, a.z).setColor(ring.color()).setNormal(p, d.x, d.y, d.z).setLineWidth(1.5f);
                    buf.addVertex(p, b.x, b.y, b.z).setColor(ring.color()).setNormal(p, d.x, d.y, d.z).setLineWidth(1.5f);
                }
            }
        });

        for (SpaceMapRenderState.Plane plane : state.planes()) {
            float h = plane.half();
            nodes.submitCustomGeometry(pose, RenderTypes.eyes(plane.texture()), (p, buf) -> {
                // both faces, so the disc is visible from below too
                quad(p, buf, plane.x() - h, plane.y(), plane.z() - h, 0, 0, plane.x() - h, plane.y(), plane.z() + h, 0, 1,
                        plane.x() + h, plane.y(), plane.z() + h, 1, 1, plane.x() + h, plane.y(), plane.z() - h, 1, 0,
                        plane.color(), 0, 1, 0);
                quad(p, buf, plane.x() + h, plane.y(), plane.z() - h, 1, 0, plane.x() + h, plane.y(), plane.z() + h, 1, 1,
                        plane.x() - h, plane.y(), plane.z() + h, 0, 1, plane.x() - h, plane.y(), plane.z() - h, 0, 0,
                        plane.color(), 0, -1, 0);
            });
        }

        TextureAtlasSprite white = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(Identifier.withDefaultNamespace("block/white_wool"));
        for (SpaceMapRenderState.Cube cube : state.cubes()) {
            pose.pushPose();
            pose.translate(cube.x(), cube.y(), cube.z());
            if (cube.spin() != 0f) pose.rotate(Axis.YP.rotation(cube.spin()));
            float h = cube.half();
            if (cube.texture() != null) {
                nodes.submitCustomGeometry(pose, RenderTypes.eyes(cube.texture()), (p, buf) ->
                        box(p, buf, h, 0, 0, 1, 1, cube.color()));
            } else {
                float u0 = white.getU0(), v0 = white.getV0(), u1 = white.getU1(), v1 = white.getV1();
                nodes.submitCustomGeometry(pose, RenderTypes.eyes(white.atlasLocation()), (p, buf) ->
                        box(p, buf, h, u0, v0, u1, v1, cube.color()));
            }
            pose.popPose();
        }
    }

    private static void box(PoseStack.Pose p, VertexConsumer buf, float h,
                            float u0, float v0, float u1, float v1, int color) {
        float a = -h, b = h;
        quad(p, buf, a, b, a, u0, v0, a, b, b, u0, v1, b, b, b, u1, v1, b, b, a, u1, v0, color, 0, 1, 0);
        quad(p, buf, b, a, a, u0, v0, b, a, b, u0, v1, a, a, b, u1, v1, a, a, a, u1, v0, color, 0, -1, 0);
        quad(p, buf, a, b, a, u0, v0, b, b, a, u1, v0, b, a, a, u1, v1, a, a, a, u0, v1, color, 0, 0, -1);
        quad(p, buf, b, b, b, u0, v0, a, b, b, u1, v0, a, a, b, u1, v1, b, a, b, u0, v1, color, 0, 0, 1);
        quad(p, buf, a, b, b, u0, v0, a, b, a, u1, v0, a, a, a, u1, v1, a, a, b, u0, v1, color, -1, 0, 0);
        quad(p, buf, b, b, a, u0, v0, b, b, b, u1, v0, b, a, b, u1, v1, b, a, a, u0, v1, color, 1, 0, 0);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer buf,
                             float x0, float y0, float z0, float u0, float v0,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             int color, float nx, float ny, float nz) {
        int ov = OverlayTexture.NO_OVERLAY;
        buf.addVertex(p, x0, y0, z0).setColor(color).setUv(u0, v0).setOverlay(ov).setLight(LIGHT).setNormal(p, nx, ny, nz);
        buf.addVertex(p, x1, y1, z1).setColor(color).setUv(u1, v1).setOverlay(ov).setLight(LIGHT).setNormal(p, nx, ny, nz);
        buf.addVertex(p, x2, y2, z2).setColor(color).setUv(u2, v2).setOverlay(ov).setLight(LIGHT).setNormal(p, nx, ny, nz);
        buf.addVertex(p, x3, y3, z3).setColor(color).setUv(u3, v3).setOverlay(ov).setLight(LIGHT).setNormal(p, nx, ny, nz);
    }
}
