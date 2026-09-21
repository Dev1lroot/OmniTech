/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.*;

/**
 * A structural formula: heavy atoms placed on a 2D canvas, connected by bonds. Hydrogen is
 * never explicit — see {@link #implicitH(int)}.
 */
public record Molecule(List<Atom> atoms, List<Bond> bonds) {

    public static final Molecule EMPTY = new Molecule(List.of(), List.of());

    public static final Codec<Molecule> CODEC = RecordCodecBuilder.create(i -> i.group(
            Atom.CODEC.listOf().fieldOf("atoms").forGetter(Molecule::atoms),
            Bond.CODEC.listOf().fieldOf("bonds").forGetter(Molecule::bonds)
    ).apply(i, Molecule::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Molecule> STREAM_CODEC = StreamCodec.composite(
            Atom.STREAM_CODEC.apply(ByteBufCodecs.list()), Molecule::atoms,
            Bond.STREAM_CODEC.apply(ByteBufCodecs.list()), Molecule::bonds,
            Molecule::new);

    // ── Basic lookups ────────────────────────────────────────────────────────

    public Atom atom(int id) {
        for (Atom a : atoms) if (a.id() == id) return a;
        return null;
    }

    public List<Bond> bondsOf(int atomId) {
        List<Bond> out = new ArrayList<>();
        for (Bond b : bonds) if (b.touches(atomId)) out.add(b);
        return out;
    }

    public List<Integer> neighbors(int atomId) {
        List<Integer> out = new ArrayList<>();
        for (Bond b : bonds) if (b.touches(atomId)) out.add(b.other(atomId));
        return out;
    }

    public Bond bondBetween(int a, int b) {
        for (Bond bond : bonds) {
            if ((bond.a() == a && bond.b() == b) || (bond.a() == b && bond.b() == a)) return bond;
        }
        return null;
    }

    /** Sum of bond orders touching this atom (its current "used" valence). */
    public int usedValence(int atomId) {
        int sum = 0;
        for (Bond b : bonds) if (b.touches(atomId)) sum += b.order();
        return sum;
    }

    /** Implicit hydrogen count: standard valence minus bonds already drawn, floored at 0. */
    public int implicitH(int atomId) {
        Atom a = atom(atomId);
        if (a == null) return 0;
        int valence = Atom.standardValence(a.element());
        if (valence < 0) return 0;
        return Math.max(0, valence - usedValence(atomId));
    }

    public boolean isEmpty() { return atoms.isEmpty(); }

    // ── Molecular formula (Hill system: C, then H, then the rest alphabetically) ──────

    public String molecularFormula() {
        Map<String, Integer> counts = new TreeMap<>();
        int hydrogens = 0;
        for (Atom a : atoms) {
            counts.merge(a.element(), 1, Integer::sum);
            hydrogens += implicitH(a.id());
        }
        StringBuilder sb = new StringBuilder();
        if (counts.containsKey("C")) {
            sb.append("C");
            appendCount(sb, counts.remove("C"));
            if (hydrogens > 0) { sb.append("H"); appendCount(sb, hydrogens); }
        } else if (hydrogens > 0) {
            sb.append("H"); appendCount(sb, hydrogens);
        }
        for (var e : counts.entrySet()) {
            sb.append(e.getKey());
            appendCount(sb, e.getValue());
        }
        return sb.isEmpty() ? "—" : sb.toString();
    }

    private static void appendCount(StringBuilder sb, int count) {
        if (count > 1) sb.append(count);
    }

    // ── Connectivity / rings ─────────────────────────────────────────────────

    /** True if every atom can reach every other atom through bonds (ignoring bond order). */
    public boolean isConnected() {
        if (atoms.isEmpty()) return true;
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(atoms.get(0).id());
        while (!stack.isEmpty()) {
            int cur = stack.pop();
            if (!seen.add(cur)) continue;
            for (int n : neighbors(cur)) if (!seen.contains(n)) stack.push(n);
        }
        return seen.size() == atoms.size();
    }

    /** Cyclomatic number of the whole graph: edges − vertices + components. 0 = acyclic (a tree/forest). */
    public int cyclomaticNumber() {
        return bonds.size() - atoms.size() + countComponents();
    }

    private int countComponents() {
        Set<Integer> unvisited = new HashSet<>();
        for (Atom a : atoms) unvisited.add(a.id());
        int components = 0;
        while (!unvisited.isEmpty()) {
            components++;
            Deque<Integer> stack = new ArrayDeque<>();
            int start = unvisited.iterator().next();
            stack.push(start);
            unvisited.remove(start);
            while (!stack.isEmpty()) {
                int cur = stack.pop();
                for (int n : neighbors(cur)) {
                    if (unvisited.remove(n)) stack.push(n);
                }
            }
        }
        return components;
    }

    /**
     * If this molecule has exactly one independent ring (cyclomatic number 1) and the whole
     * thing is connected, returns the ring's atom ids in traversal order. Otherwise {@code null}
     * — used to bound the namer to genuinely monocyclic structures rather than guessing at
     * fused/bridged/spiro systems.
     */
    public List<Integer> singleRingAtomsInOrder() {
        if (!isConnected() || cyclomaticNumber() != 1 || atoms.isEmpty()) return null;

        // DFS spanning tree from an arbitrary root; the one back edge closes the ring.
        Map<Integer, Integer> parent = new HashMap<>();
        Set<Integer> visited = new HashSet<>();
        int root = atoms.get(0).id();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(root);
        visited.add(root);
        Bond backEdge = null;

        while (!stack.isEmpty() && backEdge == null) {
            int cur = stack.pop();
            for (Bond b : bondsOf(cur)) {
                int n = b.other(cur);
                if (n == parent.getOrDefault(cur, -1)) continue; // don't walk back down the tree edge
                if (!visited.contains(n)) {
                    visited.add(n);
                    parent.put(n, cur);
                    stack.push(n);
                } else {
                    backEdge = b;
                    break;
                }
            }
        }
        if (backEdge == null) return null;

        // Walk both endpoints of the back edge up to their common ancestor via parent pointers.
        List<Integer> pathA = new ArrayList<>();
        for (Integer at = backEdge.a(); at != null; at = parent.get(at)) pathA.add(at);
        List<Integer> pathB = new ArrayList<>();
        for (Integer at = backEdge.b(); at != null; at = parent.get(at)) pathB.add(at);

        Set<Integer> inA = new HashSet<>(pathA);
        int lca = -1;
        for (int at : pathB) if (inA.contains(at)) { lca = at; break; }
        if (lca == -1) return null;

        List<Integer> ring = new ArrayList<>();
        for (int at : pathA) { ring.add(at); if (at == lca) break; }
        List<Integer> tail = new ArrayList<>();
        for (int at : pathB) { if (at == lca) break; tail.add(at); }
        Collections.reverse(tail);
        ring.addAll(tail);
        return ring;
    }

    /** True if {@code ring} is a 6-membered all-carbon ring with 3 alternating double bonds (benzene). */
    public boolean isAromaticBenzeneRing(List<Integer> ring) {
        if (ring == null || ring.size() != 6) return false;
        for (int id : ring) {
            Atom a = atom(id);
            if (a == null || !a.element().equals("C")) return false;
        }
        int doubles = 0;
        for (int i = 0; i < 6; i++) {
            Bond b = bondBetween(ring.get(i), ring.get((i + 1) % 6));
            if (b == null) return false;
            if (b.order() == 2) doubles++;
            else if (b.order() != 1) return false;
        }
        return doubles == 3;
    }
}
