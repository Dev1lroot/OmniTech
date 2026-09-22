/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The composition of a {@code omnitech:solution} fluid stack, as <em>ratios</em>: each entry's
 * parts-per-million always sum to exactly {@value #TOTAL}, and the stack's own amount says how many
 * mB that is.
 *
 * <p>Storing ratios rather than mB is what lets a mixture behave like any other fluid: a machine
 * that shrinks or extracts from the stack ({@code copyWithAmount}) leaves the mixture unchanged,
 * and entries are kept in a canonical order so two stacks holding the same mixture are equal — which
 * pipes and tanks rely on when they match resources.
 *
 * <p>Convert with {@link #of(Solution)} / {@link #toSolution(int)}; see
 * {@link com.dev1lroot.mcmods.omnitech.util.SolutionFluids} for the stack-level helpers.
 */
public record Mixture(List<Entry> entries) {

    public static final int TOTAL = 1_000_000;

    /** One component's share of the mixture. */
    public record Entry(Fluid fluid, int ppm, boolean dissolved) {}

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(Entry::fluid),
            Codec.INT.fieldOf("ppm").forGetter(Entry::ppm),
            Codec.BOOL.optionalFieldOf("dissolved", true).forGetter(Entry::dissolved)
    ).apply(i, Entry::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(Registries.FLUID), Entry::fluid,
                    ByteBufCodecs.VAR_INT,                    Entry::ppm,
                    ByteBufCodecs.BOOL,                       Entry::dissolved,
                    Entry::new);

    public static final Codec<Mixture> CODEC =
            ENTRY_CODEC.listOf().xmap(Mixture::new, Mixture::entries);

    public static final StreamCodec<RegistryFriendlyByteBuf, Mixture> STREAM_CODEC =
            ENTRY_STREAM_CODEC.apply(ByteBufCodecs.list()).map(Mixture::new, Mixture::entries);

    /** Canonical ordering: by registry id, dissolved before undissolved. */
    private static final Comparator<Solution.Part> CANONICAL = Comparator
            .<Solution.Part, String>comparing(p -> BuiltInRegistries.FLUID.getKey(p.fluid()).toString())
            .thenComparing(p -> !p.dissolved());

    /** The ratios of {@code solution}, canonicalised so equal mixtures produce equal records. */
    public static Mixture of(Solution solution) {
        List<Solution.Part> parts = new ArrayList<>(solution.components());
        parts.sort(CANONICAL);

        int[] weights = new int[parts.size()];
        for (int i = 0; i < weights.length; i++) weights[i] = parts.get(i).amount();
        int[] ppm = Solution.apportion(weights, TOTAL);

        // A component that is present must stay present, however small: never round it to 0 ppm.
        for (int i = 0; i < ppm.length; i++) {
            if (weights[i] > 0 && ppm[i] == 0) {
                int largest = 0;
                for (int j = 1; j < ppm.length; j++) if (ppm[j] > ppm[largest]) largest = j;
                if (ppm[largest] > 1) { ppm[largest]--; ppm[i] = 1; }
            }
        }

        List<Entry> entries = new ArrayList<>(parts.size());
        for (int i = 0; i < ppm.length; i++) {
            if (ppm[i] > 0) entries.add(new Entry(parts.get(i).fluid(), ppm[i], parts.get(i).dissolved()));
        }
        return new Mixture(List.copyOf(entries));
    }

    /**
     * This mixture as absolute amounts adding up to exactly {@code amount} mB. Components too small
     * to get even 1 mB of that amount drop out.
     */
    public Solution toSolution(int amount) {
        int[] weights = new int[entries.size()];
        for (int i = 0; i < weights.length; i++) weights[i] = entries.get(i).ppm();
        int[] mb = Solution.apportion(weights, amount);

        List<Solution.Part> parts = new ArrayList<>(entries.size());
        for (int i = 0; i < mb.length; i++) {
            if (mb[i] > 0) parts.add(Solution.Part.of(entries.get(i).fluid(), mb[i], entries.get(i).dissolved()));
        }
        return new Solution(parts);
    }
}
