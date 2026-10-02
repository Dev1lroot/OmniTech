/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.pcb;

import java.util.List;

/**
 * What kind of part something is, for counting and matching: the item plus, for a
 * resistor, its colour code — a 1 kΩ and a 10 kΩ resistor are different parts.
 */
public record PartKey(String itemId, List<Integer> bands) {

    public PartKey {
        bands = List.copyOf(bands);
    }

    public static PartKey of(PlacedPart p) {
        return new PartKey(p.itemId(), p.bands());
    }

    /** A part of this kind at a position. */
    public PlacedPart at(int x, int y, int rot) {
        return new PlacedPart(itemId, x, y, rot, bands);
    }

    public java.util.Optional<PartSpec> spec() {
        return at(0, 0, 0).spec();
    }
}
