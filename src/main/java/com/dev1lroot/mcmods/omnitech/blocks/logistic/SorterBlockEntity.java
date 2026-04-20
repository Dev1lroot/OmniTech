package com.dev1lroot.mcmods.omnitech.blocks.logistic;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.SorterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for {@link SorterBlock}.
 *
 * <p><b>Slots:</b>
 * <ul>
 *   <li>Slot 0 ({@link #SLOT_BUFFER}): item currently waiting to be routed.
 *       Exposed to the outside world via {@link WorldlyContainer} from the
 *       input face only (hoppers / conveyor belts push items here).</li>
 *   <li>Slots 1–9  ({@link #SLOT_FILTER_LEFT}):   filter for the LEFT  output side.</li>
 *   <li>Slots 10–18 ({@link #SLOT_FILTER_BACK}):   filter for the BACK  output side.</li>
 *   <li>Slots 19–27 ({@link #SLOT_FILTER_RIGHT}):  filter for the RIGHT output side.</li>
 * </ul>
 *
 * <p><b>Routing priority:</b>
 * <ol>
 *   <li>If at least one filter side matches the item → route only to matching
 *       filtered sides (in clock order).</li>
 *   <li>Otherwise → route to unfiltered sides (in clock order).</li>
 *   <li>Sides with a non-empty filter that does <em>not</em> match are always
 *       blocked for this item.</li>
 * </ol>
 *
 * <p>The clock pointer advances by 1 every time an item is successfully
 * routed, cycling LEFT(0) → BACK(1) → RIGHT(2) → LEFT…
 */
public class SorterBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {

    // ── Slot indices ──────────────────────────────────────────────────────────

    /** Single input/holding slot — exposed from the FACING face via WorldlyContainer. */
    public static final int SLOT_BUFFER             = 0;
    public static final int SLOT_FILTER_LEFT        = 1;   // slots  1– 9
    public static final int SLOT_FILTER_BACK        = 10;  // slots 10–18
    public static final int SLOT_FILTER_RIGHT       = 19;  // slots 19–27
    public static final int FILTER_SLOTS_PER_SIDE   = 9;
    /** Total container size (1 buffer + 27 filter). */
    public static final int SLOT_COUNT              = 28;

    // ── Output side order — indexed by clockIndex ─────────────────────────────

    /** Maps clock index → filter slot base for that side. */
    private static final int[] FILTER_BASES = {
            SLOT_FILTER_LEFT, SLOT_FILTER_BACK, SLOT_FILTER_RIGHT
    };

    /** Maps clock index → empty WorldlyContainer face array for external queries. */
    private static final int[] BUFFER_SLOT_ARRAY  = { SLOT_BUFFER };
    private static final int[] EMPTY_SLOT_ARRAY   = {};

    // ── State ─────────────────────────────────────────────────────────────────

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    /** Round-robin pointer: 0=LEFT, 1=BACK, 2=RIGHT. */
    private int clockIndex = 0;

    public SorterBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.SORTER.get(), pos, state);
    }

    // ── BaseContainerBlockEntity ──────────────────────────────────────────────

    @Override protected Component getDefaultName() {
        return Component.translatable("container.omnitech.sorter");
    }

    @Override protected NonNullList<ItemStack> getItems()           { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize()                         { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new SorterMenu(containerId, inv, this);
    }

    // ── WorldlyContainer — restricts outside access to the buffer slot ────────

    @Override
    public int[] getSlotsForFace(Direction side) {
        // Only the FACING (input) face may insert into the buffer slot.
        // No face may extract — the sorter pushes items out itself.
        Direction facing = getBlockState().getValue(SorterBlock.FACING);
        return side == facing ? BUFFER_SLOT_ARRAY : EMPTY_SLOT_ARRAY;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        // External blocks may only push into the buffer, and only when it's empty.
        return index == SLOT_BUFFER && items.get(SLOT_BUFFER).isEmpty();
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return false; // sorter pushes out; nothing can pull from it
    }

    /** Filter slots are GUI-only; external automation may only touch the buffer. */
    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return index == SLOT_BUFFER;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            SorterBlockEntity be) {

        Direction facing = state.getValue(SorterBlock.FACING);

        // 1. If buffer empty, try to pull one item from the input-face neighbour.
        if (be.items.get(SLOT_BUFFER).isEmpty()) {
            BlockEntity inputBe = level.getBlockEntity(pos.relative(facing));
            if (inputBe instanceof Container input) {
                // The face of the neighbour that touches the sorter's front
                Direction neighborFace = facing.getOpposite();
                ItemStack pulled = tryExtract(input, neighborFace);
                if (!pulled.isEmpty()) {
                    be.items.set(SLOT_BUFFER, pulled);
                    be.setChanged();
                }
            }
        }

        // 2. Route the buffer item.
        ItemStack buffer = be.items.get(SLOT_BUFFER);
        if (buffer.isEmpty()) return;

        // Determine the three output side directions for this facing.
        Direction left  = facing.getCounterClockWise(); // clock index 0
        Direction back  = facing.getOpposite();          // clock index 1
        Direction right = facing.getClockWise();         // clock index 2
        Direction[] outputSides = { left, back, right };

        // Classify each side.
        boolean anyFilteredMatch = false;
        boolean[] filteredMatch = new boolean[3];
        boolean[] sideHasFilter = new boolean[3];

        for (int s = 0; s < 3; s++) {
            int base = FILTER_BASES[s];
            for (int f = 0; f < FILTER_SLOTS_PER_SIDE; f++) {
                ItemStack filter = be.items.get(base + f);
                if (!filter.isEmpty()) {
                    sideHasFilter[s] = true;
                    if (filter.is(buffer.getItem())) {
                        filteredMatch[s] = true;
                    }
                }
            }
            if (filteredMatch[s]) anyFilteredMatch = true;
        }

        // Try to route in clock order.
        for (int attempt = 0; attempt < 3; attempt++) {
            int sideIdx = (be.clockIndex + attempt) % 3;

            boolean eligible;
            if (anyFilteredMatch) {
                // Only send to sides whose filter explicitly matches.
                eligible = filteredMatch[sideIdx];
            } else {
                // Unfiltered fallback: only sides with no filter at all.
                eligible = !sideHasFilter[sideIdx];
            }
            if (!eligible) continue;

            if (tryInsertIntoNeighbor(level, pos, outputSides[sideIdx], buffer)) {
                be.items.set(SLOT_BUFFER, ItemStack.EMPTY);
                be.clockIndex = (be.clockIndex + 1) % 3;
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
                return;
            }
        }
        // All eligible sides are full or unavailable — hold the item.
    }

    // ── Transfer helpers ──────────────────────────────────────────────────────

    /**
     * Tries to extract exactly one item from {@code container} via
     * {@code neighborFace} (the face on the neighbour that touches us).
     * Respects {@link WorldlyContainer} slot visibility and extraction rules.
     */
    private static ItemStack tryExtract(Container container, Direction neighborFace) {
        int[] slots;
        if (container instanceof WorldlyContainer wc) {
            slots = wc.getSlotsForFace(neighborFace);
        } else {
            slots = new int[container.getContainerSize()];
            for (int i = 0; i < slots.length; i++) slots[i] = i;
        }

        for (int slotIdx : slots) {
            ItemStack stack = container.getItem(slotIdx);
            if (stack.isEmpty()) continue;

            if (container instanceof WorldlyContainer wc
                    && !wc.canTakeItemThroughFace(slotIdx, stack, neighborFace)) continue;

            ItemStack extracted = stack.copyWithCount(1);
            stack.shrink(1);
            if (stack.isEmpty()) container.setItem(slotIdx, ItemStack.EMPTY);
            container.setChanged();
            return extracted;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Tries to insert exactly one item into the neighbour at {@code dir}.
     * Respects {@link WorldlyContainer} if the neighbour implements it.
     * Uses {@link Container#canPlaceItem} as an additional filter.
     */
    static boolean tryInsertIntoNeighbor(Level level, BlockPos pos,
            Direction dir, ItemStack stack) {
        BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
        if (!(neighbor instanceof Container container)) return false;

        Direction enterFace = dir.getOpposite(); // the face items enter from (neighbour's perspective)

        int size = container.getContainerSize();
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
                    && existing.getCount() < Math.min(
                            existing.getMaxStackSize(), container.getMaxStackSize())) {
                existing.grow(1);
                container.setChanged();
                return true;
            }
        }
        return false;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        clockIndex = input.getIntOr("ClockIndex", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("ClockIndex", clockIndex);
    }
}
