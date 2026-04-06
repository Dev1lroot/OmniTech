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
import net.minecraft.world.WorldlyContainer;
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
        implements IKineticReceiver, WorldlyContainer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ticks for an item to travel from the input face to the output face. */
    public static final int TRANSFER_INTERVAL = 20;
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

    // Внутри ConveyorBeltBlockEntity.java
    public float lastRenderProgress = 0f;
    public int lastRenderTick = -1;

    public ConveyorBeltBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.CONVEYOR_BELT.get(), pos, state);
    }

    // ── IKineticReceiver ──────────────────────────────────────────────────────

    @Override
    public boolean addKineticForce(float amount) {
        poweredTimer = POWERED_DECAY_TICKS;
        return true;
    }

    /** Conveyor belts are very cheap: 0.1 KF (1 fixed-point unit). */
    @Override
    public float getKfDemand() { return 1.0F; }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ConveyorBeltBlockEntity be) {
        // ── Powered timer decay ────────────────────────────────────────────────
        // Read the current POWERED state from the blockstate (not from the timer)
        // so that when addKineticForce() sets poweredTimer>0 we correctly detect
        // the false→true transition and actually set POWERED=true in the world.
        boolean wasPowered = state.getValue(ConveyorBeltBlock.POWERED);
        if (be.poweredTimer > 0) be.poweredTimer--;
        boolean isPowered = be.poweredTimer > 0;

        if (wasPowered != isPowered) {
            level.setBlock(pos, state.setValue(ConveyorBeltBlock.POWERED, isPowered), 3);
            state = level.getBlockState(pos);
        }

        // ── Item movement ──────────────────────────────────────────────────────
        if (isPowered) {
            if (!be.items.get(0).isEmpty()) {
                // Advance timer toward the output end (cap; never wraps)
                if (be.transferTimer < TRANSFER_INTERVAL) be.transferTimer++;

                if (be.transferTimer >= TRANSFER_INTERVAL) {
                    // Try to push the item to the back container
                    if (tryPushToBack(level, pos, state, be)) {
                        be.transferTimer = 0;
                        // Immediately pull a new item from the front
                        tryPullFromAnySide(level, pos, state, be);
                        level.sendBlockUpdated(pos, state, state, 3);
                    }
                    // If push failed: stay blocked, timer stays at TRANSFER_INTERVAL
                    // (item is rendered at the back edge until the output clears)
                }
            } else {
                be.transferTimer = 0;
                // Empty slot: try to pull from the front every tick
                if (tryPullFromAnySide(level, pos, state, be)) {
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }
        }
        // When unpowered: timer is preserved — item stays frozen at current position

        // ── Sync PROGRESS blockstate (server-authoritative animation) ──────────
        int progress = Math.min(be.transferTimer, TRANSFER_INTERVAL);
        if (state.getValue(ConveyorBeltBlock.PROGRESS) != progress) {
            state = state.setValue(ConveyorBeltBlock.PROGRESS, progress);
            level.setBlock(pos, state, 2); // flag=2: send to clients, no neighbour update
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

        // The face of the receiving block that the belt pushes into
        Direction enterFace = backDir.getOpposite(); // = our FACING

        ItemStack held = be.items.get(0);
        if (!tryInsert(output, held, enterFace)) return false;  // destination full → stall

        be.items.set(0, ItemStack.EMPTY);
        return true;
    }

    /**
     * Attempts to pull one item from any neighboring container except the back
     * (output) side.  Iterates all six directions; the back face is skipped
     * because it is output-only.
     *
     * @return {@code true} if an item was pulled.
     */
    /**
     * Updated pull logic: It now scans the neighbor for ALL accessible slots.
     * This allows it to "see" slots 1, 2, and 3 on your Macerator.
     */
    private static boolean tryPullFromAnySide(Level level, BlockPos pos, BlockState state,
                                              ConveyorBeltBlockEntity be) {
        if (!be.items.get(0).isEmpty()) return false;

        // The direction the belt is POINTING (the Front/Output)
        Direction facing = state.getValue(ConveyorBeltBlock.FACING);

        for (Direction dir : Direction.values()) {
            // BLOCK the BACK: Never pull from the side we are pushing into
            if (dir == facing.getOpposite()) continue;

            BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
            if (!(neighbor instanceof Container input)) continue;

            // The face of the Macerator (or other block) touching the conveyor
            Direction neighborFace = dir.getOpposite();

            // Use the extraction helper to check slots 1, 2, and 3 of the Macerator
            ItemStack pulled = tryExtract(input, neighborFace);
            if (!pulled.isEmpty()) {
                be.items.set(0, pulled);
                return true;
            }
        }
        return false;
    }

    /**
     * The Helper: This handles the actual item snatching.
     */
    private static ItemStack tryExtract(Container container, Direction sideToExtractFrom) {
        int[] accessibleSlots;

        // Ask the neighbor: "Which slots can I see from this side?"
        if (container instanceof WorldlyContainer wc) {
            accessibleSlots = wc.getSlotsForFace(sideToExtractFrom);
        } else {
            accessibleSlots = new int[container.getContainerSize()];
            for (int i = 0; i < accessibleSlots.length; i++) accessibleSlots[i] = i;
        }

        for (int slotIndex : accessibleSlots) {
            ItemStack stack = container.getItem(slotIndex);
            if (stack.isEmpty()) continue;

            // Check the Macerator's security rule (index != SLOT_INPUT)
            if (container instanceof WorldlyContainer wc) {
                if (!wc.canTakeItemThroughFace(slotIndex, stack, sideToExtractFrom)) continue;
            }

            // Skip slots that still accept items (input / fuel slots).
            // Only extract from output-only slots (canPlaceItem == false).
            if (container instanceof WorldlyContainer && container.canPlaceItem(slotIndex, stack)) continue;

            // Snatch exactly 1 item
            ItemStack extracted = stack.copyWithCount(1);
            stack.shrink(1);
            if (stack.isEmpty()) container.setItem(slotIndex, ItemStack.EMPTY);

            return extracted;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Inserts exactly one item into {@code container} via {@code enterFace}.
     * Iterates all slots and checks {@link WorldlyContainer#canPlaceItemThroughFace}
     * plus {@link Container#canPlaceItem}, without using {@code getSlotsForFace}.
     * This allows, e.g., inserting iron ore into a vanilla furnace's input slot
     * from the side (furnace {@code canPlaceItemThroughFace} is face-agnostic).
     *
     * @return {@code true} if the item was inserted.
     */
    private static boolean tryInsert(Container container, ItemStack stack, Direction enterFace) {
        int size = container.getContainerSize();
        int containerMax = container.getMaxStackSize();
        for (int slotIdx = 0; slotIdx < size; slotIdx++) {
            if (container instanceof WorldlyContainer wc
                    && !wc.canPlaceItemThroughFace(slotIdx, stack, enterFace)) continue;
            if (!container.canPlaceItem(slotIdx, stack)) continue;

            ItemStack existing = container.getItem(slotIdx);
            if (existing.isEmpty()) {
                container.setItem(slotIdx, stack.copyWithCount(1));
                container.setChanged();
                return true;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < Math.min(existing.getMaxStackSize(), containerMax)) {
                existing.grow(1);
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
        ItemStack removed = ContainerHelper.removeItem(items, 0, amount);
        if (!removed.isEmpty()) resetTimerAndProgress();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack held = items.get(0);
        items.set(0, ItemStack.EMPTY);
        resetTimerAndProgress();
        return held;
    }

    /**
     * Sets the item in the belt's slot (always clamped to count = 1).
     * Resets the transfer timer so the new item always starts its journey
     * from position 0.  Notifies nearby clients immediately.
     */
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        items.set(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        resetTimerAndProgress();
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void clearContent() {
        items.set(0, ItemStack.EMPTY);
        resetTimerAndProgress();
        setChanged();
    }

    /**
     * Resets the transfer timer to 0 and immediately pushes {@code PROGRESS=0}
     * to clients, so the item display clears as soon as the slot changes —
     * not after the next server tick.
     */
    private void resetTimerAndProgress() {
        if (transferTimer == 0) return;
        transferTimer = 0;
        if (level != null && !level.isClientSide()) {
            BlockState bs = getBlockState();
            if (bs.getValue(ConveyorBeltBlock.PROGRESS) != 0) {
                level.setBlock(getBlockPos(), bs.setValue(ConveyorBeltBlock.PROGRESS, 0), 2);
            }
        }
    }

    // ── WorldlyContainer — side-aware access rules ────────────────────────────

    private static final int[] SLOT_ARRAY = {0};

    /** Every side exposes slot 0 (hoppers etc. query this before inserting/extracting). */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOT_ARRAY;
    }

    /** Items may be inserted from any side EXCEPT the output (back) face. */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot != 0) return false;
        Direction back = getBlockState().getValue(ConveyorBeltBlock.FACING).getOpposite();
        return side != back;
    }

    /**
     * Items may only be extracted from the output (back) face AND only once the
     * belt has completed its full {@link #TRANSFER_INTERVAL}-tick journey.
     * This prevents downstream belts from pulling an item early and causing the
     * cascade-fill problem when a gap in a belt line is reconnected.
     */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot != 0) return false;
        Direction back = getBlockState().getValue(ConveyorBeltBlock.FACING).getOpposite();
        return side == back && transferTimer >= TRANSFER_INTERVAL;
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
