package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.FoundryBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Comparator;
import java.util.List;

public class FoundryMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;
    private final int machineSlotCount;

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

        GuiLayout layout = GuiLayoutLoader.load("foundry");

        List<GuiElementDef> machineSlots = layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();

        for (GuiElementDef el : machineSlots) {
            if (el.slot_index == FoundryBlockEntity.TEMPLATE_SLOT) {
                addSlot(new TemplateSlot(container, el.slot_index, el.x, el.y));
            } else {
                addSlot(new Slot(container, el.slot_index, el.x, el.y));
            }
        }
        this.machineSlotCount = machineSlots.size();

        layout.addPlayerInventory(playerInventory, this::addSlot);
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
    public float getCookProgressScaled() {
        int total = getCookTotalTime();
        if (total <= 0) return 0f;
        // Возвращаем значение от 0.0 до 100.0
        return (float) getCookProgress() * 100f / total;
    }

    // ── Shift-click logic ──────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        int playerStart = machineSlotCount;
        int playerEnd   = playerStart + 27;
        int hotbarEnd   = playerEnd + 9;

        if (index < machineSlotCount) {
            // Machine → player inventory
            if (!this.moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else {
            // Player inventory/hotbar → template slot
            if (!this.moveItemStackTo(slotStack, 0, 1, false)) {
                // Couldn't go to template; swap within player inv/hotbar
                if (index < playerEnd) {
                    if (!this.moveItemStackTo(slotStack, playerEnd, hotbarEnd, false)) return ItemStack.EMPTY;
                } else {
                    if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, false)) return ItemStack.EMPTY;
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
