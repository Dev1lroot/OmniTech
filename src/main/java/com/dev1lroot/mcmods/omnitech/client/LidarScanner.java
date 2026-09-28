/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Helmet lidar: every client tick casts {@link #RAYS_PER_TICK} rays from the eyes
 * across the field of view and leaves a {@link LidarPointParticle} where each one
 * hits. The dots are fixed in the world, so walking around shows a persistent,
 * slowly fading point cloud, coloured by distance (red close → blue far).
 *
 * <p>Most rays are scattered at random; the rest trace a line that sweeps top to
 * bottom once per {@link #SWEEP_TICKS}, giving the scan its visible rhythm.
 */
public final class LidarScanner {

    private static final int RAYS_PER_TICK = 150;
    private static final float SWEEP_SHARE = 0.3F;
    private static final int SWEEP_TICKS = 30;
    private static final double RANGE = 48.0;
    private static final int LIFETIME = 70;           // ticks; ~10k live points at 150 rays/tick
    private static final float SIZE = 0.035F;
    /** Pull the dot slightly toward the scanner so it doesn't z-fight with the surface. */
    private static final double SURFACE_OFFSET = 0.03;

    private static final Identifier SPRITE = Identifier.fromNamespaceAndPath(OmniTech.MODID, "lidar_point");

    private LidarScanner() {}

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        TextureAtlasSprite sprite = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(SPRITE);
        RandomSource random = mc.player.getRandom();

        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0F);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();

        double tanV = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double tanH = tanV * mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float sweep = 1.0F - 2.0F * (mc.level.getGameTime() % SWEEP_TICKS) / (float) SWEEP_TICKS;

        for (int i = 0; i < RAYS_PER_TICK; i++) {
            double u = random.nextDouble() * 2.0 - 1.0;
            double v = i < RAYS_PER_TICK * SWEEP_SHARE
                    ? sweep + (random.nextDouble() - 0.5) * 0.04
                    : random.nextDouble() * 2.0 - 1.0;
            Vec3 dir = look.add(right.scale(u * tanH)).add(up.scale(v * tanV)).normalize();
            BlockHitResult hit = mc.level.clip(new ClipContext(eye, eye.add(dir.scale(RANGE)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, mc.player));
            if (hit.getType() != HitResult.Type.BLOCK) continue;

            Vec3 at = hit.getLocation().subtract(dir.scale(SURFACE_OFFSET));
            float t = (float) Math.min(1.0, eye.distanceTo(at) / RANGE);
            float[] rgb = heat(t);
            mc.particleEngine.add(new LidarPointParticle(mc.level, at.x, at.y, at.z, sprite,
                    rgb[0], rgb[1], rgb[2], SIZE, LIFETIME - random.nextInt(10)));
        }
    }

    /** Red (t = 0) → yellow → green → cyan → blue (t = 1), matching the sonar ramp. */
    private static float[] heat(float t) {
        float[][] stops = {{1, 0, 0}, {1, 1, 0}, {0, 1, 0}, {0, 1, 1}, {0, 0.2F, 1}};
        float s = t * (stops.length - 1);
        int i = Math.min((int) s, stops.length - 2);
        float k = s - i;
        return new float[]{
                stops[i][0] + (stops[i + 1][0] - stops[i][0]) * k,
                stops[i][1] + (stops[i + 1][1] - stops[i][1]) * k,
                stops[i][2] + (stops[i + 1][2] - stops[i][2]) * k};
    }
}
