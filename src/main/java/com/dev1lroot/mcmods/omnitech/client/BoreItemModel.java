/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.items.BoreItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Item model type {@code omnitech:bore}: the static bore corpus plus the plugged-in head's two
 * 3D models — the outer (front + rear) disks and the middle disk — all ordinary JSON block
 * models ({@code models/item/bore.json}, {@code models/item/bore_head_3d/<material>[_middle].json}).
 * Used in every display context.
 *
 * <p>While the holder mines, the outer disks spin about the drill axis and the middle disk
 * spins the opposite way — for the local
 * player that is "attack held with a powered bore", for others the block-crack progress the
 * server sends about them ({@link BoreSpin}). The bore never plays the arm swing.
 * Display transforms for all layers come from the corpus model.
 *
 * <pre>{@code
 * { "type": "omnitech:bore", "corpus": "omnitech:item/bore",
 *   "heads": { "omnitech:iron_bore_head": { "outer": "omnitech:item/bore_head_3d/iron",
 *                                           "middle": "omnitech:item/bore_head_3d/iron_middle" }, ... } }
 * }</pre>
 */
public final class BoreItemModel implements ItemModel {

    /** Head speed while mining, degrees per millisecond (3 turns per second). */
    private static final float SPIN_DEG_PER_MS = 1.08f;
    private record Baked(ItemQuads quads, Vector3fc[] extents) {}

    private record BakedHead(Baked outer, Baked middle) {}

    /** Model ids of one head's two rotating parts. */
    public record HeadModels(Identifier outer, Identifier middle) {
        public static final Codec<HeadModels> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("outer").forGetter(HeadModels::outer),
                Identifier.CODEC.fieldOf("middle").forGetter(HeadModels::middle)
        ).apply(i, HeadModels::new));
    }

    private final Baked corpus;
    private final Map<Identifier, BakedHead> heads;
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;

    private BoreItemModel(Baked corpus, Map<Identifier, BakedHead> heads,
            ModelRenderProperties properties, Matrix4fc transformation) {
        this.corpus = corpus;
        this.heads = heads;
        this.properties = properties;
        this.transformation = transformation;
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext displayContext, @Nullable ClientLevel level,
            @Nullable ItemOwner owner, int seed) {
        output.appendModelIdentityElement(this);
        addLayer(output, stack, displayContext, corpus, transformation);

        ItemStack head = BoreItem.getHead(stack);
        BakedHead headModel = head.isEmpty() ? null : heads.get(BuiltInRegistries.ITEM.getKey(head.getItem()));
        if (headModel == null) return;
        output.appendModelIdentityElement(headModel);

        float angle = 0f;
        if (isMining(stack, displayContext, owner)) {
            angle = (float) Math.toRadians((Util.getMillis() % 1_000_000L) * SPIN_DEG_PER_MS % 360f);
            output.setAnimated();
        }
        addLayer(output, head, displayContext, headModel.outer(), spun(angle));
        addLayer(output, head, displayContext, headModel.middle(), spun(-angle));
    }

    /** The model transform turned about the drill axis (Z through x = y = 8 px). */
    private Matrix4fc spun(float angle) {
        if (angle == 0f) return transformation;
        return new Matrix4f(transformation)
                .translate(0.5f, 0.5f, 0.5f)
                .rotateZ(angle)
                .translate(-0.5f, -0.5f, -0.5f);
    }

    private void addLayer(ItemStackRenderState output, ItemStack glintSource,
            ItemDisplayContext displayContext, Baked model, Matrix4fc localTransform) {
        ItemStackRenderState.LayerRenderState layer = output.newLayer();
        if (glintSource.hasFoil()) {
            layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
            output.setAnimated();
        }
        layer.setExtents(model::extents);
        layer.setLocalTransform(localTransform);
        properties.applyToLayer(layer, displayContext);
        layer.setQuads(model.quads());
    }

    /** Held in a hand by someone who is mining with it. */
    private static boolean isMining(ItemStack stack, ItemDisplayContext ctx, @Nullable ItemOwner owner) {
        if (ctx != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && ctx != ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                && ctx != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && ctx != ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return false;
        }
        LivingEntity holder = owner != null ? owner.asLivingEntity() : null;
        if (holder == null || !(holder.getMainHandItem().getItem() instanceof BoreItem bore)) return false;

        Minecraft mc = Minecraft.getInstance();
        if (holder == mc.player) {
            boolean powered = bore.getEnergy(stack) > 0 || ((Player) holder).hasInfiniteMaterials();
            return powered && mc.gui.screen() == null && mc.options.keyAttack.isDown();
        }
        return BoreSpin.isMining(holder.getId());
    }

    // ── Unbaked ───────────────────────────────────────────────────────────────

    public record Unbaked(Identifier corpus, Map<Identifier, HeadModels> heads) implements ItemModel.Unbaked {

        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("corpus").forGetter(Unbaked::corpus),
                Codec.unboundedMap(Identifier.CODEC, HeadModels.CODEC).fieldOf("heads").forGetter(Unbaked::heads)
        ).apply(i, Unbaked::new));

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(corpus);
            heads.values().forEach(h -> {
                resolver.markDependency(h.outer());
                resolver.markDependency(h.middle());
            });
        }

        @Override
        public ItemModel bake(BakingContext ctx, Matrix4fc transformation) {
            ModelBaker baker = ctx.blockModelBaker();
            ResolvedModel corpusModel = baker.getModel(corpus);
            ModelRenderProperties props = ModelRenderProperties.fromResolvedModel(
                    baker, corpusModel, corpusModel.getTopTextureSlots());

            Map<Identifier, BakedHead> bakedHeads = new HashMap<>();
            heads.forEach((item, h) -> bakedHeads.put(item,
                    new BakedHead(bake(baker, h.outer()), bake(baker, h.middle()))));
            return new BoreItemModel(bake(baker, corpus), bakedHeads, props, transformation);
        }

        private static Baked bake(ModelBaker baker, Identifier id) {
            ResolvedModel model = baker.getModel(id);
            QuadCollection quads = model.bakeTopGeometry(model.getTopTextureSlots(), baker, BlockModelRotation.IDENTITY);
            return new Baked(ItemQuads.split(quads.getAll()),
                    CuboidItemModelWrapper.computeExtents(quads.getAll()));
        }

        @Override
        public MapCodec<Unbaked> type() { return MAP_CODEC; }
    }
}
