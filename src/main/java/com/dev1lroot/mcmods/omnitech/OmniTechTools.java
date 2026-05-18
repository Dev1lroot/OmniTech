/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import net.minecraft.tags.BlockTags;

/**
 * Central registry for all {@link ToolSet} definitions.
 *
 * <p>Each entry registers a full tool set (pickaxe, shovel, sword, axe, hoe) for one
 * material.  Tiers are balanced against vanilla progression:
 * <ul>
 *   <li>Stone tier  ({@code INCORRECT_FOR_STONE_TOOL}):  zinc, lead</li>
 *   <li>Iron tier   ({@code INCORRECT_FOR_IRON_TOOL}):   tin, nickel, aluminium, brass, cobalt, steel</li>
 *   <li>Diamond tier({@code INCORRECT_FOR_DIAMOND_TOOL}): uranium, titanium, chromium, tungsten</li>
 * </ul>
 *
 * <p>Call {@link #init()} from {@code OmniTech}'s constructor before event buses fire.
 */
public class OmniTechTools {

    // ── Tool set definitions ──────────────────────────────────────────────────
    // create(material, registry, incorrectForDrops, durability, speed, attackDmgBonus, enchantability, ingotId)

    /** Zinc — brittle, heavy; stone-tier equivalent. */
    public static final ToolSet ZINC = ToolSet.create(
            "zinc",
            OmniTechItems.REGISTRY,
            BlockTags.INCORRECT_FOR_STONE_TOOL,
            200, 5.0f, 1.0f, 10,
            "omnitech:zinc_ingot");

    /** Lead — dense and soft; poor tool material but heavy hitter. */
    public static final ToolSet LEAD = ToolSet.create(
            "lead",
            OmniTechItems.REGISTRY,
            BlockTags.INCORRECT_FOR_STONE_TOOL,
            150, 4.5f, 1.5f, 5,
            "omnitech:lead_ingot");

    /** Tin — slightly above copper; entry-level iron-tier tool material. */
    public static final ToolSet TIN = ToolSet.create(
            "tin",
            OmniTechItems.REGISTRY,
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            280, 6.0f, 1.5f, 10,
            "omnitech:tin_ingot");

    /** Nickel — iron-equivalent hardness with slightly better enchantability. */
    public static final ToolSet NICKEL = ToolSet.create(
            "nickel",
            OmniTechItems.REGISTRY,
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            320, 6.0f, 2.0f, 12,
            "omnitech:nickel_ingot");

    /** Aluminium — lightweight; fast but low damage. */
    public static final ToolSet ALUMINIUM = ToolSet.create(
            "aluminium",
            OmniTechItems.REGISTRY,
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            350, 7.0f, 1.5f, 14,
            "omnitech:aluminium_ingot");

    /** Brass — soft alloy with high enchantability; mid-range iron tier. */
    public static final ToolSet BRASS = ToolSet.create(
            "brass",
            OmniTechItems.REGISTRY,
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            400, 6.5f, 2.0f, 18,
            "omnitech:brass_ingot");

    /** Cobalt — hard and durable; sits at the top of iron tier. */
    public static final ToolSet COBALT = ToolSet.create(
            "cobalt",
            OmniTechItems.REGISTRY,
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            600, 7.0f, 2.5f, 12,
            "omnitech:cobalt_ingot");

    /** Steel — tough forged alloy; reliable high-end iron-tier option. */
    public static final ToolSet STEEL = ToolSet.create(
            "steel",
            OmniTechItems.REGISTRY,
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            800, 7.5f, 2.5f, 14,
            "omnitech:steel_ingot");

    /** Uranium — radioactive and heavy; diamond-tier mining power with low enchantability. */
    public static final ToolSet URANIUM = ToolSet.create(
            "uranium",
            OmniTechItems.REGISTRY,
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            600, 7.0f, 3.0f, 8,
            "omnitech:uranium_ingot");

    /** Titanium — extremely durable and fast; diamond-tier. */
    public static final ToolSet TITANIUM = ToolSet.create(
            "titanium",
            OmniTechItems.REGISTRY,
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            1500, 8.0f, 3.5f, 12,
            "omnitech:titanium_ingot");

    /** Chromium — highly refined; excellent sharpness and speed above diamond. */
    public static final ToolSet CHROMIUM = ToolSet.create(
            "chromium",
            OmniTechItems.REGISTRY,
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            2000, 8.5f, 3.5f, 8,
            "omnitech:chromium_ingot");

    /** Tungsten — the hardest material; near-netherite tier with immense durability. */
    public static final ToolSet TUNGSTEN = ToolSet.create(
            "tungsten",
            OmniTechItems.REGISTRY,
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            3000, 9.0f, 4.0f, 6,
            "omnitech:tungsten_ingot");

    /**
     * Triggers class loading, which runs all static field initializers.
     * Call this from {@code OmniTech}'s constructor before registering event buses.
     */
    public static void init() {}
}
