package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public class FoundryRecipe {
    private final String id;
    private final int requiredMinimalTemperature;
    private final Identifier inputFluidId;
    private final int inputAmount;
    private final Item templateItem;
    private final Item outputItem;

    // Deferred fluid lookup — avoid "components not bound yet" on early load
    private Fluid cachedInputFluid = null;

    public FoundryRecipe(String id, int requiredMinimalTemperature,
                         String inputFluidStr, int inputAmount,
                         String templateItemId, String outputItemId) {
        this.id = id;
        this.requiredMinimalTemperature = requiredMinimalTemperature;
        this.inputFluidId = inputFluidStr.contains(":")
                ? Identifier.parse(inputFluidStr)
                : Identifier.fromNamespaceAndPath("omnitech", inputFluidStr);
        this.inputAmount = inputAmount;
        this.templateItem = BuiltInRegistries.ITEM.getOptional(Identifier.parse(templateItemId)).orElse(null);
        this.outputItem   = BuiltInRegistries.ITEM.getOptional(Identifier.parse(outputItemId)).orElse(null);
    }

    public String getId()                        { return id; }
    public int getRequiredMinimalTemperature()   { return requiredMinimalTemperature; }
    public int getInputAmount()                  { return inputAmount; }
    public Item getTemplateItem()                { return templateItem; }
    public Item getOutputItem()                  { return outputItem; }

    public Fluid getInputFluid() {
        if (cachedInputFluid == null) {
            cachedInputFluid = BuiltInRegistries.FLUID.getValue(inputFluidId);
        }
        return cachedInputFluid;
    }

    /**
     * Returns true if the given fluid tank contents are compatible with this recipe's
     * required input fluid (same type; amount is checked separately in canProcess).
     */
    public boolean matchesFluid(FluidStack fluid) {
        if (fluid.isEmpty()) return false;
        return fluid.is(getInputFluid());
    }

    public boolean matchesTemplate(ItemStack stack) {
        return templateItem != null && !stack.isEmpty() && stack.is(templateItem);
    }
}
