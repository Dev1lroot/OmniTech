package com.dev1lroot.mcmods.omnitech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * MaterialSet: mass-registers a full set of items and blocks for a given material.
 *
 * <p>Usage:
 * <pre>{@code
 * MaterialSet TUNGSTEN = MaterialSet.create(
 *     "tungsten",
 *     OmniTechItems.REGISTRY,
 *     new String[]{ "%_ore", "raw_%", "%_ingot", "%_dust", "%_plate" },
 *     OmniTechBlocks.REGISTRY,
 *     new String[]{ "%_ore", "%_block" }
 * );
 *
 * // Access registered objects by pattern:
 * TUNGSTEN.item("%_ingot")    // DeferredItem<Item>
 * TUNGSTEN.block("%_ore")     // DeferredBlock<Block>
 * TUNGSTEN.blockItem("%_ore") // DeferredItem<BlockItem>
 * }</pre>
 *
 * <p>In a dev environment, missing JSON resources (models, blockstates, loot tables, lang
 * entries) are auto-generated on first load so the game doesn't crash on a missing model.
 * Images are NOT generated — place textures in {@code assets/omnitech/textures/} manually.
 */
public class MaterialSet {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String material;
    private final Map<String, DeferredItem<Item>>      items      = new LinkedHashMap<>();
    private final Map<String, DeferredBlock<Block>>    blocks     = new LinkedHashMap<>();
    private final Map<String, DeferredItem<BlockItem>> blockItems = new LinkedHashMap<>();

    private MaterialSet(String material) {
        this.material = material;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Create and register a complete material set.
     *
     * @param material      Material name, e.g. {@code "tungsten"}.
     * @param itemRegistry  {@link DeferredRegister.Items} to register items into.
     * @param itemPatterns  Item name patterns — {@code "%"} is replaced with the material name.
     *                      Example: {@code ["%_ingot", "raw_%", "%_dust", "%_plate"]}.
     *                      Patterns that also appear in {@code blockPatterns} are skipped here;
     *                      their BlockItem is created automatically with the block.
     * @param blockRegistry {@link DeferredRegister.Blocks} to register blocks into.
     * @param blockPatterns Block name patterns, e.g. {@code ["%_ore", "%_block"]}.
     *                      Block properties are chosen automatically based on the pattern name.
     * @return Fully populated {@link MaterialSet} instance.
     */
    public static MaterialSet create(
            String material,
            DeferredRegister.Items itemRegistry,
            String[] itemPatterns,
            DeferredRegister.Blocks blockRegistry,
            String[] blockPatterns) {

        MaterialSet set = new MaterialSet(material);
        Set<String> blockPatternSet = new HashSet<>(Arrays.asList(blockPatterns));
        Path resourcesDir = findResourcesDir();

        // --- Blocks (+ their BlockItems) ---
        for (String pattern : blockPatterns) {
            String name = pattern.replace("%", material);
            DeferredBlock<Block> block = blockRegistry.registerSimpleBlock(name,
                    p -> applyBlockDefaults(pattern, p));
            set.blocks.put(pattern, block);

            DeferredItem<BlockItem> blockItem = itemRegistry.registerSimpleBlockItem(name, block);
            set.blockItems.put(pattern, blockItem);

            if (resourcesDir != null) generateBlockResources(resourcesDir, name);
        }

        // --- Standalone items (skip any covered by a block) ---
        for (String pattern : itemPatterns) {
            if (blockPatternSet.contains(pattern)) continue;
            String name = pattern.replace("%", material);
            DeferredItem<Item> item = itemRegistry.registerSimpleItem(name, p -> p);
            set.items.put(pattern, item);

            if (resourcesDir != null) generateItemResources(resourcesDir, name);
        }

        // --- Lang entries ---
        if (resourcesDir != null) {
            updateLangFile(resourcesDir, material, itemPatterns, blockPatterns, blockPatternSet);
        }

        return set;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Standalone item by pattern, e.g. {@code item("%_ingot")}. */
    public DeferredItem<Item> item(String pattern) { return items.get(pattern); }

    /** Block by pattern, e.g. {@code block("%_ore")}. */
    public DeferredBlock<Block> block(String pattern) { return blocks.get(pattern); }

    /** BlockItem for a block pattern, e.g. {@code blockItem("%_ore")}. */
    public DeferredItem<BlockItem> blockItem(String pattern) { return blockItems.get(pattern); }

    public String getMaterial() { return material; }

    public Collection<DeferredItem<Item>>      allItems()      { return Collections.unmodifiableCollection(items.values()); }
    public Collection<DeferredBlock<Block>>    allBlocks()     { return Collections.unmodifiableCollection(blocks.values()); }
    public Collection<DeferredItem<BlockItem>> allBlockItems() { return Collections.unmodifiableCollection(blockItems.values()); }

    // ── Block property defaults ───────────────────────────────────────────────

    private static BlockBehaviour.Properties applyBlockDefaults(String pattern, BlockBehaviour.Properties p) {
        if (pattern.contains("ore")) {
            return p.mapColor(MapColor.STONE)
                    .strength(3.0F, 3.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops();
        } else if (pattern.contains("block")) {
            return p.mapColor(MapColor.METAL)
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops();
        } else {
            return p.mapColor(MapColor.METAL)
                    .strength(3.0F, 4.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops();
        }
    }

    // ── Resource generation ───────────────────────────────────────────────────

    private static void generateItemResources(Path res, String name) {
        String mod = OmniTech.MODID;
        writeIfAbsent(
                res.resolve("assets/" + mod + "/items/" + name + ".json"),
                itemReferenceJson(mod, "item/" + name));
        writeIfAbsent(
                res.resolve("assets/" + mod + "/models/item/" + name + ".json"),
                itemModelJson(mod, name));
    }

    private static void generateBlockResources(Path res, String name) {
        String mod = OmniTech.MODID;
        writeIfAbsent(
                res.resolve("assets/" + mod + "/blockstates/" + name + ".json"),
                blockstateJson(mod, name));
        writeIfAbsent(
                res.resolve("assets/" + mod + "/models/block/" + name + ".json"),
                blockModelJson(mod, name));
        // Block's item reference points at the block model
        writeIfAbsent(
                res.resolve("assets/" + mod + "/items/" + name + ".json"),
                itemReferenceJson(mod, "block/" + name));
        writeIfAbsent(
                res.resolve("data/" + mod + "/loot_table/blocks/" + name + ".json"),
                lootTableJson(mod, name));
    }

    private static void updateLangFile(Path res, String material,
            String[] itemPatterns, String[] blockPatterns, Set<String> blockPatternSet) {
        String mod = OmniTech.MODID;
        Path langPath = res.resolve("assets/" + mod + "/lang/en_us.json");

        JsonObject lang = new JsonObject();
        if (Files.exists(langPath)) {
            try {
                lang = JsonParser.parseString(
                        Files.readString(langPath, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                OmniTech.LOGGER.warn("[MaterialSet] Could not read lang file: {}", e.getMessage());
                return;
            }
        }

        boolean changed = false;
        for (String pattern : blockPatterns) {
            String name = pattern.replace("%", material);
            String key  = "block." + mod + "." + name;
            if (!lang.has(key)) { lang.addProperty(key, prettify(name)); changed = true; }
        }
        for (String pattern : itemPatterns) {
            if (blockPatternSet.contains(pattern)) continue;
            String name = pattern.replace("%", material);
            String key  = "item." + mod + "." + name;
            if (!lang.has(key)) { lang.addProperty(key, prettify(name)); changed = true; }
        }

        if (changed) writeFile(langPath, GSON.toJson(lang));
    }

    // ── JSON templates ────────────────────────────────────────────────────────

    /** assets/.../items/<name>.json — points the item renderer at a model */
    private static String itemReferenceJson(String mod, String modelPath) {
        return "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + mod + ":" + modelPath + "\"\n  }\n}\n";
    }

    /** assets/.../models/item/<name>.json — flat 2D item sprite */
    private static String itemModelJson(String mod, String name) {
        return "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \""
                + mod + ":item/" + name + "\"\n  }\n}\n";
    }

    /** assets/.../blockstates/<name>.json — trivial single-variant blockstate */
    private static String blockstateJson(String mod, String name) {
        return "{\n  \"variants\": {\n    \"\": { \"model\": \""
                + mod + ":block/" + name + "\" }\n  }\n}\n";
    }

    /** assets/.../models/block/<name>.json — cube_all block model */
    private static String blockModelJson(String mod, String name) {
        return "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\n    \"all\": \""
                + mod + ":block/" + name + "\"\n  }\n}\n";
    }

    /** data/.../loot_table/blocks/<name>.json — simple self-drop */
    private static String lootTableJson(String mod, String name) {
        return "{\n  \"type\": \"minecraft:block\",\n  \"pools\": [\n    {\n"
                + "      \"rolls\": 1,\n      \"entries\": [\n        {\n"
                + "          \"type\": \"minecraft:item\",\n"
                + "          \"name\": \"" + mod + ":" + name + "\"\n"
                + "        }\n      ],\n      \"conditions\": [\n        {\n"
                + "          \"condition\": \"minecraft:survives_explosion\"\n"
                + "        }\n      ]\n    }\n  ]\n}\n";
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** {@code "tungsten_ingot"} → {@code "Tungsten Ingot"} */
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
            OmniTech.LOGGER.info("[MaterialSet] Generated: {}", path.getFileName());
        } catch (IOException e) {
            OmniTech.LOGGER.warn("[MaterialSet] Could not write {}: {}", path, e.getMessage());
        }
    }

    /**
     * Walk upward from the compiled classes location to find {@code src/main/resources}.
     * Returns {@code null} when running from a packaged JAR (i.e. in production).
     */
    private static Path findResourcesDir() {
        try {
            Path candidate = Paths.get(MaterialSet.class
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
