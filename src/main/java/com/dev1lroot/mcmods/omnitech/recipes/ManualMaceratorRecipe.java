package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ManualMaceratorRecipe {
    private final String id;
    private final int requiredKineticForce;
    private final Item input;
    private final List<Item> outputs;

    public ManualMaceratorRecipe(String id, int requiredKineticForce, String inputId, List<String> outputIds) {
        this.id = id;
        this.requiredKineticForce = requiredKineticForce;
        this.input = BuiltInRegistries.ITEM.getValue(Identifier.parse(inputId));
        this.outputs = new ArrayList<>();
        for (String outputId : outputIds) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(outputId));
            if (item != null) {
                outputs.add(item);
            }
        }
    }

    public String getId() { return id; }
    public int getRequiredKineticForce() { return requiredKineticForce; }
    public Item getInput() { return input; }
    public List<Item> getOutputs() { return outputs; }

    public List<ItemStack> getOutputStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : outputs) {
            stacks.add(new ItemStack(item));
        }
        return stacks;
    }

    public boolean matches(ItemStack inputStack) {
        return input != null && !inputStack.isEmpty() && inputStack.is(input);
    }
}
