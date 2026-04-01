package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for the {@link ConveyorBeltBlock}.
 *
 * <p>Holds at most one item in its single internal slot.  Each
 * {@link #TRANSFER_INTERVAL} ticks while KF-powered:
 * <ol>
 *   <li>If the slot is occupied, the item is pushed into the container
 *       behind the belt.  On success the slot is cleared.</li>
 *   <li>If the slot is now empty, one item is pulled from the container
 *       in front of the belt.</li>
 * </ol>
 *
 * <p>KF is accepted via {@link IKineticReceiver#addKineticForce}, which
 * resets a short decay timer; when the timer reaches zero the belt
 * stops and the {@code POWERED} blockstate is cleared.
 *
 * <p>{@link #animProgress} (0–1) tracks transfer progress for the
 * client-side item floating animation.
 */
public class ConveyorBeltBlockEntity extends BlockEntity implements IKineticReceiver {

    /** Ticks between item transfer steps when powered. */
    public static final int TRANSFER_INTERVAL = 8;
    /** Ticks before POWERED turns off after the last KF pulse. */
    public static final int POWERED_DECAY_TICKS = 3;

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int   poweredTimer  = 0;
    private int   transferTimer = 0;
    /**
     * Fraction [0, 1] of the current transfer cycle that has elapsed.
     * Synced to clients via {@link #setChanged()} so the renderer can
     * interpolate the item position smoothly.
     */
    public float animProgress = 0f;

    public ConveyorBeltBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.CONVEYOR_BELT.get(), pos, state);
    }

    // ── IKineticReceiver ──────────────────────────────────────────────────────

    @Override
    public boolean addKineticForce(int amount) {
        poweredTimer = POWERED_DECAY_TICKS;
        return true;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ConveyorBeltBlockEntity be) {
        // ── Powered timer decay ────────────────────────────────────────────────
        boolean wasPowered = be.poweredTimer > 0;
        if (be.poweredTimer > 0) be.poweredTimer--;
        boolean isPowered = be.poweredTimer > 0;

        if (wasPowered != isPowered) {
            level.setBlock(pos, state.setValue(ConveyorBeltBlock.POWERED, isPowered), 3);
            state = level.getBlockState(pos); // refresh local reference
        }

        if (!isPowered) {
            if (be.transferTimer != 0 || be.animProgress != 0f) {
                be.transferTimer = 0;
                be.animProgress  = 0f;
                be.setChanged();
            }
            return;
        }

        // ── Transfer progress ──────────────────────────────────────────────────
        be.transferTimer++;
        be.animProgress = (float) be.transferTimer / TRANSFER_INTERVAL;

        if (be.transferTimer >= TRANSFER_INTERVAL) {
            be.transferTimer = 0;
            be.animProgress  = 0f;
            performTransfer(level, pos, state, be);
        }

        be.setChanged();
    }

    private static void performTransfer(Level level, BlockPos pos, BlockState state,
            ConveyorBeltBlockEntity be) {
        Direction facing  = state.getValue(ConveyorBeltBlock.FACING);
        Direction backDir = facing.getOpposite();     // output side
        // input side == facing (front of block)

        // ── Push held item out the back ────────────────────────────────────────
        ItemStack held = be.items.get(0);
        if (!held.isEmpty()) {
            BlockPos backPos = pos.relative(backDir);
            BlockEntity backBe = level.getBlockEntity(backPos);
            if (backBe instanceof Container outputContainer) {
                boolean inserted = tryInsert(outputContainer, held);
                if (inserted) {
                    be.items.set(0, ItemStack.EMPTY);
                } else {
                    return; // belt is blocked — don't pull either
                }
            } else {
                // No container behind — eject item into the world
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,
                        pos.getX() + 0.5 + backDir.getStepX() * 0.7,
                        pos.getY() + 0.25,
                        pos.getZ() + 0.5 + backDir.getStepZ() * 0.7,
                        held.copy()));
                be.items.set(0, ItemStack.EMPTY);
            }
        }

        // ── Pull a new item from the front ─────────────────────────────────────
        if (be.items.get(0).isEmpty()) {
            BlockPos frontPos = pos.relative(facing);
            BlockEntity frontBe = level.getBlockEntity(frontPos);
            if (frontBe instanceof Container inputContainer) {
                ItemStack pulled = tryExtract(inputContainer);
                if (!pulled.isEmpty()) {
                    be.items.set(0, pulled);
                }
            }
        }
    }

    /**
     * Tries to insert exactly one item into {@code container}.
     * @return {@code true} if the item was inserted.
     */
    private static boolean tryInsert(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) {
                container.setItem(i, stack.copyWithCount(1));
                return true;
            }
            if (ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                slot.grow(1);
                container.setChanged();
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts exactly one item from the first non-empty slot of {@code container}.
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

    // ── Accessors ─────────────────────────────────────────────────────────────

    public ItemStack getHeldItem()     { return items.get(0); }
    public float     getAnimProgress() { return animProgress;  }

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
