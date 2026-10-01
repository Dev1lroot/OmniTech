/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_wire.ElectricWireBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.power_relay.PowerRelayBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.suspension_insulator.SuspensionInsulatorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.suspension_insulator.SuspensionInsulatorBlockEntity;
import com.dev1lroot.mcmods.omnitech.io.CurrentType;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Propagation utility for the Electric network.
 *
 * <p>Electricity always flows to every reachable consumer — the energy output is
 * divided equally among all {@link IElectricReceiver} nodes found. Energy is in
 * kJ (see {@link ElectricUnits}); the source's line voltage and
 * {@link CurrentType} are passed along. Receivers that refuse the current type
 * (a transformer on DC) are not supplied.
 *
 * <h3>Line losses</h3>
 * Every conductor has a resistance: {@value #WIRE_OHMS} Ω per wire block,
 * {@value #RELAY_OHMS} Ω per relay and the span resistance of each overhead line
 * between suspension insulators (see {@link ConductorMetals}). The search is a
 * Dijkstra over resistance, so each receiver is fed through its least-resistance
 * path. A receiver taking power {@code P} through {@code R} at line voltage
 * {@code U} costs the source an extra {@code P²R/U²} (I²R heating), so losses
 * fall with the square of the voltage — that is what transformers are for.
 * Per-conductor traffic is reported to {@link ElectricLossTracker}.
 *
 * <p>Returns the total energy drawn from the source (delivered + lost) so that
 * a buffered source drains exactly that amount.
 */
public final class ElectricNetworkUtil {
    private ElectricNetworkUtil() {}

    /** Resistance of one electric wire block, in ohms. */
    public static final double WIRE_OHMS  = 0.005;
    /** Contact resistance of a closed relay, in ohms. */
    public static final double RELAY_OHMS = 0.001;
    /** Clamp/fitting resistance of a suspension insulator, in ohms. */
    public static final double INSULATOR_OHMS = 0.0005;
    /** No path may eat more than this fraction (voltage collapse). */
    public static final double MAX_LOSS_FRACTION = 0.95;
    /** All sources push their energy in bursts every this many ticks. */
    public static final int CLOCK_TICKS = 5;

    /** AC overload for callers that predate current types. */
    public static float propagateElectricity(Level level, BlockPos source,
            float euAmount, float volts, Direction[] outputDirections) {
        return propagateElectricity(level, source, euAmount, volts, CurrentType.AC, outputDirections);
    }

    /**
     * Propagate energy from {@code source} through the electric network,
     * offering an equal share of {@code euAmount} to every reachable
     * {@link IElectricReceiver} that accepts {@code type}.
     *
     * @param level            server-side level
     * @param source           position of the energy source
     * @param euAmount         total energy in kJ to distribute this network clock
     * @param volts            line voltage of the source
     * @param type             AC or DC
     * @param outputDirections faces from which the source may output energy
     * @return total energy in kJ drawn from the source (delivered plus line loss)
     */
    public static float propagateElectricity(Level level, BlockPos source,
            float euAmount, float volts, CurrentType type, Direction[] outputDirections) {

        if (euAmount <= 0f) return 0f;

        Search search = new Search(level, source, type);
        search.run(outputDirections);

        // Every reached conductor sees the line voltage, even with no load.
        for (BlockPos pos : search.dist.keySet()) {
            ElectricLossTracker.record(level, pos, 0f, 0f, 0f, volts, type);
        }

        if (search.receivers.isEmpty()) return 0f;

        float share = euAmount / search.receivers.size();
        float totalDrawn = 0f;

        for (Map.Entry<IElectricReceiver, Terminal> e : search.receivers.entrySet()) {
            Terminal t = e.getValue();
            double fOffer = lossFraction(share, t.ohms, volts);
            float offered = (float) (share * (1.0 - fOffer));
            float accepted = e.getKey().addElectricity(offered, volts);
            if (accepted <= 0f) continue;

            // Heating follows the current that actually flowed.
            double fActual = lossFraction(accepted, t.ohms, volts);
            float loss = (float) Math.min(accepted * fActual, share - accepted);
            totalDrawn += accepted + loss;

            if (t.via != null) search.report(t, accepted, loss, volts, fActual);
        }

        return totalDrawn;
    }

    /** {@code P·R/U²}: fraction of the power {@code energy} lost through {@code ohms}. */
    private static double lossFraction(float energy, double ohms, float volts) {
        if (ohms <= 0.0 || volts <= 0f) return 0.0;
        double watts = ElectricUnits.toWatts(energy / CLOCK_TICKS);
        return Math.min(MAX_LOSS_FRACTION, watts * ohms / ((double) volts * volts));
    }

    // ── Conductors ───────────────────────────────────────────────────────────

    /** Resistance of the conductor block {@code state}, or -1 if it does not conduct. */
    public static double conductorOhms(BlockState state) {
        if (state.getBlock() instanceof ElectricWireBlock) return WIRE_OHMS;
        if (state.getBlock() instanceof SuspensionInsulatorBlock) return INSULATOR_OHMS;
        if (state.getBlock() instanceof PowerRelayBlock && !state.getValue(PowerRelayBlock.POWERED))
            return RELAY_OHMS;
        return -1.0;
    }

    public static boolean isConductor(BlockState state) {
        return conductorOhms(state) >= 0.0;
    }

    private static Direction[] exits(BlockState state) {
        return state.getBlock() instanceof PowerRelayBlock
                ? PowerRelayBlock.passthroughDirections(state)
                : Direction.values();
    }

    /**
     * Calls {@code step} with every conductor directly connected to the conductor
     * at {@code pos} (through faces and overhead spans) and the resistance of the
     * edge. Used by the search and by the multimeter's network survey.
     */
    public static void forEachLinkedConductor(Level level, BlockPos pos, BlockState state,
            Edge step) {
        for (Direction dir : exits(state)) {
            BlockPos next = pos.relative(dir);
            BlockState ns = level.getBlockState(next);
            double r = conductorOhms(ns);
            if (r >= 0.0) step.accept(next, r);
        }
        if (state.getBlock() instanceof SuspensionInsulatorBlock
                && level.getBlockEntity(pos) instanceof SuspensionInsulatorBlockEntity ins) {
            for (SuspensionInsulatorBlockEntity.Link link : ins.getLinks()) {
                if (!level.isLoaded(link.other())) continue;
                if (!SuspensionInsulatorBlockEntity.isReciprocal(level, pos, link.other())) continue;
                step.accept(link.other(), link.resistance() + INSULATOR_OHMS);
            }
        }
    }

    @FunctionalInterface
    public interface Edge {
        void accept(BlockPos next, double ohms);
    }

    /** Every conductor in the network around {@code start} (breadth first, capped). */
    public static void surveyNetwork(Level level, BlockPos start, int limit, Consumer<BlockPos> out) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() <= limit) {
            BlockPos pos = queue.poll();
            out.accept(pos);
            forEachLinkedConductor(level, pos, level.getBlockState(pos), (next, r) -> {
                if (seen.add(next)) queue.add(next);
            });
        }
    }

    // ── Search ───────────────────────────────────────────────────────────────

    /** Where a receiver is fed from: the last conductor ({@code null} = straight from the source). */
    private record Terminal(BlockPos via, double ohms) {}

    private record Node(BlockPos pos, double dist) {}

    private static final class Search {
        final Level level;
        final BlockPos source;
        final CurrentType type;
        final Map<BlockPos, Double> dist = new HashMap<>();
        final Map<BlockPos, BlockPos> prev = new HashMap<>();
        final Map<BlockPos, Double> edge = new HashMap<>();
        final Map<IElectricReceiver, Terminal> receivers = new LinkedHashMap<>();
        final PriorityQueue<Node> queue = new PriorityQueue<>((a, b) -> Double.compare(a.dist, b.dist));

        Search(Level level, BlockPos source, CurrentType type) {
            this.level = level;
            this.source = source;
            this.type = type;
        }

        void run(Direction[] outputDirections) {
            for (Direction dir : outputDirections) {
                BlockPos next = source.relative(dir);
                double r = conductorOhms(level.getBlockState(next));
                if (r >= 0.0) relax(null, next, r, 0.0);
                else collect(null, next, dir, 0.0);
            }

            while (!queue.isEmpty()) {
                Node node = queue.poll();
                if (node.dist > dist.get(node.pos)) continue; // stale entry
                BlockPos pos = node.pos;
                BlockState state = level.getBlockState(pos);

                forEachLinkedConductor(level, pos, state,
                        (next, r) -> relax(pos, next, r, node.dist));
                for (Direction dir : exits(state)) {
                    collect(pos, pos.relative(dir), dir, node.dist);
                }
            }
        }

        private void relax(BlockPos from, BlockPos next, double r, double fromDist) {
            if (next.equals(source)) return;
            double d = fromDist + r;
            Double old = dist.get(next);
            if (old != null && old <= d) return;
            dist.put(next, d);
            edge.put(next, r);
            if (from != null) prev.put(next, from); else prev.remove(next);
            queue.add(new Node(next, d));
        }

        /**
         * Records the machine at {@code next} (reached from {@code from} going {@code dir}).
         * Nodes pop in increasing resistance, so the first record is the best path.
         * Machines are never marked visited: another conductor may touch a
         * different, accepting face of the same block.
         */
        private void collect(BlockPos from, BlockPos next, Direction dir, double d) {
            if (next.equals(source)) return;
            BlockEntity be = level.getBlockEntity(next);
            if (be instanceof IElectricReceiver receiver
                    && !receivers.containsKey(receiver)
                    && receiver.acceptsElectricityFrom(dir.getOpposite())
                    && receiver.acceptsCurrent(type)) {
                receivers.put(receiver, new Terminal(from, d));
            }
        }

        /** Spreads one delivery's flow, loss and voltage drop over the conductors on its path. */
        void report(Terminal t, float accepted, float loss, float volts, double fActual) {
            double total = t.ohms;
            for (BlockPos c = t.via; c != null; c = prev.get(c)) {
                double upTo = dist.get(c);
                double segment = edge.get(c);
                float segLoss  = total > 0 ? (float) (loss * segment / total) : 0f;
                float carried  = total > 0 ? (float) (accepted + loss * (total - upTo + segment) / total) : accepted;
                float vHere    = total > 0 ? (float) (volts * (1.0 - fActual * upTo / total)) : volts;
                ElectricLossTracker.record(level, c, carried, segLoss,
                        c == t.via ? accepted : 0f, vHere, type);
            }
        }
    }
}
