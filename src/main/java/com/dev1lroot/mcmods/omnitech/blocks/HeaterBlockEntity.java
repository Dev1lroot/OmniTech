package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.HeaterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for the {@link HeaterBlock}.
 *
 * <p>Burn logic mirrors {@link KineticGeneratorBlockEntity}: fuel is consumed from
 * slot 0 and converted to stored heat.  Once per tick, heat is radiated to all
 * directly adjacent {@link IHeatReceiver} blocks.
 *
 * <p>Heat balance:
 * <ul>
 *   <li>While burning: +{@link #HEAT_GAIN_PER_TICK} °C per tick (capped at {@link #MAX_HEAT}).</li>
 *   <li>Natural loss: −1 °C every {@link #HEAT_LOSS_INTERVAL} ticks when storedHeat &gt; 0.</li>
 *   <li>Output to each adjacent receiver: {@code storedHeat / 60} °C per tick (0–5 °C).</li>
 * </ul>
 */
public class HeaterBlockEntity extends BaseContainerBlockEntity {
    public static final int SLOT_FUEL  = 0;
    public static final int SLOT_COUNT = 1;

    public static final int MAX_HEAT           = 300;  // °C ceiling
    public static final int HEAT_GAIN_PER_TICK = 2;    // °C added per burning tick
    public static final int HEAT_LOSS_INTERVAL = 20;   // ticks between natural −1 °C drops
    // Heat transferred to each adjacent IHeatReceiver per tick:
    //   storedHeat / 60  →  0 °C at 0 °C, 5 °C at 300 °C

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private int burnTime    = 0;
    private int maxBurnTime = 0;
    private int storedHeat  = 0;
    private int heatLossTimer = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> maxBurnTime;
                case 2 -> storedHeat;
                case 3 -> MAX_HEAT;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime   = value;
                case 1 -> maxBurnTime = value;
                case 2 -> storedHeat = value;
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

    @Override
    protected NonNullList<ItemStack> getItems()                               { return items; }
    @Override
    protected void setItems(NonNullList<ItemStack> items)                     { this.items = items; }
    @Override
    public int getContainerSize()                                              { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new HeaterMenu(containerId, inv, this, dataAccess);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            HeaterBlockEntity be) {
        boolean wasLit = be.isLit();

        // ── Fuel consumption ──────────────────────────────────────────────────
        if (be.isLit()) {
            be.burnTime--;
        }
        if (!be.isLit()) {
            ItemStack fuel = be.items.get(SLOT_FUEL);
            int duration = KineticGeneratorBlockEntity.getBurnDuration(fuel);
            if (duration > 0) {
                be.maxBurnTime = duration;
                be.burnTime    = duration;
                fuel.shrink(1);
            }
        }

        // ── Heat gain while burning ───────────────────────────────────────────
        if (be.isLit() && be.storedHeat < MAX_HEAT) {
            be.storedHeat = Math.min(MAX_HEAT, be.storedHeat + HEAT_GAIN_PER_TICK);
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

        // ── Radiate heat to adjacent IHeatReceiver blocks ─────────────────────
        if (be.storedHeat > 0) {
            int transfer = be.storedHeat / 60; // 0–5 °C per tick
            if (transfer > 0) {
                for (Direction dir : Direction.values()) {
                    BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
                    if (neighbor instanceof IHeatReceiver receiver) {
                        receiver.addHeat(transfer);
                    }
                }
            }
        }

        be.setChanged();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean isLit()        { return burnTime > 0; }
    public int getStoredHeat()    { return storedHeat; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        burnTime    = input.getIntOr("BurnTime",    0);
        maxBurnTime = input.getIntOr("MaxBurnTime", 0);
        storedHeat  = input.getIntOr("StoredHeat",  0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("BurnTime",    burnTime);
        output.putInt("MaxBurnTime", maxBurnTime);
        output.putInt("StoredHeat",  storedHeat);
    }
}
