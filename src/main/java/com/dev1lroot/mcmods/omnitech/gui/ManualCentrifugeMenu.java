package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.ManualCentrifugeBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Comparator;
import java.util.List;

public class ManualCentrifugeMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final int machineSlotCount;

    // Client constructor
    public ManualCentrifugeMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(2));
    }

    // Server constructor
    public ManualCentrifugeMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.MANUAL_CENTRIFUGE.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("manual_centrifuge");

        List<GuiElementDef> machineSlots = layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();

        for (GuiElementDef el : machineSlots) {
            if (el.slot_index == ManualCentrifugeBlockEntity.SLOT_INPUT) {
                addSlot(new Slot(container, el.slot_index, el.x, el.y));
            } else {
                addSlot(new OutputSlot(container, el.slot_index, el.x, el.y));
            }
        }
        this.machineSlotCount = machineSlots.size();

        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    public int getKineticForce()         { return data.get(0); }
    public int getRequiredKineticForce() { return data.get(1); }

    public float getKfProgressScaled() {
        int required = getRequiredKineticForce();
        return required != 0 ? Math.min(100f, getKineticForce() * 100f / required) : 0f;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int playerStart = machineSlotCount;
        int playerEnd   = playerStart + 27;
        int hotbarEnd   = playerEnd + 9;

        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        // Output slots (indices 1 to machineSlotCount-1) → player inventory
        if (index >= 1 && index < machineSlotCount) {
            if (!this.moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else if (index >= playerStart) {
            if (!this.moveItemStackTo(slotStack, 0, 1, false)) {
                if (index < playerEnd) {
                    if (!this.moveItemStackTo(slotStack, playerEnd, hotbarEnd, false)) return ItemStack.EMPTY;
                } else {
                    if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, false)) return ItemStack.EMPTY;
                }
            }
        } else {
            if (!this.moveItemStackTo(slotStack, playerStart, hotbarEnd, false)) return ItemStack.EMPTY;
        }

        if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (slotStack.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, slotStack);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(
                        container instanceof BlockEntity be ? be.getLevel() : null,
                        container instanceof BlockEntity be ? be.getBlockPos() : null),
                player, OmniTechBlocks.MANUAL_CENTRIFUGE.get());
    }

    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
    }
}
