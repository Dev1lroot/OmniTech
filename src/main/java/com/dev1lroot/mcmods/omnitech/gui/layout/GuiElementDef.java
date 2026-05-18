/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui.layout;

/**
 * Raw data class populated by Gson from a GUI layout JSON.
 * Fields map directly to JSON keys; missing keys get Java defaults.
 *
 * <p>Supported types:
 * <ul>
 *   <li>{@code fluid_tank}  – renders a framed fluid bar. Needs {@code source}, {@code w}, {@code h}.</li>
 *   <li>{@code slot}        – renders a slot frame at {@code x},{@code y}.</li>
 *   <li>{@code progressbar} – renders a progress arrow. Needs {@code source} (0-100 float), {@code w}.</li>
 *   <li>{@code fluid_label} – renders a HudWriter label for a fluid. Needs {@code source}.</li>
 *   <li>{@code energy_label}   – renders "stored/max EU". Uses fixed data keys {@code energy_stored}/{@code energy_max}.</li>
 *   <li>{@code eu_cost_label}  – renders "X EU/cycle" hint. Uses fixed data key {@code eu_per_cycle}.</li>
 * </ul>
 */
public class GuiElementDef {

    // ── Common ────────────────────────────────────────────────────────────────
    public String id   = "";
    public String type = "";
    public int    x    = 0;
    public int    y    = 0;
    /** Width — used by fluid_tank and progressbar. */
    public int    w    = 0;
    /** Height — used by fluid_tank. */
    public int    h    = 0;
    /**
     * Data-binding key. Resolved via {@link GuiDataContext} at render time.
     * Not used by energy_label / eu_cost_label (they use fixed well-known keys).
     */
    public String source = "";
    /**
     * Container slot index for {@code slot} elements.
     * Must match the corresponding {@code BlockEntity} slot constant.
     * {@code -1} means unset (element is ignored when registering menu slots).
     */
    public int slot_index = -1;

    // ── fluid_label ───────────────────────────────────────────────────────────
    /** When true, renders amount/capacity on a second line. */
    public boolean show_amount  = false;
    /** AARRGGBB hex — colour when the tank has fluid. */
    public String  color_filled = "FFFFFFFF";
    /** AARRGGBB hex — colour of the amount fraction text, or "Empty" label colour. */
    public String  color_empty  = "FF888888";
    /** Text shown when the fluid source is empty. No text is drawn if blank. */
    public String  empty_text   = "";
    /** Vertical space between lines, in pixels. */
    public int     line_height  = 10;

    // ── energy_label / eu_cost_label ─────────────────────────────────────────
    /** When true, {@code x} is ignored and the text is horizontally centred. */
    public boolean centered       = false;
    /** AARRGGBB hex — general single colour (eu_cost_label). */
    public String  color          = "FFFFFFFF";
    /** AARRGGBB hex — colour when energy > 0 (energy_label). */
    public String  color_active   = "FF44AAFF";
    /** AARRGGBB hex — colour when energy == 0 (energy_label). */
    public String  color_inactive = "FF888888";

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return 0xFFFFFFFF;
        try {
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF;
        }
    }

    public int getColorFilled()   { return parseColor(color_filled); }
    public int getColorEmpty()    { return parseColor(color_empty); }
    public int getColor()         { return parseColor(color); }
    public int getColorActive()   { return parseColor(color_active); }
    public int getColorInactive() { return parseColor(color_inactive); }
}
