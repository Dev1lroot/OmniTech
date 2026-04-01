package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.slf4j.Logger;

/**
 * Block entity for the {@link ConveyorBeltBlock}.
 *
 * <p>Implements {@link Container} so adjacent belts (and other machinery) can
 * push/pull items through the standard container API.  The belt holds exactly
 * one item (max stack size = 1 for the whole container).
 *
 * <p>Transfer logic (server-side, runs every tick while KF-powered):
 * <ul>
 *   <li>If the slot is occupied the transfer timer counts up to
 *       {@link #TRANSFER_INTERVAL}.  At that point the belt tries to push
 *       the item into the container directly behind it.
 *       <ul>
 *         <li>Success → clear slot, reset timer to 0, immediately attempt a
 *             pull from the front container.</li>
 *         <li>Blocked (no output container or destination full) → timer stays
 *             at {@link #TRANSFER_INTERVAL}; item is held at the back edge
 *             visually and retried next tick.</li>
 *       </ul>
 *   </li>
 *   <li>If the slot is empty the belt tries to pull one item from the
 *       container in front of it every tick.</li>
 * </ul>
 *
 * <p>Items are <em>never</em> ejected into the world; the belt simply stalls.
 */
public class ConveyorBeltBlockEntity extends BlockEntity
        implements IKineticReceiver, Container {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ticks for an item to travel from the input face to the output face. */
    public static final int TRANSFER_INTERVAL = 8;
    /** Ticks before POWERED turns off after the last KF pulse. */
    public static final int POWERED_DECAY_TICKS = 3;

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int poweredTimer  = 0;
    /**
     * Transfer progress timer [0, TRANSFER_INTERVAL].
     * Capped at TRANSFER_INTERVAL when blocked (item at back edge, waiting
     * for a free output slot).  Reset to 0 when a transfer succeeds.
     */
    private int transferTimer = 0;

    public ConveyorBeltBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.CONVEYOR_BELT.get(), pos, state);
    }

    // ── Client tick ───────────────────────────────────────────────────────────

    /**
     * Advances the local {@link #transferTimer} on the client each tick so the
     * item slides smoothly between server update packets.
     * The timer is capped at {@link #TRANSFER_INTERVAL} — it never wraps back
     * to 0 on its own; only an incoming server packet (after a successful
     * transfer) resets it.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state,
            ConveyorBeltBlockEntity be) {
        boolean powered = state.getValue(ConveyorBeltBlock.POWERED);
        if (powered && !be.items.get(0).isEmpty()) {
            if (be.transferTimer < TRANSFER_INTERVAL) be.transferTimer++;
        } else {
            be.transferTimer = 0;
        }
    }

    // ── IKineticReceiver ──────────────────────────────────────────────────────

    @Override
    public boolean addKineticForce(int amount) {
        poweredTimer = POWERED_DECAY_TICKS;
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ConveyorBeltBlockEntity be) {
        // ── Powered timer decay ────────────────────────────────────────────────
        boolean wasPowered = be.poweredTimer > 0;
        if (be.poweredTimer > 0) be.poweredTimer--;
        boolean isPowered = be.poweredTimer > 0;

        if (wasPowered != isPowered) {
            level.setBlock(pos, state.setValue(ConveyorBeltBlock.POWERED, isPowered), 3);
            state = level.getBlockState(pos);
        }

        if (!isPowered) {
            be.transferTimer = 0;
            be.setChanged();
            return;
        }

        // ── Item movement ──────────────────────────────────────────────────────
        if (!be.items.get(0).isEmpty()) {
            // Advance timer toward the output end (cap; never wraps)
            if (be.transferTimer < TRANSFER_INTERVAL) be.transferTimer++;

            if (be.transferTimer >= TRANSFER_INTERVAL) {
                // Try to push the item to the back container
                if (tryPushToBack(level, pos, state, be)) {
                    be.transferTimer = 0;
                    // Immediately pull a new item from the front
                    tryPullFromFront(level, pos, state, be);
                    level.sendBlockUpdated(pos, state, state, 3);
                }
                // If push failed: stay blocked, timer stays at TRANSFER_INTERVAL
                // (item is rendered at the back edge until the output clears)
            }
        } else {
            be.transferTimer = 0;
            // Empty slot: try to pull from the front every tick
            if (tryPullFromFront(level, pos, state, be)) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }

        be.setChanged();
    }

    // ── Transfer helpers ──────────────────────────────────────────────────────

    /**
     * Attempts to push the held item into the container directly behind the
     * belt ({@code FACING.opposite()} direction).
     *
     * @return {@code true} if the item was accepted and the slot was cleared.
     */
    private static boolean tryPushToBack(Level level, BlockPos pos, BlockState state,
            ConveyorBeltBlockEntity be) {
        Direction backDir = state.getValue(ConveyorBeltBlock.FACING).getOpposite();
        BlockEntity backBe = level.getBlockEntity(pos.relative(backDir));
        if (!(backBe instanceof Container output)) return false; // no container → stall

        ItemStack held = be.items.get(0);
        if (!tryInsert(output, held)) return false;  // destination full → stall

        be.items.set(0, ItemStack.EMPTY);
        return true;
    }

    /**
     * Attempts to pull one item from the container directly in front of the
     * belt ({@code FACING} direction) into the belt's own slot.
     *
     * @return {@code true} if an item was pulled.
     */
    private static boolean tryPullFromFront(Level level, BlockPos pos, BlockState state,
            ConveyorBeltBlockEntity be) {
        if (!be.items.get(0).isEmpty()) return false; // already holding something
        Direction frontDir = state.getValue(ConveyorBeltBlock.FACING);
        BlockEntity frontBe = level.getBlockEntity(pos.relative(frontDir));
        if (!(frontBe instanceof Container input)) return false;

        ItemStack pulled = tryExtract(input);
        if (pulled.isEmpty()) return false;

        be.items.set(0, pulled);
        return true;
    }

    /**
     * Inserts exactly one item into {@code container}.
     * Respects {@link Container#getMaxStackSize()} so belts (max=1) are not
     * over-filled.
     *
     * @return {@code true} if the item was inserted.
     */
    private static boolean tryInsert(Container container, ItemStack stack) {
        int containerMax = container.getMaxStackSize();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) {
                container.setItem(i, stack.copyWithCount(1));
                return true;
            }
            if (ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < Math.min(slot.getMaxStackSize(), containerMax)) {
                slot.grow(1);
                container.setChanged();
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts exactly one item from the first non-empty slot of
     * {@code container}.
     *
     * @return the extracted single-item stack, or {@link ItemStack#EMPTY}.
     */
    private static ItemStack tryExtract(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slot = container.getItem(i);
            if (!slot.isEmpty()) {
                ItemStack extracted = slot.copyWithCount(1);
                slot.shrink(1);
                if (slot.isEmpty()) container.setItem(i, ItemStack.EMPTY);
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    // ── Container implementation ──────────────────────────────────────────────

    /** The belt holds at most 1 item (count = 1) — prevents stacking. */
    @Override public int getMaxStackSize()      { return 1; }
    @Override public int getContainerSize()     { return 1; }
    @Override public boolean isEmpty()          { return items.get(0).isEmpty(); }
    @Override public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? items.get(0) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0) return ItemStack.EMPTY;
        return ContainerHelper.removeItem(items, 0, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack held = items.get(0);
        items.set(0, ItemStack.EMPTY);
        return held;
    }

    /**
     * Sets the item in the belt's slot (always clamped to count = 1).
     * Notifies nearby clients so the item renderer updates immediately
     * when another machine pushes into this belt.
     */
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        items.set(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void clearContent() {
        items.set(0, ItemStack.EMPTY);
        setChanged();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public ItemStack getHeldItem()      { return items.get(0); }
    /** Raw transfer timer [0, TRANSFER_INTERVAL] — used by the client renderer. */
    public int       getTransferTimer() { return transferTimer; }

    // ── Client sync ───────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter =
                new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            ContainerHelper.saveAllItems(output, items, true);
            output.putInt("TransferTimer", transferTimer);
            return output.buildResult();
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        poweredTimer  = input.getIntOr("PoweredTimer",  0);
        transferTimer = input.getIntOr("TransferTimer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("PoweredTimer",  poweredTimer);
        output.putInt("TransferTimer", transferTimer);
    }
}
