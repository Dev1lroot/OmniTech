/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.FluidPhase;
import com.dev1lroot.mcmods.omnitech.FluidPhaseUtil;
import com.dev1lroot.mcmods.omnitech.FluidPhysicsRegistry;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase bookkeeping for {@code omnitech:solution} mixtures.
 *
 * <p>A mixture carries one temperature and one pressure for the whole stack, but every component
 * has its own phase diagram, so at those conditions the components can be in different phases:
 * water condensed while ethanol is still a vapour, one frozen while the rest stays liquid.
 * {@link #fractions} evaluates each component and sorts it into a {@link Fractions} — solid,
 * liquid, vapour, gas — by volume. A component that is suspended (<em>undissolved</em>) is always
 * counted as solid, whatever its phase diagram says.
 *
 * <p>Machines that change a mixture's temperature or pressure (heat exchanger, compressor,
 * decompressor) keep it as one mixture in their tanks and let {@link #pushByPhase} separate it on
 * the way out: the condensed part (liquid + solids) and the gas-like part (vapour + gas) leave as
 * two streams, each in the state a pipe can carry.
 */
public final class SolutionPhases {
    private SolutionPhases() {}

    /** A mixture split by phase; every part keeps its amount in mB and its dissolved flag. */
    public record Fractions(Solution solid, Solution liquid, Solution vapour, Solution gas) {
        /** Liquid and everything solid in or under it. */
        public Solution condensed() { return liquid.plus(solid); }
        /** Vapour and gas: what rises and spreads. */
        public Solution gasLike()   { return vapour.plus(gas); }
        public int total() {
            return solid.totalAmount() + liquid.totalAmount() + vapour.totalAmount() + gas.totalAmount();
        }
        /** How many of the four phases are actually present. */
        public int phaseCount() {
            int n = 0;
            if (!solid.isEmpty())  n++;
            if (!liquid.isEmpty()) n++;
            if (!vapour.isEmpty()) n++;
            if (!gas.isEmpty())    n++;
            return n;
        }
    }

    /** The phase one component is in at the given conditions (undissolved → solid). */
    public static FluidPhase phaseOf(Solution.Part part, int tempC, int pressureKPa) {
        if (!part.dissolved()) return FluidPhase.SOLID;
        FluidPhase phase = FluidPhaseUtil.getPhase(tempC, pressureKPa,
                FluidPhysicsRegistry.get(part.fluid()).phaseDiagram());
        return phase == null ? FluidPhase.LIQUID : phase;   // no phase data: assume a liquid
    }

    public static Fractions fractions(Solution mix, int tempC, int pressureKPa) {
        Solution solid = Solution.EMPTY, liquid = Solution.EMPTY, vapour = Solution.EMPTY, gas = Solution.EMPTY;
        for (Solution.Part p : mix.components()) {
            switch (phaseOf(p, tempC, pressureKPa)) {
                case SOLID  -> solid  = solid.plus(p.fluid(), p.amount(), p.dissolved());
                case LIQUID -> liquid = liquid.plus(p.fluid(), p.amount(), p.dissolved());
                case VAPOUR -> vapour = vapour.plus(p.fluid(), p.amount(), p.dissolved());
                default     -> gas    = gas.plus(p.fluid(), p.amount(), p.dissolved());   // gas, supercritical, plasma
            }
        }
        return new Fractions(solid, liquid, vapour, gas);
    }

    /** {@link #fractions(Solution, int, int)} of a stack at its own temperature and pressure. */
    public static Fractions fractions(FluidStack stack) {
        return fractions(SolutionFluids.toSolution(stack),
                FluidNetworkUtil.fluidTemp(stack), FluidNetworkUtil.fluidPressure(stack));
    }

    /**
     * True if a solid fraction contains suspended (undissolved) solids, which ride along with a
     * liquid or make a slurry. Dissolved components that are merely frozen do not count: a
     * fraction made only of those is a block of ice and stays where it is.
     */
    public static boolean hasSuspended(Solution solid) {
        for (Solution.Part p : solid.components()) if (!p.dissolved()) return true;
        return false;
    }

    /** The tightest temperature ceiling (°C) over every component of a stack. */
    public static int maxTemp(FluidStack stack) {
        int max = Integer.MAX_VALUE;
        for (Solution.Part p : SolutionFluids.toSolution(stack).components()) {
            max = Math.min(max, FluidPhysicsRegistry.get(p.fluid()).maxTemp());
        }
        return max == Integer.MAX_VALUE ? FluidPhysicsRegistry.FluidPhysics.DEFAULT.maxTemp() : max;
    }

    /** The tightest temperature floor (°C) over every component of a stack. */
    public static int minTemp(FluidStack stack) {
        int min = Integer.MIN_VALUE;
        for (Solution.Part p : SolutionFluids.toSolution(stack).components()) {
            min = Math.max(min, FluidPhysicsRegistry.get(p.fluid()).minTemp());
        }
        return min == Integer.MIN_VALUE ? FluidPhysicsRegistry.FluidPhysics.DEFAULT.minTemp() : min;
    }

    // ── Separated output ──────────────────────────────────────────────────────

    /**
     * Pushes up to {@code maxAmount} mB of each phase stream of the mixture {@code tank} into
     * {@code to}: first the condensed part (liquid and suspended solids), then the gas-like part.
     * Each stream is offered as its own stack at the tank's temperature and pressure — a stream
     * that is a single fluid arrives as that plain fluid — and only what the target accepts leaves
     * the tank. A frozen fraction never leaves.
     *
     * @return what is left in the tank ({@code tank} itself if nothing moved, or if it is not a mixture)
     */
    public static FluidStack pushByPhase(FluidStack tank, ResourceHandler<FluidResource> to, int maxAmount) {
        if (tank.isEmpty() || !SolutionFluids.isMixture(tank)) return tank;

        int temp = FluidNetworkUtil.fluidTemp(tank);
        int pressure = FluidNetworkUtil.fluidPressure(tank);
        Solution mix = SolutionFluids.toSolution(tank);
        Fractions f = fractions(mix, temp, pressure);

        List<Solution> streams = new ArrayList<>(2);
        Solution condensed = f.condensed();
        if (!f.liquid().isEmpty() || hasSuspended(f.solid())) streams.add(condensed);
        if (!f.gasLike().isEmpty()) streams.add(f.gasLike());

        Solution remaining = mix;
        boolean moved = false;
        for (Solution stream : streams) {
            Solution batch = stream.scaledTo(maxAmount);
            FluidStack out = SolutionFluids.toStack(batch, temp, pressure);
            int accepted;
            try (var tx = Transaction.openRoot()) {
                accepted = to.insert(FluidResource.of(out), out.getAmount(), tx);
                if (accepted > 0) tx.commit();
            }
            if (accepted <= 0) continue;
            remaining = remaining.minus(batch.scaledTo(accepted));
            moved = true;
        }
        return moved ? SolutionFluids.toStack(remaining, temp, pressure) : tank;
    }
}
