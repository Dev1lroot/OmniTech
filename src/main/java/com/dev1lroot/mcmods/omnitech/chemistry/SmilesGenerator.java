/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry;

import java.util.*;

/**
 * Writes a molecule's connectivity out as a SMILES-style structural code — the same "atoms and
 * bonds, not pixels" idea as {@link StructureLayout}, just as compact text instead of a picture.
 * Implicit hydrogens work exactly like real SMILES organic-subset notation: never written,
 * always inferred from standard valence (see {@link Molecule#implicitH}), which is precisely
 * how this mod already models them — no extra bookkeeping needed to bridge the two.
 *
 * <p>Single bonds are unmarked, {@code =} is double, {@code #} is triple. Branches are
 * parenthesized. Ring closures (this generator handles any number of rings, not just the one
 * {@link IupacNamer} can name) are marked with a shared digit at both ends of the closing bond,
 * exactly like real SMILES. Disconnected fragments are joined with {@code .}.
 *
 * @see SmilesParser the inverse direction
 */
public final class SmilesGenerator {

    private SmilesGenerator() {}

    public static String generate(Molecule m) {
        if (m.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        Set<Integer> globalVisited = new HashSet<>();
        for (Atom a : m.atoms()) {
            if (globalVisited.contains(a.id())) continue;
            parts.add(generateComponent(m, a.id(), globalVisited));
        }
        return String.join(".", parts);
    }

    private static String generateComponent(Molecule m, int rootId, Set<Integer> globalVisited) {
        Set<Integer> backEdgeScan = new HashSet<>();
        Set<Long> ringBondKeys = new HashSet<>();
        findRingBonds(m, rootId, -1, backEdgeScan, ringBondKeys);

        Map<Long, Integer> ringDigit = new HashMap<>();
        int counter = 1;
        for (long key : ringBondKeys) ringDigit.put(key, counter++);

        StringBuilder sb = new StringBuilder();
        Set<Integer> visited = new HashSet<>();
        writeAtom(m, rootId, -1, sb, visited, ringBondKeys, ringDigit);
        globalVisited.addAll(visited);
        return sb.toString();
    }

    private static void findRingBonds(Molecule m, int atomId, int parentId, Set<Integer> visited, Set<Long> ringBonds) {
        visited.add(atomId);
        for (int n : m.neighbors(atomId)) {
            if (n == parentId) continue;
            long key = bondKey(atomId, n);
            if (visited.contains(n)) ringBonds.add(key);
            else findRingBonds(m, n, atomId, visited, ringBonds);
        }
    }

    private static void writeAtom(Molecule m, int atomId, int parentId, StringBuilder sb, Set<Integer> visited,
            Set<Long> ringBonds, Map<Long, Integer> ringDigit) {
        visited.add(atomId);
        sb.append(m.atom(atomId).element());

        for (int n : m.neighbors(atomId)) {
            if (n == parentId) continue;
            long key = bondKey(atomId, n);
            if (ringBonds.contains(key)) {
                sb.append(bondSymbol(m.bondBetween(atomId, n).order()));
                sb.append(ringDigit.get(key));
            }
        }

        List<Integer> children = new ArrayList<>();
        for (int n : m.neighbors(atomId)) {
            if (n == parentId) continue;
            if (ringBonds.contains(bondKey(atomId, n))) continue;
            if (!visited.contains(n)) children.add(n);
        }

        for (int i = 0; i < children.size(); i++) {
            int child = children.get(i);
            StringBuilder branch = new StringBuilder();
            branch.append(bondSymbol(m.bondBetween(atomId, child).order()));
            writeAtom(m, child, atomId, branch, visited, ringBonds, ringDigit);
            boolean isLast = i == children.size() - 1;
            if (isLast) sb.append(branch);
            else sb.append('(').append(branch).append(')');
        }
    }

    private static long bondKey(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xffffffffL);
    }

    private static String bondSymbol(int order) {
        return switch (order) {
            case 2 -> "=";
            case 3 -> "#";
            default -> "";
        };
    }
}
