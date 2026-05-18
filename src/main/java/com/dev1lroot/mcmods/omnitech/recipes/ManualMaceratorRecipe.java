/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public class ManualMaceratorRecipe {
    // Добавляем record для хранения данных о предмете, количестве и шансе
    public record Output(Item item, int count, float chance) {}

    private final String id;
    private final int requiredKineticForce;
    private final Item input;
    private final List<Output> outputs;

    public ManualMaceratorRecipe(String id, int requiredKineticForce, String inputId, List<Output> outputs) {
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
     * Просчитывает шансы для каждого выхода и возвращает список полученных стаков.
     */
    public List<ItemStack> rollOutputs(RandomSource random) {
        List<ItemStack> result = new ArrayList<>();
        for (Output out : outputs) {
            if (random.nextFloat() < out.chance()) {
                result.add(new ItemStack(out.item(), out.count()));
            }
        }
        return result;
    }

    /**
     * Возвращает все возможные выходы (например, для отображения в REI/JEI)
     */
    public List<ItemStack> getOutputStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Output out : outputs) {
            stacks.add(new ItemStack(out.item(), out.count()));
        }
        return stacks;
    }

    public boolean matches(ItemStack inputStack) {
        return input != null && !inputStack.isEmpty() && inputStack.is(input);
    }
}