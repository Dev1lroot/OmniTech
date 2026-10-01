/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.power_converter;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.CurrentType;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import com.dev1lroot.mcmods.omnitech.io.IElectricSupplier;
import com.dev1lroot.mcmods.omnitech.io.IMultimeterReadable;
import com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil;
import com.dev1lroot.mcmods.omnitech.util.ElectricUnits;
import com.dev1lroot.mcmods.omnitech.util.PowerMeter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Consumer;

/**
 * Block entity for the {@link PowerConverterBlock} (rectifier / inverter).
 *
 * <ul>
 *   <li>The back face takes {@link PowerConverterBlock.Mode#input} current into a
 *       small buffer; the other current type is refused by the network.</li>
 *   <li>Every {@value #CLOCK_INTERVAL} ticks the front face pushes it out as
 *       {@link PowerConverterBlock.Mode#output} current at the input's voltage.</li>
 *   <li>{@code η = 1 − baseLoss − loadLoss · (P / P_rated)}, capped at the
 *       {@value #RATED_POWER_KW} kW rating.</li>
 * </ul>
 */
public class PowerConverterBlockEntity extends BlockEntity
        implements IElectricReceiver, IElectricSupplier, IMultimeterReadable {

    public static final int RATED_POWER_KW = 500;
    private static final int CLOCK_INTERVAL = ElectricNetworkUtil.CLOCK_TICKS;

    private float buffer       = 0f;
    private float lineVoltage  = 0f;
    private int   clockCounter = 0;

    private final PowerMeter inputMeter  = new PowerMeter();
    private final PowerMeter outputMeter = new PowerMeter();
    private final PowerMeter lossMeter   = new PowerMeter();

    public PowerConverterBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.POWER_CONVERTER.get(), pos, state);
    }

    public PowerConverterBlock.Mode mode() {
        return getBlockState().getBlock() instanceof PowerConverterBlock b
                ? b.mode() : PowerConverterBlock.Mode.RECTIFIER;
    }

    // ── Faces ────────────────────────────────────────────────────────────────

    private Direction outputFace() {
        return getBlockState().getValue(PowerConverterBlock.FACING);
    }

    @Override
    public boolean acceptsElectricityFrom(Direction side) {
        return side == outputFace().getOpposite();
    }

    @Override
    public boolean outputsElectricityTo(Direction side) {
        return side == outputFace();
    }

    @Override
    public boolean acceptsCurrent(CurrentType type) {
        return type == mode().input;
    }

    // ── IElectricReceiver ────────────────────────────────────────────────────

    @Override
    public float addElectricity(float amount, float volts) {
        float accepted = Math.max(0f, Math.min(amount, bufferCapacity() - buffer));
        inputMeter.add(accepted, volts);
        if (volts > 0f) lineVoltage = volts;
        if (accepted <= 0f) return 0f;
        buffer += accepted;
        setChanged();
        return accepted;
    }

    // ── IElectricSupplier ────────────────────────────────────────────────────

    @Override
    public float getEuSupply() {
        return Math.min(buffer / CLOCK_INTERVAL, ratedPerTick());
    }

    @Override
    public float getSupplyVoltage() {
        return buffer > 0f ? lineVoltage : 0f;
    }

    @Override
    public CurrentType getCurrentType() {
        return mode().output;
    }

    // ── Server tick ──────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, PowerConverterBlockEntity be) {
        be.inputMeter.tick();
        be.outputMeter.tick();
        be.lossMeter.tick();

        if (++be.clockCounter < CLOCK_INTERVAL) return;
        be.clockCounter = 0;

        float delivered = 0f;
        if (be.buffer > 0f && be.lineVoltage > 0f) {
            PowerConverterBlock.Mode mode = be.mode();
            float drawn = Math.min(be.buffer, be.ratedPerTick() * CLOCK_INTERVAL);
            float eta   = efficiency(mode, drawn / CLOCK_INTERVAL);
            delivered = ElectricNetworkUtil.propagateElectricity(level, pos, drawn * eta,
                    be.lineVoltage, mode.output, new Direction[]{ be.outputFace() });
            be.outputMeter.add(delivered, be.lineVoltage);
            if (delivered > 0f) {
                float used = delivered / eta;
                be.buffer = Math.max(0f, be.buffer - used);
                be.lossMeter.add(used - delivered);
                be.setChanged();
            }
        }

        boolean lit = delivered > 0f;
        if (state.getValue(PowerConverterBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(PowerConverterBlock.LIT, lit), 3);
        }
    }

    public static float efficiency(PowerConverterBlock.Mode mode, float unitsPerTick) {
        float load = (float) (ElectricUnits.toWatts(unitsPerTick) / (RATED_POWER_KW * 1000.0));
        return 1f - mode.baseLoss - mode.loadLoss * Math.min(1f, load);
    }

    private static float ratedPerTick() {
        return (float) ElectricUnits.fromWatts(RATED_POWER_KW * 1000.0);
    }

    private static float bufferCapacity() {
        return ratedPerTick() * CLOCK_INTERVAL * 2;
    }

    // ── Multimeter ───────────────────────────────────────────────────────────

    @Override
    public void appendMultimeterReadout(Consumer<Component> out) {
        PowerConverterBlock.Mode mode = mode();
        out.accept(Component.translatable("multimeter.omnitech.converter_in",
                mode.input.label(),
                ElectricUnits.formatPower(inputMeter.getWatts()),
                ElectricUnits.formatVoltage(inputMeter.getVolts())));
        out.accept(Component.translatable("multimeter.omnitech.converter_out",
                mode.output.label(),
                ElectricUnits.formatPower(outputMeter.getWatts()),
                ElectricUnits.formatVoltage(outputMeter.getVolts())));
        double sent = outputMeter.getWatts();
        double loss = lossMeter.getWatts();
        out.accept(Component.translatable("multimeter.omnitech.converter_loss",
                ElectricUnits.formatPower(loss),
                String.format("%.1f%%", sent + loss > 0 ? 100.0 * sent / (sent + loss) : 0.0))
                .withStyle(ChatFormatting.GRAY));
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        buffer      = input.getFloatOr("Buffer", 0f);
        lineVoltage = input.getFloatOr("LineVoltage", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("Buffer", buffer);
        output.putFloat("LineVoltage", lineVoltage);
    }
}
