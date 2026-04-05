package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.FoundryBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FoundryMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;

    // Machine slot positions
    private static final int TEMPLATE_X = 35, TEMPLATE_Y = 35;
    private static final int OUTPUT_X   = 98, OUTPUT_Y   = 35;

    // Client constructor
    public FoundryMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(6));
    }

    // Server constructor
    public FoundryMenu(int containerId, Inventory playerInventory,
                       BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.FOUNDRY.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        // Template slot — players can place/take freely; automation is blocked in the
        // block entity's canPlaceItem / canTakeItem overrides.
        addSlot(new TemplateSlot(container, FoundryBlockEntity.TEMPLATE_SLOT, TEMPLATE_X, TEMPLATE_Y));

        // Output slot — players can take; no one can insert.
        addSlot(new Slot(container, FoundryBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y));

        // Player inventory (slots 2–28)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar (slots 29–37)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    // ── Fluid type accessor (reads from BE, synced via getUpdatePacket) ────────

    public FluidStack getInputFluid() {
        if (container instanceof FoundryBlockEntity be) return be.getInputFluid();
        return FluidStack.EMPTY;
    }

    // ── Data accessors ─────────────────────────────────────────────────────────

    public int getTemperature()         { return data.get(0); }
    public int getRequiredTemperature() { return data.get(1); }
    public int getCookProgress()        { return data.get(2); }
    public int getCookTotalTime()       { return data.get(3); }
    public int getFluidAmount()         { return data.get(4); }
    public int getFluidCapacity()       { return data.get(5); }

    /** Progress arrow width in pixels (0–24). */
    public int getCookProgressWidth() {
        int total = getCookTotalTime();
        return total != 0 ? getCookProgress() * 24 / total : 0;
    }

    /** Fluid gauge fill height in pixels (0–52). */
    public int getFluidBarHeight() {
        int cap = getFluidCapacity();
        return cap != 0 ? getFluidAmount() * 52 / cap : 0;
    }

    // ── Shift-click logic ──────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        int invStart   = FoundryBlockEntity.SLOT_COUNT;   // 2
        int invEnd     = invStart + 27;                    // 29
        int hotbarEnd  = invEnd + 9;                       // 38

        if (index == FoundryBlockEntity.TEMPLATE_SLOT || index == FoundryBlockEntity.OUTPUT_SLOT) {
            // Machine → player inventory
            if (!this.moveItemStackTo(slotStack, invStart, hotbarEnd, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(slotStack, result);
        } else {
            // Player inventory/hotbar → template slot
            if (!this.moveItemStackTo(slotStack, 0, 1, false)) {
                // Couldn't go to template; swap within player inv/hotbar
                if (index < invEnd) {
                    if (!this.moveItemStackTo(slotStack, invEnd, hotbarEnd, false)) return ItemStack.EMPTY;
                } else {
                    if (!this.moveItemStackTo(slotStack, invStart, invEnd, false)) return ItemStack.EMPTY;
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
                player, OmniTechBlocks.FOUNDRY.get());
    }

    // ── Template slot — allows player interaction, bypasses canPlaceItem ───────

    private static class TemplateSlot extends Slot {
        TemplateSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        /** Allow players to insert any item into the template slot. */
        @Override
        public boolean mayPlace(ItemStack stack) { return true; }
    }
}
