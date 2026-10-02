/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.pcb;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * An immutable single-sided board drawing: outline, copper traces, pads (drilled
 * pin nodes) and terminal labels on pads.
 *
 * <p>Cells live on a fixed {@link #MAX_W}×{@link #MAX_H} grid (index {@code y * MAX_W + x})
 * so resizing never shuffles the drawing; {@code width × height} is the active area.
 * Copper (a trace or a pad) only exists on board cells and joins its four neighbours.
 *
 * <p>Pure Java on purpose: the circuit simulator and its tests run without Minecraft.
 */
public final class PcbDesign {

    public static final int MAX_W = 24;
    public static final int MAX_H = 16;
    public static final int MIN_SIZE = 3;
    public static final int CELLS = MAX_W * MAX_H;

    public static final byte BOARD = 1;
    public static final byte TRACE = 2;
    public static final byte PAD   = 4;

    /** Terminal labels a pad can carry; test benches connect their instruments to these. */
    public static final List<String> LABELS = List.of("V+", "GND", "IN", "OUT", "AC1", "AC2");

    public static final int MAX_NAME = 24;

    private final String name;
    private final int width, height;
    private final byte[] cells;
    private final Map<Integer, String> labels;
    private final int hash;

    public PcbDesign(String name, int width, int height, byte[] cells, Map<Integer, String> labels) {
        this.name = name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        this.width = clamp(width, MIN_SIZE, MAX_W);
        this.height = clamp(height, MIN_SIZE, MAX_H);
        this.cells = new byte[CELLS];
        System.arraycopy(cells, 0, this.cells, 0, Math.min(CELLS, cells.length));
        // Sanitise: nothing outside the active area, copper only on board, labels only on pads
        for (int i = 0; i < CELLS; i++) {
            int x = i % MAX_W, y = i / MAX_W;
            if (x >= this.width || y >= this.height || (this.cells[i] & BOARD) == 0) this.cells[i] = 0;
            else this.cells[i] &= (BOARD | TRACE | PAD);
        }
        TreeMap<Integer, String> clean = new TreeMap<>();
        labels.forEach((cell, label) -> {
            if (cell >= 0 && cell < CELLS && (this.cells[cell] & PAD) != 0 && LABELS.contains(label)) {
                clean.put(cell, label);
            }
        });
        this.labels = Collections.unmodifiableMap(clean);
        this.hash = 31 * (31 * (31 * Arrays.hashCode(this.cells) + this.width) + this.height)
                + this.labels.hashCode() + 17 * this.name.hashCode();
    }

    /** A fresh rectangular board with no copper. */
    public static PcbDesign blank(int width, int height) {
        byte[] c = new byte[CELLS];
        for (int y = 0; y < MAX_H; y++)
            for (int x = 0; x < MAX_W; x++)
                c[y * MAX_W + x] = BOARD;
        return new PcbDesign("", width, height, c, Map.of());
    }

    public static final PcbDesign DEFAULT = blank(16, 10);

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String name()   { return name; }
    public int width()     { return width; }
    public int height()    { return height; }
    public Map<Integer, String> labels() { return labels; }
    /** Raw cell flags; a copy, safe to mutate. */
    public byte[] cells()  { return cells.clone(); }

    public static int index(int x, int y) { return y * MAX_W + x; }

    public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }

    private byte at(int x, int y) { return inBounds(x, y) ? cells[index(x, y)] : 0; }

    public boolean isBoard(int x, int y)  { return (at(x, y) & BOARD) != 0; }
    public boolean isTrace(int x, int y)  { return (at(x, y) & TRACE) != 0; }
    public boolean isPad(int x, int y)    { return (at(x, y) & PAD) != 0; }
    public boolean isCopper(int x, int y) { return (at(x, y) & (TRACE | PAD)) != 0; }
    public String label(int x, int y)     { return inBounds(x, y) ? labels.get(index(x, y)) : null; }

    public int boardCells() {
        int n = 0;
        for (byte b : cells) if ((b & BOARD) != 0) n++;
        return n;
    }

    public int padCount() {
        int n = 0;
        for (byte b : cells) if ((b & PAD) != 0) n++;
        return n;
    }

    public boolean isEmpty() { return boardCells() == 0; }

    /** Identity of the geometry alone (ignores the name), used to remember part placements. */
    public int geometryHash() {
        return 31 * (31 * Arrays.hashCode(cells) + width) + height;
    }

    // ── Editing (each returns a new design) ───────────────────────────────────

    public PcbDesign withCell(int x, int y, byte flags) {
        if (!inBounds(x, y)) return this;
        byte[] c = cells.clone();
        c[index(x, y)] = flags;
        return new PcbDesign(name, width, height, c, labels);
    }

    public PcbDesign withLabel(int x, int y, String label) {
        if (!inBounds(x, y)) return this;
        TreeMap<Integer, String> l = new TreeMap<>(labels);
        if (label == null) l.remove(index(x, y));
        else l.put(index(x, y), label);
        return new PcbDesign(name, width, height, cells, l);
    }

    public PcbDesign withSize(int w, int h) {
        // Newly exposed area starts as bare board so growing the board feels natural
        byte[] c = cells.clone();
        int nw = clamp(w, MIN_SIZE, MAX_W), nh = clamp(h, MIN_SIZE, MAX_H);
        for (int y = 0; y < nh; y++)
            for (int x = 0; x < nw; x++)
                if (x >= width || y >= height) c[index(x, y)] = BOARD;
        return new PcbDesign(name, nw, nh, c, labels);
    }

    public PcbDesign withName(String n) {
        return new PcbDesign(n == null ? "" : n.strip(), width, height, cells, labels);
    }

    // ── Object contract ───────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        return o instanceof PcbDesign d && d.width == width && d.height == height
                && d.name.equals(name) && Arrays.equals(d.cells, cells) && d.labels.equals(labels);
    }

    @Override public int hashCode() { return hash; }

    @Override public String toString() { return "PcbDesign[" + name + " " + width + "x" + height + "]"; }
}
