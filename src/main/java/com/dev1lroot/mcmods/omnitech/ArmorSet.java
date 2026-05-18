/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * ArmorSet: mass-registers a complete armor set (helmet, chestplate, leggings, boots)
 * for a given material, mirroring the {@link MineralSet} pattern.
 *
 * <p>Usage:
 * <pre>{@code
 * ArmorSet STEEL_ARMOR = ArmorSet.create(
 *     "steel",
 *     OmniTechItems.REGISTRY,
 *     20,                          // durability multiplier
 *     new int[]{ 2, 5, 7, 3, 6 }, // defense: boots, legs, chest, helm, body
 *     14,                          // enchantability
 *     0.0f, 0.0f,                  // toughness, knockbackResistance
 *     "omnitech:steel_ingot"
 * );
 *
 * STEEL_ARMOR.piece(ArmorType.HELMET) // DeferredItem<Item>
 * }</pre>
 *
 * <p>In a dev environment, JSON resources (item models, equipment asset, lang entries,
 * repair tags) are auto-generated on first load. Armor layer textures must be placed
 * in {@code textures/entity/equipment/humanoid/} manually (or use armor_factory.py).
 */
public class ArmorSet {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** All ArmorSet instances in order of creation, for creative tab population. */
    public static final List<ArmorSet> ALL_SETS = new ArrayList<>();

    private final String material;
    private final Map<ArmorType, DeferredItem<Item>> pieces = new LinkedHashMap<>();

    private ArmorSet(String material) {
        this.material = material;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Create and register a complete armor set.
     *
     * @param material             Material name, e.g. {@code "steel"}.
     * @param registry             {@link DeferredRegister.Items} to register into.
     * @param durabilityMultiplier Multiplied by per-slot base values to give final durability.
     * @param defense              Defense values: {@code [boots, leggings, chestplate, helmet, body]}.
     * @param enchantability       Enchantment value.
     * @param toughness            Armor toughness (extra damage reduction).
     * @param knockbackResistance  Knockback resistance (0.0 – 1.0).
     * @param ingotId              Full item ID of the repair ingredient, e.g.
     *                             {@code "omnitech:steel_ingot"}. Used to generate
     *                             the repair item tag and tag data JSON.
     * @return Fully populated {@link ArmorSet} instance.
     */
    public static ArmorSet create(
            String material,
            DeferredRegister.Items registry,
            int durabilityMultiplier,
            int[] defense,
            int enchantability,
            float toughness,
            float knockbackResistance,
            String ingotId) {

        ArmorSet set = new ArmorSet(material);
        Path resourcesDir = findResourcesDir();

        // Build repair tag and equipment asset key
        TagKey<net.minecraft.world.item.Item> repairTag = ItemTags.create(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, "repairs_" + material + "_armor"));
        ResourceKey<EquipmentAsset> assetKey = ResourceKey.create(
                EquipmentAssets.ROOT_ID,
                Identifier.fromNamespaceAndPath(OmniTech.MODID, material + "_armor"));

        // Build ArmorMaterial
        ArmorMaterial armorMaterial = new ArmorMaterial(
                durabilityMultiplier,
                Maps.newEnumMap(Map.of(
                        ArmorType.BOOTS,      defense[0],
                        ArmorType.LEGGINGS,   defense[1],
                        ArmorType.CHESTPLATE, defense[2],
                        ArmorType.HELMET,     defense[3],
                        ArmorType.BODY,       defense[4]
                )),
                enchantability,
                SoundEvents.ARMOR_EQUIP_IRON,
                toughness,
                knockbackResistance,
                repairTag,
                assetKey
        );

        // ── Register armor pieces ─────────────────────────────────────────────

        for (ArmorType type : new ArmorType[]{ ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS }) {
            String pieceName = material + "_" + type.getName();
            DeferredItem<Item> piece = registry.registerSimpleItem(
                    pieceName, props -> props.humanoidArmor(armorMaterial, type));
            set.pieces.put(type, piece);

            if (resourcesDir != null) {
                generateItemResources(resourcesDir, pieceName);
            }
        }

        // ── Generate shared resources ──────────────────────────────────────────
        if (resourcesDir != null) {
            updateLangFile(resourcesDir, material);
            generateEquipmentAsset(resourcesDir, material);
            generateRepairTag(resourcesDir, material, ingotId, "armor");
        }

        ALL_SETS.add(set);
        return set;
    }

    // ── Creative tab ──────────────────────────────────────────────────────────

    /** Adds all armor pieces in this set to the given creative tab output. */
    public void addToTab(CreativeModeTab.Output output) {
        for (DeferredItem<Item> piece : pieces.values()) {
            output.accept(piece.get());
        }
    }

    /** Adds all armor pieces from every registered {@link ArmorSet} to the given output. */
    public static void addAllToTab(CreativeModeTab.Output output) {
        for (ArmorSet set : ALL_SETS) {
            set.addToTab(output);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Armor piece by type, e.g. {@code piece(ArmorType.HELMET)}. */
    public DeferredItem<Item> piece(ArmorType type) { return pieces.get(type); }

    public String getMaterial() { return material; }

    public Collection<DeferredItem<Item>> allPieces() {
        return Collections.unmodifiableCollection(pieces.values());
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
                OmniTech.LOGGER.warn("[ArmorSet] Could not read lang file: {}", e.getMessage());
                return;
            }
        }

        boolean changed = false;
        for (ArmorType type : new ArmorType[]{ ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS }) {
            String name = material + "_" + type.getName();
            String key  = "item." + mod + "." + name;
            if (!lang.has(key)) { lang.addProperty(key, prettify(name)); changed = true; }
        }

        if (changed) writeFile(langPath, GSON.toJson(lang));
    }

    /** Generates the equipment asset JSON defining the humanoid armor layer textures. */
    private static void generateEquipmentAsset(Path res, String material) {
        String mod = OmniTech.MODID;
        Path assetPath = res.resolve("assets/" + mod + "/equipment/" + material + "_armor.json");
        String content = "{\n"
                + "  \"layers\": {\n"
                + "    \"humanoid\": [\n"
                + "      { \"texture\": \"" + mod + ":" + material + "_armor\" }\n"
                + "    ],\n"
                + "    \"humanoid_leggings\": [\n"
                + "      { \"texture\": \"" + mod + ":" + material + "_armor_leggings\" }\n"
                + "    ]\n"
                + "  }\n"
                + "}\n";
        writeIfAbsent(assetPath, content);
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
        return "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \""
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
            OmniTech.LOGGER.info("[ArmorSet] Generated: {}", path.getFileName());
        } catch (IOException e) {
            OmniTech.LOGGER.warn("[ArmorSet] Could not write {}: {}", path, e.getMessage());
        }
    }

    private static Path findResourcesDir() {
        try {
            Path candidate = Paths.get(ArmorSet.class
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
