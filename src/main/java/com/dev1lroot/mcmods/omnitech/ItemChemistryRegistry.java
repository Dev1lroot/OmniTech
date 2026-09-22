/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.chemistry.CompoundNaming;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real-world chemical composition of vanilla and modded items alike, populated by
 * {@link ChemistryCompositionLoader} from {@code data/omnitech/chemistry/<namespace>/<item>.json}.
 * Keyed by full item id ({@code "namespace:path"}), same convention as
 * {@link FluidChemistryRegistry}. Purely additive lookup data — an item with no entry here simply
 * has no composition shown in its tooltip.
 */
public final class ItemChemistryRegistry {

    private ItemChemistryRegistry() {}

    /**
     * One compound present in an item, in millimoles (mmol) — see {@link ChemistryCompositionLoader}
     * for how that amount is meant to be derived from the item's real-world volume/mass.
     *
     * @param name display name shown in the tooltip; falls back to {@link CompoundNaming#nameOf}
     *             when absent, but most entries should set it explicitly since the auto-namer only
     *             understands organic chemistry (no minerals/metals).
     */
    public record CompoundAmount(String smiles, String name, double amountMmol) {
        public String displayName() {
            return name != null && !name.isBlank() ? name : CompoundNaming.nameOf(smiles);
        }
    }

    private static final Map<String, List<CompoundAmount>> BY_ITEM_ID = new HashMap<>();

    static void register(String itemId, List<CompoundAmount> compounds) {
        BY_ITEM_ID.put(itemId, List.copyOf(compounds));
    }

    /** The compounds registered for {@code itemId} ("namespace:path"), or an empty list if none. */
    public static List<CompoundAmount> get(String itemId) {
        return BY_ITEM_ID.getOrDefault(itemId, List.of());
    }
}
