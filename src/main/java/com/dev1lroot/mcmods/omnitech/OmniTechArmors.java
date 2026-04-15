package com.dev1lroot.mcmods.omnitech;

/**
 * Central registry for all {@link ArmorSet} definitions.
 *
 * <p>Each entry registers a complete armor set (helmet, chestplate, leggings, boots)
 * for one material. Defense progression roughly follows:
 * <ul>
 *   <li>Leather/Copper tier: zinc, lead</li>
 *   <li>Chainmail/Iron tier: tin, nickel, aluminium, brass</li>
 *   <li>Iron+/Diamond tier: cobalt, steel, uranium, titanium, chromium, tungsten</li>
 * </ul>
 *
 * <p>Call {@link #init()} from {@code OmniTech}'s constructor before event buses fire.
 */
public class OmniTechArmors {

    // ── Armor set definitions ─────────────────────────────────────────────────
    // create(material, registry, durabilityMult, int[]{boots,legs,chest,helm,body},
    //        enchantability, toughness, knockbackResist, ingotId)

    /** Zinc — brittle metal; minimal protection. */
    public static final ArmorSet ZINC = ArmorSet.create(
            "zinc",
            OmniTechItems.REGISTRY,
            10,
            new int[]{ 1, 3, 4, 2, 4 },
            10, 0.0f, 0.0f,
            "omnitech:zinc_ingot");

    /** Lead — dense; slightly better than iron but very heavy. */
    public static final ArmorSet LEAD = ArmorSet.create(
            "lead",
            OmniTechItems.REGISTRY,
            12,
            new int[]{ 2, 4, 5, 2, 4 },
            6, 0.0f, 0.0f,
            "omnitech:lead_ingot");

    /** Tin — light alloy; chainmail-equivalent protection. */
    public static final ArmorSet TIN = ArmorSet.create(
            "tin",
            OmniTechItems.REGISTRY,
            13,
            new int[]{ 2, 4, 5, 2, 4 },
            10, 0.0f, 0.0f,
            "omnitech:tin_ingot");

    /** Nickel — iron-grade protection with good durability. */
    public static final ArmorSet NICKEL = ArmorSet.create(
            "nickel",
            OmniTechItems.REGISTRY,
            15,
            new int[]{ 2, 5, 6, 2, 5 },
            12, 0.0f, 0.0f,
            "omnitech:nickel_ingot");

    /** Aluminium — lightweight; iron-equivalent defense, high enchantability. */
    public static final ArmorSet ALUMINIUM = ArmorSet.create(
            "aluminium",
            OmniTechItems.REGISTRY,
            14,
            new int[]{ 2, 4, 5, 2, 4 },
            14, 0.0f, 0.0f,
            "omnitech:aluminium_ingot");

    /** Brass — soft but highly enchantable; iron-tier protection. */
    public static final ArmorSet BRASS = ArmorSet.create(
            "brass",
            OmniTechItems.REGISTRY,
            12,
            new int[]{ 2, 4, 6, 2, 5 },
            18, 0.0f, 0.0f,
            "omnitech:brass_ingot");

    /** Cobalt — hard alloy; sits above iron in every metric. */
    public static final ArmorSet COBALT = ArmorSet.create(
            "cobalt",
            OmniTechItems.REGISTRY,
            18,
            new int[]{ 2, 5, 7, 2, 7 },
            12, 0.0f, 0.0f,
            "omnitech:cobalt_ingot");

    /** Steel — forged alloy; reliable high-end pre-diamond tier. */
    public static final ArmorSet STEEL = ArmorSet.create(
            "steel",
            OmniTechItems.REGISTRY,
            20,
            new int[]{ 2, 5, 7, 3, 6 },
            14, 0.0f, 0.0f,
            "omnitech:steel_ingot");

    /** Uranium — radioactive; solid diamond-level protection, low enchantability. */
    public static final ArmorSet URANIUM = ArmorSet.create(
            "uranium",
            OmniTechItems.REGISTRY,
            22,
            new int[]{ 2, 5, 6, 2, 6 },
            8, 0.0f, 0.0f,
            "omnitech:uranium_ingot");

    /** Titanium — diamond-grade defense, extremely durable, slight toughness. */
    public static final ArmorSet TITANIUM = ArmorSet.create(
            "titanium",
            OmniTechItems.REGISTRY,
            28,
            new int[]{ 3, 6, 8, 3, 11 },
            12, 1.0f, 0.0f,
            "omnitech:titanium_ingot");

    /** Chromium — high-end alloy; better toughness than diamond. */
    public static final ArmorSet CHROMIUM = ArmorSet.create(
            "chromium",
            OmniTechItems.REGISTRY,
            30,
            new int[]{ 3, 6, 8, 3, 11 },
            8, 1.5f, 0.0f,
            "omnitech:chromium_ingot");

    /** Tungsten — hardest material; near-netherite toughness with knockback resistance. */
    public static final ArmorSet TUNGSTEN = ArmorSet.create(
            "tungsten",
            OmniTechItems.REGISTRY,
            35,
            new int[]{ 3, 6, 8, 3, 20 },
            6, 2.0f, 0.05f,
            "omnitech:tungsten_ingot");

    /**
     * Triggers class loading, which runs all static field initializers.
     * Call this from {@code OmniTech}'s constructor before registering event buses.
     */
    public static void init() {}
}
