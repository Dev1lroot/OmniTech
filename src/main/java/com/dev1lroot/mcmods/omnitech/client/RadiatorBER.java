package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.thermal.radiator.RadiatorBlockEntity;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a translucent color overlay on the radiator block to visualize live
 * heat (red) or cold (blue) state, matching the ThermalConductor glow style.
 */
public class RadiatorBER
        implements BlockEntityRenderer<RadiatorBlockEntity, RadiatorRenderState> {

    private static final float EP = 0.002f;

    public RadiatorBER(BlockEntityRendererProvider.Context context) {}

    @Override
    public RadiatorRenderState createRenderState() {
        return new RadiatorRenderState();
    }

    @Override
    public void extractRenderState(
            RadiatorBlockEntity entity,
            RadiatorRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {

        BlockEntityRenderer.super.extractRenderState(
                entity, state, partialTicks, cameraPosition, breakProgress);

        float deviation = entity.getTemperature() - IThermalNode.AMBIENT_TEMP;

        if (Math.abs(deviation) < 1f) {
            state.argb = 0;
        } else {
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

        if (entity.getLevel() instanceof ClientLevel clientLevel) {
            state.lightCoords = LevelRenderer.getLightCoords(clientLevel, entity.getBlockPos());
        } else {
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        }
    }

    @Override
    public void submit(RadiatorRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {

        if (ARGB.alpha(state.argb) == 0) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(Identifier.withDefaultNamespace("block/white_wool"));

        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        int color = state.argb;
        int light = LightCoordsUtil.FULL_BRIGHT;

        submitNodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.eyes(sprite.atlasLocation()), (pose, buf) ->
            box(pose, buf, -EP, -EP, -EP, 1f + EP, 1f + EP, 1f + EP,
                    u0, v0, u1, v1, color, light)
        );
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buf,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float u0, float v0, float u1, float v1, int color, int light) {
        quad(pose, buf, x0,y1,z0, u0,v0, x0,y1,z1, u0,v1, x1,y1,z1, u1,v1, x1,y1,z0, u1,v0, color, light, 0,1,0);
        quad(pose, buf, x1,y0,z0, u0,v0, x1,y0,z1, u0,v1, x0,y0,z1, u1,v1, x0,y0,z0, u1,v0, color, light, 0,-1,0);
        quad(pose, buf, x0,y1,z0, u0,v0, x1,y1,z0, u1,v0, x1,y0,z0, u1,v1, x0,y0,z0, u0,v1, color, light, 0,0,-1);
        quad(pose, buf, x1,y1,z1, u0,v0, x0,y1,z1, u1,v0, x0,y0,z1, u1,v1, x1,y0,z1, u0,v1, color, light, 0,0,1);
        quad(pose, buf, x0,y1,z1, u0,v0, x0,y1,z0, u1,v0, x0,y0,z0, u1,v1, x0,y0,z1, u0,v1, color, light, -1,0,0);
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
