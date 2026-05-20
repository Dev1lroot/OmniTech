/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.blocks.analog.AnalogCableBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.power_relay.PowerRelayBlock;
import com.dev1lroot.mcmods.omnitech.io.IAnalogInput;
import com.dev1lroot.mcmods.omnitech.io.IAudioInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * BFS propagation utility for the analog signal network.
 *
 * <p>A signal source ({@link com.dev1lroot.mcmods.omnitech.io.IAnalogOutput}) pushes
 * its value through adjacent {@link AnalogCableBlock} segments to every connected
 * {@link IAnalogInput} endpoint in the same tick.
 */
public final class AnalogNetworkUtil {
    private AnalogNetworkUtil() {}

    /**
     * Push {@code signal} from {@code source} outward through the analog cable
     * network, delivering it to every reachable {@link IAnalogInput} endpoint.
     *
     * @param level            server-side level
     * @param source           position of the signal source
     * @param signal           analog value to deliver (0.0–15.0)
     * @param outputDirections faces from which the source may output
     */
    public static void pushSignal(Level level, BlockPos source, float signal,
            Direction[] outputDirections) {
        Set<BlockPos>        visited = new HashSet<>();
        ArrayDeque<BlockPos> queue   = new ArrayDeque<>();

        visited.add(source);
        for (Direction dir : outputDirections) {
            BlockPos neighbor = source.relative(dir);
            if (visited.add(neighbor)) queue.add(neighbor);
        }

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof AnalogCableBlock) {
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    if (visited.add(next)) queue.add(next);
                }
            } else if (state.getBlock() instanceof PowerRelayBlock && !state.getValue(PowerRelayBlock.POWERED)) {
                for (Direction dir : PowerRelayBlock.passthroughDirections(state)) {
                    BlockPos next = pos.relative(dir);
                    if (visited.add(next)) queue.add(next);
                }
            } else {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof IAnalogInput input) {
                    input.receiveAnalogSignal(signal);
                }
            }
        }
    }

    /**
     * Push raw audio {@code samples} from {@code source} outward through the
     * analog cable network, delivering them to every reachable {@link IAudioInput}
     * endpoint.  Uses the same cable routing as {@link #pushSignal}.
     */
    public static void pushAudio(Level level, BlockPos source, byte[] samples,
            Direction[] outputDirections) {
        if (samples == null || samples.length == 0) return;

        Set<BlockPos>        visited = new HashSet<>();
        ArrayDeque<BlockPos> queue   = new ArrayDeque<>();

        visited.add(source);
        for (Direction dir : outputDirections) {
            BlockPos neighbor = source.relative(dir);
            if (visited.add(neighbor)) queue.add(neighbor);
        }

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof AnalogCableBlock) {
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    if (visited.add(next)) queue.add(next);
                }
            } else if (state.getBlock() instanceof PowerRelayBlock && !state.getValue(PowerRelayBlock.POWERED)) {
                for (Direction dir : PowerRelayBlock.passthroughDirections(state)) {
                    BlockPos next = pos.relative(dir);
                    if (visited.add(next)) queue.add(next);
                }
            } else {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof IAudioInput input) {
                    input.receiveAudio(samples);
                }
            }
        }
    }
}
