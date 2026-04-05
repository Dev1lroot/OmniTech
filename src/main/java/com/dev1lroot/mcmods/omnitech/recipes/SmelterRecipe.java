package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class SmelterRecipe {
    private final String id;
    private final int requiredMinimalTemperature;
    private final List<Item> ingredients;

    // Stored as raw Fluid + amount to defer FluidStack construction until
    // data-components are fully bound (avoids "Components not bound yet" on load).
    private final Identifier outputFluidId;
    private final int outputAmount;
    private FluidStack cachedOutput = null;

    public SmelterRecipe(String id, int requiredMinimalTemperature,
                         List<String> ingredientIds, String outputFluid, int outputAmount) {
        this.id = id;
        this.requiredMinimalTemperature = requiredMinimalTemperature;
        this.ingredients = new ArrayList<>();

        for (String ingredientId : ingredientIds) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(ingredientId));
            if (item != null) {
                ingredients.add(item);
            }
        }

        // Resolve fluid ID — support both "omnitech:melted_brass" and "melted_brass"
        this.outputFluidId = outputFluid.contains(":")
                ? Identifier.parse(outputFluid)
                : Identifier.fromNamespaceAndPath("omnitech", outputFluid);
        this.outputAmount = outputAmount;
    }

    public String getId() { return id; }

    public int getRequiredMinimalTemperature() { return requiredMinimalTemperature; }

    public List<Item> getIngredients() { return ingredients; }

    /** Returns a copy of the output FluidStack. Built lazily after component binding. */
    public FluidStack getOutput() {
        if (cachedOutput == null) {
            Fluid fluid = BuiltInRegistries.FLUID.getValue(outputFluidId);
            cachedOutput = (fluid != null) ? new FluidStack(fluid, outputAmount) : FluidStack.EMPTY;
        }
        return cachedOutput.copy();
    }

    /** Shapeless match — checks that all required ingredients are present in the input slots. */
    public boolean matches(List<ItemStack> inputStacks) {
        List<Item> available = new ArrayList<>();
        for (ItemStack stack : inputStacks) {
            if (!stack.isEmpty()) available.add(stack.getItem());
        }
        for (Item required : ingredients) {
            if (!available.remove(required)) return false;
        }
        return true;
    }
}
