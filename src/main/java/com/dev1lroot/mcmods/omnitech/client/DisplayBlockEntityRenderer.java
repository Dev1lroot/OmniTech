package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockEntity;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class DisplayBlockEntityRenderer
        implements BlockEntityRenderer<DisplayBlockEntity, DisplayRenderState> {

    // Static texture cache: blockPos → DynamicTexture / ResourceLocation
    private static final Map<Long, DynamicTexture> TEXTURES  = new HashMap<>();
    private static final Map<Long, Identifier>     TEX_LOCS  = new HashMap<>();

    private static final float HALF = 0.5f;       // full-face coverage
    private static final float Z    = -0.5011f;   // slightly in front of the face (centered coords)

    public DisplayBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    // ── RenderState ───────────────────────────────────────────────────────────

    @Override
    public DisplayRenderState createRenderState() { return new DisplayRenderState(); }

    @Override
    public void extractRenderState(DisplayBlockEntity entity, DisplayRenderState state,
            float partialTicks, Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
        System.arraycopy(entity.pixels, 0, state.pixels, 0, 256);
        state.facing = entity.getBlockState().getValue(DisplayBlock.FACING);
        // Force FULL_BRIGHT so the screen glows regardless of ambient light
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
    }

    // ── Submission ────────────────────────────────────────────────────────────

    @Override
    public void submit(DisplayRenderState state, PoseStack pose,
            SubmitNodeCollector nodes, CameraRenderState camera) {

        Identifier texLoc = getOrCreateTexture(state.blockPos, state.pixels);
        int light = state.lightCoords;

        pose.pushPose();
        // Translate to block center, rotate so -Z faces the display's front
        pose.translate(0.5, 0.5, 0.5);
        switch (state.facing) {
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180f));
            case EAST  -> pose.mulPose(Axis.YP.rotationDegrees(-90f));
            case WEST  -> pose.mulPose(Axis.YP.rotationDegrees(90f));
            default    -> {} // NORTH — no rotation needed
        }

        nodes.submitCustomGeometry(pose, RenderTypes.text(texLoc), (p, buf) -> {
            // U is flipped: viewer's left (+X) = U=0, viewer's right (-X) = U=1
            buf.addVertex(p, -HALF,  HALF, Z).setColor(-1).setUv(1f, 0f).setLight(light);
            buf.addVertex(p,  HALF,  HALF, Z).setColor(-1).setUv(0f, 0f).setLight(light);
            buf.addVertex(p,  HALF, -HALF, Z).setColor(-1).setUv(0f, 1f).setLight(light);
            buf.addVertex(p, -HALF, -HALF, Z).setColor(-1).setUv(1f, 1f).setLight(light);
        });

        pose.popPose();
    }

    // ── Texture management ────────────────────────────────────────────────────

    private static Identifier getOrCreateTexture(BlockPos pos, int[] pixels) {
        long key = pos.asLong();
        DynamicTexture tex = TEXTURES.get(key);

        if (tex == null) {
            String id = "display/px_" + Long.toUnsignedString(key);
            tex = new DynamicTexture(id, 16, 16, true);
            Identifier loc = Identifier.fromNamespaceAndPath(OmniTech.MODID, id);
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            TEXTURES.put(key, tex);
            TEX_LOCS.put(key, loc);
        }

        // Update texture pixels — runs on render thread, safe to call upload()
        NativeImage img = tex.getPixels();
        for (int i = 0; i < 256; i++) {
            img.setPixel(i % 16, i / 16, ARGB.color(0xFF,
                    (pixels[i] >> 16) & 0xFF,
                    (pixels[i] >>  8) & 0xFF,
                     pixels[i]        & 0xFF));
        }
        tex.upload();

        return TEX_LOCS.get(key);
    }

    /** Call on level unload to prevent VRAM leaks. */
    public static void cleanupAll() {
        Minecraft mc = Minecraft.getInstance();
        TEX_LOCS.forEach((k, loc) -> mc.getTextureManager().release(loc));
        TEXTURES.values().forEach(DynamicTexture::close);
        TEXTURES.clear();
        TEX_LOCS.clear();
    }
}
