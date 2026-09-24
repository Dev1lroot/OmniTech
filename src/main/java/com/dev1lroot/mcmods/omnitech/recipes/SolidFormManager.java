/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The item a pure solid ingredient turns into once it has been separated out of a mixture (by
 * the Manual Centrifuge): e.g. every 20 mB of undissolved {@code omnitech:yeast} is one Yeast.
 * An ingredient without a solid form comes out as a single-ingredient Mixture Dust instead.
 *
 * <p>JSON layout (in {@code data/omnitech/machine_recipe/solid_form/}):
 * <pre>{@code
 * { "fluid": "omnitech:yeast", "item": "omnitech:yeast", "amount": 20 }
 * }</pre>
 */
public class SolidFormManager {

    /** {@code amount} mB of the undissolved {@code fluid} make one {@code item}. */
    public record SolidForm(Identifier fluidId, Identifier itemId, int amount) {
        public @Nullable Fluid fluid() { return BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null); }
        public @Nullable Item item()   { return BuiltInRegistries.ITEM.getOptional(itemId).orElse(null); }
        public ItemStack stack()       { Item i = item(); return i == null ? ItemStack.EMPTY : new ItemStack(i); }
    }

    private static final Gson GSON = new Gson();
    private static final String PATH = "machine_recipe/solid_form";

    private static volatile List<SolidForm> forms = List.of();

    public static void loadRecipes(ResourceManager rm) {
        List<SolidForm> loaded = new ArrayList<>();
        try {
            Map<Identifier, Resource> resources = rm.listResources(PATH, id -> id.getPath().endsWith(".json"));
            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (!id.getNamespace().equals(OmniTech.MODID)) continue;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue().open()))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    loaded.add(new SolidForm(
                            Identifier.parse(json.get("fluid").getAsString()),
                            Identifier.parse(json.get("item").getAsString()),
                            Math.max(1, json.get("amount").getAsInt())));
                } catch (Exception e) {
                    OmniTech.LOGGER.error("Failed to load solid form: {}", id, e);
                }
            }
        } catch (Exception e) {
            OmniTech.LOGGER.error("Failed to scan {}/", PATH, e);
        }
        forms = List.copyOf(loaded);
        OmniTech.LOGGER.info("Loaded {} solid form(s)", forms.size());
    }

    public static List<SolidForm> getAll() { return forms; }

    /** The solid form of {@code fluid}, or {@code null} if it has none (or its item is missing). */
    public static @Nullable SolidForm find(Fluid fluid) {
        for (SolidForm f : forms) if (f.fluid() == fluid && f.item() != null) return f;
        return null;
    }
}
