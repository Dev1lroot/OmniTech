/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.chemistry.reaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.dev1lroot.mcmods.omnitech.chemistry.reaction.ReactionResult.NoReaction;
import com.dev1lroot.mcmods.omnitech.chemistry.reaction.ReactionResult.PhysicalState;
import com.dev1lroot.mcmods.omnitech.chemistry.reaction.ReactionResult.Reaction;
import com.dev1lroot.mcmods.omnitech.chemistry.reaction.ReactionResult.Species;

/**
 * Predicts the outcome of mixing any two compounds from real inorganic-chemistry principles —
 * the activity series, acid/base neutralization, solubility-driven double displacement, direct
 * combination, and combustion — rather than a per-pair authored recipe. See the package-level
 * writeup in the class-level docs of {@link CompoundClassifier} for the classification this is
 * built on, and its "Known limitation" note for what this does NOT cover (organic acid/base
 * chemistry, reaction kinetics/equilibrium, anything needing more than two reactants).
 *
 * <p>Every {@code try*} rule returns {@code Optional.empty()} when it simply doesn't apply (so the
 * next rule gets a turn), or a present {@link ReactionResult} — which may itself be a
 * {@link NoReaction} — when it recognizes the pairing but chemistry says nothing happens (e.g. a
 * metal too unreactive to displace hydrogen from an acid).
 */
public final class ReactionEngine {

    private ReactionEngine() {}

    static final Formula WATER = Formula.of("H2O");
    private static final Set<Formula> KNOWN_GASES = Set.of(
            Formula.of("H2"), Formula.of("CO2"), Formula.of("SO2"), Formula.of("H2S"),
            Formula.of("HCN"), Formula.of("NH3"), Formula.of("O2"), Formula.of("N2"), Formula.of("Cl2"));

    public static ReactionResult react(String formulaA, String formulaB) {
        return react(CompoundClassifier.classify(Formula.of(formulaA)), CompoundClassifier.classify(Formula.of(formulaB)));
    }

    public static ReactionResult react(CompoundClass a, CompoundClass b) {
        Optional<ReactionResult> r;
        if ((r = tryMetalWater(a, b)).isPresent()) return r.get();
        if ((r = trySingleDisplacement(a, b)).isPresent()) return r.get();
        if ((r = tryAcidWithOxideOrAmmonia(a, b)).isPresent()) return r.get();
        if ((r = tryDoubleDisplacement(a, b)).isPresent()) return r.get();
        if ((r = trySynthesis(a, b)).isPresent()) return r.get();
        if ((r = tryCombustion(a, b)).isPresent()) return r.get();
        return new NoReaction("A " + describe(a) + " and a " + describe(b)
                + " have no recognized generic reaction pathway together.");
    }

    private static String describe(CompoundClass cc) {
        return cc.type().toString().toLowerCase().replace('_', ' ');
    }

    // ----------------------------------------------------------------------- metal + water -----

    /**
     * Real cold-water reactivity stops far short of the full activity series: Mg/Al/Zn/Fe etc.
     * only react with steam, not liquid water, so this uses a stricter cutoff (Ca's score) than
     * the acid pathway rather than reusing {@link Element#reactivityScore("H")} — the alkali
     * metals and heavy alkaline-earths (Ca/Sr/Ba/Ra) are the only ones that fizz in cold water.
     */
    private static Optional<ReactionResult> tryMetalWater(CompoundClass a, CompoundClass b) {
        CompoundClass metalSide, waterSide;
        if (a.type() == CompoundClass.Type.ELEMENT_METAL && b.type() == CompoundClass.Type.WATER) {
            metalSide = a; waterSide = b;
        } else if (b.type() == CompoundClass.Type.ELEMENT_METAL && a.type() == CompoundClass.Type.WATER) {
            metalSide = b; waterSide = a;
        } else {
            return Optional.empty();
        }

        String metal = metalSide.metal();
        if (Element.reactivityScore(metal) < Element.reactivityScore("Ca")) {
            return Optional.of(new NoReaction(metal + " does not react with cold water "
                    + "(only alkali metals and Ca/Sr/Ba do; less reactive metals need steam, which isn't modeled)."));
        }

        int charge = Element.defaultCationCharge(metal);
        Formula hydroxide = IonicFormula.combine(metal, charge, PolyatomicIons.ANIONS.stream()
                .filter(i -> i.name().equals("hydroxide")).findFirst().orElseThrow().atoms(), -1);
        List<Formula> reactants = List.of(metalSide.formula(), waterSide.formula());
        List<Formula> products = List.of(hydroxide, Formula.of("H2"));
        int[] coeffs = tryBalance(reactants, products);
        if (coeffs == null) return Optional.of(couldNotBalance(a, b));

        List<Species> reactantSpecies = List.of(
                new Species(metalSide.formula(), coeffs[0], PhysicalState.SOLID),
                new Species(waterSide.formula(), coeffs[1], PhysicalState.LIQUID));
        List<Species> productSpecies = List.of(
                new Species(hydroxide, coeffs[2], stateOf(hydroxide)),
                new Species(Formula.of("H2"), coeffs[3], PhysicalState.GAS));
        return Optional.of(new Reaction("Metal-water reaction", reactantSpecies, productSpecies,
                metal + " reacts vigorously with water, releasing hydrogen gas."));
    }

    // ---------------------------------------------------------------- single displacement -----

    private static Optional<ReactionResult> trySingleDisplacement(CompoundClass a, CompoundClass b) {
        CompoundClass metalSide, holder;
        if (a.type() == CompoundClass.Type.ELEMENT_METAL && (b.isAcid() || b.type() == CompoundClass.Type.SALT)) {
            metalSide = a; holder = b;
        } else if (b.type() == CompoundClass.Type.ELEMENT_METAL && (a.isAcid() || a.type() == CompoundClass.Type.SALT)) {
            metalSide = b; holder = a;
        } else {
            return Optional.empty();
        }

        String freeMetal = metalSide.metal();
        String displaced = holder.isAcid() ? "H" : holder.metal();
        if (Element.reactivityScore(freeMetal) <= Element.reactivityScore(displaced)) {
            return Optional.of(new NoReaction(freeMetal + " is not reactive enough to displace " + displaced
                    + " from " + holder.formula().canonical() + " (activity series)."));
        }

        int newCharge = Element.defaultCationCharge(freeMetal);
        Formula newSalt = IonicFormula.combine(freeMetal, newCharge, holder.anionAtoms(), holder.anionTotalCharge());
        Formula displacedFormula = holder.isAcid() ? Formula.of("H2") : Formula.of(Map.of(displaced, 1));

        List<Formula> reactants = List.of(metalSide.formula(), holder.formula());
        List<Formula> products = List.of(newSalt, displacedFormula);
        int[] coeffs = tryBalance(reactants, products);
        if (coeffs == null) return Optional.of(couldNotBalance(a, b));

        List<Species> reactantSpecies = List.of(
                new Species(metalSide.formula(), coeffs[0], PhysicalState.SOLID),
                new Species(holder.formula(), coeffs[1], PhysicalState.AQUEOUS));
        List<Species> productSpecies = List.of(
                new Species(newSalt, coeffs[2], stateOf(newSalt)),
                new Species(displacedFormula, coeffs[3], holder.isAcid() ? PhysicalState.GAS : PhysicalState.SOLID));

        String desc = freeMetal + " is more reactive than " + displaced + " and displaces it from "
                + holder.formula().canonical() + ".";
        return Optional.of(new Reaction("Single displacement", reactantSpecies, productSpecies, desc));
    }

    // -------------------------------------------------- acid + metal oxide/hydroxide/ammonia -----

    private static Optional<ReactionResult> tryAcidWithOxideOrAmmonia(CompoundClass a, CompoundClass b) {
        CompoundClass acid, other;
        if (a.isAcid() && (isBasicOxide(b) || b.type() == CompoundClass.Type.WEAK_BASE_MOLECULAR)) {
            acid = a; other = b;
        } else if (b.isAcid() && (isBasicOxide(a) || a.type() == CompoundClass.Type.WEAK_BASE_MOLECULAR)) {
            acid = b; other = a;
        } else {
            return Optional.empty();
        }

        List<Formula> reactants = List.of(acid.formula(), other.formula());
        List<Formula> products;
        String desc;
        if (other.type() == CompoundClass.Type.WEAK_BASE_MOLECULAR) {
            Formula saltFormula = IonicFormula.combine(Formula.of("NH4"), 1, acid.anionAtoms(), acid.anionTotalCharge());
            products = List.of(saltFormula);
            desc = "Ammonia accepts a proton from " + acid.formula().canonical() + ", forming " + saltFormula.canonical() + ".";
        } else {
            Formula saltFormula = IonicFormula.combine(other.metal(), other.cationCharge(), acid.anionAtoms(), acid.anionTotalCharge());
            products = List.of(saltFormula, WATER);
            desc = other.formula().canonical() + " neutralizes " + acid.formula().canonical()
                    + ", forming " + saltFormula.canonical() + " and water.";
        }

        int[] coeffs = tryBalance(reactants, products);
        if (coeffs == null) return Optional.of(couldNotBalance(a, b));
        List<Species> reactantSpecies = buildSpecies(reactants, coeffs, 0, r -> PhysicalState.AQUEOUS);
        List<Species> productSpecies = buildSpecies(products, coeffs, reactants.size(), ReactionEngine::stateOf);
        return Optional.of(new Reaction("Acid-base neutralization", reactantSpecies, productSpecies, desc));
    }

    private static boolean isBasicOxide(CompoundClass cc) {
        return cc.type() == CompoundClass.Type.OXIDE_BASIC || cc.type() == CompoundClass.Type.OXIDE_AMPHOTERIC;
    }

    // ------------------------------------------------------------------- double displacement -----

    private record IonPair(Formula cationAtoms, int cationCharge, Formula anionAtoms, int anionCharge,
                            PolyatomicIon anionIon, String simpleAnionElement, boolean cationIsHydrogen) {}

    private static IonPair asIonPair(CompoundClass cc) {
        return switch (cc.type()) {
            case BINARY_ACID, OXOACID -> new IonPair(Formula.of(Map.of("H", 1)), 1,
                    cc.anionAtoms(), cc.anionTotalCharge(), cc.anion(), cc.simpleAnionElement(), true);
            case HYDROXIDE_BASE, SALT -> new IonPair(Formula.of(Map.of(cc.metal(), 1)), cc.cationCharge(),
                    cc.anionAtoms(), cc.anionTotalCharge(), cc.anion(), cc.simpleAnionElement(), false);
            default -> null;
        };
    }

    private static Optional<ReactionResult> tryDoubleDisplacement(CompoundClass a, CompoundClass b) {
        IonPair pa = asIonPair(a);
        IonPair pb = asIonPair(b);
        if (pa == null || pb == null) return Optional.empty();

        Formula new1Raw = IonicFormula.combine(pa.cationAtoms(), pa.cationCharge(), pb.anionAtoms(), pb.anionCharge());
        Formula new2Raw = IonicFormula.combine(pb.cationAtoms(), pb.cationCharge(), pa.anionAtoms(), pa.anionCharge());
        List<Formula> slot1 = expandProtonation(new1Raw, pa.cationIsHydrogen(), pb.anionIon(), pb.simpleAnionElement());
        List<Formula> slot2 = expandProtonation(new2Raw, pb.cationIsHydrogen(), pa.anionIon(), pa.simpleAnionElement());

        boolean water = new1Raw.equals(WATER) || new2Raw.equals(WATER);
        boolean gas = slot1.stream().anyMatch(KNOWN_GASES::contains) || slot2.stream().anyMatch(KNOWN_GASES::contains);
        boolean precipitate = isNewInsoluble(slot1) || isNewInsoluble(slot2);

        if (!water && !gas && !precipitate) {
            return Optional.of(new NoReaction("No precipitate, gas, or stable molecular compound (like water) "
                    + "forms between " + a.formula().canonical() + " and " + b.formula().canonical()
                    + " — the ions would remain dissolved and unreacted."));
        }

        List<Formula> reactants = List.of(a.formula(), b.formula());
        List<Formula> products = new ArrayList<>();
        products.addAll(slot1);
        products.addAll(slot2);
        int[] coeffs = tryBalance(reactants, products);
        if (coeffs == null) return Optional.of(couldNotBalance(a, b));

        List<Species> reactantSpecies = buildSpecies(reactants, coeffs, 0, r -> PhysicalState.AQUEOUS);
        List<Species> productSpecies = buildSpecies(products, coeffs, reactants.size(), ReactionEngine::stateOf);

        String type = water && !gas ? "Acid-base neutralization"
                : gas ? "Gas-forming double displacement"
                : "Precipitation (double displacement)";
        String desc = water ? "Neutralization forms water."
                : gas ? "A volatile weak acid/gas escapes, driving the reaction forward."
                : "An insoluble product precipitates out of solution, driving the reaction forward.";
        return Optional.of(new Reaction(type, reactantSpecies, productSpecies, desc));
    }

    private static List<Formula> expandProtonation(Formula raw, boolean cationIsHydrogen,
                                                     PolyatomicIon anionIon, String simpleAnionElement) {
        if (!cationIsHydrogen) return List.of(raw);
        if (anionIon != null) {
            return switch (anionIon.gasOnProtonation()) {
                case CO2 -> List.of(Formula.of("CO2"), WATER);
                case SO2 -> List.of(Formula.of("SO2"), WATER);
                default -> List.of(raw);
            };
        }
        if ("S".equals(simpleAnionElement)) return List.of(Formula.of("H2S"));
        return List.of(raw);
    }

    private static boolean isNewInsoluble(List<Formula> slot) {
        if (slot.size() != 1) return false;
        Formula f = slot.get(0);
        if (f.equals(WATER) || KNOWN_GASES.contains(f)) return false;
        CompoundClass cc = CompoundClassifier.classify(f);
        if (cc.type() != CompoundClass.Type.SALT && cc.type() != CompoundClass.Type.HYDROXIDE_BASE) return false;
        return SolubilityRules.of(cc) == SolubilityRules.Solubility.INSOLUBLE;
    }

    // ------------------------------------------------------------------------------ synthesis -----

    private static final Map<Set<String>, String> KNOWN_NONMETAL_PAIRS = Map.ofEntries(
            Map.entry(Set.of("H", "O"), "H2O"), Map.entry(Set.of("H", "N"), "NH3"),
            Map.entry(Set.of("H", "Cl"), "HCl"), Map.entry(Set.of("H", "Br"), "HBr"),
            Map.entry(Set.of("H", "I"), "HI"), Map.entry(Set.of("H", "F"), "HF"),
            Map.entry(Set.of("H", "S"), "H2S"), Map.entry(Set.of("C", "O"), "CO2")
    );

    private static Optional<ReactionResult> trySynthesis(CompoundClass a, CompoundClass b) {
        boolean aElem = a.type() == CompoundClass.Type.ELEMENT_METAL || a.type() == CompoundClass.Type.ELEMENT_NONMETAL;
        boolean bElem = b.type() == CompoundClass.Type.ELEMENT_METAL || b.type() == CompoundClass.Type.ELEMENT_NONMETAL;
        if (!aElem || !bElem) return Optional.empty();

        Formula product;
        if (a.type() == CompoundClass.Type.ELEMENT_METAL && b.type() == CompoundClass.Type.ELEMENT_NONMETAL) {
            product = metalNonmetalProduct(a.metal(), b.formula().soleElement());
        } else if (b.type() == CompoundClass.Type.ELEMENT_METAL && a.type() == CompoundClass.Type.ELEMENT_NONMETAL) {
            product = metalNonmetalProduct(b.metal(), a.formula().soleElement());
        } else if (a.type() == CompoundClass.Type.ELEMENT_NONMETAL && b.type() == CompoundClass.Type.ELEMENT_NONMETAL) {
            String known = KNOWN_NONMETAL_PAIRS.get(Set.of(a.formula().soleElement(), b.formula().soleElement()));
            product = known == null ? null : Formula.of(known);
        } else {
            return Optional.empty(); // metal + metal: forms an alloy, not a simple ionic/covalent reaction
        }
        if (product == null) return Optional.empty();

        List<Formula> reactants = List.of(a.formula(), b.formula());
        List<Formula> products = List.of(product);
        int[] coeffs = tryBalance(reactants, products);
        if (coeffs == null) return Optional.of(couldNotBalance(a, b));

        List<Species> reactantSpecies = buildSpecies(reactants, coeffs, 0, ReactionEngine::stateOfElement);
        List<Species> productSpecies = buildSpecies(products, coeffs, reactants.size(), ReactionEngine::stateOfElement);
        String desc = "Direct combination forms " + product.canonical() + ".";
        return Optional.of(new Reaction("Synthesis (direct combination)", reactantSpecies, productSpecies, desc));
    }

    private static Formula metalNonmetalProduct(String metal, String nonmetal) {
        int anionCharge = Element.defaultAnionCharge(nonmetal);
        if (anionCharge == 0) return null;
        int cationCharge = Element.defaultCationCharge(metal);
        return IonicFormula.combine(metal, cationCharge, Formula.of(Map.of(nonmetal, 1)), anionCharge);
    }

    // ------------------------------------------------------------------------------ combustion -----

    private static final Set<String> COMBUSTIBLE_ELEMENTS = Set.of("C", "H", "O", "N", "S");

    private static Optional<ReactionResult> tryCombustion(CompoundClass a, CompoundClass b) {
        CompoundClass organic, oxygen;
        if (a.type() == CompoundClass.Type.ORGANIC && b.formula().equals(Formula.of("O2"))) {
            organic = a; oxygen = b;
        } else if (b.type() == CompoundClass.Type.ORGANIC && a.formula().equals(Formula.of("O2"))) {
            organic = b; oxygen = a;
        } else {
            return Optional.empty();
        }
        if (!COMBUSTIBLE_ELEMENTS.containsAll(organic.formula().elements())) return Optional.empty();

        List<Formula> products = new ArrayList<>();
        if (organic.formula().contains("C")) products.add(Formula.of("CO2"));
        if (organic.formula().contains("H")) products.add(WATER);
        if (organic.formula().contains("N")) products.add(Formula.of("N2"));
        if (organic.formula().contains("S")) products.add(Formula.of("SO2"));
        if (products.isEmpty()) return Optional.empty();

        List<Formula> reactants = List.of(organic.formula(), oxygen.formula());
        int[] coeffs = tryBalance(reactants, products);
        if (coeffs == null) return Optional.of(couldNotBalance(a, b));

        List<Species> reactantSpecies = List.of(
                new Species(organic.formula(), coeffs[0], PhysicalState.LIQUID),
                new Species(oxygen.formula(), coeffs[1], PhysicalState.GAS));
        List<Species> productSpecies = buildSpecies(products, coeffs, 2, f -> PhysicalState.GAS);
        return Optional.of(new Reaction("Combustion", reactantSpecies, productSpecies,
                "Full combustion oxidizes every atom to its highest common oxide."));
    }

    // ------------------------------------------------------------------------------- helpers -----

    private static int[] tryBalance(List<Formula> reactants, List<Formula> products) {
        try {
            return EquationBalancer.balance(reactants, products);
        } catch (EquationBalancer.BalanceException e) {
            return null;
        }
    }

    private static NoReaction couldNotBalance(CompoundClass a, CompoundClass b) {
        return new NoReaction("Recognized a possible reaction between " + a.formula().canonical() + " and "
                + b.formula().canonical() + " but could not balance the equation (unsupported edge case).");
    }

    private interface StateFn { PhysicalState state(Formula f); }

    private static List<Species> buildSpecies(List<Formula> formulas, int[] coeffs, int offset, StateFn stateFn) {
        List<Species> list = new ArrayList<>();
        for (int i = 0; i < formulas.size(); i++) {
            list.add(new Species(formulas.get(i), coeffs[offset + i], stateFn.state(formulas.get(i))));
        }
        return list;
    }

    static PhysicalState stateOf(Formula f) {
        if (f.equals(WATER)) return PhysicalState.LIQUID;
        if (KNOWN_GASES.contains(f)) return PhysicalState.GAS;
        CompoundClass cc = CompoundClassifier.classify(f);
        if (cc.type() == CompoundClass.Type.SALT || cc.type() == CompoundClass.Type.HYDROXIDE_BASE) {
            return SolubilityRules.of(cc) == SolubilityRules.Solubility.INSOLUBLE
                    ? PhysicalState.PRECIPITATE : PhysicalState.AQUEOUS;
        }
        return PhysicalState.SOLID;
    }

    private static PhysicalState stateOfElement(Formula f) {
        if (KNOWN_GASES.contains(f)) return PhysicalState.GAS;
        return PhysicalState.SOLID;
    }
}
