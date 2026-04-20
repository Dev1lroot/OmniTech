package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ElectricChargerMenu;
import com.dev1lroot.mcmods.omnitech.items.BoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
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
 * Block entity for the Electric Charger.
 *
 * <p>Receives EU from the electric network (implements {@link IElectricReceiver}) and
 * transfers it to any {@link BoreItem} placed in its single charge slot.
 *
 * <h3>ContainerData layout</h3>
 * <ul>
 *   <li>0 – energyStored × 10</li>
 *   <li>1 – MAX_EU × 10</li>
 *   <li>2 – item EU stored (0 if slot empty or not a bore)</li>
 *   <li>3 – item max EU (0 if slot empty or not a bore)</li>
 * </ul>
 */
public class ElectricChargerBlockEntity extends BaseContainerBlockEntity
        implements IElectricReceiver {

    public static final int SLOT_ITEM = 0;
    public static final int SLOT_COUNT = 1;

    public static final float MAX_EU = 5000f;
    /** EU transferred to the item per tick. */
    public static final int CHARGE_RATE = 20;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private float energyStored = 0f;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int)(energyStored * 10f);
                case 1 -> (int)(MAX_EU * 10f);
                case 2 -> {
                    ItemStack s = items.get(SLOT_ITEM);
                    yield s.getItem() instanceof BoreItem ? BoreItem.getEu(s) : 0;
                }
                case 3 -> {
                    ItemStack s = items.get(SLOT_ITEM);
                    yield s.getItem() instanceof BoreItem b ? b.maxEu : 0;
                }
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            if (index == 0) energyStored = value / 10f;
        }
        @Override public int getCount() { return 4; }
    };

    public ElectricChargerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.ELECTRIC_CHARGER.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.electric_charger");
    }

    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new ElectricChargerMenu(id, inv, this, dataAccess);
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

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ElectricChargerBlockEntity be) {

        boolean changed = false;
        ItemStack item = be.items.get(SLOT_ITEM);
        boolean charging = false;

        if (!item.isEmpty() && item.getItem() instanceof BoreItem bore) {
            int itemEu = BoreItem.getEu(item);
            if (itemEu < bore.maxEu && be.energyStored >= 1f) {
                int space = bore.maxEu - itemEu;
                int toTransfer = Math.min(CHARGE_RATE, space);
                toTransfer = Math.min(toTransfer, (int) be.energyStored);
                if (toTransfer > 0) {
                    BoreItem.setEu(item, itemEu + toTransfer);
                    be.energyStored -= toTransfer;
                    changed = true;
                    charging = true;
                }
            }
        }

        boolean wasLit = state.getValue(ElectricChargerBlock.LIT);
        if (wasLit != charging) {
            level.setBlock(pos, state.setValue(ElectricChargerBlock.LIT, charging), 3);
            changed = true;
        }

        if (changed) be.setChanged();
    }

    // ── Helpers for screen ────────────────────────────────────────────────────

    public float getEnergyStored() { return dataAccess.get(0) / 10f; }
    public float getMaxEu()        { return dataAccess.get(1) / 10f; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energyStored = input.getFloatOr("EnergyStored", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putFloat("EnergyStored", energyStored);
    }

    public ContainerData getContainerData() { return dataAccess; }
}
