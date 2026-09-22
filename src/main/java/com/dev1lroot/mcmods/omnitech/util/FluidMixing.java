/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.FluidPhase;
import com.dev1lroot.mcmods.omnitech.FluidPhaseUtil;
import com.dev1lroot.mcmods.omnitech.FluidPhysicsRegistry;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides whether two fluids that meet in the same pipe system may blend into one
 * {@code omnitech:solution} mixture instead of blocking each other.
 *
 * <p>Two stacks blend when
 * <ul>
 *   <li>they are in the same state — both liquid, or both gas-like (gas / vapour / supercritical /
 *       plasma); a frozen fluid never blends, and a gas never blends with a liquid;</li>
 *   <li>every component of one is <em>miscible</em> with every component of the other: neither is
 *       marked {@code "miscible": false} (molten metals, mercury, lava …), and — for liquids —
 *       their {@code "polarity"} agrees. {@code polar} (the default; water, acids, salts) mixes with
 *       {@code polar}, {@code nonpolar} (hydrocarbons) with {@code nonpolar}, and {@code both}
 *       (alcohols, acetone …) with either. Gases ignore polarity.</li>
 * </ul>
 * The traits come from the fluid JSONs, see {@link com.dev1lroot.mcmods.omnitech.FluidLoader}.
 */
public final class FluidMixing {
    private FluidMixing() {}

    public enum Polarity {
        POLAR, NONPOLAR, BOTH;

        public static Polarity parse(String s) {
            return switch (s.toLowerCase()) {
                case "nonpolar", "non_polar", "apolar" -> NONPOLAR;
                case "both", "amphiphilic" -> BOTH;
                default -> POLAR;
            };
        }
    }

    /** Whether a fluid may blend at all, and with what. */
    public record Traits(Polarity polarity, boolean miscible) {
        public static final Traits DEFAULT = new Traits(Polarity.POLAR, true);
    }

    private enum StateClass { LIQUID, GAS, NONE }

    // Keyed by full "namespace:path" like FluidPhysicsRegistry.
    private static final Map<String, Traits> BY_ID = new HashMap<>();

    /** Called by {@link com.dev1lroot.mcmods.omnitech.FluidLoader} (registers source + flowing). */
    public static void register(String namespace, String name, Traits traits) {
        BY_ID.put(namespace + ":" + name, traits);
        BY_ID.put(namespace + ":flowing_" + name, traits);
    }

    public static Traits traits(Fluid fluid) {
        var key = BuiltInRegistries.FLUID.getKey(fluid);
        if (key == null) return Traits.DEFAULT;
        Traits t = BY_ID.get(key.toString());
        if (t != null) return t;
        // Molten anything is an alloy waiting to happen, not a solvent.
        if (key.getPath().replace("flowing_", "").startsWith("molten_")) return new Traits(Polarity.POLAR, false);
        return Traits.DEFAULT;
    }

    // ── Phase ─────────────────────────────────────────────────────────────────

    /**
     * The phase of a stack at its own temperature and pressure. A mixture has no phase diagram of
     * its own: it is judged by the larger of its gas-like and condensed fractions (see
     * {@link SolutionPhases}), and is {@link FluidPhase#SOLID} only if it is entirely frozen.
     * {@code null} if unknown.
     */
    @Nullable
    public static FluidPhase phaseOf(FluidStack stack) {
        if (stack.isEmpty()) return null;
        if (SolutionFluids.isMixture(stack)) {
            var f = SolutionPhases.fractions(stack);
            int gasLike = f.gasLike().totalAmount();
            if (gasLike > f.condensed().totalAmount()) {
                return f.vapour().totalAmount() >= f.gas().totalAmount() ? FluidPhase.VAPOUR : FluidPhase.GAS;
            }
            if (f.liquid().isEmpty() && gasLike == 0 && !SolutionPhases.hasSuspended(f.solid())) return FluidPhase.SOLID;
            return FluidPhase.LIQUID;
        }
        return FluidPhaseUtil.getPhase(FluidNetworkUtil.fluidTemp(stack),
                FluidNetworkUtil.fluidPressure(stack), FluidPhysicsRegistry.get(stack.getFluid()).phaseDiagram());
    }

    private static StateClass stateOf(FluidStack stack) {
        FluidPhase phase = phaseOf(stack);
        if (phase == null || phase == FluidPhase.LIQUID) return StateClass.LIQUID;   // no data: assume a liquid
        if (phase == FluidPhase.SOLID) return StateClass.NONE;
        return StateClass.GAS;
    }

    // ── Compatibility ─────────────────────────────────────────────────────────

    /**
     * True if {@code incoming} may be poured into a container already holding {@code existing}:
     * the same plain fluid, or a miscible blend (see the class comment). Either stack may itself be
     * a mixture; then every pair of components has to be miscible.
     */
    public static boolean canAccept(FluidStack existing, FluidResource incoming) {
        if (existing.isEmpty()) return true;
        if (!SolutionFluids.isMixture(existing) && incoming.is(existing.getFluid())) return true;
        return canMix(existing, incoming.toStack(1));
    }

    /** True if the two stacks may blend into one mixture. */
    public static boolean canMix(FluidStack a, FluidStack b) {
        if (a.isEmpty() || b.isEmpty()) return true;
        boolean samePlain = !SolutionFluids.isMixture(a) && !SolutionFluids.isMixture(b) && a.is(b.getFluid());
        if (samePlain) return true;

        StateClass sa = stateOf(a), sb = stateOf(b);
        if (sa == StateClass.NONE || sb == StateClass.NONE || sa != sb) return false;

        return allMiscible(a, b, sa == StateClass.GAS);
    }

    /**
     * Like {@link #canAccept}, for a machine's own tank: those may hold a mixture of several
     * phases, so only miscibility matters, not whether the two are in the same state. (A gas is
     * taken as miscible with anything that is not marked immiscible.)
     */
    public static boolean canBlend(FluidStack existing, FluidResource incoming) {
        if (existing.isEmpty()) return true;
        if (!SolutionFluids.isMixture(existing) && incoming.is(existing.getFluid())) return true;
        FluidStack in = incoming.toStack(1);
        boolean gases = stateOf(existing) == StateClass.GAS || stateOf(in) == StateClass.GAS;
        return allMiscible(existing, in, gases);
    }

    private static boolean allMiscible(FluidStack a, FluidStack b, boolean gases) {
        Solution parts = SolutionFluids.toSolution(a);
        Solution others = SolutionFluids.toSolution(b);
        for (Solution.Part p : parts.components()) {
            for (Solution.Part q : others.components()) {
                if (p.fluid() == q.fluid()) continue;
                if (!miscible(p.fluid(), q.fluid(), gases)) return false;
            }
        }
        return true;
    }

    private static boolean miscible(Fluid x, Fluid y, boolean gases) {
        Traits tx = traits(x), ty = traits(y);
        if (!tx.miscible() || !ty.miscible()) return false;
        if (gases) return true;
        return tx.polarity() == Polarity.BOTH || ty.polarity() == Polarity.BOTH
                || tx.polarity() == ty.polarity();
    }
}
