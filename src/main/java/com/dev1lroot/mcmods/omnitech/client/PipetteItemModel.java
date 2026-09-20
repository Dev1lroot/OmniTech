/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.model.item.DynamicFluidContainerModel;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Custom item model for {@link com.dev1lroot.mcmods.omnitech.items.PipetteItem}.
 * Mirrors {@link FlaskItemModel} — own {@code item/pipette} / {@code item/pipette_fluid}
 * textures (copies of the flask's, pending dedicated art), tinted the same way via
 * {@link PipetteResourceHandler}'s reported dominant fluid.
 */
public final class PipetteItemModel implements ItemModel {

    private static final Identifier PIPETTE_MODEL =
            Identifier.fromNamespaceAndPath("omnitech", "item/pipette");

    private final ItemModel delegate;

    private PipetteItemModel(ItemModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack,
            ItemModelResolver resolver, ItemDisplayContext displayContext,
            @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        delegate.update(renderState, stack, resolver, displayContext, level, owner, seed);
    }

    public record Unbaked() implements ItemModel.Unbaked {

        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(PIPETTE_MODEL);
        }

        @Override
        public ItemModel bake(BakingContext ctx, Matrix4fc transformation) {
            var inner = new DynamicFluidContainerModel.Unbaked(
                    new DynamicFluidContainerModel.Textures(
                            Optional.empty(),
                            Optional.of(new Material(Identifier.fromNamespaceAndPath("omnitech",
                                    "item/pipette"))),
                            Optional.of(new Material(Identifier.fromNamespaceAndPath("omnitech",
                                    "item/pipette_fluid"))),
                            Optional.empty()
                    ),
                    Fluids.EMPTY,
                    false,
                    false,
                    true
            );
            return new PipetteItemModel(inner.bake(ctx, transformation));
        }

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
