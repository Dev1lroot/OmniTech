package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.StirlingEngineBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

public class StirlingEngineMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    // Slot positions in the GUI
    private static final int WATER_IN_X  = 27, WATER_IN_Y  = 17;
    private static final int EMPTY_OUT_X = 27, EMPTY_OUT_Y = 53;

    // Client constructor
    public StirlingEngineMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(4));
    }

    // Server constructor
    public StirlingEngineMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.STIRLING_ENGINE.get(), containerId);
        this.container = (Container) blockEntity;
        this.data      = data;

        addDataSlots(data);
        addSlot(new WaterBucketSlot(container, StirlingEngineBlockEntity.SLOT_WATER_IN,
                WATER_IN_X, WATER_IN_Y));
        addSlot(new OutputSlot(container, StirlingEngineBlockEntity.SLOT_EMPTY_OUT,
                EMPTY_OUT_X, EMPTY_OUT_Y));
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    // ── Data accessors ─────────────────────────────────────────────────────────

    public int getStoredHeat()  { return data.get(0); }
    public int getMaxHeat()     { return data.get(1); }
    public int getStoredWater() { return data.get(2); }
    public int getMaxWater()    { return data.get(3); }
    public boolean isRunning()  { return getStoredHeat() > StirlingEngineBlockEntity.MIN_RUNNING_HEAT
                                      && getStoredWater() > 0; }

    /** Heat gauge fill height (0–52 px). */
    public int getHeatBarHeight() {
        int max = getMaxHeat();
        return max != 0 ? getStoredHeat() * 52 / max : 0;
    }

    /** Water gauge fill height (0–52 px). */
    public int getWaterBarHeight() {
        int max = getMaxWater();
        return max != 0 ? getStoredWater() * 52 / max : 0;
    }

    // ── Menu logic ─────────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        // Machine slots (0-1) → player inventory
        if (index < 2) {
            if (!moveItemStackTo(slotStack, 2, 38, false)) return ItemStack.EMPTY;
        }
        // Player inventory (2-37) → machine slots
        else {
            if (slotStack.is(Items.WATER_BUCKET)) {
                if (!moveItemStackTo(slotStack, 0, 1, false)) {
                    if (index < 29) {
                        if (!moveItemStackTo(slotStack, 29, 38, false)) return ItemStack.EMPTY;
                    } else {
                        if (!moveItemStackTo(slotStack, 2, 29, false)) return ItemStack.EMPTY;
                    }
                }
            } else {
                if (index < 29) {
                    if (!moveItemStackTo(slotStack, 29, 38, false)) return ItemStack.EMPTY;
                } else {
                    if (!moveItemStackTo(slotStack, 2, 29, false)) return ItemStack.EMPTY;
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
        return stillValid(
                ContainerLevelAccess.create(
                        container instanceof BlockEntity be ? be.getLevel() : null,
                        container instanceof BlockEntity be ? be.getBlockPos() : null),
                player, OmniTechBlocks.STIRLING_ENGINE.get());
    }

    // ── Layout helpers ─────────────────────────────────────────────────────────

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
    }

    private void addPlayerHotbar(Inventory inventory) {
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
    }

    // ── Inner slot types ───────────────────────────────────────────────────────

    /** Only accepts water buckets. */
    private static class WaterBucketSlot extends Slot {
        public WaterBucketSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(Items.WATER_BUCKET);
        }
    }

    /** Output-only slot (players cannot manually insert items). */
    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
    }
}
