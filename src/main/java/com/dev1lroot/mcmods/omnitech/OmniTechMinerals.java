package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig;
import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig.BiomeOverride;
import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig.GenerationConfig;

import java.util.List;
import java.util.Map;

/**
 * Central registry for all mineral and ore block sets.
 *
 * <p>Each entry registers ore/mineral blocks (and their block items) in one call.
 * Standalone items such as ingots, dusts, and plates are handled by the data-driven
 * {@link ItemLoader} system and are NOT defined here.
 *
 * <p>Patterns use {@code "%"} as a placeholder for the mineral name:
 * <ul>
 *   <li>{@code "%_ore"}      → ore block that spawns in the world</li>
 *   <li>{@code "%_block"}    → compressed storage block</li>
 *   <li>{@code "raw_%_block"} → raw ore storage block</li>
 *   <li>{@code "%"}          → pure mineral block (e.g. skutterudite)</li>
 * </ul>
 *
 * <p>Call {@link #init()} from {@code OmniTech}'s constructor to ensure this class is
 * loaded before the deferred registries fire.
 */
public class OmniTechMinerals {

    // ── Ores with worldgen ────────────────────────────────────────────────────

    public static final MineralSet TUNGSTEN = MineralSet.create(
            "tungsten",
            new String[]{ "%_ore", "%_block", "raw_%_block" },
            Map.of("%_ore", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            -64, 32,
                            6,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_mountain",
                                    -32, 80,
                                    8,
                                    0, 2
                            )
                    )
            ))
    );

    public static final MineralSet CHROMIUM = MineralSet.create(
            "chromium",
            new String[]{ "%_ore", "%_block", "raw_%_block" },
            Map.of("%_ore", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            -32, 48,
                            7,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_savanna",
                                    -16, 64,
                                    10,
                                    0, 2
                            ),
                            new BiomeOverride(
                                    "#minecraft:is_badlands",
                                    -16, 64,
                                    10,
                                    0, 2
                            )
                    )
            ))
    );

    public static final MineralSet TIN = MineralSet.create(
            "tin",
            new String[]{ "%_ore", "%_block", "raw_%_block" },
            Map.of("%_ore", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            12, 64,
                            8,
                            1, 2
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_mountain",
                                    12, 100,
                                    12,
                                    1, 6
                            ),
                            new BiomeOverride(
                                    "#minecraft:is_jungle",
                                    8, 50,
                                    12,
                                    1, 6
                            )
                    )
            ))
    );

    // ── Pure mineral blocks (no processed items) ──────────────────────────────

    public static final MineralSet SKUTTERUDITE = MineralSet.create(
            "skutterudite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            0, 32,
                            20,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_mountain",
                                    0, 60,
                                    20,
                                    0, 1
                            ),
                            new BiomeOverride(
                                    "#minecraft:is_taiga",
                                    0, 24,
                                    25,
                                    0, 1
                            )
                    )
            ))
    );

    public static final MineralSet GALENA = MineralSet.create(
            "galena",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            0, 32,
                            20,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_mountain",
                                    0, 60,
                                    20,
                                    0, 1
                            )
                    )
            ))
    );

    public static final MineralSet HALITE = MineralSet.create(
            "halite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            0, 32,
                            1,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_ocean",
                                    0, 60,
                                    30,
                                    3, 6
                            )
                    )
            ))
    );

    public static final MineralSet CARNOTITE = MineralSet.create(
            "carnotite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            40, 120,
                            4,
                            0, 2
                    ),
                    List.of(
                            new BiomeOverride(
                                    "minecraft:desert",   // direct biome ID — no is_desert tag in vanilla
                                    30, 90,
                                    12,
                                    4, 14
                            ),
                            new BiomeOverride(
                                    "#minecraft:is_badlands",
                                    40, 100,
                                    15,
                                    5, 14
                            )
                    )
            ))
    );

    public static final MineralSet SPHALERITE = MineralSet.create(
            "sphalerite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            0, 32,
                            32,
                            0, 1
                    ),
                    List.of()
            ))
    );

    public static final MineralSet LEPIDOLITE = MineralSet.create(
            "lepidolite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            0, 32,
                            32,
                            0, 1
                    ),
                    List.of()
            ))
    );

    public static final MineralSet PENTLANDITE = MineralSet.create(
            "pentlandite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            -64, 20, // Глубокое магматическое происхождение
                            15,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_hill",
                                    -64, 40,
                                    20,
                                    0, 1
                            )
                    )
            ))
    );

    public static final MineralSet GARNIERITE = MineralSet.create(
            "garnierite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            40, 100, // Поверхностное выветривание
                            10,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_jungle", // Латеритные руды тропиков
                                    50, 110,
                                    25,
                                    2, 5
                            )
                    )
            ))
    );

    public static final MineralSet BAUXITE = MineralSet.create(
            "bauxite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            50, 120,
                            8,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_jungle", // Основной источник алюминия
                                    60, 128,
                                    30,
                                    4, 10
                            ),
                            new BiomeOverride(
                                    "minecraft:savanna",
                                    50, 100,
                                    15,
                                    2, 6
                            )
                    )
            ))
    );

    public static final MineralSet HEMATITE = MineralSet.create(
            "hematite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            -16, 64,
                            25,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_badlands", // "Красные" земли
                                    20, 100,
                                    40,
                                    5, 12
                            )
                    )
            ))
    );

    public static final MineralSet CASSITERITE = MineralSet.create(
            "cassiterite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            -32, 45,
                            12,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_river", // Часто встречается в россыпях
                                    -10, 60,
                                    20,
                                    1, 3
                            )
                    )
            ))
    );

    public static final MineralSet URANINITE = MineralSet.create(
            "uraninite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            -64, 0,
                            5,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "minecraft:swamp", // Историческая ассоциация с восстановительной средой
                                    -20, 20,
                                    10,
                                    1, 3
                            )
                    )
            ))
    );

    /** Монацит — главный источник редкоземельных элементов (лантан, неодим, торий). */
    public static final MineralSet MONAZITE = MineralSet.create(
            "monazite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            -48, 16,
                            8,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_beach", // Монацитовые пески
                                    0, 64,
                                    25,
                                    2, 6
                            )
                    )
            ))
    );

    /** Бастнезит — еще один важный источник РЗЭ. */
    public static final MineralSet BASTNASITE = MineralSet.create(
            "bastnasite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            -32, 32,
                            6,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_mountain",
                                    0, 80,
                                    12,
                                    1, 4
                            )
                    )
            ))
    );

    /** Рутил — чистый диоксид титана. Высокое содержание металла. */
    public static final MineralSet RUTILE = MineralSet.create(
            "rutile",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            -16, 48,
                            10,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_beach",
                                    0, 64,
                                    15,
                                    1, 4
                            )
                    )
            ))
    );

    /** Ильменит — титанистый железняк. Встречается чаще рутила. */
    public static final MineralSet ILMENITE = MineralSet.create(
            "ilmenite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            -64, 32,
                            18,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_hill",
                                    -32, 60,
                                    22,
                                    2, 5
                            )
                    )
            ))
    );

    public static final MineralSet APATITE = MineralSet.create(
            "apatite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            0, 64,
                            12,
                            1, 3
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_forest",
                                    10, 80,
                                    18,
                                    2, 5
                            )
                    )
            ))
    );

    /** Фосфорит — осадочная порода, богатая фосфатами. */
    public static final MineralSet PHOSPHORITE = MineralSet.create(
            "phosphorite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            20, 100,
                            8,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_ocean", // Осадочное происхождение на дне древних морей
                                    -10, 40,
                                    25,
                                    4, 10
                            )
                    )
            ))
    );

    /** Магнезит — главный источник магния. Легкие сплавы для авиации. */
    public static final MineralSet MAGNESITE = MineralSet.create(
            "magnesite",
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld",
                            -20, 40,
                            10,
                            0, 1
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_mountain",
                                    0, 90,
                                    20,
                                    2, 6
                            )
                    )
            ))
    );

    // ── Metal storage blocks (derived metals; items are data-driven) ──────────

    public static final MineralSet ALUMINIUM = MineralSet.create(
            "aluminium",
            new String[]{ "%_block" }
    );

    public static final MineralSet COBALT = MineralSet.create(
            "cobalt",
            new String[]{ "%_block" }
    );

    public static final MineralSet NICKEL = MineralSet.create(
            "nickel",
            new String[]{ "%_block" }
    );

    public static final MineralSet ZINC = MineralSet.create(
            "zinc",
            new String[]{ "%_block" }
    );

    public static final MineralSet LEAD = MineralSet.create(
            "lead",
            new String[]{ "%_block" }
    );

    public static final MineralSet URANIUM = MineralSet.create(
            "uranium",
            new String[]{ "%_block" }
    );

    public static final MineralSet TITANIUM = MineralSet.create(
            "titanium",
            new String[]{ "%_block" }
    );

    public static final MineralSet STEEL = MineralSet.create(
            "steel",
            new String[]{ "%_block" }
    );

    public static final MineralSet BRASS = MineralSet.create(
            "brass",
            new String[]{ "%_block" }
    );

    // ── Special geological blocks ─────────────────────────────────────────────

    /** Regolith — extraterrestrial surface and subsurface blocks. */
    public static final MineralSet REGOLITH = MineralSet.create(
            "regolith",
            new String[]{ "surface_%", "stratified_%", "paleo%", "mega%" }
    );

    /**
     * Triggers class loading, which runs all static field initializers.
     * Call this from {@code OmniTech}'s constructor before registering event buses.
     */
    public static void init() {}
}
