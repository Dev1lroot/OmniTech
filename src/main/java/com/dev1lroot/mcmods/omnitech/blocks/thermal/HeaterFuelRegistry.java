/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.thermal;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Maps fuel items to their heater-specific burn properties.
 *
 * <p>Rules are checked in declaration order — put the most specific / most
 * powerful fuels first so they win over generic tag matches.
 *
 * <p>Fields per entry:
 * <ul>
 *   <li>{@code burnTicks}   — how long one item burns (game ticks)</li>
 *   <li>{@code heatPerTick} — °C added to storedHeat each tick while burning</li>
 *   <li>{@code maxHeat}     — storedHeat ceiling (°C) while this fuel is loaded</li>
 * </ul>
 */
public final class HeaterFuelRegistry {

    public record Entry(int burnTicks, int heatPerTick, int maxHeat) {}

    private record Rule(Predicate<ItemStack> test, Entry entry) {}

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // ── Superior fuels ─────────────────────────────────────────────────────
        add(s -> s.is(Items.LAVA_BUCKET),                         new Entry(20000, 5, 800));
        add(s -> s.is(Items.BLAZE_ROD),                           new Entry( 2400, 4, 500));
        add(s -> matchesMod(s, "omnitech", "coke_coal"),          new Entry( 3200, 3, 450));

        // ── Standard fuels ─────────────────────────────────────────────────────
        add(s -> s.is(Items.COAL_BLOCK),                          new Entry(14400, 2, 300));
        add(s -> s.is(Items.COAL) || s.is(Items.CHARCOAL),        new Entry( 1600, 2, 300));

        // ── Weak fuels ─────────────────────────────────────────────────────────
        add(s -> s.is(ItemTags.PLANKS),                           new Entry(  300, 1, 200));
        add(s -> s.is(Items.STICK),                               new Entry(  100, 1, 200));
    }

    private static void add(Predicate<ItemStack> test, Entry entry) {
        RULES.add(new Rule(test, entry));
    }

    /** Matches a dynamically registered item by namespace + path (safe to call before registry freeze). */
    private static boolean matchesMod(ItemStack stack, String namespace, String path) {
        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        if (!BuiltInRegistries.ITEM.containsKey(id)) return false;
        return stack.is(BuiltInRegistries.ITEM.getValue(id));
    }

    /** Returns the {@link Entry} for {@code stack}, or {@code null} if it is not a valid fuel. */
    public static @Nullable Entry get(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Rule rule : RULES) {
            if (rule.test().test(stack)) return rule.entry();
        }
        return null;
    }

    public static boolean isValidFuel(ItemStack stack) {
        return get(stack) != null;
    }

    private HeaterFuelRegistry() {}
}
