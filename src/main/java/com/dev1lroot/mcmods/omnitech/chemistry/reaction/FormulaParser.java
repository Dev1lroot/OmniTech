/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses standard chemical formula notation: nested parenthesized groups with multipliers
 * ({@code Al2(SO4)3}), hydrate dots ({@code CuSO4.5H2O}), isotope suffixes ({@code U-235}), and
 * comma-separated solid-solution alternatives ({@code (Fe,Mn)Nb2O6}), for which the first
 * alternative is used — the same convention this mod's own datagen tooling already applies to
 * real mineral formulas.
 */
public final class FormulaParser {

    private FormulaParser() {}

    public static class ParseException extends RuntimeException {
        public ParseException(String message) { super(message); }
    }

    public static Formula parse(String raw) {
        if (raw == null || raw.isBlank()) throw new ParseException("Empty formula");
        String s = raw.strip();
        // Isotope suffix: "U-235" -> "U". Only strips a trailing dash-digits, never touches
        // legitimate internal dashes (none occur in formula notation).
        int dash = s.indexOf('-');
        if (dash > 0 && s.substring(dash + 1).chars().allMatch(Character::isDigit) && s.length() > dash + 1) {
            s = s.substring(0, dash);
        }

        // Hydrate notation: split on the first '.', '·' (middle dot) or '*'.
        Map<String, Integer> counts = new LinkedHashMap<>();
        int hydrateAt = indexOfAny(s, ".·*");
        String main = hydrateAt >= 0 ? s.substring(0, hydrateAt) : s;
        Cursor c = new Cursor(main);
        merge(counts, parseGroup(c), 1);
        if (c.pos < c.s.length()) throw new ParseException("Unexpected trailing characters in '" + raw + "'");

        if (hydrateAt >= 0) {
            String hydratePart = s.substring(hydrateAt + 1);
            int i = 0;
            while (i < hydratePart.length() && Character.isDigit(hydratePart.charAt(i))) i++;
            int n = i > 0 ? Integer.parseInt(hydratePart.substring(0, i)) : 1;
            Cursor hc = new Cursor(hydratePart.substring(i));
            merge(counts, parseGroup(hc), n);
        }

        return new Formula(counts, raw);
    }

    private static int indexOfAny(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) return i;
        }
        return -1;
    }

    private static void merge(Map<String, Integer> into, Map<String, Integer> from, int factor) {
        from.forEach((k, v) -> into.merge(k, v * factor, Integer::sum));
    }

    private static final class Cursor {
        final String s;
        int pos;
        Cursor(String s) { this.s = s; }
        char peek() { return pos < s.length() ? s.charAt(pos) : '\0'; }
    }

    private static Map<String, Integer> parseGroup(Cursor c) {
        Map<String, Integer> result = new LinkedHashMap<>();
        while (c.pos < c.s.length()) {
            char ch = c.peek();
            if (ch == ')' || ch == ']') {
                return result;
            } else if (ch == '(' || ch == '[') {
                char open = ch;
                char close = open == '(' ? ')' : ']';
                c.pos++;
                Map<String, Integer> sub = parseGroup(c);
                if (c.peek() != close && c.peek() != '\0') {
                    // tolerate mismatched bracket styles seen in real mineral formulas
                }
                if (c.pos < c.s.length()) c.pos++; // consume close bracket
                int mult = readInt(c, 1);
                merge(result, sub, mult);
            } else if (ch == ',') {
                // Solid-solution alternative separator: keep the first alternative already
                // parsed, skip everything else up to this group's matching close bracket.
                int depth = 0;
                while (c.pos < c.s.length()) {
                    char skip = c.peek();
                    if (skip == '(' || skip == '[') depth++;
                    else if (skip == ')' || skip == ']') {
                        if (depth == 0) break;
                        depth--;
                    }
                    c.pos++;
                }
                return result;
            } else if (Character.isUpperCase(ch)) {
                StringBuilder sym = new StringBuilder().append(ch);
                c.pos++;
                if (c.pos < c.s.length() && Character.isLowerCase(c.peek())) {
                    sym.append(c.peek());
                    c.pos++;
                }
                int count = readInt(c, 1);
                result.merge(sym.toString(), count, Integer::sum);
            } else {
                // Skip whitespace/dot-in-the-middle-of-a-group or any other stray character
                // rather than failing the whole batch on a minor formatting quirk.
                c.pos++;
            }
        }
        return result;
    }

    private static int readInt(Cursor c, int fallback) {
        int start = c.pos;
        while (c.pos < c.s.length() && Character.isDigit(c.peek())) c.pos++;
        return c.pos > start ? Integer.parseInt(c.s.substring(start, c.pos)) : fallback;
    }
}
