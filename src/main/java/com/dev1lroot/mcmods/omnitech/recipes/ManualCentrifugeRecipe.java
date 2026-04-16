package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ManualCentrifugeRecipe {
    public record Output(Item item, int count, float chance) {}

    private final String id;
    private final int requiredKineticForce;
    private final Item input;
    private final List<Output> outputs;

    public ManualCentrifugeRecipe(String id, int requiredKineticForce, String inputId, List<Output> outputs) {
        this.id = id;
        this.requiredKineticForce = requiredKineticForce;
        this.input = BuiltInRegistries.ITEM.getOptional(Identifier.parse(inputId)).orElse(null);
        this.outputs = outputs;
    }

    public String getId() { return id; }
    public int getRequiredKineticForce() { return requiredKineticForce; }
    public Item getInput() { return input; }
    public List<Output> getOutputs() { return outputs; }

    /**
     * Rolls all output chances and returns a list of ItemStacks that were won.
     * Uses the provided random value per slot (0.0–1.0).
     */
    public List<ItemStack> rollOutputs(net.minecraft.util.RandomSource random) {
        List<ItemStack> result = new ArrayList<>();
        for (Output out : outputs) {
            if (random.nextFloat() < out.chance()) {
                result.add(new ItemStack(out.item(), out.count()));
            }
        }
        return result;
    }

    public boolean matches(ItemStack inputStack) {
        return input != null && !inputStack.isEmpty() && inputStack.is(input);
    }
}
