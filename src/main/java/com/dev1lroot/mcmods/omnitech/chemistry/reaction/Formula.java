/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** An immutable element-count multiset — the parsed form of a chemical formula string. */
public final class Formula {

    private final Map<String, Integer> counts;
    private final String original;

    Formula(Map<String, Integer> counts, String original) {
        Map<String, Integer> clean = new LinkedHashMap<>();
        counts.forEach((k, v) -> { if (v != 0) clean.put(k, v); });
        this.counts = clean;
        this.original = original;
    }

    public static Formula of(String formula) {
        return FormulaParser.parse(formula);
    }

    public static Formula of(Map<String, Integer> counts) {
        return new Formula(counts, null);
    }

    public Map<String, Integer> counts() {
        return counts;
    }

    public int count(String element) {
        return counts.getOrDefault(element, 0);
    }

    public boolean contains(String element) {
        return counts.containsKey(element);
    }

    public java.util.Set<String> elements() {
        return counts.keySet();
    }

    public boolean isSingleElement() {
        return counts.size() == 1;
    }

    public String soleElement() {
        if (!isSingleElement()) throw new IllegalStateException("Not a single-element formula: " + this);
        return counts.keySet().iterator().next();
    }

    public int totalAtoms() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Formula scaledBy(int factor) {
        Map<String, Integer> scaled = new LinkedHashMap<>();
        counts.forEach((k, v) -> scaled.put(k, v * factor));
        return new Formula(scaled, null);
    }

    public Formula plus(Formula other) {
        Map<String, Integer> sum = new LinkedHashMap<>(counts);
        other.counts.forEach((k, v) -> sum.merge(k, v, Integer::sum));
        return new Formula(sum, null);
    }

    public String originalString() {
        return original;
    }

    /** Hill-system rendering (C first, H second if C present, then alphabetical) for readable output. */
    public String canonical() {
        StringBuilder sb = new StringBuilder();
        Map<String, Integer> rest = new TreeMap<>(counts);
        List<String> lead = new java.util.ArrayList<>();
        if (rest.containsKey("C")) {
            lead.add("C");
            if (rest.containsKey("H")) lead.add("H");
        }
        for (String e : lead) rest.remove(e);
        for (String e : lead) appendPart(sb, e, counts.get(e));
        rest.forEach((e, n) -> appendPart(sb, e, n));
        return sb.toString();
    }

    private static void appendPart(StringBuilder sb, String element, int count) {
        sb.append(element);
        if (count != 1) sb.append(count);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Formula f && counts.equals(f.counts);
    }

    @Override
    public int hashCode() {
        return counts.hashCode();
    }

    @Override
    public String toString() {
        return canonical();
    }
}
