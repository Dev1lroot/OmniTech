package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig;
import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig.BiomeOverride;
import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig.GenerationConfig;

import java.util.List;
import java.util.Map;

/**
 * Central registry for all MaterialSet definitions.
 *
 * <p>Each entry here registers a full family of items and blocks in one call.
 * Patterns use {@code "%"} as a placeholder for the material name:
 * <ul>
 *   <li>{@code "%_ore"}  → block + block-item (e.g. {@code tungsten_ore})</li>
 *   <li>{@code "%_block"} → block + block-item (e.g. {@code tungsten_block})</li>
 *   <li>{@code "raw_%"}  → standalone item   (e.g. {@code raw_tungsten})</li>
 *   <li>{@code "%_ingot"} → standalone item  (e.g. {@code tungsten_ingot})</li>
 *   <li>…and so on</li>
 * </ul>
 *
 * <p>In a dev environment, missing JSON resources are auto-generated on first load
 * (models, blockstates, loot tables, lang entries). Add your textures to
 * {@code assets/omnitech/textures/item/} and {@code assets/omnitech/textures/block/} manually.
 *
 * <p>Call {@link #init()} from {@code OmniTech}'s constructor to ensure this class is
 * loaded before the deferred registries fire.
 */
public class OmniTechMaterials {

    // ── Material definitions ──────────────────────────────────────────────────

    public static final MaterialSet TUNGSTEN = MaterialSet.create(
            "tungsten",
            OmniTechItems.REGISTRY,
            new String[]{ "raw_%", "%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget", "%_reductor", "%_cog", "%_wire", "%_coil" },
            OmniTechBlocks.REGISTRY,
            new String[]{ "%_ore", "%_block", "raw_%_block" }
    );

    public static final MaterialSet CHROMIUM = MaterialSet.create(
            "chromium",
            OmniTechItems.REGISTRY,
            new String[]{ "raw_%", "%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget", "%_reductor", "%_cog", "%_wire", "%_coil" },
            OmniTechBlocks.REGISTRY,
            new String[]{ "%_ore", "%_block", "raw_%_block" }
    );

    public static final MaterialSet TIN = MaterialSet.create(
            "tin",
            OmniTechItems.REGISTRY,
            new String[]{ "raw_%", "%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget", "%_reductor", "%_cog", "%_wire", "%_coil" },
            OmniTechBlocks.REGISTRY,
            new String[]{ "%_ore", "%_block", "raw_%_block" },
            Map.of("%_ore", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld", // all overworld biomes
                            12, 64,                    // minY, maxY
                            8,                         // veinSize (max blocks per cluster)
                            1, 32                      // minCount, maxCount per chunk
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_mountain", // mountains get extra tin
                                    12, 32,                   // slightly higher ceiling
                                    8,                        // larger clusters
                                    1, 6                      // extra 1-6 clusters per chunk
                            ),
                            new BiomeOverride(
                                    "#minecraft:is_jungle",   // jungle / tropical biomes
                                    8, 50,                    // slightly lower floor
                                    64,                       // smaller clusters
                                    1, 32                     // extra 1-32 clusters per chunk
                            )
                    )
            ))
    );

    public static final MaterialSet ALUMINIUM = MaterialSet.create(
            "aluminium",
            OmniTechItems.REGISTRY,
            new String[]{ "%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget", "%_reductor", "%_cog", "%_wire", "%_coil" },
            OmniTechBlocks.REGISTRY,
            new String[]{ "%_block" } // there is no mineral aluminium in real life
    );

    public static final MaterialSet COBALT = MaterialSet.create(
            "cobalt",
            OmniTechItems.REGISTRY,
            new String[]{ "%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget", "%_reductor", "%_cog", "%_wire", "%_coil" },
            OmniTechBlocks.REGISTRY,
            new String[]{ "%_block" } // there is no mineral aluminium in real life
    );

    public static final MaterialSet NICKEL = MaterialSet.create(
            "nickel",
            OmniTechItems.REGISTRY,
            new String[]{ "%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget" },
            OmniTechBlocks.REGISTRY,
            new String[]{ "%_block" } // there is no mineral aluminium in real life
    );

    public static final MaterialSet ZINC = MaterialSet.create(
            "zinc",
            OmniTechItems.REGISTRY,
            new String[]{ "%_ingot", "%_dust", "%_plate", "%_mote", "%_nugget" },
            OmniTechBlocks.REGISTRY,
            new String[]{ "%_block" } // sphalerite is the mineral for zinc
    );

    public static final MaterialSet IRON = MaterialSet.create(
            "iron",
            OmniTechItems.REGISTRY,
            new String[]{ "%_dust", "%_plate", "%_mote" },
            OmniTechBlocks.REGISTRY,
            new String[]{ } // the ore exists in vanilla game therefore only missing variations
    );

    public static final MaterialSet COPPER = MaterialSet.create(
            "copper",
            OmniTechItems.REGISTRY,
            new String[]{ "%_dust", "%_plate", "%_mote" },
            OmniTechBlocks.REGISTRY,
            new String[]{ } // the ore exists in vanilla game therefore only missing variations
    );

    public static final MaterialSet SKUTTERUDITE = MaterialSet.create(
            "skutterudite",
            OmniTechItems.REGISTRY,
            new String[]{ "%_dust" },
            OmniTechBlocks.REGISTRY,
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld", // all overworld biomes
                            0, 32,                    // minY, maxY
                            20,                         // veinSize (max blocks per cluster)
                            0, 1                      // minCount, maxCount per chunk
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_mountain", // mountains get extra tin
                                    0, 60,                    // minY, maxY
                                    20,                         // veinSize (max blocks per cluster)
                                    0, 1                      // minCount, maxCount per chunk
                            ),
                            new BiomeOverride(
                                    "#minecraft:is_taiga",   // jungle / tropical biomes
                                    0, 24,                    // minY, maxY
                                    25,                         // veinSize (max blocks per cluster)
                                    0, 1                      // minCount, maxCount per chunk
                            )
                    )
            ))
    );

    public static final MaterialSet GALENA = MaterialSet.create(
            "galena",
            OmniTechItems.REGISTRY,
            new String[]{ "%_dust" },
            OmniTechBlocks.REGISTRY,
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld", // all overworld biomes
                            0, 32,                    // minY, maxY
                            20,                         // veinSize (max blocks per cluster)
                            0, 1                      // minCount, maxCount per chunk
                    ),
                    List.of(
                            new BiomeOverride(
                                    "#minecraft:is_mountain", // mountains get extra tin
                                    0, 60,                    // minY, maxY
                                    20,                         // veinSize (max blocks per cluster)
                                    0, 1                      // minCount, maxCount per chunk
                            )
                    )
            ))
    );

    public static final MaterialSet SPHALERITE = MaterialSet.create(
            "sphalerite",
            OmniTechItems.REGISTRY,
            new String[]{ "%_dust" },
            OmniTechBlocks.REGISTRY,
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld", // all overworld biomes
                            0, 32,                    // minY, maxY
                            32,                         // veinSize (max blocks per cluster)
                            0, 1                      // minCount, maxCount per chunk
                    ),
                    List.of()
            ))
    );

    public static final MaterialSet LEPIDOLITE = MaterialSet.create(
            "lepidolite",
            OmniTechItems.REGISTRY,
            new String[]{ "%_dust" },
            OmniTechBlocks.REGISTRY,
            new String[]{ "%" },
            Map.of("%", new OreSpawnConfig(
                    new GenerationConfig(
                            "#minecraft:is_overworld", // all overworld biomes
                            0, 32,                    // minY, maxY
                            32,                         // veinSize (max blocks per cluster)
                            0, 1                      // minCount, maxCount per chunk
                    ),
                    List.of()
            ))
    );

    /**
     * Triggers class loading, which runs all static field initializers.
     * Call this from {@code OmniTech}'s constructor before registering event buses.
     */
    public static void init() {}
}
