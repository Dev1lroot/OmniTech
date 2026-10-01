/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.blocks.electrical.suspension_insulator.SuspensionInsulatorBlockEntity;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import com.dev1lroot.mcmods.omnitech.io.IElectricSupplier;
import com.dev1lroot.mcmods.omnitech.io.IMultimeterReadable;
import com.dev1lroot.mcmods.omnitech.util.ConductorMetals;
import com.dev1lroot.mcmods.omnitech.util.ElectricLossTracker;
import com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil;
import com.dev1lroot.mcmods.omnitech.util.ElectricUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/**
 * Multimeter — right-click any part of the electric network to read it in chat.
 *
 * <ul>
 *   <li>Wire / relay / suspension insulator: current type, line voltage
 *       (after the drop so far), current and power through it, the I²R loss in
 *       that conductor, and a survey of the whole connected line — power
 *       delivered, total line loss and transmission efficiency. Insulators also
 *       list their spans (metal, length, resistance).</li>
 *   <li>Converters and transformers: input, output and conversion loss.</li>
 *   <li>Other sources: what they push into the network.</li>
 * </ul>
 * Readings come from {@link ElectricLossTracker} (1 s averages).
 */
public class MultimeterItem extends Item {

    /** Conductors surveyed for the network summary. */
    private static final int SURVEY_LIMIT = 8192;

    public MultimeterItem(Properties properties) {
        super(properties);
    }

    /** Runs before the block's own interaction, so machines with a GUI can be probed too. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Consumer<Component> out = player::sendSystemMessage;

        out.accept(Component.translatable("multimeter.omnitech.header", state.getBlock().getName(),
                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.YELLOW));

        BlockEntity be = level.getBlockEntity(pos);
        if (ElectricNetworkUtil.isConductor(state)) {
            readConductor(level, pos, state, be, out);
        } else if (be instanceof IMultimeterReadable readable) {
            readable.appendMultimeterReadout(out);
        } else if (be instanceof IElectricSupplier supplier) {
            float volts = supplier.getSupplyVoltage();
            double watts = ElectricUnits.toWatts(supplier.getEuSupply());
            out.accept(Component.translatable("multimeter.omnitech.source",
                    supplier.getCurrentType().label(),
                    ElectricUnits.formatVoltage(volts),
                    ElectricUnits.formatCurrent(volts > 0 ? ElectricUnits.current(watts, volts) : 0),
                    ElectricUnits.formatPower(watts)));
        } else if (be instanceof IElectricReceiver) {
            out.accept(Component.translatable("multimeter.omnitech.consumer").withStyle(ChatFormatting.GRAY));
        } else {
            out.accept(Component.translatable("multimeter.omnitech.not_electric").withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    private static void readConductor(Level level, BlockPos pos, BlockState state, BlockEntity be,
            Consumer<Component> out) {
        ElectricLossTracker.Reading here = ElectricLossTracker.read(level, pos);
        if (!here.active()) {
            out.accept(Component.translatable("multimeter.omnitech.dead").withStyle(ChatFormatting.GRAY));
        } else {
            double watts = ElectricUnits.toWatts(here.flow());
            out.accept(Component.translatable("multimeter.omnitech.line",
                    here.type().label(),
                    ElectricUnits.formatVoltage(here.volts()),
                    ElectricUnits.formatCurrent(here.volts() > 0 ? ElectricUnits.current(watts, here.volts()) : 0),
                    ElectricUnits.formatPower(watts)));
            out.accept(Component.translatable("multimeter.omnitech.segment_loss",
                    ElectricUnits.formatPower(ElectricUnits.toWatts(here.loss())),
                    ElectricUnits.formatResistance(ElectricNetworkUtil.conductorOhms(state))));
        }

        if (be instanceof SuspensionInsulatorBlockEntity insulator) {
            for (SuspensionInsulatorBlockEntity.Link link : insulator.getLinks()) {
                BlockPos o = link.other();
                out.accept(Component.translatable("multimeter.omnitech.span",
                        o.getX(), o.getY(), o.getZ(),
                        ConductorMetals.displayName(link.metal()),
                        String.format("%.1f", link.length()),
                        ElectricUnits.formatResistance(link.resistance()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        // Network survey: every conductor reachable from here.
        double[] sum = new double[3]; // loss, delivered, count
        ElectricNetworkUtil.surveyNetwork(level, pos, SURVEY_LIMIT, p -> {
            ElectricLossTracker.Reading r = ElectricLossTracker.read(level, p);
            sum[0] += r.loss();
            sum[1] += r.delivered();
            sum[2]++;
        });
        double lossW = ElectricUnits.toWatts(sum[0]);
        double deliveredW = ElectricUnits.toWatts(sum[1]);
        double input = lossW + deliveredW;
        out.accept(Component.translatable("multimeter.omnitech.network",
                (int) sum[2],
                ElectricUnits.formatPower(deliveredW),
                ElectricUnits.formatPower(lossW),
                String.format("%.1f%%", input > 0 ? 100.0 * lossW / input : 0.0),
                String.format("%.1f%%", input > 0 ? 100.0 * deliveredW / input : 100.0))
                .withStyle(lossW > 0.1 * Math.max(input, 1e-9) ? ChatFormatting.RED : ChatFormatting.GREEN));
    }
}
