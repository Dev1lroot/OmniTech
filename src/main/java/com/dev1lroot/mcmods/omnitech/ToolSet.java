package com.dev1lroot.mcmods.omnitech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * ToolSet: mass-registers a complete tool set (pickaxe, shovel, sword, axe, hoe)
 * for a given material, mirroring the {@link MaterialSet} pattern.
 *
 * <p>Usage:
 * <pre>{@code
 * ToolSet STEEL_TOOLS = ToolSet.create(
 *     "steel",
 *     OmniTechItems.REGISTRY,
 *     BlockTags.INCORRECT_FOR_IRON_TOOL,
 *     800, 7.5f, 2.5f, 14,
 *     "omnitech:steel_ingot"
 * );
 *
 * STEEL_TOOLS.tool("%_pickaxe")  // DeferredItem<Item>
 * }</pre>
 *
 * <p>In a dev environment, missing JSON resources (models, lang entries, repair tags)
 * are auto-generated on first load. Textures must be placed manually (or use tool_factory.py).
 */
public class ToolSet {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** All ToolSet instances in order of creation, for creative tab population. */
    public static final List<ToolSet> ALL_SETS = new ArrayList<>();

    private final String material;
    private final Map<String, DeferredItem<Item>> tools = new LinkedHashMap<>();

    private ToolSet(String material) {
        this.material = material;
    }

    // ── Patterns ──────────────────────────────────────────────────────────────

    private static final String[] PATTERNS = {
        "%_pickaxe", "%_shovel", "%_sword", "%_axe", "%_hoe"
    };

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Create and register a complete tool set.
     *
     * @param material           Material name, e.g. {@code "steel"}.
     * @param registry           {@link DeferredRegister.Items} to register into.
     * @param incorrectForDrops  Blocks this tool tier CANNOT mine for drops
     *                           (e.g. {@link BlockTags#INCORRECT_FOR_IRON_TOOL}).
     * @param durability         Base tool durability (uses).
     * @param speed              Mining speed multiplier.
     * @param attackDamageBonus  Added on top of each tool's attack baseline.
     * @param enchantability     Enchantment value.
     * @param ingotId            Full item ID of the repair ingredient, e.g.
     *                           {@code "omnitech:steel_ingot"}. Used to generate
     *                           the repair item tag and tag data JSON.
     * @return Fully populated {@link ToolSet} instance.
     */
    public static ToolSet create(
            String material,
            DeferredRegister.Items registry,
            TagKey<Block> incorrectForDrops,
            int durability,
            float speed,
            float attackDamageBonus,
            int enchantability,
            String ingotId) {

        ToolSet set = new ToolSet(material);
        Path resourcesDir = findResourcesDir();

        // Build repair tag and ToolMaterial
        TagKey<Item> repairTag = ItemTags.create(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, "repairs_" + material + "_tools"));
        ToolMaterial toolMaterial = new ToolMaterial(incorrectForDrops, durability, speed, attackDamageBonus, enchantability, repairTag);

        // ── Register tools ────────────────────────────────────────────────────

        // Pickaxe — plain Item with pickaxe properties (vanilla 1.21.4 pattern)
        String pickaxeName = material + "_pickaxe";
        set.tools.put("%_pickaxe",
                registry.registerSimpleItem(pickaxeName, props -> props.pickaxe(toolMaterial, 1.0f, -2.8f)));

        // Shovel — ShovelItem for flattening/campfire-extinguishing behavior
        String shovelName = material + "_shovel";
        @SuppressWarnings("unchecked")
        DeferredItem<Item> shovel = (DeferredItem<Item>) (DeferredItem<?>)
                registry.registerItem(shovelName, props -> new ShovelItem(toolMaterial, 1.5f, -3.0f, props));
        set.tools.put("%_shovel", shovel);

        // Sword — plain Item with sword properties (vanilla 1.21.4 pattern)
        String swordName = material + "_sword";
        set.tools.put("%_sword",
                registry.registerSimpleItem(swordName, props -> props.sword(toolMaterial, 3.0f, -2.4f)));

        // Axe — AxeItem for stripping/scraping behavior
        String axeName = material + "_axe";
        @SuppressWarnings("unchecked")
        DeferredItem<Item> axe = (DeferredItem<Item>) (DeferredItem<?>)
                registry.registerItem(axeName, props -> new AxeItem(toolMaterial, 6.0f, -3.1f, props));
        set.tools.put("%_axe", axe);

        // Hoe — HoeItem for tilling behavior
        String hoeName = material + "_hoe";
        @SuppressWarnings("unchecked")
        DeferredItem<Item> hoe = (DeferredItem<Item>) (DeferredItem<?>)
                registry.registerItem(hoeName, props -> new HoeItem(toolMaterial, -2.0f, -1.0f, props));
        set.tools.put("%_hoe", hoe);

        // ── Generate resources ─────────────────────────────────────────────────
        if (resourcesDir != null) {
            for (String name : List.of(pickaxeName, shovelName, swordName, axeName, hoeName)) {
                generateItemResources(resourcesDir, name);
            }
            updateLangFile(resourcesDir, material);
            generateRepairTag(resourcesDir, material, ingotId, "tools");
        }

        ALL_SETS.add(set);
        return set;
    }

    // ── Creative tab ──────────────────────────────────────────────────────────

    /** Adds all tools in this set to the given creative tab output. */
    public void addToTab(CreativeModeTab.Output output) {
        for (DeferredItem<Item> tool : tools.values()) {
            output.accept(tool.get());
        }
    }

    /** Adds all tools from every registered {@link ToolSet} to the given output. */
    public static void addAllToTab(CreativeModeTab.Output output) {
        for (ToolSet set : ALL_SETS) {
            set.addToTab(output);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Tool item by pattern, e.g. {@code tool("%_pickaxe")}. */
    public DeferredItem<Item> tool(String pattern) { return tools.get(pattern); }

    public String getMaterial() { return material; }

    public Collection<DeferredItem<Item>> allTools() {
        return Collections.unmodifiableCollection(tools.values());
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

    private static void updateLangFile(Path res, String material) {
        String mod = OmniTech.MODID;
        Path langPath = res.resolve("assets/" + mod + "/lang/en_us.json");

        JsonObject lang = new JsonObject();
        if (Files.exists(langPath)) {
            try {
                lang = JsonParser.parseString(
                        Files.readString(langPath, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                OmniTech.LOGGER.warn("[ToolSet] Could not read lang file: {}", e.getMessage());
                return;
            }
        }

        boolean changed = false;
        for (String pattern : PATTERNS) {
            String name = pattern.replace("%", material);
            String key  = "item." + mod + "." + name;
            if (!lang.has(key)) { lang.addProperty(key, prettify(name)); changed = true; }
        }

        if (changed) writeFile(langPath, GSON.toJson(lang));
    }

    private static void generateRepairTag(Path res, String material, String ingotId, String kind) {
        Path tagPath = res.resolve("data/" + OmniTech.MODID + "/tags/item/repairs_" + material + "_" + kind + ".json");
        writeIfAbsent(tagPath,
                "{\n  \"values\": [ \"" + ingotId + "\" ]\n}\n");
    }

    // ── JSON templates ────────────────────────────────────────────────────────

    private static String itemReferenceJson(String mod, String modelPath) {
        return "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \""
                + mod + ":" + modelPath + "\"\n  }\n}\n";
    }

    private static String itemModelJson(String mod, String name) {
        return "{\n  \"parent\": \"minecraft:item/handheld\",\n  \"textures\": {\n    \"layer0\": \""
                + mod + ":item/" + name + "\"\n  }\n}\n";
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
            OmniTech.LOGGER.info("[ToolSet] Generated: {}", path.getFileName());
        } catch (IOException e) {
            OmniTech.LOGGER.warn("[ToolSet] Could not write {}: {}", path, e.getMessage());
        }
    }

    private static Path findResourcesDir() {
        try {
            Path candidate = Paths.get(ToolSet.class
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
