/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Renders a five-layer nuclear mushroom cloud for each active
 * {@link NuclearExplosionEffect}.
 *
 * <p>All five layers are submitted in a single draw-call per explosion (same
 * atlas render-type), sharing the midpoint UV of {@code block/white_concrete}.
 * Per-vertex ARGB drives both colour and opacity; {@link RenderTypes#eyes} keeps
 * every layer full-bright and unaffected by world lighting.
 *
 * <p>Layer summary (times in ms, radii in blocks):
 * <pre>
 *  Layer         Time            Geometry          Peak colour
 *  ─────────────────────────────────────────────────────────────────────────────
 *  Flash         0 → 800        sphere 0→80        white, fades fast
 *  Fireball      0 → 6500       sphere rising,     white→orange→red, fades
 *                               r≤48, rise≤64
 *  Shockwave     150 → 6000     torus R→300,r=8    off-white, bell-curve α
 *  Stem column   800 → 11000    cylinder h→140     grey-brown, fades late
 *  Mushroom cap  4000 → 13000   torus rising,      dark grey-brown, fades late
 *                               R→90, r→35
 * </pre>
 */
public final class NuclearExplosionRenderer {

    // Geometry resolution — enough detail without excessive vertex count
    private static final int SPHERE_STACKS = 12;
    private static final int SPHERE_SLICES = 20;
    private static final int TORUS_U       = 28;  // segments around the major ring
    private static final int TORUS_V       =  8;  // segments around the tube
    private static final int STEM_SEGS     = 16;  // cylinder cross-section
    private static final int STEM_ROWS     =  6;  // cylinder height quads

    private NuclearExplosionRenderer() {}

    // ── Event handler ─────────────────────────────────────────────────────────

    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        if (NuclearExplosionEffect.ACTIVE.isEmpty()) return;

        long now = System.currentTimeMillis();
        NuclearExplosionEffect.ACTIVE.removeIf(
                e -> now - e.startMs() >= NuclearExplosionEffect.DURATION_MS);
        if (NuclearExplosionEffect.ACTIVE.isEmpty()) return;

        Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;

        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(Identifier.withDefaultNamespace("block/white_concrete"));
        final float umid     = (sprite.getU0() + sprite.getU1()) * 0.5f;
        final float vmid     = (sprite.getV0() + sprite.getV1()) * 0.5f;
        final Identifier atlasLoc = sprite.atlasLocation();

        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector nodes = event.getSubmitNodeCollector();

        for (NuclearExplosionEffect.ActiveEffect effect : NuclearExplosionEffect.ACTIVE) {
            long elapsed = now - effect.startMs();
            if (elapsed >= NuclearExplosionEffect.DURATION_MS) continue;

            Vec3 center = effect.center();
            poseStack.pushPose();
            poseStack.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);

            final long el = elapsed;
            nodes.submitCustomGeometry(poseStack, RenderTypes.eyes(atlasLoc), (pose, buf) -> {
                renderFlash(pose, buf, el, umid, vmid);
                renderBlastColumn(pose, buf, el, umid, vmid);
                renderFireball(pose, buf, el, umid, vmid);
                renderShockwave(pose, buf, el, umid, vmid);
                renderBiomeRing(pose, buf, el, umid, vmid);
                renderStem(pose, buf, el, umid, vmid);
                renderMushroomCap(pose, buf, el, umid, vmid);
            });

            poseStack.popPose();
        }
    }

    // ── Particle shockwave (game thread) ─────────────────────────────────────

    /**
     * Spawns a single spherical burst of {@link ParticleTypes#FIREWORK} sparks
     * at the explosion centre the first game tick after each detonation.
     *
     * <p>All particles originate at the explosion centre and fly outward with
     * velocities calculated to carry them to {@link NuclearExplosionEffect#BURST_RADIUS_BLOCKS}
     * before drag halts them (firework drag ≈ 0.91/tick →
     * v₀ = radius × (1 − 0.91) = radius × 0.09).
     *
     * <p>Direction is derived from a Fibonacci sphere (golden-angle spiral) so
     * coverage is perfectly uniform across the full sphere with no pole bunching.
     *
     * <p>Safe to call from {@link ClientTickEvent.Post} (game thread).
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || NuclearExplosionEffect.ACTIVE.isEmpty()) return;

        // v₀ to travel (BURST_RADIUS_BLOCKS − BURST_START_RADIUS) before drag halts it.
        // Firework drag ≈ 0.91/tick → Σ v·0.91^n = v/(1−0.91) → v = travel × 0.09
        final double travel = NuclearExplosionEffect.BURST_RADIUS_BLOCKS
                            - NuclearExplosionEffect.BURST_START_RADIUS;  // 96 blocks
        final double speed  = travel * (1.0 - 0.91);
        final double startR = NuclearExplosionEffect.BURST_START_RADIUS;
        final int    n      = NuclearExplosionEffect.BURST_PARTICLE_COUNT;
        // Golden angle in radians — drives the Fibonacci sphere distribution
        final double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

        for (NuclearExplosionEffect.ActiveEffect effect : NuclearExplosionEffect.ACTIVE) {
            if (effect.burstFired) continue;
            effect.burstFired = true;

            Vec3 c = effect.center();
            for (int i = 0; i < n; i++) {
                // Uniform Y from 1 → -1 gives equal-area latitude bands
                double cosφ = 1.0 - 2.0 * (i + 0.5) / n;
                double sinφ = Math.sqrt(Math.max(0.0, 1.0 - cosφ * cosφ));
                double θ    = goldenAngle * i;
                double dx   = sinφ * Math.cos(θ);
                double dy   = cosφ;
                double dz   = sinφ * Math.sin(θ);
                // Spawn on the surface of the start sphere, velocity pointing outward
                mc.level.addParticle(ParticleTypes.FIREWORK,
                        c.x + dx * startR,
                        c.y + dy * startR,
                        c.z + dz * startR,
                        dx * speed, dy * speed, dz * speed);
            }
        }
    }

    // ── Layer renderers ───────────────────────────────────────────────────────

    /** Layer 1 — Blinding white flash that blooms and vanishes in under a second. */
    private static void renderFlash(PoseStack.Pose pose, VertexConsumer buf,
                                     long elapsed, float u, float v) {
        if (elapsed >= NuclearExplosionEffect.FLASH_END_MS) return;
        float t      = elapsed / (float) NuclearExplosionEffect.FLASH_END_MS;
        float radius = easeOut(t) * NuclearExplosionEffect.FLASH_MAX_R;
        int   alpha  = (int)(255 * (1f - t));
        renderSphere(pose, buf, radius, 0f, ARGB.color(alpha, 255, 255, 255), u, v);
    }

    /**
     * Layer 1b — Sky-clearing blast column.
     * A bright white cylinder of {@value NuclearExplosionEffect#BLAST_COL_RADIUS}-block
     * radius rises from the explosion height to the sky during the flash window (0–800 ms).
     * It represents the nuclear fireball column vaporising the atmosphere above the burst.
     */
    private static void renderBlastColumn(PoseStack.Pose pose, VertexConsumer buf,
                                           long elapsed, float u, float v) {
        if (elapsed >= NuclearExplosionEffect.FLASH_END_MS) return;
        float t     = elapsed / (float) NuclearExplosionEffect.FLASH_END_MS;
        int   alpha = (int)(220 * (1f - t));
        renderCylinder(pose, buf,
                NuclearExplosionEffect.BLAST_COL_RADIUS,
                NuclearExplosionEffect.BLAST_COL_HEIGHT,
                ARGB.color(alpha, 255, 255, 255), u, v);
    }

    /**
     * Layer 3b — Biome-change sweep ring.
     * A large torus expands from 0 to {@value NuclearExplosionEffect#BIOME_RING_MAX_R} blocks
     * over 10 seconds, fading linearly to transparent.  It marks the outer boundary of the
     * area affected by the nuclear event (fallout radius / biome mutation zone).
     */
    private static void renderBiomeRing(PoseStack.Pose pose, VertexConsumer buf,
                                         long elapsed, float u, float v) {
        if (elapsed < NuclearExplosionEffect.BIOME_RING_START_MS
                || elapsed >= NuclearExplosionEffect.BIOME_RING_END_MS) return;
        float t      = (elapsed - NuclearExplosionEffect.BIOME_RING_START_MS)
                     / (float)(NuclearExplosionEffect.BIOME_RING_END_MS
                               - NuclearExplosionEffect.BIOME_RING_START_MS);
        float majorR = easeOut(t) * NuclearExplosionEffect.BIOME_RING_MAX_R;
        // Linear fade: opaque at t=0, transparent at t=1
        int   alpha  = (int)(160 * (1f - t));
        renderTorus(pose, buf, majorR, NuclearExplosionEffect.BIOME_RING_MINOR_R, 0f,
                ARGB.color(alpha, 160, 80, 40), u, v);
    }

    /**
     * Layer 2 — Rising fireball. Colour transitions white→orange-yellow→orange-red
     * and the sphere lifts off the ground as it fades.
     */
    private static void renderFireball(PoseStack.Pose pose, VertexConsumer buf,
                                        long elapsed, float u, float v) {
        if (elapsed >= NuclearExplosionEffect.FIRE_END_MS) return;
        float t = elapsed / (float) NuclearExplosionEffect.FIRE_END_MS;

        // Lift-off: ease-out so it decelerates as it rises
        float riseY  = easeOut(Math.min(t * 1.4f, 1f)) * NuclearExplosionEffect.FIRE_RISE;
        // Radius blooms fast then holds
        float radius = Math.min(t / 0.22f, 1f) * NuclearExplosionEffect.FIRE_MAX_R;

        // Colour arc: white → orange-yellow → orange-red (fading)
        int fr, fg, fb, fa;
        if (t < 0.12f) {
            float ct = t / 0.12f;
            fr = 255; fg = lerp(255, 210, ct); fb = lerp(255, 50, ct); fa = 245;
        } else if (t < 0.55f) {
            float ct = (t - 0.12f) / 0.43f;
            fr = 255; fg = lerp(210, 90, ct);  fb = lerp(50, 10, ct);  fa = 235;
        } else {
            float ct = (t - 0.55f) / 0.45f;
            fr = lerp(255, 70, ct); fg = lerp(90, 30, ct); fb = 10;
            fa = (int)(235 * (1f - easeIn(ct)));
        }
        renderSphere(pose, buf, radius, riseY, ARGB.color(fa, fr, fg, fb), u, v);
    }

    /**
     * Layer 3 — Horizontal shockwave ring (Mach stem).
     * Expands radially at ground level; alpha peaks at 30% progress then fades.
     */
    private static void renderShockwave(PoseStack.Pose pose, VertexConsumer buf,
                                         long elapsed, float u, float v) {
        if (elapsed < NuclearExplosionEffect.SHOCK_START_MS
                || elapsed >= NuclearExplosionEffect.SHOCK_END_MS) return;

        float t      = (elapsed - NuclearExplosionEffect.SHOCK_START_MS)
                     / (float)(NuclearExplosionEffect.SHOCK_END_MS - NuclearExplosionEffect.SHOCK_START_MS);
        float majorR = easeOut(t) * NuclearExplosionEffect.SHOCK_MAX_R;

        // Bell-curve alpha: rises to peak then fades
        float alpha01 = t < 0.30f ? t / 0.30f : 1f - (t - 0.30f) / 0.70f;
        int   alpha   = (int)(185 * alpha01);
        renderTorus(pose, buf, majorR, NuclearExplosionEffect.SHOCK_MINOR_R, 0f,
                ARGB.color(alpha, 245, 240, 220), u, v);
    }

    /**
     * Layer 4 — Debris / smoke stem column rising from the explosion centre.
     * Fades in over 15% of its lifetime, fades out over the last 25%.
     */
    private static void renderStem(PoseStack.Pose pose, VertexConsumer buf,
                                    long elapsed, float u, float v) {
        if (elapsed < NuclearExplosionEffect.STEM_START_MS
                || elapsed >= NuclearExplosionEffect.STEM_END_MS) return;

        float t      = (elapsed - NuclearExplosionEffect.STEM_START_MS)
                     / (float)(NuclearExplosionEffect.STEM_END_MS - NuclearExplosionEffect.STEM_START_MS);
        float height = easeOut(Math.min(t / 0.55f, 1f)) * NuclearExplosionEffect.STEM_MAX_HEIGHT;

        float alpha01 = t < 0.15f ? t / 0.15f
                      : t > 0.75f ? 1f - (t - 0.75f) / 0.25f
                      : 1f;
        int alpha = (int)(205 * alpha01);
        renderCylinder(pose, buf, NuclearExplosionEffect.STEM_RADIUS, height,
                ARGB.color(alpha, 150, 120, 90), u, v);
    }

    /**
     * Layer 5 — Toroidal mushroom cap that rises above the stem and billows outward.
     * Major and minor radii both grow from zero; the torus centre climbs from ~70 %
     * of stem height to ~120 % as the animation progresses.
     */
    private static void renderMushroomCap(PoseStack.Pose pose, VertexConsumer buf,
                                           long elapsed, float u, float v) {
        if (elapsed < NuclearExplosionEffect.CAP_START_MS
                || elapsed >= NuclearExplosionEffect.CAP_END_MS) return;

        float t      = (elapsed - NuclearExplosionEffect.CAP_START_MS)
                     / (float)(NuclearExplosionEffect.CAP_END_MS - NuclearExplosionEffect.CAP_START_MS);
        float majorR = smoothstep(Math.min(t / 0.50f, 1f)) * NuclearExplosionEffect.CAP_MAX_MAJOR_R;
        float minorR = smoothstep(Math.min(t / 0.40f, 1f)) * NuclearExplosionEffect.CAP_MAX_MINOR_R;

        // Cap centre rises from 70 % → 120 % of stem height
        float h      = NuclearExplosionEffect.STEM_MAX_HEIGHT;
        float capY   = h * (0.70f + t * 0.50f);

        float alpha01 = t < 0.20f ? t / 0.20f
                      : t > 0.65f ? 1f - (t - 0.65f) / 0.35f
                      : 1f;
        int alpha = (int)(220 * alpha01);
        renderTorus(pose, buf, majorR, minorR, capY, ARGB.color(alpha, 110, 90, 75), u, v);
    }

    // ── Geometry primitives ───────────────────────────────────────────────────

    /** UV sphere of the given radius offset upward by {@code yOffset}. */
    private static void renderSphere(PoseStack.Pose pose, VertexConsumer buf,
                                      float radius, float yOffset, int color,
                                      float u, float v) {
        if (radius < 0.01f) return;
        for (int si = 0; si < SPHERE_STACKS; si++) {
            double phi0  = Math.PI * si       / SPHERE_STACKS - Math.PI / 2.0;
            double phi1  = Math.PI * (si + 1) / SPHERE_STACKS - Math.PI / 2.0;
            double cp0 = Math.cos(phi0), sp0 = Math.sin(phi0);
            double cp1 = Math.cos(phi1), sp1 = Math.sin(phi1);
            for (int sl = 0; sl < SPHERE_SLICES; sl++) {
                double t0 = 2.0 * Math.PI * sl       / SPHERE_SLICES;
                double t1 = 2.0 * Math.PI * (sl + 1) / SPHERE_SLICES;
                double ct0 = Math.cos(t0), st0 = Math.sin(t0);
                double ct1 = Math.cos(t1), st1 = Math.sin(t1);

                float x00 = (float)(radius * cp0 * ct0), y00 = (float)(radius * sp0) + yOffset, z00 = (float)(radius * cp0 * st0);
                float x10 = (float)(radius * cp1 * ct0), y10 = (float)(radius * sp1) + yOffset, z10 = (float)(radius * cp1 * st0);
                float x11 = (float)(radius * cp1 * ct1), y11 = (float)(radius * sp1) + yOffset, z11 = (float)(radius * cp1 * st1);
                float x01 = (float)(radius * cp0 * ct1), y01 = (float)(radius * sp0) + yOffset, z01 = (float)(radius * cp0 * st1);

                float nx = (float)(cp0 * ct0), ny = (float)sp0, nz = (float)(cp0 * st0);
                vertex(pose, buf, x00, y00, z00, u, v, color, nx, ny, nz);
                vertex(pose, buf, x10, y10, z10, u, v, color, nx, ny, nz);
                vertex(pose, buf, x11, y11, z11, u, v, color, nx, ny, nz);
                vertex(pose, buf, x01, y01, z01, u, v, color, nx, ny, nz);
            }
        }
    }

    /**
     * Standard torus lying in the XZ plane, tube centre at {@code (0, yOffset, 0)}.
     * Major radius {@code R} (distance from centre to tube centre),
     * minor radius {@code r} (tube cross-section radius).
     */
    private static void renderTorus(PoseStack.Pose pose, VertexConsumer buf,
                                     float majorR, float minorR, float yOffset, int color,
                                     float u, float v) {
        if (majorR < 0.01f || minorR < 0.01f) return;
        for (int ui = 0; ui < TORUS_U; ui++) {
            double u0 = 2.0 * Math.PI * ui       / TORUS_U;
            double u1 = 2.0 * Math.PI * (ui + 1) / TORUS_U;
            double cu0 = Math.cos(u0), su0 = Math.sin(u0);
            double cu1 = Math.cos(u1), su1 = Math.sin(u1);
            for (int vi = 0; vi < TORUS_V; vi++) {
                double v0 = 2.0 * Math.PI * vi       / TORUS_V;
                double v1 = 2.0 * Math.PI * (vi + 1) / TORUS_V;
                double cv0 = Math.cos(v0), sv0 = Math.sin(v0);
                double cv1 = Math.cos(v1), sv1 = Math.sin(v1);

                float x00 = (float)((majorR + minorR*cv0)*cu0), y00 = (float)(minorR*sv0) + yOffset, z00 = (float)((majorR + minorR*cv0)*su0);
                float x10 = (float)((majorR + minorR*cv0)*cu1), y10 = (float)(minorR*sv0) + yOffset, z10 = (float)((majorR + minorR*cv0)*su1);
                float x11 = (float)((majorR + minorR*cv1)*cu1), y11 = (float)(minorR*sv1) + yOffset, z11 = (float)((majorR + minorR*cv1)*su1);
                float x01 = (float)((majorR + minorR*cv1)*cu0), y01 = (float)(minorR*sv1) + yOffset, z01 = (float)((majorR + minorR*cv1)*su0);

                float nx = (float)(cv0*cu0), ny = (float)sv0, nz = (float)(cv0*su0);
                vertex(pose, buf, x00, y00, z00, u, v, color, nx, ny, nz);
                vertex(pose, buf, x10, y10, z10, u, v, color, nx, ny, nz);
                vertex(pose, buf, x11, y11, z11, u, v, color, nx, ny, nz);
                vertex(pose, buf, x01, y01, z01, u, v, color, nx, ny, nz);
            }
        }
    }

    /** Outward-facing cylinder, open-ended, growing upward from Y=0 to Y={@code height}. */
    private static void renderCylinder(PoseStack.Pose pose, VertexConsumer buf,
                                        float radius, float height, int color,
                                        float u, float v) {
        if (height < 0.01f) return;
        float rowH = height / STEM_ROWS;
        for (int seg = 0; seg < STEM_SEGS; seg++) {
            double a0 = 2.0 * Math.PI * seg       / STEM_SEGS;
            double a1 = 2.0 * Math.PI * (seg + 1) / STEM_SEGS;
            float x0 = (float)(radius * Math.cos(a0)), z0 = (float)(radius * Math.sin(a0));
            float x1 = (float)(radius * Math.cos(a1)), z1 = (float)(radius * Math.sin(a1));
            float nx  = (float) Math.cos((a0 + a1) * 0.5);
            float nz  = (float) Math.sin((a0 + a1) * 0.5);
            for (int row = 0; row < STEM_ROWS; row++) {
                float y0 = row * rowH, y1 = y0 + rowH;
                vertex(pose, buf, x0, y0, z0, u, v, color, nx, 0f, nz);
                vertex(pose, buf, x1, y0, z1, u, v, color, nx, 0f, nz);
                vertex(pose, buf, x1, y1, z1, u, v, color, nx, 0f, nz);
                vertex(pose, buf, x0, y1, z0, u, v, color, nx, 0f, nz);
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

    // ── Easing helpers ────────────────────────────────────────────────────────

    /** Smooth S-curve: slow start, fast middle, slow end. */
    private static float smoothstep(float t) { return t * t * (3f - 2f * t); }
    /** Decelerate: fast start, slow end. */
    private static float easeOut(float t)    { return 1f - (1f - t) * (1f - t); }
    /** Accelerate: slow start, fast end. */
    private static float easeIn(float t)     { return t * t; }
    /** Integer colour channel linear interpolation. */
    private static int lerp(int a, int b, float t) { return a + (int)((b - a) * t); }
}
