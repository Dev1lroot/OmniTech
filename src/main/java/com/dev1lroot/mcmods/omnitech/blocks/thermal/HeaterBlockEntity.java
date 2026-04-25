package com.dev1lroot.mcmods.omnitech.blocks.thermal;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.HeaterMenu;
import com.dev1lroot.mcmods.omnitech.io.IThermalNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for the {@link HeaterBlock}.
 *
 * <p>Fuel is consumed from slot 0; different fuels produce different amounts of
 * heat per tick and allow different temperature ceilings (see {@link HeaterFuelRegistry}).
 *
 * <p>Heat balance:
 * <ul>
 *   <li>While burning: +{@code currentHeatPerTick} °C per tick, capped at {@code currentMaxHeat}.</li>
 *   <li>Natural loss: −1 °C every {@link #HEAT_LOSS_INTERVAL} ticks when storedHeat &gt; 0.</li>
 *   <li>Network output via {@link IThermalNode}: adjacent conductors pull heat proportionally.</li>
 * </ul>
 */
public class HeaterBlockEntity extends BaseContainerBlockEntity implements IThermalNode {

    public static final int SLOT_FUEL  = 0;
    public static final int SLOT_COUNT = 1;

    /** Default heat ceiling for the weakest fuels. */
    public static final int DEFAULT_MAX_HEAT = 200;
    /** Ticks between natural −1 °C drops. */
    public static final int HEAT_LOSS_INTERVAL = 20;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private int burnTime            = 0;
    private int maxBurnTime         = 0;
    private int storedHeat          = 0;
    private int heatLossTimer       = 0;
    /** Heat-per-tick for the currently loaded fuel; set when fuel is consumed. */
    private int currentHeatPerTick  = 1;
    /** storedHeat ceiling for the currently loaded fuel; set when fuel is consumed. */
    private int currentMaxHeat      = DEFAULT_MAX_HEAT;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> maxBurnTime;
                case 2 -> storedHeat;
                case 3 -> currentMaxHeat;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime          = value;
                case 1 -> maxBurnTime       = value;
                case 2 -> storedHeat        = value;
                case 3 -> currentMaxHeat    = value;
            }
        }
        @Override public int getCount() { return 4; }
    };

    public HeaterBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.HEATER.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.heater");
    }

    @Override protected NonNullList<ItemStack> getItems()                  { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items)        { this.items = items; }
    @Override public int getContainerSize()                                { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new HeaterMenu(containerId, inv, this, dataAccess);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            HeaterBlockEntity be) {
        boolean wasLit = be.isLit();

        // ── Fuel burn countdown ───────────────────────────────────────────────
        if (be.isLit()) {
            be.burnTime--;
        }

        // ── Try to consume a new fuel item ────────────────────────────────────
        if (!be.isLit()) {
            ItemStack fuel = be.items.get(SLOT_FUEL);
            HeaterFuelRegistry.Entry entry = HeaterFuelRegistry.get(fuel);
            if (entry != null) {
                be.currentHeatPerTick = entry.heatPerTick();
                be.currentMaxHeat     = entry.maxHeat();
                be.maxBurnTime        = entry.burnTicks();
                be.burnTime           = entry.burnTicks();

                // Lava bucket leaves an empty bucket in the slot
                if (fuel.is(Items.LAVA_BUCKET)) {
                    be.items.set(SLOT_FUEL, new ItemStack(Items.BUCKET));
                } else {
                    fuel.shrink(1);
                }
            }
        }

        // ── Heat gain while burning ───────────────────────────────────────────
        if (be.isLit() && be.storedHeat < be.currentMaxHeat) {
            be.storedHeat = Math.min(be.currentMaxHeat, be.storedHeat + be.currentHeatPerTick);
        }

        // ── Natural heat loss ─────────────────────────────────────────────────
        if (be.storedHeat > 0) {
            be.heatLossTimer++;
            if (be.heatLossTimer >= HEAT_LOSS_INTERVAL) {
                be.heatLossTimer = 0;
                be.storedHeat--;
            }
        } else {
            be.heatLossTimer = 0;
        }

        // ── Sync LIT blockstate ───────────────────────────────────────────────
        if (wasLit != be.isLit()) {
            level.setBlock(pos, state.setValue(HeaterBlock.LIT, be.isLit()), 3);
        }

        be.setChanged();
    }

    // ── IThermalNode ──────────────────────────────────────────────────────────

    @Override
    public float getTemperature() { return AMBIENT_TEMP + storedHeat; }

    @Override
    public void applyHeat(float dT) {
        if (dT < 0) storedHeat = Math.max(0, storedHeat + (int) dT);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean isLit()       { return burnTime > 0; }
    public int getStoredHeat()   { return storedHeat; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        burnTime           = input.getIntOr("BurnTime",          0);
        maxBurnTime        = input.getIntOr("MaxBurnTime",        0);
        storedHeat         = input.getIntOr("StoredHeat",         0);
        currentHeatPerTick = input.getIntOr("CurrentHeatPerTick", 1);
        currentMaxHeat     = input.getIntOr("CurrentMaxHeat",     DEFAULT_MAX_HEAT);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("BurnTime",           burnTime);
        output.putInt("MaxBurnTime",        maxBurnTime);
        output.putInt("StoredHeat",         storedHeat);
        output.putInt("CurrentHeatPerTick", currentHeatPerTick);
        output.putInt("CurrentMaxHeat",     currentMaxHeat);
    }
}
