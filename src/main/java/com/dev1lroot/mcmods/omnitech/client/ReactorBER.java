/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorStructure;
import net.minecraft.core.BlockPos;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Renders two things inside the reactor structure:
 *
 * <ol>
 *   <li>Control-rod geometry (1×5×1 bars) – delegated to {@link ReactorCellBER};
 *       the rod list is always cleared here since that renderer handles it.</li>
 *   <li>Coolant fluid volume – a box that fills the entire XZ inner cavity and
 *       rises from y=1 to y=1+(fill×5), rendered with the fluid's real sprite
 *       and tint colour.</li>
 * </ol>
 */
public class ReactorBER implements BlockEntityRenderer<ReactorBlockEntity, ReactorBERRenderState>,
        IBlockEntityRendererExtension<ReactorBlockEntity> {

    // Control-rod placeholder constants (kept from original, rendering delegated to CellBER)
    private static final float X0 = 0.0f, X1 = 1.0f, Z0 = 0.0f, Z1 = 1.0f, ROD_H = 5.0f;
    private static final int   ROD_COLOR = ARGB.color(0xFF, 0x50, 0x58, 0x60);

    // Inner cavity Y bounds (block-local, relative to origin BE block)
    private static final float Y_CAVITY_BOT = 1.0f;
    private static final float INNER_HEIGHT  = 5.0f; // ReactorStructure.HEIGHT - 2

    // Alpha for translucent coolants (≈ 75%)
    private static final int TRANSLUCENT_ALPHA = 191;

    public ReactorBER(BlockEntityRendererProvider.Context ctx) {}

    // ── Frustum culling & distance ────────────────────────────────────────────

    @Override
    public AABB getRenderBoundingBox(ReactorBlockEntity entity) {
        if (!entity.isFormed()) return new AABB(entity.getBlockPos());
        ReactorStructure s = entity.getStructure();
        if (s == null) return new AABB(entity.getBlockPos());
        BlockPos o = s.origin;
        return new AABB(o.getX(), o.getY(), o.getZ(),
                o.getX() + s.width, o.getY() + ReactorStructure.HEIGHT, o.getZ() + s.depth);
    }

    @Override
    public boolean shouldRender(ReactorBlockEntity entity, Vec3 cameraPos) {
        return Vec3.atCenterOf(entity.getBlockPos()).distanceToSqr(cameraPos) <= 16.0 * 16.0;
    }

    // ── Render state factory ──────────────────────────────────────────────────

    @Override
    public ReactorBERRenderState createRenderState() { return new ReactorBERRenderState(); }

    // ── State extraction (main thread) ────────────────────────────────────────

    @Override
    public void extractRenderState(ReactorBlockEntity entity, ReactorBERRenderState state,
                                   float partialTicks, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPos, breakProgress);

        // Rod rendering is handled per-cell by ReactorCellBER
        state.rods.clear();

        // Reset per-frame visualization state
        state.cherenkovLX      = null;
        state.cherenkovLZ      = null;
        state.cherenkovFluxPct = null;

        // Coolant fluid
        ReactorStructure s = entity.isFormed() ? entity.getStructure() : null;
        if (s == null) {
            state.coolantSprite = null;
            state.coolantFill   = 0f;
            return;
        }

        FluidStack coolant = entity.getCoolantTank();
        int capacity = entity.getTankCapacity();

        if (coolant.isEmpty() || capacity <= 0 || coolant.getAmount() <= 0) {
            state.coolantSprite = null;
            state.coolantFill   = 0f;
            return;
        }

        state.coolantFill  = (float) coolant.getAmount() / capacity;
        state.innerWidth   = s.width  - 2;
        state.innerDepth   = s.depth  - 2;

        FluidStateModelSet modelSet = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        FluidModel fm = modelSet.get(coolant.getFluid().defaultFluidState());

        state.coolantSprite      = fm.stillMaterial().sprite();
        state.coolantTranslucent = fm.layer() == ChunkSectionLayer.TRANSLUCENT;
        state.coolantTintARGB    = fm.fluidTintSource() != null
                                   ? fm.fluidTintSource().colorAsStack(coolant) : -1;

        if (coolant.getFluid().getFluidType().getLightLevel() > 0) {
            state.coolantLight = LightCoordsUtil.FULL_BRIGHT;
        } else if (entity.getLevel() instanceof ClientLevel cl) {
            state.coolantLight = LevelRenderer.getLightCoords(cl, entity.getBlockPos());
        } else {
            state.coolantLight = LightCoordsUtil.FULL_BRIGHT;
        }

        // Cherenkov: per-cell neutron flux → XZ layer visualization
        int[] flowPct = entity.getCellNeutronFlowPct();
        if (flowPct != null && !s.cells.isEmpty() && flowPct.length == s.cells.size()) {
            int count = s.cells.size();
            int[] lxData  = new int[count];
            int[] lzData  = new int[count];
            BlockPos origin = s.origin;
            for (int i = 0; i < count; i++) {
                BlockPos cp = s.cells.get(i);
                lxData[i] = cp.getX() - origin.getX();
                lzData[i] = cp.getZ() - origin.getZ();
            }
            state.cherenkovLX      = lxData;
            state.cherenkovLZ      = lzData;
            state.cherenkovFluxPct = flowPct.clone();
        }
    }

    // ── Submission (render thread) ────────────────────────────────────────────

    @Override
    public void submit(ReactorBERRenderState state, PoseStack pose,
                       SubmitNodeCollector nodes, CameraRenderState camera) {

        // Rod pass (currently delegated, list is always empty)
        if (!state.rods.isEmpty()) {
            TextureAtlasSprite rodSprite = Minecraft.getInstance()
                    .getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)
                    .getSprite(Identifier.withDefaultNamespace("block/white_wool"));
            float u0 = rodSprite.getU0(), u1 = rodSprite.getU1();
            float v0 = rodSprite.getV0(), v1 = rodSprite.getV1();
            for (ReactorBERRenderState.RodEntry rod : state.rods) {
                float t       = rod.control() / 100.0f;
                float yBottom = 1.0f - ROD_H * t;
                float yTop    = yBottom + ROD_H;
                pose.pushPose();
                pose.translate(rod.dx(), rod.dy(), rod.dz());
                nodes.submitCustomGeometry(pose, RenderTypes.eyes(rodSprite.atlasLocation()),
                        (p, buf) -> box(p, buf, X0, yBottom, Z0, X1, yTop, Z1,
                                u0, v0, u1, v1, ROD_COLOR, rod.light()));
                pose.popPose();
            }
        }

        // Coolant fluid pass
        if (state.coolantSprite != null && state.coolantFill > 0f) {
            submitCoolant(state, pose, nodes);
        }

        // Cherenkov radiation pass (emissive semi-transparent layers)
        if (state.cherenkovFluxPct != null && state.coolantFill > 0f) {
            submitCherenkov(state, pose, nodes);
        }
    }

    // ── Coolant volume renderer ───────────────────────────────────────────────

    private static void submitCoolant(ReactorBERRenderState state,
                                      PoseStack pose, SubmitNodeCollector nodes) {
        final TextureAtlasSprite sp = state.coolantSprite;
        final float u0 = sp.getU0(), u1 = sp.getU1();
        final float v0 = sp.getV0(), v1 = sp.getV1();

        final int iw = state.innerWidth;
        final int id = state.innerDepth;

        // Block-local coords of the inner cavity
        final float xStart = 1.0f;
        final float zStart = 1.0f;
        final float yBot   = Y_CAVITY_BOT;
        final float yTop   = Math.min(yBot + state.coolantFill * INNER_HEIGHT, yBot + INNER_HEIGHT);
        final float fluidH = yTop - yBot;
        if (fluidH <= 0f) return;

        final int r = ARGB.red(state.coolantTintARGB);
        final int g = ARGB.green(state.coolantTintARGB);
        final int b = ARGB.blue(state.coolantTintARGB);
        final int alpha = state.coolantTranslucent ? TRANSLUCENT_ALPHA : 0xFF;
        final int color = ARGB.color(alpha, r, g, b);
        final int light = state.coolantLight;

        var renderType = state.coolantTranslucent
                ? RenderTypes.entityTranslucent(sp.atlasLocation())
                : RenderTypes.entitySolid(sp.atlasLocation());

        nodes.submitCustomGeometry(pose, renderType, (p, buf) -> {
            // Top surface only (+Y): tiled iw × id
            for (int cx = 0; cx < iw; cx++) {
                float bx0 = xStart + cx, bx1 = bx0 + 1f;
                for (int cz = 0; cz < id; cz++) {
                    float bz0 = zStart + cz, bz1 = bz0 + 1f;
                    quad(p, buf,
                            bx0, yTop, bz0,  u0, v0,
                            bx0, yTop, bz1,  u0, v1,
                            bx1, yTop, bz1,  u1, v1,
                            bx1, yTop, bz0,  u1, v0,
                            color, light, 0, 1, 0);
                }
            }
        });
    }

    // ── Cherenkov radiation renderer ──────────────────────────────────────────

    private static void submitCherenkov(ReactorBERRenderState state,
                                        PoseStack pose, SubmitNodeCollector nodes) {
        TextureAtlasSprite sp = Minecraft.getInstance()
                .getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(Identifier.withDefaultNamespace("block/white_concrete"));
        final float u0 = sp.getU0(), u1 = sp.getU1();
        final float v0 = sp.getV0(), v1 = sp.getV1();

        final float yBot = Y_CAVITY_BOT;
        final float yTop = Math.min(yBot + state.coolantFill * INNER_HEIGHT, yBot + INNER_HEIGHT);

        final int[] lxArr   = state.cherenkovLX;
        final int[] lzArr   = state.cherenkovLZ;
        final int[] fluxArr = state.cherenkovFluxPct;
        final int   count   = lxArr.length;

        // 16 layers descending into the water, starting 0.1 below the surface.
        // Layer 0 is brightest (near surface), fading deeper.
        nodes.submitCustomGeometry(pose, RenderTypes.eyes(sp.atlasLocation()), (p, buf) -> {
            for (int layer = 0; layer < 16; layer++) {
                float layerY = yTop - 0.1f - layer * 0.25f;
                if (layerY < yBot) break; // don't go below cavity floor
                float fade = (float) Math.pow(0.75, layer);
                for (int i = 0; i < count; i++) {
                    float cx = lxArr[i];
                    float cz = lzArr[i];
                    int color = cherenkovColor(fluxArr[i], fade);
                    // Top face (visible from above)
                    quad(p, buf,
                            cx,     layerY, cz,     u0, v0,
                            cx,     layerY, cz + 1, u0, v1,
                            cx + 1, layerY, cz + 1, u1, v1,
                            cx + 1, layerY, cz,     u1, v0,
                            color, LightCoordsUtil.FULL_BRIGHT, 0, 1, 0);
                    // Bottom face (visible from inside the reactor looking up)
                    quad(p, buf,
                            cx + 1, layerY, cz,     u1, v0,
                            cx + 1, layerY, cz + 1, u1, v1,
                            cx,     layerY, cz + 1, u0, v1,
                            cx,     layerY, cz,     u0, v0,
                            color, LightCoordsUtil.FULL_BRIGHT, 0, -1, 0);
                }
            }
        });
    }

    /**
     * Maps flux 0-100 to azure-blue ARGB.
     * At flux=0: deep navy, alpha≈120.  At flux=100: bright cyan-white, alpha≈220.
     * {@code fade} reduces alpha for higher-altitude layers.
     */
    private static int cherenkovColor(int fluxPct, float fade) {
        float t = Math.max(0, Math.min(100, fluxPct)) / 100f;
        int r = (int)( 20 + t * 80f);     //  20 →  100
        int g = (int)(120 + t * 120f);    // 120 →  240
        int b = (int)(210 + t * 45f);     // 210 →  255
        int a = (int)((120 + t * 100f) * fade); // 120-220 scaled by fade
        return ARGB.color(Math.max(10, a), r, g, b);
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private static void box(PoseStack.Pose pose, VertexConsumer buf,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float u0, float v0, float u1, float v1,
                             int color, int light) {
        quad(pose, buf, x0,y1,z0, u0,v0, x0,y1,z1, u0,v1, x1,y1,z1, u1,v1, x1,y1,z0, u1,v0, color, light,  0, 1, 0);
        quad(pose, buf, x1,y0,z0, u0,v0, x1,y0,z1, u0,v1, x0,y0,z1, u1,v1, x0,y0,z0, u1,v0, color, light,  0,-1, 0);
        quad(pose, buf, x0,y1,z0, u0,v0, x1,y1,z0, u1,v0, x1,y0,z0, u1,v1, x0,y0,z0, u0,v1, color, light,  0, 0,-1);
        quad(pose, buf, x1,y1,z1, u0,v0, x0,y1,z1, u1,v0, x0,y0,z1, u1,v1, x1,y0,z1, u0,v1, color, light,  0, 0, 1);
        quad(pose, buf, x0,y1,z1, u0,v0, x0,y1,z0, u1,v0, x0,y0,z0, u1,v1, x0,y0,z1, u0,v1, color, light, -1, 0, 0);
        quad(pose, buf, x1,y1,z0, u0,v0, x1,y1,z1, u1,v0, x1,y0,z1, u1,v1, x1,y0,z0, u0,v1, color, light,  1, 0, 0);
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
