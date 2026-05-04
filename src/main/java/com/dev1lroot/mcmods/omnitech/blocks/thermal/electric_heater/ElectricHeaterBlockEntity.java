package com.dev1lroot.mcmods.omnitech.blocks.thermal.electric_heater;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ElectricHeaterMenu;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import com.dev1lroot.mcmods.omnitech.io.IThermalNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for the {@link ElectricHeaterBlock}.
 *
 * <p>Consumes EU from the electric network to raise {@code currentTemp} toward {@code targetTemp}.
 * Energy cost increases geometrically with target temperature:
 * {@code EU/tick = BASE_EU_PER_TICK * GEOMETRIC_RATIO^(targetTemp - AMBIENT_TEMP)}
 *
 * <p>Target temperature is adjustable via +/− buttons in the GUI (range 20°C–10 000 000°C).
 * The heater implements {@link IThermalNode} so thermal conductors can draw heat from it.
 */
public class ElectricHeaterBlockEntity extends BaseContainerBlockEntity
        implements IElectricReceiver, IThermalNode {

    // ── Constants ─────────────────────────────────────────────────────────────

    public static final int    SLOT_COUNT       = 0;
    public static final float  MAX_EU           = 2000f;
    /** EU/tick consumed at ambient temperature (base cost). */
    public static final float  BASE_EU_PER_TICK = 0.5f;
    /** Geometric growth ratio per degree above ambient.  0.2 % per °C. */
    public static final double GEOMETRIC_RATIO  = 1.002;
    /** Degrees raised per tick while actively heating. */
    public static final float  HEAT_RATE        = 2.0f;
    /** Ticks between natural −1 °C losses. */
    public static final int    HEAT_LOSS_INTERVAL = 20;
    /** Absolute maximum settable target temperature (°C). */
    public static final int    MAX_TARGET_TEMP  = 10_000_000;

    // ── Button IDs ────────────────────────────────────────────────────────────

    public static final int BTN_MINUS_1   = 0;
    public static final int BTN_PLUS_1    = 1;
    public static final int BTN_MINUS_10  = 2;
    public static final int BTN_PLUS_10   = 3;
    public static final int BTN_MINUS_100 = 4;
    public static final int BTN_PLUS_100  = 5;

    // ── State ─────────────────────────────────────────────────────────────────

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private float energyStored  = 0f;
    private float currentTemp   = AMBIENT_TEMP;
    private int   targetTemp    = (int) AMBIENT_TEMP;
    private int   heatLossTimer = 0;

    // ── ContainerData ─────────────────────────────────────────────────────────

    /**
     * Index layout:
     * <ul>
     *   <li>0 – energyStored × 10</li>
     *   <li>1 – currentTemp × 10</li>
     *   <li>2 – targetTemp</li>
     *   <li>3 – MAX_EU × 10 (constant)</li>
     * </ul>
     */
    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int)(energyStored * 10f);
                case 1 -> (int)(currentTemp  * 10f);
                case 2 -> targetTemp;
                case 3 -> (int)(MAX_EU       * 10f);
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStored = value / 10f;
                case 1 -> currentTemp  = value / 10f;
                case 2 -> targetTemp   = value;
            }
        }
        @Override public int getCount() { return 4; }
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public ElectricHeaterBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.ELECTRIC_HEATER.get(), pos, state);
    }

    // ── BaseContainerBlockEntity ──────────────────────────────────────────────

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.electric_heater");
    }

    @Override protected NonNullList<ItemStack> getItems()           { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize()                         { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ElectricHeaterMenu(containerId, inv, this, dataAccess);
    }

    // ── IElectricReceiver ─────────────────────────────────────────────────────

    @Override
    public float addElectricity(float amount) {
        float space = MAX_EU - energyStored;
        if (space <= 0f) return 0f;
        float accepted = Math.min(amount, space);
        energyStored += accepted;
        setChanged();
        return accepted;
    }

    // ── IThermalNode ──────────────────────────────────────────────────────────

    @Override
    public float getTemperature() { return currentTemp; }

    @Override
    public void applyHeat(float dT) {
        if (dT < 0) currentTemp = Math.max(0f, currentTemp + dT);
    }

    // ── Target temperature control ────────────────────────────────────────────

    /**
     * Called server-side by {@link ElectricHeaterMenu#clickMenuButton} when the player
     * presses a +/− button in the GUI.
     */
    public boolean adjustTargetTemp(int buttonId) {
        int delta = switch (buttonId) {
            case BTN_MINUS_1   -> -1;
            case BTN_PLUS_1    -> +1;
            case BTN_MINUS_10  -> -10;
            case BTN_PLUS_10   -> +10;
            case BTN_MINUS_100 -> -100;
            case BTN_PLUS_100  -> +100;
            default            -> 0;
        };
        if (delta == 0) return false;
        targetTemp = Math.clamp(targetTemp + delta, (int) AMBIENT_TEMP, MAX_TARGET_TEMP);
        setChanged();
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ElectricHeaterBlockEntity be) {
        boolean wasLit = state.getValue(ElectricHeaterBlock.LIT);

        float euNeeded = computeEuPerTick(be.targetTemp);
        boolean active = false;

        if (be.currentTemp < be.targetTemp && be.energyStored >= euNeeded) {
            be.energyStored -= euNeeded;
            be.currentTemp = Math.min(be.targetTemp, be.currentTemp + HEAT_RATE);
            active = true;
        }

        // Natural heat loss every HEAT_LOSS_INTERVAL ticks
        if (be.currentTemp > AMBIENT_TEMP) {
            be.heatLossTimer++;
            if (be.heatLossTimer >= HEAT_LOSS_INTERVAL) {
                be.heatLossTimer = 0;
                be.currentTemp = Math.max(AMBIENT_TEMP, be.currentTemp - 1.0f);
            }
        } else {
            be.heatLossTimer = 0;
        }

        boolean isLit = active || be.currentTemp > AMBIENT_TEMP + 5;
        if (wasLit != isLit) {
            level.setBlock(pos, state.setValue(ElectricHeaterBlock.LIT, isLit), 3);
        }

        be.setChanged();
    }

    // ── EU cost formula ───────────────────────────────────────────────────────

    /**
     * Returns the EU consumed per tick to actively heat toward {@code targetTemp}.
     * Cost grows geometrically: each degree above ambient multiplies cost by
     * {@link #GEOMETRIC_RATIO}.
     */
    public static float computeEuPerTick(int targetTemp) {
        int delta = Math.max(0, targetTemp - (int) AMBIENT_TEMP);
        return (float)(BASE_EU_PER_TICK * Math.pow(GEOMETRIC_RATIO, delta));
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public float getEnergyStored() { return energyStored; }
    public float getCurrentTemp()  { return currentTemp; }
    public int   getTargetTemp()   { return targetTemp; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        energyStored  = input.getFloatOr("EnergyStored",  0f);
        currentTemp   = input.getFloatOr("CurrentTemp",   AMBIENT_TEMP);
        targetTemp    = input.getIntOr  ("TargetTemp",    (int) AMBIENT_TEMP);
        heatLossTimer = input.getIntOr  ("HeatLossTimer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putFloat("EnergyStored",  energyStored);
        output.putFloat("CurrentTemp",   currentTemp);
        output.putInt  ("TargetTemp",    targetTemp);
        output.putInt  ("HeatLossTimer", heatLossTimer);
    }
}
