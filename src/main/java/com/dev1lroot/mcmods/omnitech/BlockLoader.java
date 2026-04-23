package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.OmniTechOreBlock;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Reads every {@code data/omnitech/block/<id>.json} from the mod JAR at startup
 * and registers a block + block item into {@link OmniTechBlocks#REGISTRY} /
 * {@link OmniTechItems#REGISTRY} for each one.
 *
 * <p>Must be called before the registries' {@code register(IEventBus)} call.
 * {@link OreSpawnLoader} must be called immediately after so it can look up
 * the registered blocks by name.
 *
 * <p>JSON schema (all fields optional, defaults inferred from the block name):
 * <pre>{@code
 * {
 *   "ore":          false,     // true = OmniTechOreBlock (XP drops); auto-true if name ends in "_ore"
 *   "map_color":    null,      // "stone" | "metal" | "raw_iron" | "sand" | "ice" | "dirt" | "wood" | "clay"
 *   "strength":     null,      // [hardness, resistance], e.g. [3.0, 3.0]
 *   "sound":        null,      // "stone" | "metal" | "gravel" | "sand" | "grass" | "glass" | "wood"
 *   "requires_tool": true      // false = can be mined by hand
 * }
 * }</pre>
 *
 * <p>Name-based defaults (applied when the corresponding field is absent):
 * <ul>
 *   <li>ends in {@code _ore} → stone, 3/3, ore=true</li>
 *   <li>starts with {@code raw_} and ends in {@code _block} → stone, 5/6</li>
 *   <li>ends in {@code _block} → metal, 5/6</li>
 *   <li>otherwise (pure mineral, geological) → stone, 3/4</li>
 * </ul>
 *
 * <p>An empty {@code {}} file produces a plain block with all defaults applied.
 *
 * <p>In a dev environment, missing asset files are auto-generated (blockstates,
 * block model, item reference, loot table, lang entry). Textures are NOT generated.
 */
public class BlockLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final String BLOCK_DATA_PATH = "data/omnitech/block";

    // Insertion-ordered so addAllToTab follows declaration order in the JAR.
    private static final Map<String, DeferredBlock<Block>>    REGISTERED   = new LinkedHashMap<>();
    private static final List<DeferredItem<BlockItem>>        BLOCK_ITEMS  = new ArrayList<>();

    public static void loadAll() {
        JarContents contents = ModList.get()
                .getModFileById(OmniTech.MODID)
                .getFile()
                .getContents();

        Path resourcesDir = findResourcesDir();
        int[] count = {0};

        contents.visitContent(BLOCK_DATA_PATH, (relativePath, resource) -> {
            if (!relativePath.endsWith(".json")) return;
            String remainder = relativePath.substring(BLOCK_DATA_PATH.length() + 1);
            if (remainder.contains("/")) return; // skip sub-directories

            String name = remainder.substring(0, remainder.length() - 5); // strip .json

            try (var reader = resource.bufferedReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                boolean isOre = json.has("ore")
                        ? json.get("ore").getAsBoolean()
                        : name.endsWith("_ore");

                MapColor  mapColor     = json.has("map_color")   ? parseMapColor(json.get("map_color").getAsString())    : defaultMapColor(name, isOre);
                float[]   strength     = json.has("strength")    ? parseStrength(json.getAsJsonArray("strength"))         : defaultStrength(name, isOre);
                SoundType sound        = json.has("sound")       ? parseSound(json.get("sound").getAsString())            : defaultSound(name, isOre);
                boolean   requiresTool = !json.has("requires_tool") || json.get("requires_tool").getAsBoolean();

                final MapColor   mc  = mapColor;
                final float[]    st  = strength;
                final SoundType  snd = sound;
                final boolean    rt  = requiresTool;

                DeferredBlock<Block> block;
                if (isOre) {
                    @SuppressWarnings("unchecked")
                    DeferredBlock<Block> cast = (DeferredBlock<Block>) (DeferredBlock<?>)
                            OmniTechBlocks.register(name, p -> new OmniTechOreBlock(applyProps(p, mc, st, snd, rt)));
                    block = cast;
                } else {
                    block = OmniTechBlocks.REGISTRY.registerSimpleBlock(name, p -> applyProps(p, mc, st, snd, rt));
                }

                DeferredItem<BlockItem> blockItem = OmniTechItems.REGISTRY.registerSimpleBlockItem(name, block);
                REGISTERED.put(name, block);
                BLOCK_ITEMS.add(blockItem);

                if (resourcesDir != null) generateResources(resourcesDir, name);
                count[0]++;
                OmniTech.LOGGER.debug("[BlockLoader] Registered: {} (ore={})", name, isOre);

            } catch (Exception e) {
                OmniTech.LOGGER.error("[BlockLoader] Failed to parse block '{}': {}", name, e.getMessage());
            }
        });

        OmniTech.LOGGER.info("[BlockLoader] {} blocks registered from JSON", count[0]);
    }

    /** Adds all registered block items (in load order) to the given creative tab. */
    public static void addAllToTab(CreativeModeTab.Output output) {
        for (DeferredItem<BlockItem> bi : BLOCK_ITEMS) {
            output.accept(bi.get());
        }
    }

    /** Returns the DeferredBlock registered for {@code name}, or {@code null} if unknown. */
    public static DeferredBlock<Block> getBlock(String name) {
        return REGISTERED.get(name);
    }

    /** Resolves a block-item from the game registry by its block name. */
    public static Optional<Item> getBlockItem(String name) {
        return BuiltInRegistries.ITEM.getOptional(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, name));
    }

    // ── Property helpers ──────────────────────────────────────────────────────

    private static BlockBehaviour.Properties applyProps(
            BlockBehaviour.Properties p,
            MapColor color, float[] strength, SoundType sound, boolean requiresTool) {
        p = p.mapColor(color).strength(strength[0], strength[1]).sound(sound);
        if (requiresTool) p = p.requiresCorrectToolForDrops();
        return p;
    }

    private static MapColor defaultMapColor(String name, boolean isOre) {
        if (isOre || !name.endsWith("_block")) return MapColor.STONE;
        return MapColor.METAL;
    }

    private static float[] defaultStrength(String name, boolean isOre) {
        if (isOre)               return new float[]{ 3.0F, 3.0F };
        if (name.endsWith("_block")) return new float[]{ 5.0F, 6.0F };
        return new float[]{ 3.0F, 4.0F };
    }

    private static SoundType defaultSound(String name, boolean isOre) {
        if (isOre)               return SoundType.STONE;
        if (name.startsWith("raw_") && name.endsWith("_block")) return SoundType.STONE;
        if (name.endsWith("_block")) return SoundType.METAL;
        return SoundType.STONE;
    }

    private static MapColor parseMapColor(String s) {
        return switch (s.toLowerCase()) {
            case "metal"    -> MapColor.METAL;
            case "raw_iron" -> MapColor.RAW_IRON;
            case "sand"     -> MapColor.SAND;
            case "ice"      -> MapColor.ICE;
            case "grass"    -> MapColor.GRASS;
            case "dirt"     -> MapColor.DIRT;
            case "wood"     -> MapColor.WOOD;
            case "clay"     -> MapColor.CLAY;
            default         -> MapColor.STONE;
        };
    }

    private static float[] parseStrength(com.google.gson.JsonArray arr) {
        return new float[]{ arr.get(0).getAsFloat(), arr.get(1).getAsFloat() };
    }

    private static SoundType parseSound(String s) {
        return switch (s.toLowerCase()) {
            case "metal"  -> SoundType.METAL;
            case "gravel" -> SoundType.GRAVEL;
            case "sand"   -> SoundType.SAND;
            case "grass"  -> SoundType.GRASS;
            case "glass"  -> SoundType.GLASS;
            case "wood"   -> SoundType.WOOD;
            default       -> SoundType.STONE;
        };
    }

    // ── Dev-mode resource generation ──────────────────────────────────────────

    private static void generateResources(Path res, String name) {
        String mod = OmniTech.MODID;
        writeIfAbsent(res.resolve("assets/" + mod + "/blockstates/" + name + ".json"),   blockstateJson(mod, name));
        writeIfAbsent(res.resolve("assets/" + mod + "/models/block/" + name + ".json"),  blockModelJson(mod, name));
        writeIfAbsent(res.resolve("assets/" + mod + "/items/" + name + ".json"),         itemReferenceJson(mod, name));
        writeIfAbsent(res.resolve("data/" + mod + "/loot_table/blocks/" + name + ".json"), lootTableJson(mod, name));
        updateLangFile(res, mod, name);
    }

    private static String blockstateJson(String mod, String name) {
        return "{\n  \"variants\": {\n    \"\": { \"model\": \"" + mod + ":block/" + name + "\" }\n  }\n}\n";
    }

    private static String blockModelJson(String mod, String name) {
        return "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\n    \"all\": \""
                + mod + ":block/" + name + "\"\n  }\n}\n";
    }

    private static String itemReferenceJson(String mod, String name) {
        return "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
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

    private static void updateLangFile(Path res, String mod, String name) {
        Path langPath = res.resolve("assets/" + mod + "/lang/en_us.json");
        String key = "block." + mod + "." + name;

        JsonObject lang = new JsonObject();
        if (Files.exists(langPath)) {
            try {
                lang = JsonParser.parseString(
                        Files.readString(langPath, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                OmniTech.LOGGER.warn("[BlockLoader] Could not read lang file: {}", e.getMessage());
                return;
            }
        }
        if (!lang.has(key)) {
            lang.addProperty(key, prettify(name));
            writeFile(langPath, GSON.toJson(lang));
        }
    }

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
            OmniTech.LOGGER.info("[BlockLoader] Generated: {}", path.getFileName());
        } catch (IOException e) {
            OmniTech.LOGGER.warn("[BlockLoader] Could not write {}: {}", path, e.getMessage());
        }
    }

    private static Path findResourcesDir() {
        try {
            Path candidate = Paths.get(BlockLoader.class
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
