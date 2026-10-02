/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.pcb;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A component placed on a board: centre cell plus quarter-turn rotation.
 *
 * @param bands colour code of a resistor ({@link ResistorCode.Band} ordinals), empty otherwise
 */
public record PlacedPart(String itemId, int x, int y, int rot, List<Integer> bands) {

    public PlacedPart {
        bands = List.copyOf(bands);
    }

    public PlacedPart(String itemId, int x, int y, int rot) {
        this(itemId, x, y, rot, List.of());
    }

    /** The electrical spec; a resistor gets the value and tolerance its bands encode. */
    public Optional<PartSpec> spec() {
        return PartSpec.of(itemId).map(s -> {
            if (s.kind() != PartSpec.Kind.RESISTOR) return s;
            ResistorCode.Value v = ResistorCode.decodeCodes(bands);
            return v == null ? s : s.withValue(v.ohms(), v.tolerance());
        });
    }

    /** Same kind of part as {@code other} (item and colour code), ignoring position. */
    public boolean sameKind(String otherItem, List<Integer> otherBands) {
        return itemId.equals(otherItem) && bands.equals(otherBands);
    }

    /** Absolute pin cells, in the spec's pin order. Empty when the item is not a part. */
    public List<int[]> pinCells() {
        List<int[]> out = new ArrayList<>();
        spec().ifPresent(s -> {
            for (int[] p : s.pins()) {
                int[] r = PartSpec.rotate(p[0], p[1], rot);
                out.add(new int[]{x + r[0], y + r[1]});
            }
        });
        return out;
    }

    public List<int[]> bodyCells() {
        List<int[]> out = new ArrayList<>();
        spec().ifPresent(s -> {
            for (int[] p : s.body()) {
                int[] r = PartSpec.rotate(p[0], p[1], rot);
                out.add(new int[]{x + r[0], y + r[1]});
            }
        });
        return out;
    }

    /** Every cell this part occupies (pins + body). */
    public List<int[]> footprint() {
        List<int[]> all = new ArrayList<>(pinCells());
        all.addAll(bodyCells());
        return all;
    }

    /**
     * Whether {@code part} fits on {@code design} next to {@code others}: every leg in a
     * pad, the body over board, and no cell shared with another part.
     */
    public static boolean canPlace(PcbDesign design, List<PlacedPart> others, PlacedPart part) {
        if (part.spec().isEmpty()) return false;
        for (int[] p : part.pinCells()) if (!design.isPad(p[0], p[1])) return false;
        for (int[] b : part.bodyCells()) if (!design.isBoard(b[0], b[1])) return false;
        for (PlacedPart o : others) {
            for (int[] a : o.footprint())
                for (int[] b : part.footprint())
                    if (a[0] == b[0] && a[1] == b[1]) return false;
        }
        return true;
    }

    /** Index of the part covering the cell, or -1. */
    public static int partAt(List<PlacedPart> parts, int x, int y) {
        for (int i = 0; i < parts.size(); i++)
            for (int[] c : parts.get(i).footprint())
                if (c[0] == x && c[1] == y) return i;
        return -1;
    }
}
