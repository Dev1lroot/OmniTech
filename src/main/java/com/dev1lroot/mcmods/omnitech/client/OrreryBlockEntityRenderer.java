/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.space.OrreryBlockEntity;
import com.dev1lroot.mcmods.omnitech.client.spacemap.SpaceScene;
import com.dev1lroot.mcmods.omnitech.space.Galaxy;
import com.dev1lroot.mcmods.omnitech.space.SpaceMap;
import com.dev1lroot.mcmods.omnitech.space.SpaceMapLoader;
import com.dev1lroot.mcmods.omnitech.space.StarSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a holographic 3-D star-system miniature above the Orrery block — the same
 * scene as the space map's system view ({@link SpaceScene}): textured star, planets and
 * moons, orbit lines, belts as clouds of stone particles.
 *
 * <p>Shows the star system the player is in, or the first one on the map.
 */
public class OrreryBlockEntityRenderer implements BlockEntityRenderer<OrreryBlockEntity, OrreryRenderState> {

    /** How far above the block surface the hologram floats (blocks). */
    private static final float DISPLAY_Y = 5.0f;
    /** Radius of the hologram's outermost orbit (blocks). */
    private static final float DISPLAY_R = 100.0f;

    public OrreryBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public OrreryRenderState createRenderState() { return new OrreryRenderState(); }

    @Override
    public void extractRenderState(OrreryBlockEntity entity, OrreryRenderState state, float partialTicks, Vec3 cameraPosition,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
        state.scene = null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        SpaceMap spaceMap = SpaceMapLoader.load(mc.getResourceManager());
        if (spaceMap.galaxies == null) return;

        StarSystem system = null;
        String dimId = mc.level.dimension().identifier().toString();
        outer:
        for (Galaxy g : spaceMap.galaxies) {
            if (g.star_systems == null) continue;
            for (StarSystem s : g.star_systems) {
                if (s.bodies == null) continue;
                if (s.containsDimension(dimId)) { system = s; break outer; }
                if (system == null) system = s;
            }
        }
        if (system == null) return;

        double time = mc.level.getOverworldClockTime() + partialTicks;
        state.scene = SpaceScene.buildSystem(system, time, null, 0f);
        state.sceneScale = DISPLAY_R / SpaceScene.systemExtent(system);
    }

    @Override
    public void submit(OrreryRenderState state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera) {
        if (state.scene == null) return;
        pose.pushPose();
        pose.translate(0.5f, DISPLAY_Y, 0.5f);
        pose.scale(state.sceneScale, state.sceneScale, state.sceneScale);
        SpaceScene.submit(state.scene.cubes, state.scene.rings, state.scene.planes, pose, nodes, 2.0f);
        pose.popPose();
    }
}
