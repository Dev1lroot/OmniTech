/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.ArrayList;
import java.util.List;

/**
 * A mixture of fluids sharing one container's volume, each component tracked by its own amount
 * (mB) and by whether it is <em>dissolved</em> in the mixture or merely suspended in it as
 * undissolved solid (a dust / slurry). Used in absolute mB by {@link FlaskItem},
 * {@link PipetteItem} and the Fermenter; the same mixture travels through pipes and machines as a
 * {@code omnitech:solution} fluid stack carrying a ratio-only {@code Mixture} component — see
 * {@link com.dev1lroot.mcmods.omnitech.util.SolutionFluids}.
 *
 * <p>A component is identified by its fluid <em>and</em> its dissolved flag: 100 mB of dissolved
 * sugar and 100 mB of sugar dust are two separate components.
 */
public record Solution(List<Part> components) {

    /** One component: a fluid, how much of it there is, and whether it is dissolved. */
    public record Part(FluidResource resource, int amount, boolean dissolved) {
        public static Part of(Fluid fluid, int amount, boolean dissolved) {
            return new Part(FluidResource.of(fluid), amount, dissolved);
        }
        public Fluid fluid() { return resource.getFluid(); }
        public Part withAmount(int newAmount) { return new Part(resource, newAmount, dissolved); }
        boolean isKey(Fluid fluid, boolean dissolvedFlag) {
            return dissolved == dissolvedFlag && resource.is(fluid);
        }
    }

    public static final Solution EMPTY = new Solution(List.of());

    private static final Codec<Part> PART_CODEC = RecordCodecBuilder.create(i -> i.group(
            FluidResource.OPTIONAL_CODEC.fieldOf("fluid").forGetter(Part::resource),
            Codec.INT.fieldOf("amount").forGetter(Part::amount),
            // absent in flasks saved before the flag existed -> they were plain dissolved fluids
            Codec.BOOL.optionalFieldOf("dissolved", true).forGetter(Part::dissolved)
    ).apply(i, Part::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, Part> PART_STREAM_CODEC =
            StreamCodec.composite(
                    FluidResource.STREAM_CODEC, Part::resource,
                    ByteBufCodecs.INT,          Part::amount,
                    ByteBufCodecs.BOOL,         Part::dissolved,
                    Part::new);

    public static final Codec<Solution> CODEC =
            PART_CODEC.listOf().xmap(Solution::new, Solution::components);

    public static final StreamCodec<RegistryFriendlyByteBuf, Solution> STREAM_CODEC =
            PART_STREAM_CODEC.apply(ByteBufCodecs.list())
                    .map(Solution::new, Solution::components);

    public boolean isEmpty() { return components.isEmpty(); }

    public int totalAmount() {
        int sum = 0;
        for (Part c : components) sum += c.amount();
        return sum;
    }

    /** The component holding the largest amount, or {@code null} if this solution is empty. */
    public Part dominant() {
        Part best = null;
        for (Part c : components) if (best == null || c.amount() > best.amount()) best = c;
        return best;
    }

    /** How many mB of {@code fluid} this solution holds, dissolved or not (0 if absent). */
    public int amountOf(Fluid fluid) {
        if (fluid == null) return 0;
        int sum = 0;
        for (Part c : components) if (c.resource().is(fluid)) sum += c.amount();
        return sum;
    }

    /** True if every component is dissolved (nothing is left as suspended solid). */
    public boolean isFullyDissolved() {
        for (Part c : components) if (!c.dissolved()) return false;
        return true;
    }

    /**
     * Returns a new solution with up to {@code amount} mB of {@code fluid} removed, taking from
     * matching components in order. A component drained to zero disappears entirely, so
     * {@link #isEmpty()} stays meaningful.
     */
    public Solution minus(Fluid fluid, int amount) {
        if (amount <= 0 || fluid == null) return this;
        List<Part> next = new ArrayList<>(components.size());
        int remaining = amount;
        for (Part c : components) {
            if (remaining > 0 && c.resource().is(fluid)) {
                int taken = Math.min(remaining, c.amount());
                remaining -= taken;
                if (c.amount() - taken > 0) next.add(c.withAmount(c.amount() - taken));
            } else {
                next.add(c);
            }
        }
        return new Solution(next);
    }

    /**
     * Returns a new solution with every component of {@code other} removed from the component
     * with the same fluid <em>and</em> dissolved flag (clamped at what is actually there).
     */
    public Solution minus(Solution other) {
        List<Part> next = new ArrayList<>(components);
        for (Part o : other.components) {
            for (int i = 0; i < next.size(); i++) {
                Part c = next.get(i);
                if (c.isKey(o.fluid(), o.dissolved())) {
                    int left = c.amount() - o.amount();
                    if (left > 0) next.set(i, c.withAmount(left)); else next.remove(i);
                    break;
                }
            }
        }
        return new Solution(next);
    }

    /** Merges {@code amount} mB of a dissolved {@code fluid} in. */
    public Solution plus(Fluid fluid, int amount) {
        return plus(fluid, amount, true);
    }

    /**
     * Returns a new solution with {@code amount} mB of {@code fluid} merged in — added to the
     * component with the same fluid and dissolved flag if there is one, otherwise appended.
     * Does not enforce any capacity; callers clamp first.
     */
    public Solution plus(Fluid fluid, int amount, boolean dissolved) {
        if (amount <= 0 || fluid == null) return this;
        List<Part> next = new ArrayList<>(components);
        for (int i = 0; i < next.size(); i++) {
            Part c = next.get(i);
            if (c.isKey(fluid, dissolved)) {
                next.set(i, c.withAmount(c.amount() + amount));
                return new Solution(next);
            }
        }
        next.add(Part.of(fluid, amount, dissolved));
        return new Solution(next);
    }

    /** Returns every component of {@code other} merged into this one, flags preserved. */
    public Solution plus(Solution other) {
        Solution result = this;
        for (Part c : other.components) result = result.plus(c.fluid(), c.amount(), c.dissolved());
        return result;
    }

    /**
     * Returns a solution with the same ratios as this one, scaled to a total of exactly
     * {@code targetTotal} mB (this solution itself if {@code targetTotal} is at least its own
     * total, {@link #EMPTY} if it is not positive). Rounding uses the largest-remainder method, so
     * the result always sums to exactly {@code targetTotal} and no component ever exceeds what this
     * solution held — used for proportional draws (10 mB out of a 30 % / 70 % flask is 3 + 7).
     */
    public Solution scaledTo(int targetTotal) {
        int original = totalAmount();
        if (targetTotal <= 0 || original <= 0) return EMPTY;
        if (targetTotal >= original) return this;

        int[] weights = new int[components.size()];
        for (int i = 0; i < weights.length; i++) weights[i] = components.get(i).amount();
        int[] scaled = apportion(weights, targetTotal);

        List<Part> next = new ArrayList<>(components.size());
        for (int i = 0; i < scaled.length; i++) {
            if (scaled[i] > 0) next.add(components.get(i).withAmount(scaled[i]));
        }
        return new Solution(next);
    }

    /**
     * Splits {@code total} across {@code weights} proportionally with the largest-remainder
     * method: the returned integers sum to exactly {@code total} (all zero if there is nothing to
     * split).
     */
    public static int[] apportion(int[] weights, int total) {
        int[] out = new int[weights.length];
        long sum = 0;
        for (int w : weights) sum += Math.max(0, w);
        if (sum <= 0 || total <= 0) return out;

        long[] remainder = new long[weights.length];
        long assigned = 0;
        for (int i = 0; i < weights.length; i++) {
            long exact = (long) Math.max(0, weights[i]) * total;
            out[i] = (int) (exact / sum);
            remainder[i] = exact % sum;
            assigned += out[i];
        }
        for (long left = total - assigned; left > 0; left--) {
            int best = -1;
            for (int i = 0; i < weights.length; i++) {
                if (weights[i] > 0 && (best < 0 || remainder[i] > remainder[best])) best = i;
            }
            out[best]++;
            remainder[best] = -1;   // each component gets at most one extra unit
        }
        return out;
    }
}
