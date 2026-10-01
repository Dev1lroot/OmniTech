/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.solar_panel;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.SolarPanelMenu;
import com.dev1lroot.mcmods.omnitech.io.CurrentType;
import com.dev1lroot.mcmods.omnitech.io.IElectricSupplier;
import com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil;
import com.dev1lroot.mcmods.omnitech.util.PowerMeter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Block entity for the Solar Panel.
 *
 * <h3>Generation model</h3>
 * <p>Each network clock ({@value #CLOCK_INTERVAL} ticks) the panel:
 * <ol>
 *   <li>Checks that the dimension has a sky and that the block directly
 *       above the panel can see the sky (via the lighting engine).</li>
 *   <li>Calculates the sun intensity from the time-of-day using a
 *       sine curve: 0 at sunrise/sunset, 1.0 at solar noon, 0 at night.</li>
 *   <li>Propagates {@code intensity × CLOCK_INTERVAL} kJ into the
 *       electric wire network from all 6 faces.</li>
 * </ol>
 *
 * <p>Peak output is {@value #EU_PER_TICK} kJ/tick (20 kW). The terminal voltage follows
 * the photovoltaic open-circuit law: it depends logarithmically on irradiance G,
 * {@code U = U_mpp · (1 + k · ln G)}, so it stays near {@value #MPP_VOLTAGE} V for most
 * of the day and only sags at dawn and dusk.
 *
 * <h3>ContainerData layout</h3>
 * <ul>
 *   <li>0 – currentOutput × 1000 (generated kJ/tick, fixed-point, decode ÷ 1000)</li>
 *   <li>1 – effective sky light (raw sky light minus skyDarken, 0–15)</li>
 *   <li>2 – power actually delivered to the network in W (1 s average)</li>
 *   <li>3 – terminal voltage × 10</li>
 * </ul>
 */
public class SolarPanelBlockEntity extends BaseContainerBlockEntity implements IElectricSupplier {

    /** Max output in kJ per game tick at solar noon (20 kW). */
    public static final float EU_PER_TICK = 1f;

    /** Maximum-power-point voltage of the panel string at full irradiance. */
    public static final float MPP_VOLTAGE = 380f;

    /** Logarithmic voltage coefficient of the PV cells (per ln of irradiance). */
    private static final float PV_LOG_COEFF = 0.05f;

    /** Network clock interval (ticks). BFS fires once per this many ticks. */
    private static final int CLOCK_INTERVAL = 5;

    private int clockCounter = 0;

    /** Current generated kJ/tick (0..EU_PER_TICK). Updated every clock cycle. */
    private float currentOutput = 0f;

    /** Sky light level directly above the panel (0-15). Updated every clock cycle. */
    private int skyLight = 0;

    /** Energy actually accepted by the network. */
    private final PowerMeter outputMeter = new PowerMeter();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int)(currentOutput * 1000f);
                case 1 -> skyLight;
                case 2 -> outputMeter.syncWatts();
                case 3 -> PowerMeter.encodeVolts(getSupplyVoltage());
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            if (index == 0) currentOutput = value / 1000f;
            if (index == 1) skyLight = value;
        }
        @Override public int getCount() { return 4; }
    };

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.SOLAR_PANEL.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.solar_panel");
    }

    @Override protected NonNullList<ItemStack> getItems() { return NonNullList.create(); }
    @Override protected void setItems(NonNullList<ItemStack> items) {}
    @Override public int getContainerSize() { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new SolarPanelMenu(containerId, inv, this, dataAccess);
    }

    // ── IElectricSupplier ─────────────────────────────────────────────────────

    @Override
    public float getEuSupply() { return currentOutput; }

    @Override
    public float getSupplyVoltage() { return pvVoltage(currentOutput / EU_PER_TICK); }

    /** Photovoltaic cells produce direct current. */
    @Override
    public CurrentType getCurrentType() { return CurrentType.DC; }

    /** PV terminal voltage at relative irradiance {@code g} (0..1). */
    public static float pvVoltage(float g) {
        if (g <= 0f) return 0f;
        float v = MPP_VOLTAGE * (1f + PV_LOG_COEFF * (float) Math.log(Math.max(g, 0.01f)));
        return Math.max(0f, v);
    }

    public ContainerData getContainerData() { return dataAccess; }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            SolarPanelBlockEntity be) {

        be.outputMeter.tick();

        be.clockCounter++;
        if (be.clockCounter < CLOCK_INTERVAL) return;
        be.clockCounter = 0;

        // Effective sky light: raw stored value minus the time-of-day darkening.
        // Raw sky light is always 15 for sky-exposed positions; skyDarken varies
        // from 0 (noon) to ~11 (midnight), giving a meaningful 0–15 display value.
        be.skyLight = Math.max(0,
                level.getBrightness(LightLayer.SKY, pos.above()) - level.getSkyDarken());

        float output = computeOutput(level, pos);
        be.currentOutput = output;
        be.setChanged();

        boolean isLit  = output > 0f;
        boolean wasLit = state.getValue(SolarPanelBlock.LIT);
        if (wasLit != isLit) {
            level.setBlock(pos, state.setValue(SolarPanelBlock.LIT, isLit), 3);
        }

        if (output <= 0f) return;

        float burst = output * CLOCK_INTERVAL;
        float volts = be.getSupplyVoltage();
        float delivered = ElectricNetworkUtil.propagateElectricity(
                level, pos, burst, volts, CurrentType.DC, Direction.values());
        be.outputMeter.add(delivered, volts);
    }

    // ── Output calculation ────────────────────────────────────────────────────────

    /**
     * Computes the kJ/tick output using a sine curve over the overworld day cycle.
     *
     * <p>Time mapping (Minecraft overworld, 24 000 ticks/day):
     * <ul>
     *   <li>time 0     – dawn (output = 0)</li>
     *   <li>time 6 000 – solar noon (output = EU_PER_TICK)</li>
     *   <li>time 12 000 – dusk (output = 0)</li>
     *   <li>time 12 001–23 999 – night (output = 0)</li>
     * </ul>
     *
     * <p>Sky access is determined by the lighting engine's sky-light value
     * at the block immediately above the panel (≥ 15 = unobstructed).
     */
    private static float computeOutput(Level level, BlockPos pos) {
        if (!level.dimensionType().hasSkyLight()) return 0f;

        // Use the lighting engine's sky-access check (handles glass, leaves, etc.)
        if (!level.canSeeSky(pos.above())) return 0f;

        long time = level.getOverworldClockTime() % 24000L;

        // Night: from dusk (12 000) through midnight to dawn (0)
        if (time >= 12000L) return 0f;

        // Sine curve: 0 at dawn, peaks at noon (time=6000), back to 0 at dusk
        float angle = (float)(Math.PI * time / 12000.0);
        return Mth.clamp((float)Math.sin(angle) * EU_PER_TICK, 0f, EU_PER_TICK);
    }
}
