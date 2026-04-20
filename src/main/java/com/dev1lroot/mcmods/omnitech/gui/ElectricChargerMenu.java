package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Comparator;
import java.util.List;

public class ElectricChargerMenu extends AbstractContainerMenu {

    private final ContainerData data;
    private final BlockEntity blockEntity;
    private final int machineSlotCount;

    /** Client-side constructor. */
    public ElectricChargerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(4));
    }

    /** Server-side constructor. */
    public ElectricChargerMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ELECTRIC_CHARGER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);

        Container container = (Container) blockEntity;
        GuiLayout layout = GuiLayoutLoader.load("electric_charger");

        List<GuiElementDef> machineSlots = layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();

        for (GuiElementDef el : machineSlots) {
            addSlot(new Slot(container, el.slot_index, el.x, el.y));
        }
        this.machineSlotCount = machineSlots.size();

        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    public float getEnergyStored() { return data.get(0) / 10f; }
    public float getMaxEu()        { return data.get(1) / 10f; }
    public int   getItemEu()       { return data.get(2); }
    public int   getItemMaxEu()    { return data.get(3); }

    /** Item charge 0–100 for a progressbar element. */
    public float getItemChargeScaled() {
        int max = getItemMaxEu();
        return max > 0 ? getItemEu() * 100f / max : 0f;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int playerStart = machineSlotCount;
        int playerEnd   = playerStart + 27;
        int hotbarEnd   = playerEnd + 9;

        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        if (index < machineSlotCount) {
            if (!moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else if (index < playerEnd) {
            if (!moveItemStackTo(slotStack, 0, machineSlotCount, false))
                if (!moveItemStackTo(slotStack, playerEnd, hotbarEnd, false)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(slotStack, 0, machineSlotCount, false))
                if (!moveItemStackTo(slotStack, playerStart, playerEnd, false)) return ItemStack.EMPTY;
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
                        blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, OmniTechBlocks.ELECTRIC_CHARGER.get());
    }
}
