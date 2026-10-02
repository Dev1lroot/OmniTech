/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.blocks.thermal.thermal_conductor.ThermalConductorBlockEntity;
import com.dev1lroot.mcmods.omnitech.io.IColdReceiver;
import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
import com.dev1lroot.mcmods.omnitech.io.IThermalNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Direct contact between a heat source and a heat-consuming machine.
 *
 * <p>Sources ({@link IThermalNode}) only expose a temperature and sinks
 * ({@link IHeatReceiver} / {@link IColdReceiver}) only accept pushed heat, so without
 * a thermal conductor in between nothing moved. Sources call this every tick to do
 * what a conductor would: same {@link ThermalConductorBlockEntity#CONDUCTIVITY} rate,
 * and what the machine absorbs leaves the source.
 */
public final class ThermalTransfer {

    private ThermalTransfer() {}

    public static void pushToReceivers(Level level, BlockPos pos, IThermalNode source) {
        for (Direction dir : Direction.values()) {
            BlockEntity n = level.getBlockEntity(pos.relative(dir));
            // Conductors and other thermal nodes run their own exchange with us
            if (n == null || n instanceof IThermalNode) continue;
            float t = source.getTemperature();
            if (t > IThermalNode.AMBIENT_TEMP + 1f && n instanceof IHeatReceiver hr) {
                int deliver = (int) (ThermalConductorBlockEntity.CONDUCTIVITY * (t - IThermalNode.AMBIENT_TEMP));
                if (deliver > 0) {
                    int absorbed = hr.addHeat(deliver);
                    if (absorbed > 0) source.applyHeat(-absorbed);
                }
            } else if (t < IThermalNode.AMBIENT_TEMP - 1f && n instanceof IColdReceiver cr) {
                int deliver = (int) (ThermalConductorBlockEntity.CONDUCTIVITY * (IThermalNode.AMBIENT_TEMP - t));
                if (deliver > 0) {
                    int absorbed = cr.addCold(deliver);
                    if (absorbed > 0) source.applyHeat(absorbed);
                }
            }
        }
    }
}
