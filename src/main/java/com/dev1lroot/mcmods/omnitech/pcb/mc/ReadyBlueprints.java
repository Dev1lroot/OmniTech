/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.pcb.mc;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import com.dev1lroot.mcmods.omnitech.pcb.PlacedPart;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * The ready-made blueprints from {@code data/omnitech/pcb_blueprint/*.json}: a drawn
 * board, a reference part placement and the test bench it is built to pass. They are
 * crafted as normal recipes; this loader exists so JEI can show the full chain.
 */
public final class ReadyBlueprints {

    public record Ready(String id, String bench, PcbDesign design, List<PlacedPart> parts) {
        /** The design on a given item, with the reference placement attached. */
        public ItemStack stack(Supplier<? extends Item> item) {
            ItemStack s = new ItemStack(item.get());
            s.set(OmniTechDataComponents.PCB_DESIGN.get(), design);
            s.set(OmniTechDataComponents.PCB_PARTS.get(), parts);
            return s;
        }

        public ItemStack blueprint() { return stack(OmniTechItems.PCB_BLUEPRINT); }
        public ItemStack photomask() { return stack(OmniTechItems.PHOTOMASK); }
        public ItemStack exposed()   { return stack(OmniTechItems.EXPOSED_CIRCUIT_BOARD); }
        public ItemStack board()     { return stack(OmniTechItems.PRINTED_CIRCUIT_BOARD); }
    }

    private static final Gson GSON = new Gson();
    private static final String DATA_PATH = "data/omnitech/pcb_blueprint";
    private static final List<Ready> ALL = new ArrayList<>();

    private ReadyBlueprints() {}

    public static void loadAll() {
        ALL.clear();
        TreeMap<String, Ready> sorted = new TreeMap<>();
        ModList.get().getModFileById(OmniTech.MODID).getFile().getContents()
                .visitContent(DATA_PATH, (path, resource) -> {
                    if (!path.endsWith(".json")) return;
                    String id = path.substring(DATA_PATH.length() + 1).replace(".json", "");
                    if (id.contains("/")) return;
                    try (var reader = resource.bufferedReader()) {
                        JsonObject j = GSON.fromJson(reader, JsonObject.class);
                        PcbDesign design = PcbCodecs.DESIGN.parse(JsonOps.INSTANCE, j.get("design")).getOrThrow();
                        List<PlacedPart> parts = PcbCodecs.PARTS.parse(JsonOps.INSTANCE, j.get("parts")).getOrThrow();
                        sorted.put(id, new Ready(id, j.get("bench").getAsString(), design, parts));
                    } catch (Exception e) {
                        OmniTech.LOGGER.error("[ReadyBlueprints] Failed to parse '{}': {}", path, e.getMessage());
                    }
                });
        ALL.addAll(sorted.values());
        OmniTech.LOGGER.info("[ReadyBlueprints] {} ready-made PCB blueprints loaded", ALL.size());
    }

    public static List<Ready> all() { return ALL; }
}
