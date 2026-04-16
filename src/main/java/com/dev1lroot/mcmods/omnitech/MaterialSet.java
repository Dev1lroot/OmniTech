package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.OmniTechOreBlock;
import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.world.item.CreativeModeTab;

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
 * TUNGSTEN.item("%_ingot")    // Optional<Item> resolved from registry
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

    /**
     * All MaterialSet instances in the order they were created.
     * Populated automatically by every {@link #create} call.
     * Used by {@link #addAllToTab} to populate the Materials creative tab.
     */
    public static final List<MaterialSet> ALL_SETS = new ArrayList<>();

    private final String material;
    // Standalone items are registered by ItemLoader from data/omnitech/item/<name>.json.
    // We only track the pattern→name mapping so addToTab can resolve them from the registry.
    private final Map<String, String>                  itemNames  = new LinkedHashMap<>();
    private final Map<String, DeferredBlock<Block>>    blocks     = new LinkedHashMap<>();
    private final Map<String, DeferredItem<BlockItem>> blockItems = new LinkedHashMap<>();

    private MaterialSet(String material) {
        this.material = material;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Create and register a complete material set without any worldgen.
     * Convenience overload — delegates to the full {@code create} with an empty ore-config map.
     */
    public static MaterialSet create(
            String material,
            DeferredRegister.Items itemRegistry,
            String[] itemPatterns,
            DeferredRegister.Blocks blockRegistry,
            String[] blockPatterns) {
        return create(material, itemRegistry, itemPatterns, blockRegistry, blockPatterns,
                Map.of());
    }

    /**
     * Create and register a complete material set, with optional worldgen for ore blocks.
     *
     * @param material      Material name, e.g. {@code "tungsten"}.
     * @param itemRegistry  {@link DeferredRegister.Items} to register items into.
     * @param itemPatterns  Item name patterns — {@code "%"} is replaced with the material name.
     *                      Patterns that also appear in {@code blockPatterns} are skipped here;
     *                      their BlockItem is created automatically with the block.
     * @param blockRegistry {@link DeferredRegister.Blocks} to register blocks into.
     * @param blockPatterns Block name patterns, e.g. {@code ["%_ore", "%_block"]}.
     * @param oreConfigs    Map of block pattern → {@link OreSpawnConfig}.  Patterns listed here
     *                      are registered as {@link OmniTechOreBlock} and added to
     *                      {@link OmniTechBlocks#ALL_ORES} so datagen picks them up.
     *                      Use {@link Map#of} or {@link Map#entry} to build this inline.
     * @return Fully populated {@link MaterialSet} instance.
     */
    public static MaterialSet create(
            String material,
            DeferredRegister.Items itemRegistry,
            String[] itemPatterns,
            DeferredRegister.Blocks blockRegistry,
            String[] blockPatterns,
            Map<String, OreSpawnConfig> oreConfigs) {

        MaterialSet set = new MaterialSet(material);
        Set<String> blockPatternSet = new HashSet<>(Arrays.asList(blockPatterns));
        Path resourcesDir = findResourcesDir();

        // --- Blocks (+ their BlockItems) ---
        for (String pattern : blockPatterns) {
            String name = pattern.replace("%", material);
            OreSpawnConfig oreConfig = oreConfigs.get(pattern);

            DeferredBlock<Block> block;
            if (oreConfig != null) {
                // Register as OmniTechOreBlock so it gets experience drops and participates in datagen
                DeferredBlock<OmniTechOreBlock> oreBlock = OmniTechBlocks.register(name,
                        p -> new OmniTechOreBlock(applyBlockDefaults(pattern, p), oreConfig));
                OmniTechBlocks.ALL_ORES.add(new OmniTechBlocks.OreEntry(oreBlock, oreConfig));
                @SuppressWarnings("unchecked")
                DeferredBlock<Block> cast = (DeferredBlock<Block>) (DeferredBlock<?>) oreBlock;
                block = cast;
            } else {
                block = blockRegistry.registerSimpleBlock(name, p -> applyBlockDefaults(pattern, p));
            }

            set.blocks.put(pattern, block);
            set.blockItems.put(pattern, itemRegistry.registerSimpleBlockItem(name, block));

            if (resourcesDir != null) generateBlockResources(resourcesDir, name);
        }

        // --- Standalone items (registration delegated to ItemLoader via data/omnitech/item/*.json) ---
        for (String pattern : itemPatterns) {
            if (blockPatternSet.contains(pattern)) continue;
            String name = pattern.replace("%", material);
            set.itemNames.put(pattern, name);
            // Resource files (items ref, model) are generated by ItemLoader.
            // Lang is still handled below in updateLangFile.
        }

        // --- Lang entries ---
        if (resourcesDir != null) {
            updateLangFile(resourcesDir, material, itemPatterns, blockPatterns, blockPatternSet);
        }

        // --- Auto-generate material recipes (dev mode only) ---
        if (resourcesDir != null) {
            Set<String> allPatterns = new HashSet<>(Arrays.asList(itemPatterns));
            allPatterns.addAll(blockPatternSet);
            generateRecipes(resourcesDir, material, allPatterns);
        }

        ALL_SETS.add(set);
        return set;
    }

    // ── Creative tab ──────────────────────────────────────────────────────────

    /**
     * Adds all items in this set to the given creative tab output.
     * Order: block-items first (in block-pattern order), then standalone items
     * (in item-pattern order) — matching the patterns arrays passed to {@link #create}.
     */
    public void addToTab(CreativeModeTab.Output output) {
        for (DeferredItem<BlockItem> bi : blockItems.values()) {
            output.accept(bi.get());
        }
        for (String name : itemNames.values()) {
            BuiltInRegistries.ITEM
                    .getOptional(Identifier.fromNamespaceAndPath(OmniTech.MODID, name))
                    .ifPresent(output::accept);
        }
    }

    /**
     * Adds all items from every registered {@link MaterialSet} to the given output.
     * Sets are added in the order they were created (i.e. the order they appear in
     * {@link OmniTechMaterials}).
     */
    public static void addAllToTab(CreativeModeTab.Output output) {
        for (MaterialSet set : ALL_SETS) {
            set.addToTab(output);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Looks up a standalone item from the game registry by pattern, e.g. {@code item("%_ingot")}.
     * Returns empty if the pattern is unknown or the registry is not yet populated.
     */
    public java.util.Optional<Item> item(String pattern) {
        String name = itemNames.get(pattern);
        if (name == null) return java.util.Optional.empty();
        return BuiltInRegistries.ITEM.getOptional(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, name));
    }

    /** Block by pattern, e.g. {@code block("%_ore")}. */
    public DeferredBlock<Block> block(String pattern) { return blocks.get(pattern); }

    /** BlockItem for a block pattern, e.g. {@code blockItem("%_ore")}. */
    public DeferredItem<BlockItem> blockItem(String pattern) { return blockItems.get(pattern); }

    public String getMaterial() { return material; }

    /** Returns all standalone items resolved from the game registry. */
    public Collection<Item> allItems() {
        return itemNames.values().stream()
                .map(name -> BuiltInRegistries.ITEM.getOptional(
                        Identifier.fromNamespaceAndPath(OmniTech.MODID, name)))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();
    }
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

    // ── Recipe generation ─────────────────────────────────────────────────────

    /**
     * Melting temperatures (°C) for smelter and foundry recipes, keyed by material name.
     * Materials not listed here will not get smelter/foundry recipes.
     */
    private static final Map<String, Integer> MELT_TEMPS = Map.ofEntries(
            Map.entry("tin",       300),
            Map.entry("lead",      400),
            Map.entry("zinc",      500),
            Map.entry("aluminium", 700),
            Map.entry("brass",     1000),
            Map.entry("cobalt",    1500),
            Map.entry("nickel",    1500),
            Map.entry("steel",     1500),
            Map.entry("uranium",   1200),
            Map.entry("titanium",  1700),
            Map.entry("chromium",  2000),
            Map.entry("tungsten",  3500),
            Map.entry("iron",      1600),
            Map.entry("copper",    1100)
    );

    /**
     * Which ingot item to use for vanilla materials (material name → full ID).
     * OmniTech materials default to {@code omnitech:<material>_ingot}.
     */
    private static final Map<String, String> VANILLA_INGOTS = Map.of(
            "iron",   "minecraft:iron_ingot",
            "copper", "minecraft:copper_ingot",
            "gold",   "minecraft:gold_ingot"
    );

    private static void generateRecipes(Path res, String material, Set<String> patterns) {
        String mod = OmniTech.MODID;
        Path recipeDir = res.resolve("data/" + mod + "/recipe");

        boolean hasMote   = patterns.contains("%_mote");
        boolean hasDust   = patterns.contains("%_dust");
        boolean hasIngot  = patterns.contains("%_ingot");
        boolean hasBlock  = patterns.contains("%_block");
        boolean hasCog    = patterns.contains("%_cog");

        String ingotId = VANILLA_INGOTS.getOrDefault(material, mod + ":" + material + "_ingot");
        String dustId  = mod + ":" + material + "_dust";
        String moteId  = mod + ":" + material + "_mote";
        String blockId = mod + ":" + material + "_block";
        String cogId   = mod + ":" + material + "_cog";

        // 1. Mote ↔ Dust (shapeless crafting)
        if (hasMote && hasDust) {
            writeIfAbsent(recipeDir.resolve(material + "_mote_to_dust.json"),
                    moteTodustJson(moteId, dustId));
            writeIfAbsent(recipeDir.resolve(material + "_dust_to_mote.json"),
                    dustToMoteJson(dustId, moteId));
        }

        // 2. Ingot ↔ Block (crafting)
        if (hasIngot && hasBlock) {
            writeIfAbsent(recipeDir.resolve(material + "_ingot_to_block.json"),
                    ingotToBlockJson(ingotId, blockId));
            writeIfAbsent(recipeDir.resolve(material + "_block_to_ingot.json"),
                    blockToIngotJson(blockId, ingotId));
        }

        // 3. Ingot → Dust (macerator)
        if (hasIngot && hasDust) {
            writeIfAbsent(recipeDir.resolve("manual_macerator/" + material + "_ingot.json"),
                    maceratorIngotJson(ingotId, dustId, moteId, hasMote));
        }

        // 4. Ingot → Molten fluid (smelter) + Molten → Cog (foundry)
        Integer meltTemp = MELT_TEMPS.get(material);
        if (meltTemp != null && hasIngot) {
            String fluidId = mod + ":molten_" + material;
            // Smelter: 1 ingot → 1000 mB molten fluid
            writeIfAbsent(recipeDir.resolve("smelting/" + material + "_ingot_melt.json"),
                    smelterMeltJson(ingotId, fluidId, meltTemp));
            // Foundry: 200 mB molten → cog (if this material has a cog)
            if (hasCog) {
                writeIfAbsent(recipeDir.resolve("foundry/" + material + "_cog.json"),
                        foundryCogJson(fluidId, cogId, meltTemp));
            }
        }
    }

    private static String moteTodustJson(String moteId, String dustId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"type\": \"minecraft:crafting_shapeless\",\n");
        sb.append("  \"category\": \"misc\",\n");
        sb.append("  \"ingredients\": [\n");
        for (int i = 0; i < 9; i++) {
            sb.append("    \"").append(moteId).append("\"");
            if (i < 8) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"result\": { \"count\": 1, \"id\": \"").append(dustId).append("\" }\n}\n");
        return sb.toString();
    }

    private static String dustToMoteJson(String dustId, String moteId) {
        return "{\n  \"type\": \"minecraft:crafting_shapeless\",\n" +
               "  \"category\": \"misc\",\n" +
               "  \"ingredients\": [ \"" + dustId + "\" ],\n" +
               "  \"result\": { \"count\": 9, \"id\": \"" + moteId + "\" }\n}\n";
    }

    private static String ingotToBlockJson(String ingotId, String blockId) {
        return "{\n  \"type\": \"minecraft:crafting_shaped\",\n" +
               "  \"category\": \"misc\",\n" +
               "  \"key\": { \"I\": \"" + ingotId + "\" },\n" +
               "  \"pattern\": [ \"III\", \"III\", \"III\" ],\n" +
               "  \"result\": { \"count\": 1, \"id\": \"" + blockId + "\" }\n}\n";
    }

    private static String blockToIngotJson(String blockId, String ingotId) {
        return "{\n  \"type\": \"minecraft:crafting_shapeless\",\n" +
               "  \"category\": \"misc\",\n" +
               "  \"ingredients\": [ \"" + blockId + "\" ],\n" +
               "  \"result\": { \"count\": 9, \"id\": \"" + ingotId + "\" }\n}\n";
    }

    private static String maceratorIngotJson(String ingotId, String dustId,
                                              String moteId, boolean hasMote) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"requiredKineticForce\": 30,\n");
        sb.append("  \"input\": \"").append(ingotId).append("\",\n");
        sb.append("  \"output\": [\n");
        sb.append("    { \"item\": \"").append(dustId).append("\", \"count\": 1, \"chance\": 1.0 }");
        if (hasMote) {
            sb.append(",\n");
            sb.append("    { \"item\": \"").append(moteId).append("\", \"count\": 2, \"chance\": 0.4 }");
        }
        sb.append("\n  ]\n}\n");
        return sb.toString();
    }

    private static String smelterMeltJson(String ingotId, String fluidId, int tempC) {
        return "{\n  \"requiredMinimalTemperature\": " + tempC + ",\n" +
               "  \"input\": [ \"" + ingotId + "\" ],\n" +
               "  \"output\": { \"fluid\": \"" + fluidId + "\", \"amount\": 1000 }\n}\n";
    }

    private static String foundryCogJson(String fluidId, String cogId, int tempC) {
        return "{\n  \"requiredMinimalTemperature\": " + tempC + ",\n" +
               "  \"input\": { \"fluid\": \"" + fluidId + "\", \"amount\": 200 },\n" +
               "  \"template\": \"omnitech:cog_template\",\n" +
               "  \"output\": \"" + cogId + "\"\n}\n";
    }

    // ── JSON templates ────────────────────────────────────────────────────────

    /** assets/.../items/<name>.json — points the item renderer at a model */
    private static String itemReferenceJson(String mod, String modelPath) {
        return "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + mod + ":" + modelPath + "\"\n  }\n}\n";
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
