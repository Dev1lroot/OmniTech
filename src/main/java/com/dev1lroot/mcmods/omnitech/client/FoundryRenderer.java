package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.blocks.thermal.FoundryBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.HashCommon;
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
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Renders three visual layers inside the Foundry block:
 *
 * <ol>
 *   <li><b>Template</b> — item particle sprite on a centred 12×12 px slab,
 *       rendered with full alpha support and voxel depth (top face + 4 thin
 *       side faces, each 1/16 block thick), mirroring how MC renders flat items
 *       in the world.</li>
 *   <li><b>Fluid</b> — fluid still-texture on a 12×12 top face whose Y
 *       position rises from {@link #Y_FLUID_BOT} to {@link #Y_FLUID_MAX} as
 *       crafting progresses.</li>
 *   <li><b>Output item</b> — full 3D item floating at {@link #ITEM_Y} for
 *       {@link #FLASH_TICKS} after each completed craft.</li>
 * </ol>
 */
public class FoundryRenderer
        implements BlockEntityRenderer<FoundryBlockEntity, FoundryRenderState> {

    // 12×12 px centred bounds  (2 px margin on each side of a 16 px block)
    private static final float L0 = 2f / 16f;
    private static final float L1 = 14f / 16f;

    // Y positions (user-adjusted to match the foundry bowl geometry)
    private static final float Y_TEMPLATE_BOT = 1f / 16f;
    private static final float Y_TEMPLATE_TOP = 2f / 16f;
    private static final float Y_FLUID_BOT    = 1f / 16f;
    private static final float Y_FLUID_MAX    = 2f / 16f;

    /** Y for the floating output item. */
    private static final float ITEM_Y     = 8f / 16f;
    private static final float ITEM_SCALE = 0.9f;

    /** Craft-completion flash duration in game ticks (≈ 400 ms at 20 TPS). */
    private static final long FLASH_TICKS = 8L;

    private final ItemModelResolver itemModelResolver;

    /** Scratch state reused each frame to look up item particle sprites. */
    private final ItemStackRenderState tempItemState = new ItemStackRenderState();
    private static final RandomSource RANDOM = RandomSource.create();

    public FoundryRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    // ── RenderState extraction ────────────────────────────────────────────────

    @Override
    public FoundryRenderState createRenderState() {
        return new FoundryRenderState();
    }

    @Override
    public void extractRenderState(
            FoundryBlockEntity entity,
            FoundryRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay breakProgress) {

        BlockEntityRenderer.super.extractRenderState(
                entity, state, partialTicks, cameraPosition, breakProgress);

        if (!(entity.getLevel() instanceof ClientLevel clientLevel)) {
            state.templateSprite  = null;
            state.fluidSprite     = null;
            state.outputItemState = null;
            return;
        }

        // ── Template sprite ───────────────────────────────────────────────────
        state.templateSprite = particleSprite(
                entity.getItem(FoundryBlockEntity.TEMPLATE_SLOT), clientLevel);

        // ── Fluid sprite & progress ───────────────────────────────────────────
        FluidStack fluid = entity.getInputFluid();
        if (!fluid.isEmpty()) {
            FluidStateModelSet modelSet = Minecraft.getInstance()
                    .getModelManager().getFluidStateModelSet();
            FluidModel fluidModel = modelSet.get(fluid.getFluid().defaultFluidState());

            state.fluidSprite      = fluidModel.stillMaterial().sprite();
            state.fluidTranslucent = fluidModel.layer() == ChunkSectionLayer.TRANSLUCENT;
            state.fluidTintARGB    = fluidModel.fluidTintSource() != null
                    ? fluidModel.fluidTintSource().colorAsStack(fluid) : -1;

            int progress  = entity.getContainerData().get(2);
            int totalTime = entity.getContainerData().get(3);
            state.fluidProgress = (totalTime > 0) ? (float) progress / totalTime : 0f;
        } else {
            state.fluidSprite   = null;
            state.fluidProgress = 0f;
        }

        // ── Output item ───────────────────────────────────────────────────────
        long sinceLastCraft = clientLevel.getGameTime() - entity.getLastCraftGameTime();
        if (sinceLastCraft >= 0 && sinceLastCraft < FLASH_TICKS) {
            ItemStack output = entity.getItem(FoundryBlockEntity.OUTPUT_SLOT);
            if (!output.isEmpty()) {
                ItemStackRenderState itemState = new ItemStackRenderState();
                int seed = HashCommon.long2int(entity.getBlockPos().asLong());
                itemModelResolver.updateForTopItem(
                        itemState, output, ItemDisplayContext.GROUND, clientLevel, null, seed);
                state.outputItemState = itemState;
            } else {
                state.outputItemState = null;
            }
        } else {
            state.outputItemState = null;
        }

        // ── Lighting ──────────────────────────────────────────────────────────
        state.lightCoords = LevelRenderer.getLightCoords(clientLevel, entity.getBlockPos());
    }

    // ── Submission ────────────────────────────────────────────────────────────

    @Override
    public void submit(FoundryRenderState state, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {

        final int light = state.lightCoords;

        // ── Layer 1: template — voxel depth (top + 4 thin sides), alpha-aware ─
        if (state.templateSprite != null) {
            final TextureAtlasSprite ts = state.templateSprite;
            // Always use translucent so per-pixel alpha in the item texture works.
            submitNodeCollector.submitCustomGeometry(poseStack,
                    RenderTypes.entityTranslucent(ts.atlasLocation()), (pose, buf) ->
                templateVoxel(pose, buf,
                        L0, L1, L0, L1,
                        Y_TEMPLATE_BOT, Y_TEMPLATE_TOP,
                        ts, ARGB.color(0xFF, 255, 255, 255), light));
        }

        // ── Layer 2: fluid — top face only, grows with craft progress ─────────
        if (state.fluidSprite != null && state.fluidProgress > 0f) {
            final TextureAtlasSprite fs = state.fluidSprite;
            final float yTop = Y_FLUID_BOT + state.fluidProgress * (Y_FLUID_MAX - Y_FLUID_BOT);

            final int r = ARGB.red(state.fluidTintARGB);
            final int g = ARGB.green(state.fluidTintARGB);
            final int b = ARGB.blue(state.fluidTintARGB);
            final int color = ARGB.color(state.fluidTranslucent ? 191 : 0xFF, r, g, b);

            var rt = state.fluidTranslucent
                    ? RenderTypes.entityTranslucent(fs.atlasLocation())
                    : RenderTypes.entitySolid(fs.atlasLocation());

            submitNodeCollector.submitCustomGeometry(poseStack, rt, (pose, buf) ->
                topFace(pose, buf,
                        L0, L1, L0, L1, yTop,
                        fs.getU0(), fs.getU1(), fs.getV0(), fs.getV1(),
                        color, light));
        }

        // ── Layer 3: output item ──────────────────────────────────────────────
        if (state.outputItemState != null) {
            poseStack.pushPose();
            poseStack.translate(0.5f, ITEM_Y, 0.5f);
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            state.outputItemState.submit(
                    poseStack, submitNodeCollector,
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /**
     * Renders a 1/16-thick item-style voxel slab:
     * <ul>
     *   <li>Top face — full texture with alpha.</li>
     *   <li>North/South sides — top/bottom pixel row of the texture.</li>
     *   <li>West/East sides — left/right pixel column of the texture.</li>
     * </ul>
     * Assumes a 16×16 sprite; pixel size = 1/16 of the sprite's UV extent.
     */
    private static void templateVoxel(
            PoseStack.Pose pose, VertexConsumer buf,
            float x0, float x1, float z0, float z1,
            float yBot, float yTop,
            TextureAtlasSprite s,
            int color, int light) {

        final float u0 = s.getU0(), u1 = s.getU1();
        final float v0 = s.getV0(), v1 = s.getV1();
        // One-pixel slice in atlas UV space (assumes 16×16 sprite)
        final float pu = (u1 - u0) / 16f;
        final float pv = (v1 - v0) / 16f;

        // Top face — full texture (u0→u1, v0→v1)
        quad(pose, buf,
                x0, yTop, z0,  u0, v0,
                x0, yTop, z1,  u0, v1,
                x1, yTop, z1,  u1, v1,
                x1, yTop, z0,  u1, v0,
                color, light, 0, 1, 0);

        // North side (-Z) — top pixel row (v0 → v0+pv)
        quad(pose, buf,
                x0, yTop, z0,  u0, v0,
                x1, yTop, z0,  u1, v0,
                x1, yBot, z0,  u1, v0 + pv,
                x0, yBot, z0,  u0, v0 + pv,
                color, light, 0, 0, -1);

        // South side (+Z) — bottom pixel row (v1-pv → v1)
        quad(pose, buf,
                x1, yTop, z1,  u0, v1 - pv,
                x0, yTop, z1,  u1, v1 - pv,
                x0, yBot, z1,  u1, v1,
                x1, yBot, z1,  u0, v1,
                color, light, 0, 0, 1);

        // West side (-X) — left pixel column (u0 → u0+pu)
        quad(pose, buf,
                x0, yTop, z1,  u0,      v0,
                x0, yTop, z0,  u0 + pu, v0,
                x0, yBot, z0,  u0 + pu, v1,
                x0, yBot, z1,  u0,      v1,
                color, light, -1, 0, 0);

        // East side (+X) — right pixel column (u1-pu → u1)
        quad(pose, buf,
                x1, yTop, z0,  u1 - pu, v0,
                x1, yTop, z1,  u1,      v0,
                x1, yBot, z1,  u1,      v1,
                x1, yBot, z0,  u1 - pu, v1,
                color, light, 1, 0, 0);
    }

    /** Single top-face quad. */
    private static void topFace(
            PoseStack.Pose pose, VertexConsumer buf,
            float x0, float x1, float z0, float z1, float y,
            float u0, float u1, float v0, float v1,
            int color, int light) {

        quad(pose, buf,
                x0, y, z0,  u0, v0,
                x0, y, z1,  u0, v1,
                x1, y, z1,  u1, v1,
                x1, y, z0,  u1, v0,
                color, light, 0, 1, 0);
    }

    private static void quad(
            PoseStack.Pose pose, VertexConsumer buf,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            int color, int light, float nx, float ny, float nz) {

        buf.addVertex(pose, x0, y0, z0).setColor(color)
                .setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x1, y1, z1).setColor(color)
                .setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x2, y2, z2).setColor(color)
                .setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
        buf.addVertex(pose, x3, y3, z3).setColor(color)
                .setUv(u3, v3).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** Returns the particle sprite for {@code stack}, or null. */
    private @Nullable TextureAtlasSprite particleSprite(ItemStack stack, ClientLevel level) {
        if (stack.isEmpty()) return null;
        tempItemState.clear();
        itemModelResolver.updateForTopItem(
                tempItemState, stack, ItemDisplayContext.GROUND, level, null, 0);
        Material.Baked mat = tempItemState.pickParticleMaterial(RANDOM);
        return mat != null ? mat.sprite() : null;
    }
}
