/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads every {@code data/omnitech/item/<name>.json} from the mod JAR at startup
 * and registers a plain {@link net.minecraft.world.item.Item} into
 * {@link OmniTechItems#REGISTRY} for each one.
 *
 * <p>Must be called before {@link OmniTechItems#REGISTRY}'s {@code register(IEventBus)}
 * so that all DeferredRegister entries are queued before the RegisterEvent fires.
 *
 * <p>JSON schema (all fields optional, defaults shown):
 * <pre>{@code
 * {
 *   "stack_size":    64,     // max items per stack (ignored when durability > 0)
 *   "durability":     0,     // damage capacity; > 0 forces stack_size = 1 (MC rule)
 *   "formula":       null,   // chemical formula shown in tooltip, e.g. "W"
 *   "fire_resistant": false, // true = item survives lava/fire (burns: false in yml)
 *   "food":          null    // makes it edible: { "nutrition": 5, "saturation": 0.6, "always_edible": false }
 * }
 * }</pre>
 *
 * <p>An empty {@code {}} file produces a plain 64-stack ingredient item.
 *
 * <p>In a development environment (running from source), missing asset files are
 * auto-generated so the game does not crash on a missing model:
 * <ul>
 *   <li>{@code assets/omnitech/items/<name>.json} — item renderer reference</li>
 *   <li>{@code assets/omnitech/models/item/<name>.json} — flat {@code item/generated} model</li>
 *   <li>A lang entry in {@code assets/omnitech/lang/en_us.json}</li>
 * </ul>
 * Textures are NOT generated — place them in
 * {@code assets/omnitech/textures/item/} manually.
 */
public class ItemLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final String ITEM_DATA_PATH = "data/omnitech/item";

    public static void loadAll() {
        JarContents contents = ModList.get()
                .getModFileById(OmniTech.MODID)
                .getFile()
                .getContents();

        Path resourcesDir = findResourcesDir();
        int[] count = {0};

        contents.visitContent(ITEM_DATA_PATH, (relativePath, resource) -> {
            if (!relativePath.endsWith(".json")) return;
            String remainder = relativePath.substring(ITEM_DATA_PATH.length() + 1);
            if (remainder.contains("/")) return; // skip sub-directories

            String name = remainder.substring(0, remainder.length() - 5); // strip .json

            try (var reader = resource.bufferedReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                int stackSize      = json.has("stack_size")    ? json.get("stack_size").getAsInt()     : 64;
                int durability     = json.has("durability")    ? json.get("durability").getAsInt()     : 0;
                String formula     = json.has("formula")       ? json.get("formula").getAsString()     : null;
                boolean fireRes    = json.has("fire_resistant") && json.get("fire_resistant").getAsBoolean();
                FoodProperties food = json.has("food") ? parseFood(json.getAsJsonObject("food")) : null;

                if (durability > 0 && stackSize != 64) {
                    OmniTech.LOGGER.warn(
                            "[ItemLoader] '{}' has durability={} — stack_size={} will be ignored (MC forces stacksTo(1))",
                            name, durability, stackSize);
                }

                final int    finalDurability = durability;
                final int    finalStackSize  = stackSize;
                final String finalFormula    = formula;
                final boolean finalFireRes   = fireRes;

                OmniTechItems.REGISTRY.registerSimpleItem(name, p -> {
                    if (finalDurability > 0) p = p.durability(finalDurability);
                    else if (finalStackSize != 64) p = p.stacksTo(finalStackSize);
                    if (finalFireRes) p = p.fireResistant();
                    if (food != null) p = p.food(food);
                    if (finalFormula != null)
                        p = p.component(OmniTechDataComponents.FORMULA.get(), finalFormula);
                    return p;
                });

                if (resourcesDir != null) generateResources(resourcesDir, name);
                count[0]++;
                OmniTech.LOGGER.debug("[ItemLoader] Registered: {} (stack={}, durability={})",
                        name, durability > 0 ? 1 : stackSize, durability);

            } catch (Exception e) {
                OmniTech.LOGGER.error("[ItemLoader] Failed to parse item '{}': {}", name, e.getMessage());
            }
        });

        OmniTech.LOGGER.info("[ItemLoader] {} items registered from JSON", count[0]);
    }

    private static FoodProperties parseFood(JsonObject food) {
        var builder = new FoodProperties.Builder()
                .nutrition(food.has("nutrition") ? food.get("nutrition").getAsInt() : 1)
                .saturationModifier(food.has("saturation") ? food.get("saturation").getAsFloat() : 0.1F);
        if (food.has("always_edible") && food.get("always_edible").getAsBoolean()) builder.alwaysEdible();
        return builder.build();
    }

    // ── Dev-mode resource generation ──────────────────────────────────────────

    private static void generateResources(Path res, String name) {
        String mod = OmniTech.MODID;

        writeIfAbsent(
                res.resolve("assets/" + mod + "/items/" + name + ".json"),
                itemReferenceJson(mod, name));
        writeIfAbsent(
                res.resolve("assets/" + mod + "/models/item/" + name + ".json"),
                itemModelJson(mod, name));
        updateLangFile(res, mod, name);
    }

    private static String itemReferenceJson(String mod, String name) {
        return "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + mod + ":item/" + name + "\"\n  }\n}\n";
    }

    private static String itemModelJson(String mod, String name) {
        return "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \""
                + mod + ":item/" + name + "\"\n  }\n}\n";
    }

    private static void updateLangFile(Path res, String mod, String name) {
        Path langPath = res.resolve("assets/" + mod + "/lang/en_us.json");
        String key = "item." + mod + "." + name;

        JsonObject lang = new JsonObject();
        if (Files.exists(langPath)) {
            try {
                lang = JsonParser.parseString(
                        Files.readString(langPath, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                OmniTech.LOGGER.warn("[ItemLoader] Could not read lang file: {}", e.getMessage());
                return;
            }
        }

        if (!lang.has(key)) {
            lang.addProperty(key, prettify(name));
            writeFile(langPath, GSON.toJson(lang));
        }
    }

    /** {@code "granite_dust"} → {@code "Granite Dust"} */
    private static String prettify(String name) {
        String[] words = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            if (!word.isEmpty())
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private static void writeIfAbsent(Path path, String content) {
        if (Files.exists(path)) return;
        writeFile(path, content);
    }

    private static void writeFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
            OmniTech.LOGGER.info("[ItemLoader] Generated: {}", path.getFileName());
        } catch (IOException e) {
            OmniTech.LOGGER.warn("[ItemLoader] Could not write {}: {}", path, e.getMessage());
        }
    }

    /**
     * Walk upward from the compiled classes location to find {@code src/main/resources}.
     * Returns {@code null} when running from a packaged JAR (production).
     */
    private static Path findResourcesDir() {
        try {
            Path candidate = Paths.get(ItemLoader.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            for (int i = 0; i < 8; i++) {
                if (candidate == null) break;
                Path resources = candidate.resolve("src/main/resources");
                if (Files.isDirectory(resources)) return resources;
                candidate = candidate.getParent();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
