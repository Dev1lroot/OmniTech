package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ElectricCapacitorMenu;
import com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Block entity for the Electric Capacitor.
 *
 * <p>Implements both {@link IElectricReceiver} (charges from 5 non-front sides)
 * and {@link IElectricSupplier} (discharges from the front face).
 *
 * <p>Exposes a NeoForge {@link EnergyHandler} via {@link #energyHandler} for
 * interoperability with other mods.  The handler is transaction-aware using
 * {@link SnapshotJournal}.
 *
 * <p>Capacity: {@value #MAX_EU} EU. Discharge propagation happens each tick
 * from the capacitor's front face, making stored EU available to downstream
 * machines via the electric wire network.
 */
public class ElectricCapacitorBlockEntity extends BaseContainerBlockEntity
        implements IElectricReceiver, IElectricSupplier {

    public static final int MAX_EU = 50_000;

    /** EU discharged into the network per tick (when has stored energy). */
    private static final int DISCHARGE_RATE = 40;

    int storedEu = 0;

    /** NeoForge-compatible energy handler (transaction-aware). */
    public final CapacitorEnergyHandler energyHandler = new CapacitorEnergyHandler();

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> storedEu;
                case 1 -> MAX_EU;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            if (index == 0) storedEu = value;
        }
        @Override public int getCount() { return 2; }
    };

    public ElectricCapacitorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.ELECTRIC_CAPACITOR.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.electric_capacitor");
    }

    // BaseContainerBlockEntity requires item handling even for non-item blocks
    @Override protected NonNullList<ItemStack> getItems() { return NonNullList.create(); }
    @Override protected void setItems(NonNullList<ItemStack> items) {}
    @Override public int getContainerSize() { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ElectricCapacitorMenu(containerId, inv, this, dataAccess);
    }

    // ── IElectricReceiver ─────────────────────────────────────────────────────

    @Override
    public boolean addElectricity(int amount) {
        int accepted = Math.min(amount, MAX_EU - storedEu);
        if (accepted <= 0) return false;
        storedEu += accepted;
        setChanged();
        return true;
    }

    // ── IElectricSupplier ─────────────────────────────────────────────────────

    @Override
    public int getEuSupply() {
        return Math.min(DISCHARGE_RATE, storedEu);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ElectricCapacitorBlockEntity be) {

        boolean wasLit = state.getValue(ElectricCapacitorBlock.LIT);
        boolean isLit  = be.storedEu > MAX_EU / 10;

        if (wasLit != isLit) {
            level.setBlock(pos, state.setValue(ElectricCapacitorBlock.LIT, isLit), 3);
        }

        // Discharge: propagate stored EU from the front (output) face.
        // storedEu is only decremented when at least one receiver accepted the EU —
        // this prevents the capacitor from silently draining into the void when
        // nothing is connected to its front face.
        if (be.storedEu > 0) {
            Direction front = state.getValue(ElectricCapacitorBlock.FACING);
            int dischargeAmount = Math.min(DISCHARGE_RATE, be.storedEu);
            boolean delivered = ElectricNetworkUtil.propagateElectricity(
                    level, pos, dischargeAmount, new Direction[]{ front });
            if (delivered) {
                be.storedEu -= dischargeAmount;
                be.setChanged();
            }
        }
    }

    public ContainerData getContainerData() { return dataAccess; }
    public int getStoredEu()               { return storedEu; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        storedEu = input.getIntOr("StoredEu", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("StoredEu", storedEu);
    }

    // ── NeoForge EnergyHandler (inner class) ──────────────────────────────────

    /**
     * Transaction-aware {@link EnergyHandler} backed by this capacitor's {@link #storedEu}.
     * Extends {@link SnapshotJournal}{@code <Integer>} so that energy changes made inside
     * a transaction can be rolled back if the transaction is aborted.
     */
    public class CapacitorEnergyHandler extends SnapshotJournal<Integer> implements EnergyHandler {

        @Override
        protected Integer createSnapshot() { return storedEu; }

        @Override
        protected void revertToSnapshot(Integer snapshot) { storedEu = snapshot; }

        @Override
        protected void onRootCommit(Integer originalState) {
            setChanged();
        }

        @Override
        public long getAmountAsLong()   { return storedEu; }

        @Override
        public long getCapacityAsLong() { return MAX_EU; }

        @Override
        public int insert(int amount, TransactionContext tx) {
            int accepted = Math.min(amount, MAX_EU - storedEu);
            if (accepted > 0) {
                updateSnapshots(tx);
                storedEu += accepted;
            }
            return accepted;
        }

        @Override
        public int extract(int amount, TransactionContext tx) {
            int extracted = Math.min(amount, storedEu);
            if (extracted > 0) {
                updateSnapshots(tx);
                storedEu -= extracted;
            }
            return extracted;
        }
    }
}
