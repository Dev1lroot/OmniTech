package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a flat-colored overlay band on {@link FluidPipeBlockEntity} based on
 * the pipe's {@link FluidPipeBlock#COLOR} block-state property.
 *
 * <p>Uses {@link RenderTypes#debugFilledBox()} which is POSITION_COLOR only
 * (no texture atlas, no UV) — the color comes entirely from vertex color.
 *
 * <p>Two visual elements per colored pipe:
 * <ol>
 *   <li><b>Core equatorial band</b> – a ring around the 4×4 core cube's
 *       mid-height. Arm geometry in the opaque chunk-mesh depth-buffers away
 *       band faces hidden inside arms.</li>
 *   <li><b>Arm cap stripe</b> – a thin ring near the outer tip of every
 *       connected arm. Always visible perpendicular to the arm, ensuring color
 *       is shown even on fully connected 6-way junctions.</li>
 * </ol>
 *
 * <p>COLOR 0 = uncolored (no overlay). COLOR 1–16 maps to
 * {@link DyeColor#values()} indices 0–15.
 */
public class FluidPipeRenderer
        implements BlockEntityRenderer<FluidPipeBlockEntity, FluidPipeRenderState> {

    // ── Core box bounds (in block-local units, 0–1) ───────────────────────────
    private static final float C0 = 4f  / 16f;   // 0.250
    private static final float C1 = 12f / 16f;   // 0.750

    // ── Equatorial band on the core ───────────────────────────────────────────
    private static final float B0 = 6f  / 16f;   // band lower edge
    private static final float B1 = 10f / 16f;   // band upper edge

    // ── Arm-cap stripe position (within the 0..2/16 outer cap) ───────────────
    private static final float AS = 0.5f / 16f;  // stripe near edge
    private static final float AE = 1.5f / 16f;  // stripe far  edge

    // ── Z-fight offset ────────────────────────────────────────────────────────
    private static final float E = 0.002f;

    public FluidPipeRenderer(BlockEntityRendererProvider.Context ctx) {}

    // ── RenderState ───────────────────────────────────────────────────────────

    @Override
    public FluidPipeRenderState createRenderState() {
        return new FluidPipeRenderState();
    }

    @Override
    public void extractRenderState(
            FluidPipeBlockEntity entity, FluidPipeRenderState state,
            float partialTick, Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {

        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, cameraPos, crumbling);

        BlockState bs = entity.getBlockState();
        state.colorIndex = bs.getValue(FluidPipeBlock.COLOR);
        state.north = bs.getValue(FluidPipeBlock.NORTH);
        state.south = bs.getValue(FluidPipeBlock.SOUTH);
        state.east  = bs.getValue(FluidPipeBlock.EAST);
        state.west  = bs.getValue(FluidPipeBlock.WEST);
        state.up    = bs.getValue(FluidPipeBlock.UP);
        state.down  = bs.getValue(FluidPipeBlock.DOWN);

        if (entity.getLevel() instanceof ClientLevel cl) {
            state.lightCoords = LevelRenderer.getLightCoords(cl, entity.getBlockPos());
        } else {
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        }
    }

    // ── Submission ────────────────────────────────────────────────────────────

    @Override
    public void submit(
            FluidPipeRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {

        if (state.colorIndex == 0) return;

        int idx   = Math.clamp(state.colorIndex - 1, 0, DyeColor.values().length - 1);
        int rgb   = DyeColor.values()[idx].getFireworkColor();
        int color = ARGB.color(200, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);

        // POSITION_COLOR render type — no texture, no UV, pure vertex color
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buf) -> {

            // ── Core equatorial band ─────────────────────────────────────────
            // All 6 faces rendered; opaque arm geometry depth-culls hidden ones.

            quad(pose, buf, color,
                C0, B1, C0-E,   C1, B1, C0-E,   C1, B0, C0-E,   C0, B0, C0-E);  // North -Z
            quad(pose, buf, color,
                C1, B1, C1+E,   C0, B1, C1+E,   C0, B0, C1+E,   C1, B0, C1+E);  // South +Z
            quad(pose, buf, color,
                C0-E, B1, C1,   C0-E, B1, C0,   C0-E, B0, C0,   C0-E, B0, C1);  // West  -X
            quad(pose, buf, color,
                C1+E, B1, C0,   C1+E, B1, C1,   C1+E, B0, C1,   C1+E, B0, C0);  // East  +X
            quad(pose, buf, color,
                C0, B1+E, C0,   C0, B1+E, C1,   C1, B1+E, C1,   C1, B1+E, C0);  // Top   +Y
            quad(pose, buf, color,
                C1, B0-E, C0,   C1, B0-E, C1,   C0, B0-E, C1,   C0, B0-E, C0);  // Bot   -Y

            // ── Arm cap stripes ──────────────────────────────────────────────
            // Thin rings near each arm tip, always visible from perpendicular
            // directions regardless of how many connections the pipe has.

            if (state.north) ringZ(pose, buf, color, C0, C1, C0, C1,    AS,    AE);
            if (state.south) ringZ(pose, buf, color, C0, C1, C0, C1, 1f-AE, 1f-AS);
            if (state.west)  ringX(pose, buf, color, C0, C1, C0, C1,    AS,    AE);
            if (state.east)  ringX(pose, buf, color, C0, C1, C0, C1, 1f-AE, 1f-AS);
            if (state.down)  ringY(pose, buf, color, C0, C1, C0, C1,    AS,    AE);
            if (state.up)    ringY(pose, buf, color, C0, C1, C0, C1, 1f-AE, 1f-AS);
        });
    }

    // ── Arm stripe ring helpers ───────────────────────────────────────────────

    /** Ring on a Z-axis arm. Cross-section X=[xMin,xMax], Y=[yMin,yMax]. */
    private static void ringZ(PoseStack.Pose pose, VertexConsumer buf, int color,
            float xMin, float xMax, float yMin, float yMax, float zMin, float zMax) {
        quad(pose, buf, color,  xMax+E,yMax,zMin,  xMax+E,yMax,zMax,  xMax+E,yMin,zMax,  xMax+E,yMin,zMin);  // East
        quad(pose, buf, color,  xMin-E,yMax,zMax,  xMin-E,yMax,zMin,  xMin-E,yMin,zMin,  xMin-E,yMin,zMax);  // West
        quad(pose, buf, color,  xMin,yMax+E,zMin,  xMin,yMax+E,zMax,  xMax,yMax+E,zMax,  xMax,yMax+E,zMin);  // Top
        quad(pose, buf, color,  xMax,yMin-E,zMin,  xMax,yMin-E,zMax,  xMin,yMin-E,zMax,  xMin,yMin-E,zMin);  // Bottom
    }

    /** Ring on an X-axis arm. Cross-section Z=[zMin,zMax], Y=[yMin,yMax]. */
    private static void ringX(PoseStack.Pose pose, VertexConsumer buf, int color,
            float zMin, float zMax, float yMin, float yMax, float xMin, float xMax) {
        quad(pose, buf, color,  xMin,yMax,zMax+E,  xMax,yMax,zMax+E,  xMax,yMin,zMax+E,  xMin,yMin,zMax+E);  // South
        quad(pose, buf, color,  xMax,yMax,zMin-E,  xMin,yMax,zMin-E,  xMin,yMin,zMin-E,  xMax,yMin,zMin-E);  // North
        quad(pose, buf, color,  xMin,yMax+E,zMin,  xMin,yMax+E,zMax,  xMax,yMax+E,zMax,  xMax,yMax+E,zMin);  // Top
        quad(pose, buf, color,  xMax,yMin-E,zMin,  xMax,yMin-E,zMax,  xMin,yMin-E,zMax,  xMin,yMin-E,zMin);  // Bottom
    }

    /** Ring on a Y-axis arm. Cross-section X=[xMin,xMax], Z=[zMin,zMax]. */
    private static void ringY(PoseStack.Pose pose, VertexConsumer buf, int color,
            float xMin, float xMax, float zMin, float zMax, float yMin, float yMax) {
        quad(pose, buf, color,  xMin,yMax,zMin-E,  xMax,yMax,zMin-E,  xMax,yMin,zMin-E,  xMin,yMin,zMin-E);  // North
        quad(pose, buf, color,  xMax,yMax,zMax+E,  xMin,yMax,zMax+E,  xMin,yMin,zMax+E,  xMax,yMin,zMax+E);  // South
        quad(pose, buf, color,  xMax+E,yMax,zMin,  xMax+E,yMax,zMax,  xMax+E,yMin,zMax,  xMax+E,yMin,zMin);  // East
        quad(pose, buf, color,  xMin-E,yMax,zMax,  xMin-E,yMax,zMin,  xMin-E,yMin,zMin,  xMin-E,yMin,zMax);  // West
    }

    // ── Quad helper ───────────────────────────────────────────────────────────

    private static void quad(PoseStack.Pose pose, VertexConsumer buf, int color,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3) {
        buf.addVertex(pose, x0, y0, z0).setColor(color);
        buf.addVertex(pose, x1, y1, z1).setColor(color);
        buf.addVertex(pose, x2, y2, z2).setColor(color);
        buf.addVertex(pose, x3, y3, z3).setColor(color);
    }
}
