/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry;

import java.util.*;

/**
 * Best-effort IUPAC-style systematic naming for structures sketched on the Structure Table.
 *
 * <p>Covers: acyclic carbon chains (straight or branched, with branched/unsaturated substituent
 * groups like isopropyl-style and vinyl-style prefixes) with common functional groups
 * (carboxylic acid, ester, amide, nitrile, aldehyde, ketone, alcohol, amine, ether, halogens)
 * named by seniority, correct locant numbering (including lowest-locant ring numbering), and
 * substituent alphabetization/multiplication; single monocyclic rings — cycloalkanes/-enes and
 * benzene, including retained trivial names (phenol, aniline, toluene, benzoic acid,
 * benzaldehyde, styrene, cumene) for monosubstituted benzenes; a ring that sits off the principal
 * characteristic group (e.g. a phenyl group dangling off an amine chain) is named as a
 * substituent (phenyl / substituted phenyl / N-phenyl) rather than forced to be the parent; and
 * retained acid/ester/amide names for the one- and two-carbon cases (formic/acetic acid, methyl
 * formate/acetate, acetamide).
 *
 * <p><b>Deliberately out of scope</b> (real IUPAC nomenclature is enormous): fused/bridged/spiro
 * polycyclic systems, stereodescriptors (R/S, E/Z, cis/trans), more than one instance of the
 * principal characteristic group, and "far-end" renumbering of branched substituent groups (an
 * isopropyl group is named "1-methylethyl" here, not the modern preferred "propan-2-yl" — both
 * are valid, unambiguous IUPAC substituent names). Molecules that fall outside this get an honest
 * generic name built from the molecular formula rather than a guess — wrong chemistry is worse
 * than an admitted gap.
 *
 * <p><b>Inorganic compounds</b> (ores, minerals, alloys — anything containing a metal or
 * metalloid) take a completely separate, formula-count-based path (see "Inorganic naming" below)
 * rather than any of the above organic machinery: single elements ("iron"), binary ionic
 * compounds with a worked-out oxidation state for variable-valence metals ("iron(III) oxide",
 * "copper(I) sulfide" vs. "copper(II) sulfide"), metal hydroxides ("aluminium hydroxide"), and
 * covalent metalloid oxides/sulfides with Greek numeric prefixes and proper elision ("silicon
 * dioxide", "diboron trioxide"). Ternary+ inorganic compounds and unrecognized element pairings
 * fall back to a formula-based name, same philosophy as the organic gap above.
 */
public final class IupacNamer {

    private IupacNamer() {}

    private static final String[] CHAIN_STEM = {
            null, "meth", "eth", "prop", "but", "pent", "hex", "hept", "oct", "non", "dec",
            "undec", "dodec", "tridec", "tetradec", "pentadec", "hexadec", "heptadec", "octadec", "nonadec", "icos"
    };
    private static final String[] MULTIPLIER = {
            null, null, "di", "tri", "tetra", "penta", "hexa", "hepta", "octa", "nona", "deca"
    };

    private enum GroupType { ACID, ESTER, AMIDE, NITRILE, ALDEHYDE, KETONE, ALCOHOL, AMINE, NONE }

    public static String name(Molecule m) {
        try {
            if (m.isEmpty()) return "Nothing";

            // Inorganic compounds (ores, minerals, alloys, ...) get an entirely separate naming
            // path — see the "Inorganic naming" section below. This also runs ahead of the
            // isConnected() check below, since real-world ionic compounds are conventionally
            // written as disconnected ion fragments (e.g. "[Na+].[Cl-]" for salt) rather than a
            // single bonded structure.
            if (containsInorganicElement(m)) {
                String inorganic = nameInorganic(m);
                return inorganic != null ? capitalize(inorganic) : ("Compound (" + m.molecularFormula() + ")");
            }

            if (!m.isConnected()) return "Mixture (" + m.molecularFormula() + ")";

            int cyclomatic = m.cyclomaticNumber();
            if (cyclomatic > 1) return "Complex compound (" + m.molecularFormula() + ")";

            List<Integer> ring = cyclomatic == 1 ? m.singleRingAtomsInOrder() : null;
            if (cyclomatic == 1 && ring == null) return "Complex compound (" + m.molecularFormula() + ")";

            // More than one instance of the principal characteristic group (e.g. a diketone) is
            // explicitly out of scope — an honest generic name beats silently dropping one of them.
            List<Group> groups = scanGroups(m, ring);
            Group globalPrincipal = principal(groups);
            if (globalPrincipal != null) {
                long sameType = groups.stream().filter(g -> g.type() == globalPrincipal.type()).count();
                if (sameType > 1) return "Unnamed compound (" + m.molecularFormula() + ")";
            }

            String result;
            if (ring == null) {
                result = nameAcyclic(m, null);
            } else {
                boolean ringIsParent = globalPrincipal == null
                        || ring.contains(globalPrincipal.carbonId())
                        || isSingleExocyclicOnRing(m, ring, globalPrincipal.carbonId());
                result = ringIsParent ? nameRing(m, ring) : nameAcyclic(m, ring);
            }
            if (result != null) return capitalize(result);
        } catch (Exception ignored) {
            // Fall through to the honest fallback below rather than surface a half-built name.
        }
        return "Unnamed compound (" + m.molecularFormula() + ")";
    }

    // ── Inorganic naming (ores, minerals, alloys — metals/metalloids) ──────────

    private static final Set<String> ORGANIC_ELEMENTS = Set.of("C", "N", "O", "S", "P", "F", "Cl", "Br", "I");

    private static final Set<String> METALS = Set.of(
            "Li", "Na", "K", "Rb", "Cs", "Mg", "Ca", "Sr", "Ba", "Al",
            "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn",
            "Zr", "Nb", "Mo", "Ag", "Cd", "Sn", "Hf", "Ta", "W", "Au", "Pt", "Hg", "Pb", "Bi", "U");

    /** Metalloids get covalent (Greek-prefix) binary naming instead of ionic — "silicon dioxide",
     *  not "silicon(IV) oxide". */
    private static final Set<String> METALLOIDS = Set.of("B", "Si", "Sb");

    private static final Map<String, String> ELEMENT_NAME = Map.ofEntries(
            Map.entry("Li", "lithium"), Map.entry("Na", "sodium"), Map.entry("K", "potassium"),
            Map.entry("Rb", "rubidium"), Map.entry("Cs", "caesium"), Map.entry("Mg", "magnesium"),
            Map.entry("Ca", "calcium"), Map.entry("Sr", "strontium"), Map.entry("Ba", "barium"),
            Map.entry("B", "boron"), Map.entry("Al", "aluminium"), Map.entry("Si", "silicon"),
            Map.entry("Ti", "titanium"), Map.entry("V", "vanadium"), Map.entry("Cr", "chromium"),
            Map.entry("Mn", "manganese"), Map.entry("Fe", "iron"), Map.entry("Co", "cobalt"),
            Map.entry("Ni", "nickel"), Map.entry("Cu", "copper"), Map.entry("Zn", "zinc"),
            Map.entry("Zr", "zirconium"), Map.entry("Nb", "niobium"), Map.entry("Mo", "molybdenum"),
            Map.entry("Ag", "silver"), Map.entry("Cd", "cadmium"), Map.entry("Sn", "tin"),
            Map.entry("Sb", "antimony"), Map.entry("Hf", "hafnium"), Map.entry("Ta", "tantalum"),
            Map.entry("W", "tungsten"), Map.entry("Au", "gold"), Map.entry("Pt", "platinum"),
            Map.entry("Hg", "mercury"), Map.entry("Pb", "lead"), Map.entry("Bi", "bismuth"),
            Map.entry("U", "uranium"));

    /** Metals with only one common oxidation state don't take a Roman-numeral suffix — "sodium
     *  chloride", never "sodium(I) chloride". Everything else in {@link #METALS} is treated as
     *  variable-valence (iron, copper, tin, lead, chromium, ...) and gets one worked out from the
     *  formula's stoichiometry (e.g. Fe2O3 → "iron(III) oxide"). */
    private static final Set<String> FIXED_VALENCE_METALS = Set.of(
            "Li", "Na", "K", "Rb", "Cs", "Mg", "Ca", "Sr", "Ba", "Al", "Zn", "Ag", "Cd");

    private static final Map<String, Integer> ANION_CHARGE = Map.of(
            "O", 2, "S", 2, "N", 3, "P", 3, "F", 1, "Cl", 1, "Br", 1, "I", 1);
    private static final Map<String, String> ANION_NAME = Map.of(
            "O", "oxide", "S", "sulfide", "N", "nitride", "P", "phosphide",
            "F", "fluoride", "Cl", "chloride", "Br", "bromide", "I", "iodide");

    private static final String[] GREEK_PREFIX = {
            null, "mono", "di", "tri", "tetra", "penta", "hexa", "hepta", "octa", "nona", "deca"
    };
    private static final String[] ROMAN = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII" };

    private static boolean containsInorganicElement(Molecule m) {
        for (Atom a : m.atoms()) if (!ORGANIC_ELEMENTS.contains(a.element())) return true;
        return false;
    }

    /** Formula-count-based (not bond-topology-based) inorganic naming — ionic compounds don't
     *  really have discrete covalent bonds the way this parser's bond orders imply, so working
     *  from element counts is both simpler and more honest than pretending the drawn bonds mean
     *  something structural. Returns {@code null} for anything outside its scope (ternary+
     *  compounds, unrecognized element pairings), which the caller turns into a formula fallback.
     */
    private static String nameInorganic(Molecule m) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Atom a : m.atoms()) counts.merge(a.element(), 1, Integer::sum);

        if (counts.size() == 1) return ELEMENT_NAME.get(counts.keySet().iterator().next());
        if (counts.size() != 2) return null;

        var it = counts.entrySet().iterator();
        var e1 = it.next();
        var e2 = it.next();
        String sym1 = e1.getKey(), sym2 = e2.getKey();
        int n1 = e1.getValue(), n2 = e2.getValue();

        if (METALS.contains(sym1) && "O".equals(sym2) && isAllHydroxide(m, sym1)) return hydroxideName(sym1);
        if (METALS.contains(sym2) && "O".equals(sym1) && isAllHydroxide(m, sym2)) return hydroxideName(sym2);

        if (METALS.contains(sym1) && ANION_CHARGE.containsKey(sym2)) return binaryIonicName(sym1, sym2, n1, n2);
        if (METALS.contains(sym2) && ANION_CHARGE.containsKey(sym1)) return binaryIonicName(sym2, sym1, n2, n1);

        if (METALLOIDS.contains(sym1) && ("O".equals(sym2) || "S".equals(sym2))) return covalentBinaryName(sym1, sym2, n1, n2);
        if (METALLOIDS.contains(sym2) && ("O".equals(sym1) || "S".equals(sym1))) return covalentBinaryName(sym2, sym1, n2, n1);

        return null;
    }

    /** True if every oxygen atom in the molecule carries exactly one (implicit) hydrogen and is
     *  bonded to {@code metalSym} — i.e. the O/H pairs are hydroxide groups, not oxide oxygens. */
    private static boolean isAllHydroxide(Molecule m, String metalSym) {
        boolean sawAny = false;
        for (Atom a : m.atoms()) {
            if (!"O".equals(a.element())) continue;
            sawAny = true;
            if (m.implicitH(a.id()) != 1) return false;
            boolean bondedToMetal = false;
            for (int nb : m.neighbors(a.id())) {
                Atom other = m.atom(nb);
                if (other != null && metalSym.equals(other.element())) bondedToMetal = true;
            }
            if (!bondedToMetal) return false;
        }
        return sawAny;
    }

    private static String hydroxideName(String metalSym) {
        String metalName = ELEMENT_NAME.get(metalSym);
        return metalName == null ? null : metalName + " hydroxide";
    }

    /** Ionic binary compound, e.g. Fe2O3 → "iron(III) oxide", NaCl → "sodium chloride". */
    private static String binaryIonicName(String metalSym, String anionSym, int metalCount, int anionCount) {
        String metalName = ELEMENT_NAME.get(metalSym);
        String anionName = ANION_NAME.get(anionSym);
        if (metalName == null || anionName == null) return null;

        String oxidationState = "";
        if (!FIXED_VALENCE_METALS.contains(metalSym) && metalCount > 0) {
            int totalNegative = ANION_CHARGE.get(anionSym) * anionCount;
            if (totalNegative % metalCount == 0) {
                int state = totalNegative / metalCount;
                if (state >= 1 && state <= ROMAN.length) oxidationState = "(" + ROMAN[state - 1] + ")";
            }
        }
        return metalName + oxidationState + " " + anionName;
    }

    /** Covalent binary compound (metalloid + oxide/sulfide), e.g. SiO2 → "silicon dioxide",
     *  B2O3 → "diboron trioxide" — Greek numeric prefixes, no oxidation-state Roman numeral. */
    private static String covalentBinaryName(String elemSym, String anionSym, int elemCount, int anionCount) {
        String elemName = ELEMENT_NAME.get(elemSym);
        String anionName = ANION_NAME.get(anionSym);
        if (elemName == null || anionName == null) return null;

        String first = elemCount > 1 ? elideGreekPrefix(greekPrefix(elemCount), elemName) + elemName : elemName;
        String second = elideGreekPrefix(greekPrefix(anionCount), anionName) + anionName;
        return first + " " + second;
    }

    private static String greekPrefix(int count) {
        return count >= 1 && count < GREEK_PREFIX.length ? GREEK_PREFIX[count] : "";
    }

    /** "mono" + "oxide" → "monoxide", "tetra" + "oxide" → "tetroxide" — real IUPAC elides a
     *  trailing a/o off the prefix before a vowel-initial element/anion name ("trioxide" and
     *  "disulfide" need no elision, since "tri"/"di" don't end in a/o and "sulfide" isn't
     *  vowel-initial). */
    private static String elideGreekPrefix(String prefix, String followingName) {
        if (prefix == null || prefix.isEmpty() || followingName.isEmpty()) return prefix == null ? "" : prefix;
        char last = prefix.charAt(prefix.length() - 1);
        boolean vowelStart = "aeiou".indexOf(Character.toLowerCase(followingName.charAt(0))) >= 0;
        return (vowelStart && (last == 'a' || last == 'o')) ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    // ── Functional-group scan ────────────────────────────────────────────────

    private record Group(GroupType type, int carbonId, int heteroId) {}

    /**
     * Scans every carbon for a carbonyl/nitrile/hydroxyl/amine role. One entry per hit.
     *
     * @param ring the molecule's one ring (if any) — used only to steer the amine anchor-carbon
     *             heuristic (see below); pass {@code null} for a ring-free molecule.
     */
    private static List<Group> scanGroups(Molecule m, List<Integer> ring) {
        List<Group> groups = new ArrayList<>();
        Set<Integer> claimedO = new HashSet<>();

        for (Atom c : m.atoms()) {
            if (!c.element().equals("C")) continue;

            Bond carbonyl = null;
            for (Bond b : m.bondsOf(c.id())) {
                Atom other = m.atom(b.other(c.id()));
                if (other != null && other.element().equals("O") && b.order() == 2) { carbonyl = b; break; }
            }
            if (carbonyl != null) {
                int oId = carbonyl.other(c.id());
                claimedO.add(oId);
                List<Integer> others = new ArrayList<>();
                for (int n : m.neighbors(c.id())) if (n != oId) others.add(n);

                Integer singleO = others.stream().filter(id -> "O".equals(elementOf(m, id))).findFirst().orElse(null);
                Integer nAtom = others.stream().filter(id -> "N".equals(elementOf(m, id))).findFirst().orElse(null);

                if (singleO != null) {
                    claimedO.add(singleO);
                    if (m.implicitH(singleO) > 0) groups.add(new Group(GroupType.ACID, c.id(), singleO));
                    else groups.add(new Group(GroupType.ESTER, c.id(), singleO));
                } else if (nAtom != null) {
                    groups.add(new Group(GroupType.AMIDE, c.id(), nAtom));
                } else if (others.size() <= 1) {
                    groups.add(new Group(GroupType.ALDEHYDE, c.id(), -1));
                } else {
                    groups.add(new Group(GroupType.KETONE, c.id(), -1));
                }
                continue;
            }

            for (Bond b : m.bondsOf(c.id())) {
                Atom other = m.atom(b.other(c.id()));
                if (other != null && other.element().equals("N") && b.order() == 3) {
                    groups.add(new Group(GroupType.NITRILE, c.id(), other.id()));
                }
            }
        }

        for (Atom o : m.atoms()) {
            if (!o.element().equals("O") || claimedO.contains(o.id())) continue;
            if (m.usedValence(o.id()) == 1 && m.implicitH(o.id()) == 1) {
                int carbon = m.neighbors(o.id()).stream().filter(id -> "C".equals(elementOf(m, id)))
                        .findFirst().orElse(-1);
                if (carbon != -1) groups.add(new Group(GroupType.ALCOHOL, carbon, o.id()));
            }
        }

        Set<Integer> amideN = new HashSet<>();
        for (Group g : groups) if (g.type() == GroupType.AMIDE) amideN.add(g.heteroId());
        Set<Integer> ringSet = ring == null ? Set.of() : new HashSet<>(ring);
        for (Atom n : m.atoms()) {
            if (!n.element().equals("N") || amideN.contains(n.id())) continue;
            boolean isNitrileN = groups.stream().anyMatch(g -> g.type() == GroupType.NITRILE && g.heteroId() == n.id());
            if (isNitrileN) continue;
            List<Integer> carbons = m.neighbors(n.id()).stream().filter(id -> "C".equals(elementOf(m, id))).toList();
            if (carbons.isEmpty()) continue;

            // An amine's nitrogen can carry more than one carbon substituent (secondary/tertiary
            // amines, or a chain vs. a ring). The "main chain" anchor is whichever branch is the
            // biggest structure — a ring attachment always outweighs a plain alkyl branch (an
            // aniline-style ring is conventionally the parent over an N-alkyl group), otherwise
            // the longer carbon chain wins.
            int anchor = carbons.get(0);
            int bestLen = -1;
            for (int c : carbons) {
                int len = ringSet.contains(c) ? ringSet.size() + 1 : longestBranch(m, c, n.id(), ringSet).size();
                if (len > bestLen) { bestLen = len; anchor = c; }
            }
            groups.add(new Group(GroupType.AMINE, anchor, n.id()));
        }

        return groups;
    }

    private static String elementOf(Molecule m, int id) {
        Atom a = m.atom(id);
        return a == null ? null : a.element();
    }

    private static Group principal(List<Group> groups) {
        for (GroupType t : List.of(GroupType.ACID, GroupType.ESTER, GroupType.AMIDE, GroupType.NITRILE,
                GroupType.ALDEHYDE, GroupType.KETONE, GroupType.ALCOHOL, GroupType.AMINE)) {
            for (Group g : groups) if (g.type() == t) return g;
        }
        return null;
    }

    private static String suffixFor(GroupType t) {
        return switch (t) {
            case ACID -> "oic acid";
            case ESTER -> "oate";
            case AMIDE -> "amide";
            case NITRILE -> "nitrile";
            case ALDEHYDE -> "al";
            case KETONE -> "one";
            case ALCOHOL -> "ol";
            case AMINE -> "amine";
            default -> null;
        };
    }

    // ── Acyclic naming ───────────────────────────────────────────────────────

    /** @param ring the molecule's one ring, if it has one and the principal group sits off it (a
     *              ring-free molecule, or one where the ring itself is the parent, passes null). */
    private static String nameAcyclic(Molecule m, List<Integer> ring) {
        List<Group> groups = scanGroups(m, ring);
        Group principal = principal(groups);
        Set<Integer> ringAtoms = ring == null ? Set.of() : new HashSet<>(ring);

        List<Integer> chain;
        if (principal != null) {
            chain = chainThrough(m, principal.carbonId(), ringAtoms);
        } else {
            chain = longestChainOverall(m, ringAtoms);
        }
        if (chain == null || chain.isEmpty()) return null;

        // Esters need the two-part "alkyl alkanoate" treatment.
        if (principal != null && principal.type() == GroupType.ESTER) {
            return nameEster(m, principal, chain, ring);
        }

        return buildParentName(m, chain, principal, ring);
    }

    /** For an ester R-C(=O)-O-R': name as "R'yl R-oate" (or "R'yl acetate" for the 2-carbon acid). */
    private static String nameEster(Molecule m, Group ester, List<Integer> acylChain, List<Integer> ring) {
        String acidName = buildParentName(m, acylChain, ester, ring);

        int alkylO = ester.heteroId();
        int alkylC = m.neighbors(alkylO).stream().filter(id -> id != ester.carbonId()).findFirst().orElse(-1);
        String alkylName;
        if (alkylC == -1) {
            alkylName = "unknown";
        } else if (ring != null && ring.contains(alkylC)) {
            alkylName = ringAsSubstituent(m, ring, alkylC, alkylO);
        } else {
            // Branch/ring/unsaturation-aware, same as any other substituent — a plain
            // length-only lookup here mislabels e.g. benzyl acetate's O-CH2-phenyl as "methyl".
            alkylName = alkylSubstituentName(m, alkylC, alkylO, ring);
        }
        return alkylName + " " + acidName;
    }

    /** Longest simple carbon-only path passing through {@code anchor}, never stepping into {@code ringAtoms}. */
    private static List<Integer> chainThrough(Molecule m, int anchor, Set<Integer> ringAtoms) {
        List<Integer> carbonNeighbors = new ArrayList<>();
        for (int n : m.neighbors(anchor)) if ("C".equals(elementOf(m, n)) && !ringAtoms.contains(n)) carbonNeighbors.add(n);

        if (carbonNeighbors.isEmpty()) return List.of(anchor);

        List<List<Integer>> branches = new ArrayList<>();
        for (int n : carbonNeighbors) branches.add(longestBranch(m, n, anchor, ringAtoms));
        branches.sort((a, b) -> Integer.compare(b.size(), a.size()));

        List<Integer> chain = new ArrayList<>();
        if (!branches.isEmpty()) {
            List<Integer> first = new ArrayList<>(branches.get(0));
            Collections.reverse(first);
            chain.addAll(first);
        }
        chain.add(anchor);
        if (branches.size() > 1) chain.addAll(branches.get(1));
        return chain;
    }

    /** Longest simple carbon-only path anywhere in the molecule, never stepping into {@code ringAtoms}. */
    private static List<Integer> longestChainOverall(Molecule m, Set<Integer> ringAtoms) {
        List<Integer> best = List.of();
        for (Atom a : m.atoms()) {
            if (!a.element().equals("C") || ringAtoms.contains(a.id())) continue;
            List<Integer> branch = longestBranch(m, a.id(), -1, ringAtoms);
            if (branch.size() > best.size()) best = branch;
        }
        return best.isEmpty() ? null : best;
    }

    /** Longest simple carbon-only path starting AT {@code from}, never stepping back through
     *  {@code avoid} or into {@code ringAtoms} (keeps a ring from being walked into as if it were
     *  an open-ended chain — it gets named as a substituent instead). */
    private static List<Integer> longestBranch(Molecule m, int from, int avoid, Set<Integer> ringAtoms) {
        List<Integer> best = new ArrayList<>(List.of(from));
        for (int n : m.neighbors(from)) {
            if (n == avoid || !"C".equals(elementOf(m, n)) || ringAtoms.contains(n)) continue;
            List<Integer> branch = longestBranch(m, n, from, ringAtoms);
            if (branch.size() + 1 > best.size()) {
                List<Integer> candidate = new ArrayList<>();
                candidate.add(from);
                candidate.addAll(branch);
                best = candidate;
            }
        }
        return best;
    }

    // ── Chain name assembly (shared by acyclic + ring "carbo-" exocyclic chains) ──

    private static String buildParentName(Molecule m, List<Integer> chain, Group principal, List<Integer> ring) {
        List<Integer> forward = chain;
        List<Integer> backward = new ArrayList<>(chain);
        Collections.reverse(backward);

        List<Integer> best = chooseNumbering(m, forward, backward, principal, ring);
        int n = best.size();

        int principalLocant = 0;
        if (principal != null) principalLocant = best.indexOf(principal.carbonId()) + 1;

        // Unsaturation within the chain.
        List<Integer> eneLocants = new ArrayList<>();
        List<Integer> yneLocants = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            Bond b = m.bondBetween(best.get(i), best.get(i + 1));
            if (b == null) continue;
            if (b.order() == 2) eneLocants.add(i + 1);
            else if (b.order() == 3) yneLocants.add(i + 1);
        }

        // Substituents: anything hanging off a chain carbon that isn't the next/previous chain atom
        // or the principal group's own heteroatom.
        Map<String, List<Integer>> substituentLocants = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            int atomId = best.get(i);
            int locant = i + 1;
            Set<Integer> chainNeighbors = new HashSet<>();
            if (i > 0) chainNeighbors.add(best.get(i - 1));
            if (i < n - 1) chainNeighbors.add(best.get(i + 1));

            for (int nb : m.neighbors(atomId)) {
                if (chainNeighbors.contains(nb)) continue;
                if (principal != null && principal.carbonId() == atomId
                        && (nb == principal.heteroId() || isCarbonylOfPrincipal(m, principal, nb))) continue;

                String sub = substituentName(m, atomId, nb, principal, ring);
                if (sub == null) continue;
                substituentLocants.computeIfAbsent(sub, k -> new ArrayList<>()).add(locant);
            }
        }
        boolean chainHasNoSubstituents = substituentLocants.isEmpty() && eneLocants.isEmpty() && yneLocants.isEmpty();

        // Merge regular (numeric-locant) substituents with any "N-" substituents hanging directly
        // off the principal group's nitrogen (secondary/tertiary amine alkyls, N-aryl amides) —
        // real IUPAC alphabetizes all of these together, ignoring the locant style.
        List<String[]> fragments = new ArrayList<>();
        for (var e : substituentLocants.entrySet()) {
            List<Integer> locants = e.getValue();
            Collections.sort(locants);
            String locantStr = String.join(",", locants.stream().map(String::valueOf).toList());
            String mult = locants.size() < MULTIPLIER.length ? MULTIPLIER[locants.size()] : "";
            fragments.add(new String[]{ e.getKey(), locantStr + "-" + (mult == null ? "" : mult) + maybeParenthesize(e.getKey()) });
        }
        if (principal != null && (principal.type() == GroupType.AMINE || principal.type() == GroupType.AMIDE)) {
            fragments.addAll(renderNSubstituentFragments(m, principal, ring));
        }
        fragments.sort((a, b) -> a[0].compareTo(b[0]));
        // No hyphen before the parent stem — "2-methylpropan-1-ol", not "2-methyl-propan-1-ol".
        String prefix = String.join("-", fragments.stream().map(f -> f[1]).toList());

        String stem = n < CHAIN_STEM.length ? CHAIN_STEM[n] : ("C" + n + "-");

        String infix;
        if (!yneLocants.isEmpty()) infix = unsaturationInfix(yneLocants, "yn");
        else if (!eneLocants.isEmpty()) infix = unsaturationInfix(eneLocants, "en");
        else infix = "an";
        if (!eneLocants.isEmpty() && !yneLocants.isEmpty()) {
            infix = unsaturationInfix(eneLocants, "en") + "-" + unsaturationInfix(yneLocants, "yn");
        }

        // Carboxylic acid and aldehyde carbons are always chain-terminal (this namer only ever
        // builds the chain through them as an endpoint), so their locant is universally 1 and
        // is conventionally omitted — "propanoic acid" / "butanal", never "propan-1-oic acid".
        // The one- and two-carbon acid/ester/amide cases use their retained common names (formic/
        // acetic acid, formate/acetate, formamide/acetamide) rather than the fully systematic
        // form, same as real IUPAC preferred names — but only when that little chain itself is
        // unadorned (no substituents or unsaturation to fold into the retained name).
        String suffix = principal != null ? suffixFor(principal.type()) : null;
        String body;
        if (suffix == null) {
            body = stem + infix + "e";
        } else if (suffix.equals("oic acid")) {
            if (chainHasNoSubstituents && n == 1) body = "formic acid";
            else if (chainHasNoSubstituents && n == 2) body = "acetic acid";
            else body = stem + infix + "oic acid";
        } else if (suffix.equals("oate")) {
            if (chainHasNoSubstituents && n == 1) body = "formate";
            else if (chainHasNoSubstituents && n == 2) body = "acetate";
            else body = stem + infix + "oate";
        } else if (suffix.equals("al")) {
            body = stem + infix + "al";
        } else if (suffix.equals("nitrile")) {
            body = stem + infix + "e" + "nitrile";
        } else if (suffix.equals("amide")) {
            if (chainHasNoSubstituents && n == 1) body = "formamide";
            else if (chainHasNoSubstituents && n == 2) body = "acetamide";
            else body = stem + infix + "amide";
        } else {
            body = stem + infix + "-" + principalLocant + "-" + suffix;
        }

        return prefix + body;
    }

    private static boolean isCarbonylOfPrincipal(Molecule m, Group principal, int atomId) {
        Bond b = m.bondBetween(principal.carbonId(), atomId);
        return b != null && b.order() == 2 && "O".equals(elementOf(m, atomId));
    }

    /** Returns e.g. {@code "-2-en"} (with its own leading hyphen) or {@code "an"} for no unsaturation. */
    private static String unsaturationInfix(List<Integer> locants, String kind) {
        List<Integer> sorted = new ArrayList<>(locants);
        Collections.sort(sorted);
        String locantStr = String.join(",", sorted.stream().map(String::valueOf).toList());
        String mult = sorted.size() < MULTIPLIER.length ? MULTIPLIER[sorted.size()] : "";
        if (sorted.size() == 1) return "-" + locantStr + "-" + kind;
        return "-" + locantStr + "-" + mult + kind;
    }

    /** Any of the amine/amide principal group's nitrogen substituents beyond its own chain
     *  connection, rendered with an "N-" (or "N,N-di...") locant instead of a numeric one. */
    private static List<String[]> renderNSubstituentFragments(Molecule m, Group principal, List<Integer> ring) {
        Map<String, Integer> counts = new TreeMap<>();
        for (int nb : m.neighbors(principal.heteroId())) {
            if (nb == principal.carbonId()) continue;
            String sub = substituentName(m, principal.heteroId(), nb, null, ring);
            if (sub == null) continue;
            counts.merge(sub, 1, Integer::sum);
        }
        List<String[]> out = new ArrayList<>();
        for (var e : counts.entrySet()) {
            int count = e.getValue();
            String name = maybeParenthesize(e.getKey());
            if (count == 1) out.add(new String[]{ e.getKey(), "N-" + name });
            else {
                String mult = count < MULTIPLIER.length ? MULTIPLIER[count] : "";
                out.add(new String[]{ e.getKey(), "N,N-" + mult + name });
            }
        }
        return out;
    }

    private static String substituentName(Molecule m, int chainAtomId, int subAtomId, Group principal, List<Integer> ring) {
        Atom sub = m.atom(subAtomId);
        if (sub == null) return null;

        switch (sub.element()) {
            case "F" -> { return "fluoro"; }
            case "Cl" -> { return "chloro"; }
            case "Br" -> { return "bromo"; }
            case "I" -> { return "iodo"; }
            case "C" -> {
                if (ring != null && ring.contains(subAtomId)) {
                    return ringAsSubstituent(m, ring, subAtomId, chainAtomId);
                }
                return alkylSubstituentName(m, subAtomId, chainAtomId, ring);
            }
            case "O" -> {
                int deg = m.usedValence(subAtomId);
                if (deg == 1 && m.implicitH(subAtomId) == 1) {
                    boolean isPrincipalAlcohol = principal != null && principal.type() == GroupType.ALCOHOL
                            && principal.heteroId() == subAtomId;
                    return isPrincipalAlcohol ? null : "hydroxy";
                }
                int farC = m.neighbors(subAtomId).stream().filter(id -> id != chainAtomId).findFirst().orElse(-1);
                if (farC == -1) return null;
                // Ester oxygen (R-C(=O)-O-): name as an acyloxy group ("acetyloxy"), not a plain
                // ether — the far side is a carbonyl carbon, not just an alkyl/aryl group.
                Integer carbonylO = carbonylOxygenOf(m, farC, subAtomId);
                if (carbonylO != null) {
                    return acylName(m, farC, subAtomId, carbonylO, ring) + "oxy";
                }
                // Ether: name the far side as an alkoxy group (or "...oxy" off a ring, e.g. phenoxy).
                if (ring != null && ring.contains(farC)) {
                    String ringSub = ringAsSubstituent(m, ring, farC, subAtomId);
                    return ringSub.endsWith("yl") ? ringSub.substring(0, ringSub.length() - 2) + "oxy" : ringSub + "oxy";
                }
                Set<Integer> ringAtoms = ring == null ? Set.of() : new HashSet<>(ring);
                List<Integer> branch = longestBranch(m, farC, subAtomId, ringAtoms);
                int len = branch.size();
                return (len < CHAIN_STEM.length ? CHAIN_STEM[len] : "alk") + "oxy";
            }
            case "N" -> {
                boolean isPrincipalAmine = principal != null && principal.type() == GroupType.AMINE
                        && principal.heteroId() == subAtomId;
                return isPrincipalAmine ? null : "amino";
            }
            default -> { return null; }
        }
    }

    /** Names a carbon substituent branch (possibly branched/unsaturated) rooted at {@code start},
     *  attached via {@code cameFrom} — e.g. "methyl", "ethenyl", "1-methylethyl" (isopropyl). */
    private static String alkylSubstituentName(Molecule m, int start, int cameFrom, List<Integer> ring) {
        Set<Integer> ringAtoms = ring == null ? Set.of() : new HashSet<>(ring);
        List<Integer> chain = longestBranch(m, start, cameFrom, ringAtoms);
        int n = chain.size();

        List<Integer> eneLocants = new ArrayList<>();
        List<Integer> yneLocants = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            Bond b = m.bondBetween(chain.get(i), chain.get(i + 1));
            if (b == null) continue;
            if (b.order() == 2) eneLocants.add(i + 1);
            else if (b.order() == 3) yneLocants.add(i + 1);
        }

        Map<String, List<Integer>> subs = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            int atomId = chain.get(i);
            int locant = i + 1;
            Set<Integer> chainNeighbors = new HashSet<>();
            if (i > 0) chainNeighbors.add(chain.get(i - 1));
            if (i < n - 1) chainNeighbors.add(chain.get(i + 1));
            if (i == 0) chainNeighbors.add(cameFrom);
            for (int nb : m.neighbors(atomId)) {
                if (chainNeighbors.contains(nb)) continue;
                String sub = substituentName(m, atomId, nb, null, ring);
                if (sub == null) continue;
                subs.computeIfAbsent(sub, k -> new ArrayList<>()).add(locant);
            }
        }

        String stem = n < CHAIN_STEM.length ? CHAIN_STEM[n] : ("C" + n);
        String tail;
        if (!yneLocants.isEmpty() || !eneLocants.isEmpty()) {
            // The attachment point is always locant 1 in this scheme, so for the unambiguous
            // 2-carbon case ("ethenyl"/"ethynyl") the locant is redundant and conventionally dropped.
            if (n == 2 && subs.isEmpty()) {
                tail = stem + (yneLocants.isEmpty() ? "enyl" : "ynyl");
            } else {
                String infix;
                if (!yneLocants.isEmpty() && !eneLocants.isEmpty()) {
                    infix = unsaturationInfix(eneLocants, "en") + "-" + unsaturationInfix(yneLocants, "yn");
                } else if (!yneLocants.isEmpty()) {
                    infix = unsaturationInfix(yneLocants, "yn");
                } else {
                    infix = unsaturationInfix(eneLocants, "en");
                }
                tail = stem + infix + "yl";
            }
        } else {
            tail = stem + "yl";
        }

        // "Benzyl" — the retained trivial name for an unadorned phenylmethyl group.
        if (n == 1 && eneLocants.isEmpty() && yneLocants.isEmpty()
                && subs.size() == 1 && List.of(1).equals(subs.get("phenyl"))) {
            return "benzyl";
        }
        // With only one atom in this branch, any substituent on it can only be at locant 1 —
        // showing that locant is redundant ("phenylmethyl", not "1-phenylmethyl").
        String subsRendered = n == 1 ? renderSubstituentPrefixesNoLocants(subs) : renderSubstituentPrefixes(subs);
        return subsRendered + tail;
    }

    private static String renderSubstituentPrefixesNoLocants(Map<String, List<Integer>> subs) {
        if (subs.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (var e : subs.entrySet()) {
            int count = e.getValue().size();
            String mult = count < MULTIPLIER.length ? MULTIPLIER[count] : "";
            parts.add((mult == null ? "" : mult) + maybeParenthesize(e.getKey()));
        }
        return String.join("-", parts);
    }

    /** Wraps a substituent name in parentheses when it carries its own internal locant (e.g.
     *  "(1-methylethyl)", "(4-chlorophenyl)") so it can't be confused with the outer locant. */
    private static String maybeParenthesize(String subName) {
        return subName.chars().anyMatch(Character::isDigit) ? "(" + subName + ")" : subName;
    }

    /** If {@code carbonId} is a carbonyl carbon (has a C=O other than {@code excludeO}), returns
     *  that oxygen's atom id; otherwise null. Used to tell an ester/acyloxy oxygen (R-C(=O)-O-)
     *  apart from a plain ether oxygen (R-O-). */
    private static Integer carbonylOxygenOf(Molecule m, int carbonId, int excludeO) {
        for (Bond b : m.bondsOf(carbonId)) {
            int other = b.other(carbonId);
            if (other == excludeO) continue;
            Atom a = m.atom(other);
            if (a != null && "O".equals(a.element()) && b.order() == 2) return other;
        }
        return null;
    }

    /** Names the acyl group R-C(=O)- rooted at {@code carbonylC} (e.g. "formyl", "acetyl",
     *  "propanoyl") for use in an acyloxy substituent like "acetyloxy". Doesn't chase branching
     *  or unsaturation within R — a fair trade-off given how rarely that combination shows up. */
    private static String acylName(Molecule m, int carbonylC, int excludeA, int excludeB, List<Integer> ring) {
        Set<Integer> ringAtoms = ring == null ? Set.of() : new HashSet<>(ring);
        int rCarbon = m.neighbors(carbonylC).stream()
                .filter(id -> id != excludeA && id != excludeB)
                .findFirst().orElse(-1);
        if (rCarbon == -1) return "formyl";
        List<Integer> rChain = longestBranch(m, rCarbon, carbonylC, ringAtoms);
        int n = 1 + rChain.size();
        if (n == 2) return "acetyl";
        String stem = n < CHAIN_STEM.length ? CHAIN_STEM[n] : ("C" + n);
        return stem + "anoyl";
    }

    /** Real IUPAC numbering priority is tiered — the principal group's own locant is decided
     *  first, and only a tie there falls through to unsaturation, and only a tie there falls
     *  through to substituents as a set. Merging everything into one sorted list (as an earlier
     *  version of this method did) silently lets a low substituent locant outrank the principal
     *  group, which is backwards. */
    private static List<Integer> chooseNumbering(Molecule m, List<Integer> forward, List<Integer> backward,
            Group principal, List<Integer> ring) {
        if (principal != null) {
            int locF = forward.indexOf(principal.carbonId()) + 1;
            int locB = backward.indexOf(principal.carbonId()) + 1;
            if (locF != locB) return locF < locB ? forward : backward;
        }

        int c = compareLocantLists(unsaturationLocants(m, forward), unsaturationLocants(m, backward));
        if (c != 0) return c < 0 ? forward : backward;

        c = compareLocantLists(substituentLocantsOnly(m, forward, principal), substituentLocantsOnly(m, backward, principal));
        if (c != 0) return c < 0 ? forward : backward;

        return forward;
    }

    private static List<Integer> unsaturationLocants(Molecule m, List<Integer> chain) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < chain.size() - 1; i++) {
            Bond b = m.bondBetween(chain.get(i), chain.get(i + 1));
            if (b != null && b.order() > 1) out.add(i + 1);
        }
        return out;
    }

    /** Positions bearing any substituent (a ring attachment counts too) — doesn't care what the
     *  substituent actually is, only where it sits, which is all numbering priority needs. */
    private static List<Integer> substituentLocantsOnly(Molecule m, List<Integer> chain, Group principal) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            Set<Integer> chainNeighbors = new HashSet<>();
            if (i > 0) chainNeighbors.add(chain.get(i - 1));
            if (i < chain.size() - 1) chainNeighbors.add(chain.get(i + 1));
            int atomId = chain.get(i);
            for (int nb : m.neighbors(atomId)) {
                if (chainNeighbors.contains(nb)) continue;
                if (principal != null && principal.carbonId() == atomId
                        && (nb == principal.heteroId() || isCarbonylOfPrincipal(m, principal, nb))) continue;
                out.add(i + 1);
            }
        }
        Collections.sort(out);
        return out;
    }

    // ── Ring-as-substituent (phenyl / substituted phenyl / cycloalkyl) ─────────

    /** Names the molecule's ring as a substituent attached via {@code attachAtomId}, e.g. "phenyl",
     *  "4-chlorophenyl", "cyclohexyl" — numbering the ring from the attachment point (locant 1),
     *  choosing whichever direction around it gives the lowest substituent locants. */
    private static String ringAsSubstituent(Molecule m, List<Integer> ring, int attachAtomId, int cameFromAtomId) {
        boolean aromatic = m.isAromaticBenzeneRing(ring);
        int ringSize = ring.size();
        int startIdx = ring.indexOf(attachAtomId);
        List<Integer> orderA = ringOrderFrom(ring, startIdx, true);
        List<Integer> orderB = ringOrderFrom(ring, startIdx, false);
        Map<String, List<Integer>> subsA = collectRingSubs(m, orderA, cameFromAtomId, ring);
        Map<String, List<Integer>> subsB = collectRingSubs(m, orderB, cameFromAtomId, ring);
        Map<String, List<Integer>> subs = compareLocantLists(allLocantsSorted(subsA), allLocantsSorted(subsB)) <= 0 ? subsA : subsB;

        String stem = aromatic ? "phenyl" : (ringSize < CHAIN_STEM.length ? "cyclo" + CHAIN_STEM[ringSize] + "yl" : "cyclyl");
        return renderSubstituentPrefixes(subs) + stem;
    }

    private static List<Integer> ringOrderFrom(List<Integer> ring, int startIdx, boolean forward) {
        int size = ring.size();
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int idx = forward ? (startIdx + i) % size : ((startIdx - i) % size + size) % size;
            out.add(ring.get(idx));
        }
        return out;
    }

    private static Map<String, List<Integer>> collectRingSubs(Molecule m, List<Integer> orderedRing,
            int excludeNeighbor, List<Integer> ring) {
        int size = orderedRing.size();
        Map<String, List<Integer>> subs = new TreeMap<>();
        for (int i = 0; i < size; i++) {
            int atomId = orderedRing.get(i);
            Set<Integer> ringNeighbors = Set.of(orderedRing.get((i - 1 + size) % size), orderedRing.get((i + 1) % size));
            for (int nb : m.neighbors(atomId)) {
                if (ringNeighbors.contains(nb)) continue;
                if (i == 0 && nb == excludeNeighbor) continue;
                String sub = substituentName(m, atomId, nb, null, ring);
                if (sub == null) continue;
                subs.computeIfAbsent(sub, k -> new ArrayList<>()).add(i + 1);
            }
        }
        return subs;
    }

    private static List<Integer> allLocantsSorted(Map<String, List<Integer>> subs) {
        List<Integer> out = new ArrayList<>();
        for (var v : subs.values()) out.addAll(v);
        Collections.sort(out);
        return out;
    }

    private static int compareLocantLists(List<Integer> a, List<Integer> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int c = Integer.compare(a.get(i), b.get(i));
            if (c != 0) return c;
        }
        return Integer.compare(a.size(), b.size());
    }

    // ── Ring naming (ring is the parent) ────────────────────────────────────────

    private static final Map<String, String> RETAINED_BENZENE = Map.of(
            "methyl", "toluene",
            "hydroxy", "phenol",
            "amino", "aniline",
            "carboxylic acid", "benzoic acid",
            "carbaldehyde", "benzaldehyde",
            "ethenyl", "styrene",
            "1-methylethyl", "cumene"
    );

    private static String nameRing(Molecule m, List<Integer> ring) {
        boolean aromatic = m.isAromaticBenzeneRing(ring);
        List<Group> groups = scanGroups(m, ring);

        // Only consider principal groups whose defining carbon sits on the ring or is the sole
        // exocyclic carbon directly attached to it (this namer doesn't chase a ring substituent
        // out into its own long chain for the principal group — see the chain-is-parent path
        // in name() for that case).
        Group principal = null;
        for (Group g : groups) {
            if (ring.contains(g.carbonId())) { principal = g; break; }
            if (isSingleExocyclicOnRing(m, ring, g.carbonId())) { principal = g; break; }
        }

        int ringSize = ring.size();
        boolean principalOnRing = principal != null && ring.contains(principal.carbonId());
        boolean principalExocyclic = principal != null && !principalOnRing;

        Integer forcedStart = null;
        if (principalOnRing) {
            forcedStart = principal.carbonId();
        } else if (principalExocyclic) {
            for (int rAtom : ring) if (m.neighbors(principal.carbonId()).contains(rAtom)) { forcedStart = rAtom; break; }
        }
        List<Integer> orderedRing = bestRingOrder(m, ring, forcedStart);

        // Collect substituents around the ring (locant = ring position in the chosen ordering).
        Map<String, List<Integer>> subs = new TreeMap<>();
        for (int i = 0; i < ringSize; i++) {
            int atomId = orderedRing.get(i);
            Set<Integer> ringNeighbors = Set.of(orderedRing.get((i - 1 + ringSize) % ringSize), orderedRing.get((i + 1) % ringSize));
            for (int nb : m.neighbors(atomId)) {
                if (ringNeighbors.contains(nb)) continue;
                if (principal != null && ((principalOnRing && principal.carbonId() == atomId
                        && (nb == principal.heteroId() || isCarbonylOfPrincipal(m, principal, nb)))
                        || (principalExocyclic && nb == principal.carbonId()))) continue;
                String sub = substituentName(m, atomId, nb, principal, ring);
                if (sub == null) continue;
                subs.computeIfAbsent(sub, k -> new ArrayList<>()).add(i + 1);
            }
        }

        String suffix = principal != null ? suffixFor(principal.type()) : null;
        String exoSuffix = principalExocyclic ? exoSuffixFor(principal.type()) : null;

        if (aromatic) {
            // Retained trivial names for a clean monosubstituted case.
            if (subs.size() == 1 && principal == null) {
                var only = subs.entrySet().iterator().next();
                if (only.getValue().size() == 1 && RETAINED_BENZENE.containsKey(only.getKey())) {
                    return RETAINED_BENZENE.get(only.getKey());
                }
            }
            if (subs.isEmpty() && exoSuffix != null && exoSuffix.equals("carboxylic acid")) return "benzoic acid";
            if (subs.isEmpty() && exoSuffix != null && exoSuffix.equals("carbaldehyde")) return "benzaldehyde";
            if (subs.isEmpty() && principalOnRing && "ol".equals(suffix)) return "phenol";
            // An N-substituted amine (e.g. N,N-dimethylaniline) still uses "aniline" as its stem —
            // the N-alkyl/N-aryl groups just prepend onto it, same as the systematic path below.
            if (subs.isEmpty() && principalOnRing && "amine".equals(suffix)) {
                return renderNPrefixOnly(m, principal, ring) + "aniline";
            }

            // A single substituent with nothing else going on doesn't need a locant at all —
            // "chlorobenzene", not "1-chlorobenzene" (the position is unambiguous).
            String subsRendered;
            if (subs.size() == 1 && principal == null && subs.values().iterator().next().size() == 1) {
                subsRendered = maybeParenthesize(subs.keySet().iterator().next());
            } else {
                subsRendered = renderRingPrefixWithN(subs, m, principal, ring, principalOnRing);
            }

            String base = subsRendered + "benzene";
            if (exoSuffix != null) base = subsRendered + "benzene" + carboAppend(exoSuffix);
            else if (suffix != null) base = subsRendered + "benzen" + suffixJoin(suffix);
            return base;
        }

        // Non-aromatic ring: cyclo + stem (+ ene for one ring double bond) + suffix.
        int doubleBonds = 0;
        for (int i = 0; i < ringSize; i++) {
            Bond b = m.bondBetween(orderedRing.get(i), orderedRing.get((i + 1) % ringSize));
            if (b != null && b.order() == 2) doubleBonds++;
        }
        String stem = ringSize < CHAIN_STEM.length ? CHAIN_STEM[ringSize] : ("C" + ringSize);
        String infix = doubleBonds > 0 ? "en" : "an";

        // Same "no locant needed" rule as the aromatic case — "methylcyclohexane", not
        // "1-methylcyclohexane" — when it's the only substituent and there's no suffix to anchor.
        String prefix;
        if (subs.size() == 1 && principal == null && subs.values().iterator().next().size() == 1) {
            prefix = maybeParenthesize(subs.keySet().iterator().next());
        } else {
            prefix = renderRingPrefixWithN(subs, m, principal, ring, principalOnRing);
        }
        if (exoSuffix != null) {
            return prefix + "cyclo" + stem + infix + "e" + carboAppend(exoSuffix);
        }
        if (suffix != null) {
            return prefix + "cyclo" + stem + infix + suffixJoin(suffix);
        }
        return prefix + "cyclo" + stem + infix + "e";
    }

    /** Same as {@link #renderSubstituentPrefixes} but also folds in any "N-" substituents hanging
     *  off an on-ring amine/amide's nitrogen (e.g. N,N-dimethylaniline's two N-methyls) — without
     *  this, they'd silently vanish since they never show up in the ring's own substituent scan. */
    private static String renderRingPrefixWithN(Map<String, List<Integer>> subs, Molecule m, Group principal,
            List<Integer> ring, boolean principalOnRing) {
        List<String[]> fragments = new ArrayList<>();
        for (var e : subs.entrySet()) {
            List<Integer> locants = e.getValue();
            Collections.sort(locants);
            String locantStr = String.join(",", locants.stream().map(String::valueOf).toList());
            String mult = locants.size() < MULTIPLIER.length ? MULTIPLIER[locants.size()] : "";
            fragments.add(new String[]{ e.getKey(), locantStr + "-" + (mult == null ? "" : mult) + maybeParenthesize(e.getKey()) });
        }
        if (principalOnRing && principal != null && (principal.type() == GroupType.AMINE || principal.type() == GroupType.AMIDE)) {
            fragments.addAll(renderNSubstituentFragments(m, principal, ring));
        }
        fragments.sort((a, b) -> a[0].compareTo(b[0]));
        return String.join("-", fragments.stream().map(f -> f[1]).toList());
    }

    /** Just the "N-"/"N,N-di..." fragments (sorted, joined), with no numeric-locant substituents —
     *  for prepending onto a retained ring name like "aniline" that already implies the ring itself. */
    private static String renderNPrefixOnly(Molecule m, Group principal, List<Integer> ring) {
        List<String[]> fragments = renderNSubstituentFragments(m, principal, ring);
        fragments.sort((a, b) -> a[0].compareTo(b[0]));
        return String.join("-", fragments.stream().map(f -> f[1]).toList());
    }

    /** Tries every rotation/reflection of the ring (or, if {@code forcedStartAtom} is given, just
     *  the two directions from that fixed atom) and returns whichever gives the lowest set of
     *  substituent locants — the standard IUPAC lowest-locants rule. */
    private static List<Integer> bestRingOrder(Molecule m, List<Integer> ring, Integer forcedStartAtom) {
        int size = ring.size();
        List<Integer> best = null;
        List<Integer> bestLocants = null;
        for (int start = 0; start < size; start++) {
            if (forcedStartAtom != null && !ring.get(start).equals(forcedStartAtom)) continue;
            for (boolean forward : new boolean[] { true, false }) {
                List<Integer> cand = ringOrderFrom(ring, start, forward);
                List<Integer> locants = ringSubstituentLocantsOnly(m, cand);
                if (best == null || compareLocantLists(locants, bestLocants) < 0) { best = cand; bestLocants = locants; }
            }
        }
        return best;
    }

    private static List<Integer> ringSubstituentLocantsOnly(Molecule m, List<Integer> orderedRing) {
        int size = orderedRing.size();
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int atomId = orderedRing.get(i);
            Set<Integer> ringNeighbors = Set.of(orderedRing.get((i - 1 + size) % size), orderedRing.get((i + 1) % size));
            for (int nb : m.neighbors(atomId)) {
                if (!ringNeighbors.contains(nb)) { out.add(i + 1); break; }
            }
        }
        Collections.sort(out);
        return out;
    }

    private static boolean isSingleExocyclicOnRing(Molecule m, List<Integer> ring, int carbonId) {
        for (int nb : m.neighbors(carbonId)) if (ring.contains(nb)) return true;
        return false;
    }

    private static String exoSuffixFor(GroupType t) {
        return switch (t) {
            case ACID -> "carboxylic acid";
            case ALDEHYDE -> "carbaldehyde";
            case NITRILE -> "carbonitrile";
            case AMIDE -> "carboxamide";
            default -> null;
        };
    }

    private static String carboAppend(String exoSuffix) { return exoSuffix; }

    private static String suffixJoin(String suffix) {
        // "ol", "one", "amine", "oic acid" — drop the leading vowel clash with "-e" stem ending
        // handled by caller supplying the bare stem; here we just prefix a locant-free join since
        // ring suffixes conventionally don't carry a locant when there's only one ring position.
        return switch (suffix) {
            case "oic acid" -> "carboxylic acid".equals(suffix) ? suffix : "oic acid";
            default -> suffix;
        };
    }

    private static String renderSubstituentPrefixes(Map<String, List<Integer>> subs) {
        if (subs.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (var e : subs.entrySet()) {
            List<Integer> locants = e.getValue();
            Collections.sort(locants);
            String locantStr = String.join(",", locants.stream().map(String::valueOf).toList());
            String mult = locants.size() < MULTIPLIER.length ? MULTIPLIER[locants.size()] : "";
            parts.add(locantStr + "-" + (mult == null ? "" : mult) + maybeParenthesize(e.getKey()));
        }
        // No hyphen before the parent stem — see the matching note in buildParentName.
        return String.join("-", parts);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
