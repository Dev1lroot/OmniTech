/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.power_transformer;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.PowerTransformerMenu;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import com.dev1lroot.mcmods.omnitech.io.IElectricSupplier;
import com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil;
import com.dev1lroot.mcmods.omnitech.util.ElectricUnits;
import com.dev1lroot.mcmods.omnitech.util.PowerMeter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for the {@link PowerTransformerBlock}.
 *
 * <h3>Model</h3>
 * <ul>
 *   <li>Primary (back face) accepts energy from the input network into a small buffer.</li>
 *   <li>Every {@value #CLOCK_INTERVAL} ticks the secondary (front face) pushes it into the
 *       output network at {@link #outputVoltage}, capped by the player's {@link #powerLimitKw}.</li>
 *   <li>Power is conserved apart from losses, so current scales inversely with voltage:
 *       {@code I_out = I_in · U_in / U_out · η}.</li>
 *   <li>Losses follow a real transformer: a constant core (no-load) part plus a copper
 *       (I²R) part that grows with the square of the load —
 *       {@code η = 1 − CORE_LOSS − COPPER_LOSS · (P / P_rated)}, i.e. 99.5 % at light load
 *       and 98 % at the {@value #RATED_POWER_KW} kW rating.</li>
 * </ul>
 *
 * <h3>ContainerData layout</h3>
 * <ul>
 *   <li>0 – output voltage setpoint (V)</li>
 *   <li>1 – power limit (kW)</li>
 *   <li>2 – enabled (1/0)</li>
 *   <li>3 – input power (W, 1 s average)</li>
 *   <li>4 – input voltage × 10</li>
 *   <li>5 – output power (W, 1 s average)</li>
 *   <li>6 – output voltage × 10 (measured)</li>
 *   <li>7 – losses (W, 1 s average)</li>
 *   <li>8 – buffer kJ × 10</li>
 *   <li>9 – buffer capacity kJ × 10</li>
 * </ul>
 */
public class PowerTransformerBlockEntity extends BaseContainerBlockEntity
        implements IElectricReceiver, IElectricSupplier {

    // ── Ratings ──────────────────────────────────────────────────────────────

    /** Nameplate rating (1 MVA). */
    public static final int   RATED_POWER_KW  = 1000;
    public static final int   MIN_VOLTAGE     = 1;
    public static final int   MAX_VOLTAGE     = 110_000;
    public static final int   DEFAULT_VOLTAGE = (int) ElectricUnits.GRID_VOLTAGE;
    public static final int   MIN_LIMIT_KW    = 1;

    /** Core (no-load) loss as a fraction of throughput. */
    public static final float CORE_LOSS   = 0.005f;
    /** Copper loss fraction at full rated load (scales linearly with load fraction → I²). */
    public static final float COPPER_LOSS = 0.015f;

    /** Output is pushed every N ticks. */
    private static final int CLOCK_INTERVAL = 5;

    // ── Button IDs (see PowerTransformerMenu) ────────────────────────────────

    /** Voltage steps for button ids 0–5; ids 6–11 are the same steps ×100 (Shift). */
    public static final int[] VOLTAGE_STEPS = {-100, -10, -1, +1, +10, +100};
    public static final int   VOLTAGE_SHIFT_MULT = 100;
    /** Power-limit steps (kW) for ids 12–17; ids 18–23 are the same steps ×10 (Shift). */
    public static final int[] LIMIT_STEPS = {-100, -10, -1, +1, +10, +100};
    public static final int   LIMIT_SHIFT_MULT = 10;
    public static final int   BTN_VOLTAGE       = 0;
    public static final int   BTN_VOLTAGE_SHIFT = 6;
    public static final int   BTN_LIMIT         = 12;
    public static final int   BTN_LIMIT_SHIFT   = 18;
    public static final int   BTN_TOGGLE        = 24;

    // ── State ────────────────────────────────────────────────────────────────

    private int     outputVoltage = DEFAULT_VOLTAGE;
    private int     powerLimitKw  = RATED_POWER_KW;
    private boolean enabled       = true;
    private float   buffer        = 0f;
    private int     clockCounter  = 0;

    private final PowerMeter inputMeter  = new PowerMeter();
    private final PowerMeter outputMeter = new PowerMeter();
    private final PowerMeter lossMeter   = new PowerMeter();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> outputVoltage;
                case 1 -> powerLimitKw;
                case 2 -> enabled ? 1 : 0;
                case 3 -> inputMeter.syncWatts();
                case 4 -> inputMeter.syncDeciVolts();
                case 5 -> outputMeter.syncWatts();
                case 6 -> outputMeter.syncDeciVolts();
                case 7 -> lossMeter.syncWatts();
                case 8 -> (int) (buffer * 10f);
                case 9 -> (int) (bufferCapacity() * 10f);
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> outputVoltage = value;
                case 1 -> powerLimitKw  = value;
                case 2 -> enabled       = value == 1;
            }
        }
        @Override public int getCount() { return 10; }
    };

    public PowerTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.POWER_TRANSFORMER.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.power_transformer");
    }

    @Override protected NonNullList<ItemStack> getItems() { return NonNullList.create(); }
    @Override protected void setItems(NonNullList<ItemStack> items) {}
    @Override public int getContainerSize() { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new PowerTransformerMenu(containerId, inv, this, dataAccess);
    }

    // ── Faces ────────────────────────────────────────────────────────────────

    private Direction outputFace() {
        return getBlockState().getValue(PowerTransformerBlock.FACING);
    }

    @Override
    public boolean acceptsElectricityFrom(Direction side) {
        return side == outputFace().getOpposite();
    }

    @Override
    public boolean outputsElectricityTo(Direction side) {
        return side == outputFace();
    }

    // ── IElectricReceiver (primary) ──────────────────────────────────────────

    @Override
    public float addElectricity(float amount, float volts) {
        float accepted = enabled ? Math.max(0f, Math.min(amount, bufferCapacity() - buffer)) : 0f;
        inputMeter.add(accepted, volts);
        if (accepted <= 0f) return 0f;
        buffer += accepted;
        setChanged();
        return accepted;
    }

    // ── IElectricSupplier (secondary) ────────────────────────────────────────

    @Override
    public float getEuSupply() {
        return enabled ? Math.min(buffer / CLOCK_INTERVAL, limitPerTick()) : 0f;
    }

    @Override
    public float getSupplyVoltage() {
        return enabled ? outputVoltage : 0f;
    }

    // ── Server tick ──────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            PowerTransformerBlockEntity be) {
        be.inputMeter.tick();
        be.outputMeter.tick();
        be.lossMeter.tick();

        if (++be.clockCounter < CLOCK_INTERVAL) return;
        be.clockCounter = 0;
        if (!be.enabled || be.buffer <= 0f) return;

        float drawn  = Math.min(be.buffer, be.limitPerTick() * CLOCK_INTERVAL);
        float eta    = efficiency(drawn / CLOCK_INTERVAL);
        float volts  = be.outputVoltage;
        float delivered = ElectricNetworkUtil.propagateElectricity(
                level, pos, drawn * eta, volts, new Direction[]{ be.outputFace() });
        if (delivered <= 0f) {
            be.outputMeter.add(0f, volts);
            return;
        }

        float used = delivered / eta;
        be.buffer = Math.max(0f, be.buffer - used);
        be.outputMeter.add(delivered, volts);
        be.lossMeter.add(used - delivered);
        be.setChanged();
    }

    /** Efficiency when passing {@code unitsPerTick} kJ/t (core + load-dependent copper loss). */
    public static float efficiency(float unitsPerTick) {
        float load = (float) (ElectricUnits.toWatts(unitsPerTick) / (RATED_POWER_KW * 1000.0));
        return 1f - CORE_LOSS - COPPER_LOSS * Math.min(1f, load);
    }

    // ── Settings (called by the menu, server side) ───────────────────────────

    public boolean handleButton(int id) {
        if (id == BTN_TOGGLE) {
            enabled = !enabled;
        } else if (id >= BTN_VOLTAGE && id < BTN_VOLTAGE + 6) {
            outputVoltage = clampVoltage(outputVoltage + VOLTAGE_STEPS[id - BTN_VOLTAGE]);
        } else if (id >= BTN_VOLTAGE_SHIFT && id < BTN_VOLTAGE_SHIFT + 6) {
            outputVoltage = clampVoltage(outputVoltage
                    + VOLTAGE_STEPS[id - BTN_VOLTAGE_SHIFT] * VOLTAGE_SHIFT_MULT);
        } else if (id >= BTN_LIMIT && id < BTN_LIMIT + 6) {
            powerLimitKw = clampLimit(powerLimitKw + LIMIT_STEPS[id - BTN_LIMIT]);
        } else if (id >= BTN_LIMIT_SHIFT && id < BTN_LIMIT_SHIFT + 6) {
            powerLimitKw = clampLimit(powerLimitKw + LIMIT_STEPS[id - BTN_LIMIT_SHIFT] * LIMIT_SHIFT_MULT);
        } else {
            return false;
        }
        // A lower limit shrinks the buffer; the excess is dissipated.
        buffer = Math.min(buffer, bufferCapacity());
        setChanged();
        return true;
    }

    private static int clampVoltage(long v) { return (int) Math.clamp(v, MIN_VOLTAGE, MAX_VOLTAGE); }
    private static int clampLimit(long kw)  { return (int) Math.clamp(kw, MIN_LIMIT_KW, RATED_POWER_KW); }

    /** Power limit in kJ/tick. */
    private float limitPerTick() {
        return (float) ElectricUnits.fromWatts(powerLimitKw * 1000.0);
    }

    /** Buffer holds two output clocks' worth of energy at the power limit. */
    private float bufferCapacity() {
        return limitPerTick() * CLOCK_INTERVAL * 2;
    }

    public ContainerData getContainerData() { return dataAccess; }

    // ── Persistence ──────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        outputVoltage = clampVoltage(input.getIntOr("OutputVoltage", DEFAULT_VOLTAGE));
        powerLimitKw  = clampLimit(input.getIntOr("PowerLimitKw", RATED_POWER_KW));
        enabled       = input.getBooleanOr("Enabled", true);
        buffer        = input.getFloatOr("Buffer", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("OutputVoltage", outputVoltage);
        output.putInt("PowerLimitKw",  powerLimitKw);
        output.putBoolean("Enabled",   enabled);
        output.putFloat("Buffer",      buffer);
    }
}
