package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.SorterBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Menu for {@link SorterBlockEntity}.
 *
 * <p>Slot layout (menu indices):
 * <pre>
 *  0– 8  : Left-side filter (9 slots, row 0)
 *  9–17  : Back-side filter (9 slots, row 1)
 * 18–26  : Right-side filter (9 slots, row 2)
 * 27–53  : Player inventory (3 rows × 9)
 * 54–62  : Player hotbar (9)
 * </pre>
 *
 * <p>The buffer slot (container slot 0) is intentionally hidden from the GUI
 * so players cannot accidentally place items there — it is populated by
 * adjacent containers or by the sorter's own active-pull logic.
 */
public class SorterMenu extends AbstractContainerMenu {

    private final Container container;

    // ── Slot layout constants ─────────────────────────────────────────────────

    // Filter rows start x position and y positions per row (relative to GUI top-left)
    private static final int FILTER_X          = 7;
    private static final int FILTER_Y_LEFT      = 18;
    private static final int FILTER_Y_BACK      = 36;
    private static final int FILTER_Y_RIGHT     = 54;
    private static final int PLAYER_INV_X       = 7;
    private static final int PLAYER_INV_Y       = 84;
    private static final int PLAYER_HOTBAR_Y    = 142;

    /** First menu index of the player inventory rows. */
    private static final int PLAYER_INV_START   = 27;
    /** First menu index of the player hotbar. */
    private static final int PLAYER_HOT_START   = 54;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Client-side constructor — receives block pos via extra data buffer. */
    public SorterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    /** Server-side constructor — receives the actual block entity. */
    public SorterMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity) {
        super(OmniTechMenuTypes.SORTER.get(), containerId);
        this.container = (Container) blockEntity;

        // ── Filter slots (container slots 1-27 → menu slots 0-26) ─────────────
        addFilterRow(SorterBlockEntity.SLOT_FILTER_LEFT,  FILTER_Y_LEFT);
        addFilterRow(SorterBlockEntity.SLOT_FILTER_BACK,  FILTER_Y_BACK);
        addFilterRow(SorterBlockEntity.SLOT_FILTER_RIGHT, FILTER_Y_RIGHT);

        // ── Player inventory ──────────────────────────────────────────────────
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory,
                        col + row * 9 + 9,
                        PLAYER_INV_X + col * 18,
                        PLAYER_INV_Y + row * 18));
            }
        }

        // ── Player hotbar ─────────────────────────────────────────────────────
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col,
                    PLAYER_INV_X + col * 18, PLAYER_HOTBAR_Y));
        }
    }

    private void addFilterRow(int containerSlotBase, int y) {
        for (int i = 0; i < SorterBlockEntity.FILTER_SLOTS_PER_SIDE; i++) {
            final int slotContainerIdx = containerSlotBase + i;
            addSlot(new Slot(container, slotContainerIdx, FILTER_X + i * 18, y) {
                @Override public boolean mayPlace(ItemStack stack) { return true; }
            });
        }
    }

    // ── Shift-click ───────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        if (index < PLAYER_INV_START) {
            // Filter slot → player inventory
            if (!moveItemStackTo(slotStack, PLAYER_INV_START, 63, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else {
            // Player inv/hotbar → first empty filter slot
            if (!moveItemStackTo(slotStack, 0, PLAYER_INV_START, false)) {
                // Swap between hotbar and inv
                if (index < PLAYER_HOT_START) {
                    if (!moveItemStackTo(slotStack, PLAYER_HOT_START, 63, false)) return ItemStack.EMPTY;
                } else {
                    if (!moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_HOT_START, false)) return ItemStack.EMPTY;
                }
            }
        }

        if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (slotStack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, slotStack);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container instanceof BlockEntity be
                && stillValid(ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()),
                        player, OmniTechBlocks.SORTER.get());
    }
}
