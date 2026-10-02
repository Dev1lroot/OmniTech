/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipe;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import com.dev1lroot.mcmods.omnitech.items.PipetteItem;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.util.RecipeMatcher;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shapeless crafting with two extras over vanilla:
 * <ul>
 *   <li>{@code fluid} — a filled {@link PipetteItem} must also be in the grid holding at least
 *       {@code amount} mB of that fluid; the dose is drawn off and the pipette stays behind.</li>
 *   <li>{@code copy_components_from} — the result inherits every data component of the grid
 *       item matching this ingredient (a blueprint's drawing onto its photomask).</li>
 * </ul>
 * <pre>{@code
 * {"type": "omnitech:pipette_shapeless",
 *  "ingredients": ["omnitech:textolite_plate", "omnitech:copper_plate"],
 *  "fluid": {"fluid": "omnitech:photoresist", "amount": 5},
 *  "result": {"id": "omnitech:empty_circuit_board"}}
 * }</pre>
 */
public class PipetteShapelessRecipe extends NormalCraftingRecipe {

    public record FluidDose(Fluid fluid, int amount) {
        public static final Codec<FluidDose> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidDose::fluid),
                Codec.intRange(1, PipetteItem.MAX_AMOUNT).fieldOf("amount").forGetter(FluidDose::amount)
        ).apply(i, FluidDose::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidDose> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.FLUID), FluidDose::fluid,
                ByteBufCodecs.VAR_INT, FluidDose::amount,
                FluidDose::new);
    }

    public static final MapCodec<PipetteShapelessRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result),
            Ingredient.CODEC.listOf(1, 8).fieldOf("ingredients").forGetter(r -> r.ingredients),
            FluidDose.CODEC.optionalFieldOf("fluid").forGetter(r -> r.fluid),
            Ingredient.CODEC.optionalFieldOf("copy_components_from").forGetter(r -> r.copyFrom)
    ).apply(i, PipetteShapelessRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PipetteShapelessRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, r -> r.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, r -> r.bookInfo,
            ItemStackTemplate.STREAM_CODEC, r -> r.result,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), r -> r.ingredients,
            ByteBufCodecs.optional(FluidDose.STREAM_CODEC), r -> r.fluid,
            ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC), r -> r.copyFrom,
            PipetteShapelessRecipe::new);

    public static final RecipeSerializer<PipetteShapelessRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ItemStackTemplate result;
    private final List<Ingredient> ingredients;
    private final Optional<FluidDose> fluid;
    private final Optional<Ingredient> copyFrom;

    public PipetteShapelessRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
            ItemStackTemplate result, List<Ingredient> ingredients,
            Optional<FluidDose> fluid, Optional<Ingredient> copyFrom) {
        super(commonInfo, bookInfo);
        this.result = result;
        this.ingredients = ingredients;
        this.fluid = fluid;
        this.copyFrom = copyFrom;
    }

    public List<Ingredient> ingredients() { return ingredients; }
    public Optional<FluidDose> fluid()    { return fluid; }
    public ItemStackTemplate result()     { return result; }

    // ── Matching ──────────────────────────────────────────────────────────────

    private boolean isDosingPipette(ItemStack stack) {
        return fluid.isPresent() && stack.getItem() instanceof PipetteItem
                && PipetteItem.getSolution(stack).amountOf(fluid.get().fluid()) >= fluid.get().amount();
    }

    /** The grid's items split into the pipette (if the recipe wants one) and the rest, or null if they don't fit. */
    private @Nullable Split split(CraftingInput input) {
        ItemStack pipette = null;
        int pipetteSlot = -1;
        List<ItemStack> rest = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (pipette == null && isDosingPipette(stack)) {
                pipette = stack;
                pipetteSlot = i;
            } else {
                rest.add(stack);
            }
        }
        if (fluid.isPresent() && pipette == null) return null;
        if (rest.size() != ingredients.size()) return null;
        int[] match = RecipeMatcher.findMatches(rest, ingredients);
        return match == null ? null : new Split(pipetteSlot, rest, match);
    }

    /** {@code match[i]} is the ingredient index that {@code rest.get(i)} satisfied. */
    private record Split(int pipetteSlot, List<ItemStack> rest, int[] match) {}

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return split(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack out = result.create();
        if (copyFrom.isEmpty()) return out;
        Split s = split(input);
        if (s == null) return out;
        for (ItemStack stack : s.rest()) {
            if (copyFrom.get().test(stack)) {
                out.applyComponents(stack.getComponentsPatch());
                break;
            }
        }
        return out;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = CraftingRecipe.defaultCraftingReminder(input);
        Split s = split(input);
        if (s == null || s.pipetteSlot() < 0) return remaining;
        FluidDose dose = fluid.orElseThrow();
        ItemStack pipette = input.getItem(s.pipetteSlot()).copyWithCount(1);
        PipetteItem.setSolution(pipette, PipetteItem.getSolution(pipette).minus(dose.fluid(), dose.amount()));
        remaining.set(s.pipetteSlot(), pipette);
        return remaining;
    }

    // ── Recipe book / JEI ─────────────────────────────────────────────────────

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.create(ingredients);
    }

    /** A pipette holding exactly the dose, for displays. */
    public static ItemStackTemplate dosedPipette(FluidDose dose) {
        Solution sol = Solution.EMPTY.plus(dose.fluid(), dose.amount());
        return new ItemStackTemplate(OmniTechItems.PIPETTE.get(),
                DataComponentPatch.builder().set(OmniTechDataComponents.SOLUTION.get(), sol).build());
    }

    @Override
    public List<RecipeDisplay> display() {
        List<SlotDisplay> slots = new ArrayList<>(ingredients.stream().map(Ingredient::display).toList());
        fluid.ifPresent(d -> slots.add(new SlotDisplay.ItemStackSlotDisplay(dosedPipette(d))));
        return List.of(new ShapelessCraftingRecipeDisplay(slots,
                new SlotDisplay.ItemStackSlotDisplay(result),
                new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }

    @Override
    public RecipeSerializer<PipetteShapelessRecipe> getSerializer() {
        return SERIALIZER;
    }
}
