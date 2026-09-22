/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads every {@code data/omnitech/chemistry/<namespace>/<path>.json} from the mod JAR at startup
 * and registers its real-world chemical composition into {@link ItemChemistryRegistry}, keyed by
 * the item id the file path spells out — e.g. {@code chemistry/minecraft/ender_pearl.json} becomes
 * {@code minecraft:ender_pearl}. Purely additive lookup data, same JarContents-at-construction-time
 * approach as {@link ItemLoader}/{@link FluidLoader} (so it's available on the client without
 * needing a datapack sync, and has no ordering dependency on any registry since it never resolves
 * an {@code Item} — only the id string).
 *
 * <p>JSON schema:
 * <pre>{@code
 * {
 *   "note": "free-text: what real-world thing this represents and how amounts were derived",
 *   "compounds": [
 *     { "smiles": "O", "name": "Water", "amount": 40 }
 *   ]
 * }
 * }</pre>
 * {@code amount} is millimoles (mmol). {@code name} is optional (falls back to an auto-derived
 * name) but should normally be set — see {@link ItemChemistryRegistry.CompoundAmount}.
 *
 * <p>Volume/mass convention used when authoring these files: a full block = 1×1×1 m; stairs =
 * 3/4 block; slabs = 1/2 block; walls = 1/2 block; fences = 1/3 block; trapdoors = 1/6 block;
 * pressure plates = 1/16 block; buttons = 1/20 block. Non-block items are estimated from their
 * real-world equivalent (e.g. a steak from an average 250 g portion).
 */
public class ChemistryCompositionLoader {
    private static final Gson GSON = new GsonBuilder().create();
    static final String CHEMISTRY_DATA_PATH = "data/omnitech/chemistry";

    public static void loadAll() {
        JarContents contents = ModList.get()
                .getModFileById(OmniTech.MODID)
                .getFile()
                .getContents();

        int[] count = {0};

        contents.visitContent(CHEMISTRY_DATA_PATH, (relativePath, resource) -> {
            if (!relativePath.endsWith(".json")) return;
            String remainder = relativePath.substring(CHEMISTRY_DATA_PATH.length() + 1);
            int slash = remainder.indexOf('/');
            if (slash < 0) {
                OmniTech.LOGGER.warn(
                        "[ChemistryCompositionLoader] '{}' is not namespaced (expected " +
                        "chemistry/<namespace>/<item>.json) — skipped", remainder);
                return;
            }
            String namespace = remainder.substring(0, slash);
            String itemPath = remainder.substring(slash + 1, remainder.length() - 5); // strip .json
            String itemId = namespace + ":" + itemPath;

            try (var reader = resource.bufferedReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                List<ItemChemistryRegistry.CompoundAmount> compounds = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("compounds");
                for (var element : array) {
                    JsonObject c = element.getAsJsonObject();
                    String smiles = c.get("smiles").getAsString();
                    String name = c.has("name") ? c.get("name").getAsString() : null;
                    double amount = c.get("amount").getAsDouble();
                    compounds.add(new ItemChemistryRegistry.CompoundAmount(smiles, name, amount));
                }
                ItemChemistryRegistry.register(itemId, compounds);
                count[0]++;
            } catch (Exception e) {
                OmniTech.LOGGER.error(
                        "[ChemistryCompositionLoader] Failed to parse '{}': {}", itemId, e.getMessage());
            }
        });

        OmniTech.LOGGER.info("[ChemistryCompositionLoader] {} item composition(s) registered from JSON", count[0]);
    }
}
