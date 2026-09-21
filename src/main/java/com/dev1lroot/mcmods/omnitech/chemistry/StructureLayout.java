/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry;

import java.util.*;

/**
 * Regenerates a clean, deterministic 2D depiction from a molecule's connectivity alone —
 * the same separation of concerns real cheminformatics tools use: the atoms/bonds list
 * <i>is</i> the structure ("structure code"), and on-screen positions are just one possible
 * rendering of it, never the source of truth. What the player free-clicks together on the
 * Structure Table's canvas is convenient for sketching, but it's the topology that gets
 * printed — {@link #layout} is run once at print time to turn that topology back into a
 * picture, so what comes out is always the same tidy drawing regardless of where the pixels
 * happened to land while sketching.
 *
 * <p>Chains are drawn as the classic skeletal-formula zigzag (each bond alternating ±30° off
 * horizontal — a 60° turn at every atom, i.e. a hexagonal/triangular grid), and the one ring
 * this whole feature supports ({@link Molecule#singleRingAtomsInOrder()}) is drawn as a true
 * regular polygon — a hexagon for a 6-ring, matching how benzene/cyclohexane are always drawn.
 */
public final class StructureLayout {

    private static final float BOND_LEN = 24f;

    private StructureLayout() {}

    public static Molecule layout(Molecule topology) {
        if (topology.isEmpty()) return topology;

        Map<Integer, float[]> pos = new HashMap<>();
        Set<Integer> visited = new HashSet<>();
        float nextComponentX = 0f;

        for (Atom a : topology.atoms()) {
            if (visited.contains(a.id())) continue;
            nextComponentX = layoutComponent(topology, a.id(), pos, visited, nextComponentX) + BOND_LEN * 2;
        }

        List<Atom> result = new ArrayList<>();
        for (Atom a : topology.atoms()) {
            float[] p = pos.getOrDefault(a.id(), new float[] { 0f, 0f });
            result.add(new Atom(a.id(), a.element(), p[0], p[1]));
        }
        return recenter(new Molecule(result, topology.bonds()));
    }

    // ── One connected component ──────────────────────────────────────────────

    private static float layoutComponent(Molecule m, int startId, Map<Integer, float[]> pos,
            Set<Integer> visited, float startX) {
        List<Integer> componentAtoms = componentOf(m, startId);
        Molecule sub = subMolecule(m, componentAtoms);
        List<Integer> ring = sub.cyclomaticNumber() == 1 ? sub.singleRingAtomsInOrder() : null;

        if (ring == null) {
            return layoutChain(m, startId, -1, startX, 0f, -Math.PI / 6, pos, visited);
        }

        float maxX = layoutRing(ring, pos, visited, startX);
        for (int ringAtom : ring) {
            float[] rp = pos.get(ringAtom);
            float[] center = ringCenter(pos, ring);
            double outward = Math.atan2(rp[1] - center[1], rp[0] - center[0]);
            for (int n : m.neighbors(ringAtom)) {
                if (ring.contains(n) || visited.contains(n)) continue;
                float nx = rp[0] + (float) (BOND_LEN * Math.cos(outward));
                float ny = rp[1] + (float) (BOND_LEN * Math.sin(outward));
                maxX = Math.max(maxX, layoutChain(m, n, ringAtom, nx, ny, outward, pos, visited));
            }
        }
        return maxX;
    }

    /** Classic skeletal-formula zigzag: each bond alternates ±30° off horizontal. Branch points fan children evenly. */
    private static float layoutChain(Molecule m, int atomId, int parentId, float x, float y,
            double incomingAngle, Map<Integer, float[]> pos, Set<Integer> visited) {
        pos.put(atomId, new float[] { x, y });
        visited.add(atomId);
        float maxX = x;

        List<Integer> children = new ArrayList<>();
        for (int n : m.neighbors(atomId)) {
            if (n != parentId && !visited.contains(n)) children.add(n);
        }
        if (children.isEmpty()) return maxX;

        if (children.size() == 1) {
            double nextAngle = -incomingAngle; // reflect across horizontal -> alternating zigzag
            float nx = x + (float) (BOND_LEN * Math.cos(nextAngle));
            float ny = y + (float) (BOND_LEN * Math.sin(nextAngle));
            maxX = Math.max(maxX, layoutChain(m, children.get(0), atomId, nx, ny, nextAngle, pos, visited));
        } else {
            double spread = Math.toRadians(60);
            double start = incomingAngle - spread * (children.size() - 1) / 2.0;
            for (int i = 0; i < children.size(); i++) {
                double childAngle = start + i * spread;
                float nx = x + (float) (BOND_LEN * Math.cos(childAngle));
                float ny = y + (float) (BOND_LEN * Math.sin(childAngle));
                maxX = Math.max(maxX, layoutChain(m, children.get(i), atomId, nx, ny, childAngle, pos, visited));
            }
        }
        return maxX;
    }

    /** Regular N-gon with the given bond length as its edge length, vertex pointing up. */
    private static float layoutRing(List<Integer> ring, Map<Integer, float[]> pos, Set<Integer> visited, float startX) {
        int n = ring.size();
        float radius = (float) (BOND_LEN / (2 * Math.sin(Math.PI / n)));
        float cx = startX + radius;
        float cy = 0f;
        float maxX = startX;
        for (int i = 0; i < n; i++) {
            double angle = -Math.PI / 2 + i * (2 * Math.PI / n);
            float x = cx + (float) (radius * Math.cos(angle));
            float y = cy + (float) (radius * Math.sin(angle));
            pos.put(ring.get(i), new float[] { x, y });
            visited.add(ring.get(i));
            maxX = Math.max(maxX, x);
        }
        return maxX;
    }

    private static float[] ringCenter(Map<Integer, float[]> pos, List<Integer> ring) {
        float sx = 0, sy = 0;
        for (int id : ring) { float[] p = pos.get(id); sx += p[0]; sy += p[1]; }
        return new float[] { sx / ring.size(), sy / ring.size() };
    }

    private static List<Integer> componentOf(Molecule m, int startId) {
        List<Integer> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(startId);
        while (!stack.isEmpty()) {
            int cur = stack.pop();
            if (!seen.add(cur)) continue;
            out.add(cur);
            for (int n : m.neighbors(cur)) if (!seen.contains(n)) stack.push(n);
        }
        return out;
    }

    private static Molecule subMolecule(Molecule m, List<Integer> componentAtomIds) {
        Set<Integer> ids = new HashSet<>(componentAtomIds);
        List<Atom> atoms = m.atoms().stream().filter(a -> ids.contains(a.id())).toList();
        List<Bond> bonds = m.bonds().stream().filter(b -> ids.contains(b.a()) && ids.contains(b.b())).toList();
        return new Molecule(atoms, bonds);
    }

    private static Molecule recenter(Molecule m) {
        if (m.isEmpty()) return m;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Atom a : m.atoms()) {
            minX = Math.min(minX, a.x()); maxX = Math.max(maxX, a.x());
            minY = Math.min(minY, a.y()); maxY = Math.max(maxY, a.y());
        }
        float cx = (minX + maxX) / 2f, cy = (minY + maxY) / 2f;
        List<Atom> shifted = new ArrayList<>();
        for (Atom a : m.atoms()) shifted.add(new Atom(a.id(), a.element(), a.x() - cx, a.y() - cy));
        return new Molecule(shifted, m.bonds());
    }
}
