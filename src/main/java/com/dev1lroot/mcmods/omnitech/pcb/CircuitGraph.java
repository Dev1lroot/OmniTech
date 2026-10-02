/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.pcb;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Electrical view of an assembled board: nets (connected copper islands), the
 * devices soldered between them, and which net each terminal label sits on.
 *
 * @param netOfCell net index per grid cell ({@link PcbDesign#index}), -1 for no copper
 * @param terminals label → net; pads sharing a label are treated as wired together
 */
public record CircuitGraph(int netCount, int[] netOfCell, List<Device> devices, Map<String, Integer> terminals) {

    /** A soldered part with the net under each of its legs (spec pin order). */
    public record Device(PartSpec spec, int[] nets) {}

    /** Flood-fills the copper and maps parts and labels onto the resulting nets. */
    public static CircuitGraph of(PcbDesign d, List<PlacedPart> parts) {
        int[] net = new int[PcbDesign.CELLS];
        Arrays.fill(net, -1);
        int count = 0;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int y = 0; y < d.height(); y++) {
            for (int x = 0; x < d.width(); x++) {
                if (!d.isCopper(x, y) || net[PcbDesign.index(x, y)] >= 0) continue;
                net[PcbDesign.index(x, y)] = count;
                q.add(new int[]{x, y});
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    int[][] nbs = {{c[0] + 1, c[1]}, {c[0] - 1, c[1]}, {c[0], c[1] + 1}, {c[0], c[1] - 1}};
                    for (int[] n : nbs) {
                        if (!d.isCopper(n[0], n[1])) continue;
                        int i = PcbDesign.index(n[0], n[1]);
                        if (net[i] >= 0) continue;
                        net[i] = count;
                        q.add(n);
                    }
                }
                count++;
            }
        }

        // Pads that share a label are joined by the test fixture's wiring
        int[] remap = new int[count];
        for (int i = 0; i < count; i++) remap[i] = i;
        Map<String, Integer> first = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> e : d.labels().entrySet()) {
            int n = net[e.getKey()];
            if (n < 0) continue;
            Integer f = first.putIfAbsent(e.getValue(), n);
            if (f != null) union(remap, f, n);
        }
        // Compact net ids after the unions
        int[] compact = new int[count];
        Arrays.fill(compact, -1);
        int nets = 0;
        for (int i = 0; i < count; i++) {
            int r = find(remap, i);
            if (compact[r] < 0) compact[r] = nets++;
        }
        for (int i = 0; i < net.length; i++) if (net[i] >= 0) net[i] = compact[find(remap, net[i])];

        Map<String, Integer> terminals = new LinkedHashMap<>();
        first.forEach((label, n) -> terminals.put(label, compact[find(remap, n)]));

        List<Device> devices = new ArrayList<>();
        for (PlacedPart p : parts) {
            PartSpec spec = p.spec().orElse(null);
            // an unpainted resistor has no value — it is not a working part yet
            if (spec == null || Double.isNaN(spec.value())) continue;
            List<int[]> pins = p.pinCells();
            int[] nets_ = new int[pins.size()];
            boolean ok = true;
            for (int i = 0; i < pins.size(); i++) {
                int[] c = pins.get(i);
                nets_[i] = d.inBounds(c[0], c[1]) ? net[PcbDesign.index(c[0], c[1])] : -1;
                if (nets_[i] < 0) ok = false;
            }
            if (ok) devices.add(new Device(spec, nets_));
        }
        return new CircuitGraph(nets, net, devices, terminals);
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) i = p[i] = p[p[i]];
        return i;
    }

    private static void union(int[] p, int a, int b) {
        p[find(p, a)] = find(p, b);
    }
}
