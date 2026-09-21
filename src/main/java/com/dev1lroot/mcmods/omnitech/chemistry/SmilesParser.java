/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads SMILES back into a {@link Molecule}. Positions in the result are all (0,0) — run it
 * through {@link StructureLayout#layout} to get an actual picture, same as the Structure Table
 * does for a freshly-sketched molecule.
 *
 * <p>Beyond what {@link SmilesGenerator} itself writes (plain single/double/triple bonds,
 * branches, ring closures, disjoint fragments), this also accepts two things real-world SMILES
 * (copy-pasted from Wikipedia, PubChem, etc.) commonly use that this mod doesn't otherwise model:
 * <ul>
 *   <li><b>Bracket atoms</b> like {@code [C@@H]} or {@code [nH+]} — isotope, stereochemistry,
 *       explicit hydrogen count and charge are all parsed-past and discarded; only the element
 *       survives. This mod has no stereochemistry or charge model to preserve them in.</li>
 *   <li><b>Lowercase aromatic atoms</b> like {@code c1ccccc1} for a benzene ring — normalized to
 *       their uppercase element, then {@link #kekulize} converts the ring to explicit alternating
 *       single/double bonds afterward (a real, valid Kekulé structure — just not the aromatic
 *       bond order this mod doesn't have a third option for). Only handled for the one ring size
 *       this whole feature supports being even-membered and fully aromatic; anything the that
 *       doesn't cleanly apply to is left as literal single bonds rather than guessed at.</li>
 * </ul>
 */
public final class SmilesParser {

    private static final Set<String> ELEMENTS = Set.of("C", "N", "O", "S", "P", "F", "Cl", "Br", "I");

    private SmilesParser() {}

    public static class ParseException extends RuntimeException {
        public ParseException(String message) { super(message); }
    }

    public static Molecule parse(String code) {
        if (code == null || code.isBlank()) return Molecule.EMPTY;
        String trimmed = code.trim();

        List<Atom> atoms = new ArrayList<>();
        List<Bond> bonds = new ArrayList<>();
        int[] nextId = { 0 };
        Set<Integer> aromaticAtomIds = new HashSet<>();

        for (String component : trimmed.split("\\.")) {
            if (component.isBlank()) continue;
            Map<Integer, int[]> ringOpen = new HashMap<>();
            int[] pos = { 0 };
            parseChain(component, pos, -1, 1, atoms, bonds, nextId, ringOpen, aromaticAtomIds);
            if (pos[0] != component.length()) {
                throw new ParseException("Unexpected character at position " + pos[0] + " in \"" + component + "\"");
            }
            if (!ringOpen.isEmpty()) {
                throw new ParseException("Unclosed ring bond number(s): " + ringOpen.keySet());
            }
        }
        if (atoms.isEmpty()) throw new ParseException("No atoms found");
        return kekulize(new Molecule(atoms, bonds), aromaticAtomIds);
    }

    private static int parseChain(String s, int[] pos, int prevAtomId, int bondOrderToPrev,
            List<Atom> atoms, List<Bond> bonds, int[] nextId, Map<Integer, int[]> ringOpen,
            Set<Integer> aromaticAtomIds) {
        if (pos[0] >= s.length()) throw new ParseException("Expected an atom");

        boolean[] aromatic = { false };
        String element = readElement(s, pos, aromatic);
        int atomId = nextId[0]++;
        atoms.add(new Atom(atomId, element, 0, 0));
        if (aromatic[0]) aromaticAtomIds.add(atomId);
        if (prevAtomId != -1) bonds.add(new Bond(prevAtomId, atomId, bondOrderToPrev));

        // Ring closures: an optional bond symbol immediately followed by a single digit.
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            int order = 1;
            int consumeBondSymbol = 0;
            if (c == '=' || c == '#') {
                if (pos[0] + 1 >= s.length() || !Character.isDigit(s.charAt(pos[0] + 1))) break;
                order = (c == '=') ? 2 : 3;
                consumeBondSymbol = 1;
                c = s.charAt(pos[0] + 1);
            }
            if (!Character.isDigit(c)) break;
            pos[0] += consumeBondSymbol + 1;
            int digit = c - '0';
            if (ringOpen.containsKey(digit)) {
                int[] open = ringOpen.remove(digit);
                bonds.add(new Bond(open[0], atomId, Math.max(open[1], order)));
            } else {
                ringOpen.put(digit, new int[] { atomId, order });
            }
        }

        // Branches attached to this atom.
        while (pos[0] < s.length() && s.charAt(pos[0]) == '(') {
            pos[0]++;
            int branchOrder = 1;
            if (pos[0] < s.length() && s.charAt(pos[0]) == '=') { branchOrder = 2; pos[0]++; }
            else if (pos[0] < s.length() && s.charAt(pos[0]) == '#') { branchOrder = 3; pos[0]++; }
            parseChain(s, pos, atomId, branchOrder, atoms, bonds, nextId, ringOpen, aromaticAtomIds);
            if (pos[0] >= s.length() || s.charAt(pos[0]) != ')') throw new ParseException("Missing ')'");
            pos[0]++;
        }

        // Linear continuation of the chain.
        if (pos[0] < s.length() && s.charAt(pos[0]) != ')') {
            int order = 1;
            if (s.charAt(pos[0]) == '=') { order = 2; pos[0]++; }
            else if (s.charAt(pos[0]) == '#') { order = 3; pos[0]++; }
            parseChain(s, pos, atomId, order, atoms, bonds, nextId, ringOpen, aromaticAtomIds);
        }

        return atomId;
    }

    private static String readElement(String s, int[] pos, boolean[] aromaticOut) {
        if (s.charAt(pos[0]) == '[') return readBracketElement(s, pos, aromaticOut);

        if (pos[0] + 1 < s.length()) {
            String two = s.substring(pos[0], pos[0] + 2);
            if (ELEMENTS.contains(two)) { pos[0] += 2; return two; }
        }

        char c = s.charAt(pos[0]);
        String one = String.valueOf(c);
        if (ELEMENTS.contains(one)) { pos[0]++; return one; }

        String upper = one.toUpperCase();
        if (Character.isLowerCase(c) && ELEMENTS.contains(upper)) {
            pos[0]++;
            aromaticOut[0] = true;
            return upper;
        }
        throw new ParseException("Unknown element at position " + pos[0] + ": '" + c + "'");
    }

    /** {@code [<isotope digits>?<element><anything else — stereo/H-count/charge/class, discarded>]} */
    private static String readBracketElement(String s, int[] pos, boolean[] aromaticOut) {
        int end = s.indexOf(']', pos[0]);
        if (end < 0) throw new ParseException("Unclosed '[' at position " + pos[0]);
        String inner = s.substring(pos[0] + 1, end);
        pos[0] = end + 1;

        int i = 0;
        while (i < inner.length() && Character.isDigit(inner.charAt(i))) i++; // skip isotope number
        if (i >= inner.length()) throw new ParseException("Empty bracket atom: [" + inner + "]");

        if (i + 1 < inner.length()) {
            String two = inner.substring(i, i + 2);
            if (ELEMENTS.contains(two)) return two;
        }
        char c = inner.charAt(i);
        String one = String.valueOf(c);
        if (ELEMENTS.contains(one)) return one;

        String upper = one.toUpperCase();
        if (Character.isLowerCase(c) && ELEMENTS.contains(upper)) {
            aromaticOut[0] = true;
            return upper;
        }
        throw new ParseException("Unsupported bracket atom: [" + inner + "]");
    }

    /**
     * If every atom of the molecule's one ring came from lowercase aromatic notation and the
     * ring has an even number of atoms, rewrites its bonds to alternate single/double — a real
     * Kekulé structure for that aromatic ring. Anything else (odd ring size, mixed/no aromatic
     * atoms, more than one ring) is left exactly as parsed.
     */
    private static Molecule kekulize(Molecule m, Set<Integer> aromaticAtomIds) {
        if (aromaticAtomIds.isEmpty() || m.cyclomaticNumber() != 1) return m;
        List<Integer> ring = m.singleRingAtomsInOrder();
        if (ring == null || ring.size() % 2 != 0 || !aromaticAtomIds.containsAll(ring)) return m;

        int n = ring.size();
        List<Bond> newBonds = new ArrayList<>();
        for (Bond b : m.bonds()) {
            int ringEdgeIndex = ringEdgeIndexOf(ring, b.a(), b.b());
            if (ringEdgeIndex < 0) {
                newBonds.add(b);
            } else {
                newBonds.add(new Bond(b.a(), b.b(), ringEdgeIndex % 2 == 0 ? 2 : 1));
            }
        }
        return new Molecule(m.atoms(), newBonds);
    }

    /** Index of the ring edge (ring[i] — ring[i+1]) matching this bond, or -1 if it's not a ring edge. */
    private static int ringEdgeIndexOf(List<Integer> ring, int a, int b) {
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            int x = ring.get(i), y = ring.get((i + 1) % n);
            if ((x == a && y == b) || (x == b && y == a)) return i;
        }
        return -1;
    }
}
