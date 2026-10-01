/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Overhead-line conductor data for the metal coils strung between suspension
 * insulators: electrical resistivity relative to copper and the rope colour.
 *
 * <p>A span made of {@code metal} that is {@code L} blocks long has
 * {@code R = COPPER_OHMS_PER_BLOCK · ρ_rel · L}. The relative resistivities are
 * the real room-temperature values (copper 1.68 µΩ·cm = 1.0).
 */
public final class ConductorMetals {
    private ConductorMetals() {}

    /** Coils that can be strung as an overhead line. */
    public static final TagKey<Item> CONDUCTOR_COILS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "conductor_coils"));

    /** Resistance of one block of copper overhead conductor, in ohms. */
    public static final double COPPER_OHMS_PER_BLOCK = 0.002;

    /** Unlisted metals: about as poor as steel. */
    private static final Metal FALLBACK = new Metal(8.5, 0xFF8A8D91);

    private record Metal(double relResistivity, int color) {}

    private static final Map<String, Metal> METALS = Map.ofEntries(
            Map.entry("copper",                    new Metal(1.00, 0xFFC0703F)),
            Map.entry("gold",                      new Metal(1.45, 0xFFE8C040)),
            Map.entry("aluminium",                 new Metal(1.58, 0xFFC9CED3)),
            Map.entry("tungsten",                  new Metal(3.35, 0xFF63676C)),
            Map.entry("zinc",                      new Metal(3.53, 0xFFA9B3B8)),
            Map.entry("cobalt",                    new Metal(3.70, 0xFF8796B0)),
            Map.entry("brass",                     new Metal(4.10, 0xFFC9A646)),
            Map.entry("nickel",                    new Metal(4.16, 0xFFADA88F)),
            Map.entry("cadmium",                   new Metal(4.37, 0xFF9FA6AB)),
            Map.entry("iron",                      new Metal(5.77, 0xFF8E8E8E)),
            Map.entry("tin",                       new Metal(6.49, 0xFFC2C6CA)),
            Map.entry("chromium",                  new Metal(7.44, 0xFFDCE2E7)),
            Map.entry("tantalum",                  new Metal(7.85, 0xFF7C8191)),
            Map.entry("steel",                     new Metal(8.50, 0xFF70757A)),
            Map.entry("tungsten_carbide",          new Metal(11.9, 0xFF4E5052)),
            Map.entry("lead",                      new Metal(13.1, 0xFF5E6573)),
            Map.entry("uranium",                   new Metal(16.7, 0xFF606D50)),
            Map.entry("hafnium",                   new Metal(19.6, 0xFF9C9C9C)),
            Map.entry("hafnium_carbide",           new Metal(22.0, 0xFF4A4A4E)),
            Map.entry("titanium",                  new Metal(25.0, 0xFF8E9196)),
            Map.entry("hafnium_tantalum_carbide",  new Metal(25.0, 0xFF45464C)));

    public static boolean isConductorCoil(ItemStack stack) {
        return stack.is(CONDUCTOR_COILS);
    }

    /** {@code omnitech:copper_coil} → {@code copper}. */
    public static String metalOf(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.endsWith("_coil") ? path.substring(0, path.length() - "_coil".length()) : path;
    }

    /** The coil item a span of {@code metal} drops when it is taken down. */
    public static Item coilItem(String metal) {
        return BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, metal + "_coil"));
    }

    public static double relativeResistivity(String metal) {
        return METALS.getOrDefault(metal, FALLBACK).relResistivity();
    }

    public static double spanResistance(String metal, double lengthBlocks) {
        return COPPER_OHMS_PER_BLOCK * relativeResistivity(metal) * lengthBlocks;
    }

    /** ARGB rope colour. */
    public static int color(String metal) {
        return METALS.getOrDefault(metal, FALLBACK).color();
    }

    public static Component displayName(String metal) {
        ItemStack coil = new ItemStack(coilItem(metal));
        return coil.isEmpty() ? Component.literal(metal) : coil.getHoverName();
    }
}
