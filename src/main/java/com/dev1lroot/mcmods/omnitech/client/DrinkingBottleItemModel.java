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
 * Custom item model for {@link com.dev1lroot.mcmods.omnitech.items.DrinkingBottleItem} — the
 * {@link DrinkingBottleItemModel} setup with the bottle's own sprites: {@code item/drinking_bottle} (green
 * glass) with the drink tinted into {@code item/drinking_bottle_fluid}.
 */
public final class DrinkingBottleItemModel implements ItemModel {

    private static final Identifier BOTTLE_MODEL =
            Identifier.fromNamespaceAndPath("omnitech", "item/drinking_bottle");

    private final ItemModel delegate;

    private DrinkingBottleItemModel(ItemModel delegate) {
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
            resolver.markDependency(BOTTLE_MODEL);
        }

        @Override
        public ItemModel bake(BakingContext ctx, Matrix4fc transformation) {
            var inner = new DynamicFluidContainerModel.Unbaked(
                    new DynamicFluidContainerModel.Textures(
                            Optional.empty(),
                            Optional.of(new Material(Identifier.fromNamespaceAndPath("omnitech",
                                    "item/drinking_bottle"))),
                            Optional.of(new Material(Identifier.fromNamespaceAndPath("omnitech",
                                    "item/drinking_bottle_fluid"))),
                            Optional.empty()
                    ),
                    Fluids.EMPTY,
                    false,
                    false,
                    true
            );
            return new DrinkingBottleItemModel(inner.bake(ctx, transformation));
        }

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
