package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.OmniTechOreBlock;
import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * MineralSet: mass-registers ore/mineral blocks and their block items for a given mineral.
 *
 * <p>Standalone items (ingots, dusts, plates, etc.) are handled by the data-driven
 * {@link ItemLoader} system and do not belong here.
 *
 * <p>Usage:
 * <pre>{@code
 * // Ore mineral with worldgen
 * MineralSet TUNGSTEN = MineralSet.create("tungsten",
 *     new String[]{ "%_ore", "%_block", "raw_%_block" },
 *     Map.of("%_ore", new OreSpawnConfig(...)));
 *
 * // Pure mineral block or storage-block only
 * MineralSet ALUMINIUM = MineralSet.create("aluminium", new String[]{ "%_block" });
 *
 * // Access registered objects by pattern:
 * TUNGSTEN.block("%_ore")      // DeferredBlock<Block>
 * TUNGSTEN.blockItem("%_ore")  // DeferredItem<BlockItem>
 * }</pre>
 *
 * <p>In a dev environment, missing JSON resources (blockstates, models, loot tables, lang
 * entries) are auto-generated on first load. Add textures to
 * {@code assets/omnitech/textures/block/} manually.
 */
public class MineralSet {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * All MineralSet instances in creation order.
     * Populated automatically by every {@link #create} call.
     * Used by {@link #addAllToTab} to populate the Minerals creative tab.
     */
    public static final List<MineralSet> ALL_SETS = new ArrayList<>();

    private final String mineral;
    private final Map<String, DeferredBlock<Block>>    blocks     = new LinkedHashMap<>();
    private final Map<String, DeferredItem<BlockItem>> blockItems = new LinkedHashMap<>();

    private MineralSet(String mineral) {
        this.mineral = mineral;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Create and register a mineral block set without worldgen.
     * Convenience overload — delegates to {@link #create(String, String[], Map)} with an empty config map.
     */
    public static MineralSet create(String mineral, String[] blockPatterns) {
        return create(mineral, blockPatterns, Map.of());
    }

    /**
     * Create and register a mineral block set, with optional worldgen for ore blocks.
     *
     * @param mineral       Mineral name, e.g. {@code "tungsten"}.
     * @param blockPatterns Block name patterns — {@code "%"} is replaced with the mineral name.
     *                      e.g. {@code ["%_ore", "%_block", "raw_%_block"]}.
     * @param oreConfigs    Map of block pattern → {@link OreSpawnConfig}. Patterns listed here
     *                      are registered as {@link OmniTechOreBlock} and added to
     *                      {@link OmniTechBlocks#ALL_ORES} so datagen picks them up.
     * @return Fully populated {@link MineralSet} instance.
     */
    public static MineralSet create(String mineral, String[] blockPatterns, Map<String, OreSpawnConfig> oreConfigs) {
        MineralSet set = new MineralSet(mineral);
        Path resourcesDir = findResourcesDir();

        for (String pattern : blockPatterns) {
            String name = pattern.replace("%", mineral);
            OreSpawnConfig oreConfig = oreConfigs.get(pattern);

            DeferredBlock<Block> block;
            if (oreConfig != null) {
                DeferredBlock<OmniTechOreBlock> oreBlock = OmniTechBlocks.register(name,
                        p -> new OmniTechOreBlock(applyBlockDefaults(pattern, p), oreConfig));
                OmniTechBlocks.ALL_ORES.add(new OmniTechBlocks.OreEntry(oreBlock, oreConfig));
                @SuppressWarnings("unchecked")
                DeferredBlock<Block> cast = (DeferredBlock<Block>) (DeferredBlock<?>) oreBlock;
                block = cast;
            } else {
                block = OmniTechBlocks.REGISTRY.registerSimpleBlock(name, p -> applyBlockDefaults(pattern, p));
            }

            set.blocks.put(pattern, block);
            set.blockItems.put(pattern, OmniTechItems.REGISTRY.registerSimpleBlockItem(name, block));

            if (resourcesDir != null) generateBlockResources(resourcesDir, name);
        }

        if (resourcesDir != null) updateLangFile(resourcesDir, mineral, blockPatterns);

        ALL_SETS.add(set);
        return set;
    }

    // ── Creative tab ──────────────────────────────────────────────────────────

    /** Adds all block items in this set to the given creative tab output. */
    public void addToTab(CreativeModeTab.Output output) {
        for (DeferredItem<BlockItem> bi : blockItems.values()) {
            output.accept(bi.get());
        }
    }

    /**
     * Adds all block items from every registered {@link MineralSet} to the given output,
     * in the order the sets were created (i.e. the order in {@link OmniTechMinerals}).
     */
    public static void addAllToTab(CreativeModeTab.Output output) {
        for (MineralSet set : ALL_SETS) {
            set.addToTab(output);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Block by pattern, e.g. {@code block("%_ore")}. */
    public DeferredBlock<Block> block(String pattern) { return blocks.get(pattern); }

    /** BlockItem for a block pattern, e.g. {@code blockItem("%_ore")}. */
    public DeferredItem<BlockItem> blockItem(String pattern) { return blockItems.get(pattern); }

    public String getMineral() { return mineral; }

    public Collection<DeferredBlock<Block>>    allBlocks()     { return Collections.unmodifiableCollection(blocks.values()); }
    public Collection<DeferredItem<BlockItem>> allBlockItems() { return Collections.unmodifiableCollection(blockItems.values()); }

    // ── Block property defaults ───────────────────────────────────────────────

    private static BlockBehaviour.Properties applyBlockDefaults(String pattern, BlockBehaviour.Properties p) {
        if (pattern.contains("ore")) {
            return p.mapColor(MapColor.STONE)
                    .strength(3.0F, 3.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops();
        }
        if (pattern.contains("block")) {
            return p.mapColor(MapColor.METAL)
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops();
        }
        // Pure mineral blocks (e.g. skutterudite, galena) and raw ore blocks (surface_%, etc.)
        return p.mapColor(MapColor.STONE)
                .strength(3.0F, 4.0F)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
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
        writeIfAbsent(
                res.resolve("assets/" + mod + "/items/" + name + ".json"),
                itemReferenceJson(mod, "block/" + name));
        writeIfAbsent(
                res.resolve("data/" + mod + "/loot_table/blocks/" + name + ".json"),
                lootTableJson(mod, name));
    }

    private static void updateLangFile(Path res, String mineral, String[] blockPatterns) {
        String mod = OmniTech.MODID;
        Path langPath = res.resolve("assets/" + mod + "/lang/en_us.json");

        JsonObject lang = new JsonObject();
        if (Files.exists(langPath)) {
            try {
                lang = JsonParser.parseString(
                        Files.readString(langPath, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                OmniTech.LOGGER.warn("[MineralSet] Could not read lang file: {}", e.getMessage());
                return;
            }
        }

        boolean changed = false;
        for (String pattern : blockPatterns) {
            String name = pattern.replace("%", mineral);
            String key  = "block." + mod + "." + name;
            if (!lang.has(key)) { lang.addProperty(key, prettify(name)); changed = true; }
        }

        if (changed) writeFile(langPath, GSON.toJson(lang));
    }

    // ── JSON templates ────────────────────────────────────────────────────────

    private static String itemReferenceJson(String mod, String modelPath) {
        return "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + mod + ":" + modelPath + "\"\n  }\n}\n";
    }

    private static String blockstateJson(String mod, String name) {
        return "{\n  \"variants\": {\n    \"\": { \"model\": \""
                + mod + ":block/" + name + "\" }\n  }\n}\n";
    }

    private static String blockModelJson(String mod, String name) {
        return "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\n    \"all\": \""
                + mod + ":block/" + name + "\"\n  }\n}\n";
    }

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
            OmniTech.LOGGER.info("[MineralSet] Generated: {}", path.getFileName());
        } catch (IOException e) {
            OmniTech.LOGGER.warn("[MineralSet] Could not write {}: {}", path, e.getMessage());
        }
    }

    /**
     * Walk upward from the compiled classes location to find {@code src/main/resources}.
     * Returns {@code null} when running from a packaged JAR (i.e. in production).
     */
    private static Path findResourcesDir() {
        try {
            Path candidate = Paths.get(MineralSet.class
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
