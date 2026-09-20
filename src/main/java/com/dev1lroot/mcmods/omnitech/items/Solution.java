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
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The contents of a {@link FlaskItem} or {@link PipetteItem}: zero or more distinct fluids
 * sharing the container's mB of space, each tracked by its own amount so the tooltip can show
 * every component's name, mL, and share of the total.
 *
 * <p>Unlike a real {@code Fluid}, a solution is never registered — it only ever exists as
 * this container-local record, since NeoForge fluids are static registry entries and an
 * arbitrary runtime blend (e.g. "10 mB water + 20 mB ethanol") has no such entry to be.
 */
public record Solution(List<ResourceStack<FluidResource>> components) {

    public static final Solution EMPTY = new Solution(List.of());

    private static final Codec<ResourceStack<FluidResource>> COMPONENT_CODEC =
            RecordCodecBuilder.create(i -> i.group(
                    FluidResource.OPTIONAL_CODEC.fieldOf("fluid").forGetter(ResourceStack::resource),
                    Codec.INT.fieldOf("amount").forGetter(ResourceStack::amount)
            ).apply(i, ResourceStack::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, ResourceStack<FluidResource>> COMPONENT_STREAM_CODEC =
            StreamCodec.composite(
                    FluidResource.STREAM_CODEC, ResourceStack::resource,
                    ByteBufCodecs.INT,          ResourceStack::amount,
                    ResourceStack::new);

    public static final Codec<Solution> CODEC =
            COMPONENT_CODEC.listOf().xmap(Solution::new, Solution::components);

    public static final StreamCodec<RegistryFriendlyByteBuf, Solution> STREAM_CODEC =
            COMPONENT_STREAM_CODEC.apply(ByteBufCodecs.list())
                    .map(Solution::new, Solution::components);

    public boolean isEmpty() { return components.isEmpty(); }

    public int totalAmount() {
        int sum = 0;
        for (var c : components) sum += c.amount();
        return sum;
    }

    /** The component holding the largest amount, or {@code null} if this solution is empty. */
    public ResourceStack<FluidResource> dominant() {
        ResourceStack<FluidResource> best = null;
        for (var c : components) if (best == null || c.amount() > best.amount()) best = c;
        return best;
    }

    /**
     * Returns a new solution with {@code amount} mB of {@code fluid} merged in — added to the
     * matching existing component if there is one, otherwise appended as a new component.
     * Does not enforce any capacity; callers (see {@link FlaskItem#addFluid}) clamp first.
     */
    public Solution plus(Fluid fluid, int amount) {
        if (amount <= 0 || fluid == null) return this;
        List<ResourceStack<FluidResource>> next = new ArrayList<>(components);
        for (int i = 0; i < next.size(); i++) {
            ResourceStack<FluidResource> c = next.get(i);
            if (c.resource().is(fluid)) {
                next.set(i, new ResourceStack<>(c.resource(), c.amount() + amount));
                return new Solution(next);
            }
        }
        next.add(new ResourceStack<>(FluidResource.of(fluid), amount));
        return new Solution(next);
    }

    /**
     * Returns every other component of {@code other} merged into this one (same fluid →
     * amounts add; new fluid → appended). Used when pouring one container's solution into
     * another (pipette → flask).
     */
    public Solution plus(Solution other) {
        Solution result = this;
        for (var c : other.components) {
            result = result.plus(c.resource().toStack(1).getFluid(), c.amount());
        }
        return result;
    }

    /**
     * Returns a solution with the same fluid ratios as this one, scaled to a total of exactly
     * {@code targetTotal} mB (clamped to this solution's own total if {@code targetTotal} is
     * larger). Used for proportional pipette draws: pulling 10 mB out of a flask that's 30 %
     * water / 70 % ethanol yields 3 mB water + 7 mB ethanol, keeping the ratio intact — real lab
     * technique, not a "pick one fluid" shortcut.
     */
    public Solution scaledTo(int targetTotal) {
        int original = totalAmount();
        if (targetTotal <= 0 || original <= 0) return EMPTY;
        if (targetTotal >= original) return this;

        List<ResourceStack<FluidResource>> next = new ArrayList<>(components.size());
        int assigned = 0;
        for (int i = 0; i < components.size(); i++) {
            ResourceStack<FluidResource> c = components.get(i);
            int amt = (i == components.size() - 1)
                    ? targetTotal - assigned
                    : (int) ((long) c.amount() * targetTotal / original);
            if (amt > 0) next.add(new ResourceStack<>(c.resource(), amt));
            assigned += amt;
        }
        return new Solution(next);
    }
}
