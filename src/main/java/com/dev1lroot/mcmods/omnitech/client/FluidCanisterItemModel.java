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
 * Custom item model for {@link com.dev1lroot.mcmods.omnitech.items.FluidCanisterItem}.
 *
 * <p>Wraps NeoForge's built-in {@link DynamicFluidContainerModel}, which samples the
 * fluid's actual still-sprite (e.g. water texture, lava texture) and uses a mask texture
 * to shape it inside the canister outline.
 *
 * <p>The wrapper exists for two reasons:
 * <ol>
 *   <li>To declare {@code omnitech:item/fluid_canister} as a model dependency so its
 *       two texture references get stitched into the atlas before {@link #bake} runs.</li>
 *   <li>To avoid a JSON codec for every field — all configuration is hard-coded here.</li>
 * </ol>
 *
 * <p>Registered as {@code omnitech:fluid_canister} via
 * {@link net.neoforged.neoforge.client.event.RegisterItemModelsEvent}.
 */
public final class FluidCanisterItemModel implements ItemModel {

    private static final Identifier FLUID_CANISTER_MODEL =
            Identifier.fromNamespaceAndPath("omnitech", "item/fluid_canister");

    private final ItemModel delegate;

    private FluidCanisterItemModel(ItemModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack,
            ItemModelResolver resolver, ItemDisplayContext displayContext,
            @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        delegate.update(renderState, stack, resolver, displayContext, level, owner, seed);
    }

    // ── Unbaked ───────────────────────────────────────────────────────────────

    public record Unbaked() implements ItemModel.Unbaked {

        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            // Loading the existing fluid_canister.json model stitches both textures
            // (fluid_canister_fluid and fluid_canister) into the block/item atlas.
            resolver.markDependency(FLUID_CANISTER_MODEL);
        }

        @Override
        public ItemModel bake(BakingContext ctx, Matrix4fc transformation) {
            var inner = new DynamicFluidContainerModel.Unbaked(
                    new DynamicFluidContainerModel.Textures(
                            Optional.empty(), // particle — inherit from fluid
                            // base: the full canister body, always visible (shows when empty too)
                            Optional.of(new Material(Identifier.fromNamespaceAndPath("omnitech",
                                    "item/fluid_canister"))),
                            // fluid: white interior mask — fluid sprite renders here on top of the base
                            Optional.of(new Material(Identifier.fromNamespaceAndPath("omnitech",
                                    "item/fluid_canister_fluid"))),
                            Optional.empty() // no cover — the base already shows the canister border
                    ),
                    Fluids.EMPTY, // default fluid when empty → renders base only
                    false,        // flip_gas
                    false,        // cover_is_mask (no cover anyway)
                    true          // apply_fluid_luminosity: lava etc. glow
            );
            return new FluidCanisterItemModel(inner.bake(ctx, transformation));
        }

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
