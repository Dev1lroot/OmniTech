package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CokingRecipe {
    private final String id;
    private final String inputItem;
    private final Item output;
    private final int outputCount;
    private final int creosoteAmount;
    private final int productionTime;

    public CokingRecipe(String id, String inputItem, String outputItemId,
                        int outputCount, int creosoteAmount, int productionTime) {
        this.id = id;
        this.inputItem = inputItem;
        this.outputCount = outputCount;
        this.creosoteAmount = creosoteAmount;
        this.productionTime = productionTime;
        this.output = BuiltInRegistries.ITEM.getOptional(Identifier.parse(outputItemId)).orElse(null);
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && key.toString().equals(inputItem);
    }

    public ItemStack getOutput() {
        if (output == null) return ItemStack.EMPTY;
        return new ItemStack(output, outputCount);
    }

    public String getId()             { return id; }
    public String getInputItem()      { return inputItem; }
    public int getCreosoteAmount()    { return creosoteAmount; }
    public int getProductionTime()    { return productionTime; }
}
