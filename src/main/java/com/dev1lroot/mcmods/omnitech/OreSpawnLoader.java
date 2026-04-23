package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads every {@code data/omnitech/worldgen/ore/<id>.json} and pairs each
 * spawn config with the already-registered block of the same name.
 *
 * <p>Must be called <em>after</em> {@link BlockLoader#loadAll()} and before
 * {@link OmniTechBlocks#REGISTRY} fires its {@code RegisterEvent}.
 *
 * <p>JSON schema:
 * <pre>{@code
 * {
 *   "default": {
 *     "biomes":    "#minecraft:is_overworld",
 *     "min_y":     -64,
 *     "max_y":      32,
 *     "vein_size":   6,
 *     "min_count":   0,
 *     "max_count":   1
 *   },
 *   "overrides": [             // optional
 *     {
 *       "biomes":   "#minecraft:is_mountain",
 *       "min_y":    -32,
 *       "max_y":     80,
 *       "vein_size":  8,
 *       "min_count":  0,
 *       "max_count":  2
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>The filename (without extension) must match the registry name of the ore
 * block, e.g. {@code tungsten_ore.json} targets {@code omnitech:tungsten_ore}.
 */
public class OreSpawnLoader {

    private static final Gson GSON = new GsonBuilder().create();
    static final String ORE_DATA_PATH = "data/omnitech/worldgen/ore";

    public static void loadAll() {
        JarContents contents = ModList.get()
                .getModFileById(OmniTech.MODID)
                .getFile()
                .getContents();

        int[] count = {0};

        contents.visitContent(ORE_DATA_PATH, (relativePath, resource) -> {
            if (!relativePath.endsWith(".json")) return;
            String remainder = relativePath.substring(ORE_DATA_PATH.length() + 1);
            if (remainder.contains("/")) return;

            String name = remainder.substring(0, remainder.length() - 5);

            DeferredBlock<?> block = BlockLoader.getBlock(name);
            if (block == null) {
                OmniTech.LOGGER.warn("[OreSpawnLoader] Block '{}' not found — run BlockLoader first.", name);
                return;
            }

            try (var reader = resource.bufferedReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                OreSpawnConfig.GenerationConfig defaultCfg = parseDefault(json.getAsJsonObject("default"));

                List<OreSpawnConfig.BiomeOverride> overrides = new ArrayList<>();
                if (json.has("overrides")) {
                    JsonArray arr = json.getAsJsonArray("overrides");
                    for (int i = 0; i < arr.size(); i++) {
                        overrides.add(parseOverride(arr.get(i).getAsJsonObject()));
                    }
                }

                OreSpawnConfig config = new OreSpawnConfig(defaultCfg, List.copyOf(overrides));
                OmniTechBlocks.ALL_ORES.add(new OmniTechBlocks.OreEntry(block, config));
                count[0]++;
                OmniTech.LOGGER.debug("[OreSpawnLoader] Loaded: {}", name);

            } catch (Exception e) {
                OmniTech.LOGGER.error("[OreSpawnLoader] Failed to parse '{}': {}", name, e.getMessage());
            }
        });

        OmniTech.LOGGER.info("[OreSpawnLoader] {} ore spawn configs loaded", count[0]);
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private static OreSpawnConfig.GenerationConfig parseDefault(JsonObject o) {
        return new OreSpawnConfig.GenerationConfig(
                o.get("biomes").getAsString(),
                o.get("min_y").getAsInt(),
                o.get("max_y").getAsInt(),
                o.get("vein_size").getAsInt(),
                o.get("min_count").getAsInt(),
                o.get("max_count").getAsInt()
        );
    }

    private static OreSpawnConfig.BiomeOverride parseOverride(JsonObject o) {
        return new OreSpawnConfig.BiomeOverride(
                o.get("biomes").getAsString(),
                o.get("min_y").getAsInt(),
                o.get("max_y").getAsInt(),
                o.get("vein_size").getAsInt(),
                o.get("min_count").getAsInt(),
                o.get("max_count").getAsInt()
        );
    }
}
