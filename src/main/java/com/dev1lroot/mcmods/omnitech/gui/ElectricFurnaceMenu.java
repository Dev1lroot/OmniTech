package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.ElectricFurnaceBlockEntity;
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

/**
 * Menu for the Electric Furnace.
 *
 * <p>ContainerData layout (mirrors {@link ElectricFurnaceBlockEntity}):
 * <ul>
 *   <li>0 – energyStored × 10 (fixed-point)</li>
 *   <li>1 – maxEu × 10 (fixed-point)</li>
 *   <li>2 – cookProgress (0..COOK_TIME)</li>
 *   <li>3 – COOK_TIME</li>
 *   <li>4 – EU_PER_RECIPE × 10</li>
 * </ul>
 */
public class ElectricFurnaceMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockEntity blockEntity;
    private final int machineSlotCount;

    /** Client-side constructor. */
    public ElectricFurnaceMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(5));
    }

    /** Server-side constructor. */
    public ElectricFurnaceMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ELECTRIC_FURNACE.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addDataSlots(data);

        Container container = (Container) blockEntity;
        GuiLayout layout = GuiLayoutLoader.load("electric_furnace");

        List<GuiElementDef> machineSlots = layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();

        for (GuiElementDef el : machineSlots) {
            if (el.slot_index == ElectricFurnaceBlockEntity.SLOT_OUTPUT) {
                addSlot(new OutputSlot(container, el.slot_index, el.x, el.y));
            } else {
                addSlot(new Slot(container, el.slot_index, el.x, el.y));
            }
        }
        this.machineSlotCount = machineSlots.size();

        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    /** EU currently stored (decoded from fixed-point). */
    public float getEnergyStored() { return data.get(0) / 10f; }
    /** Maximum EU the buffer can hold (decoded from fixed-point). */
    public float getMaxEu()        { return data.get(1) / 10f; }
    /** Cook timer (0..getCookTime()). */
    public int getCookProgress()   { return data.get(2); }
    /** Total cook time in ticks. */
    public int getCookTime()       { return data.get(3); }
    /** EU consumed per recipe (decoded from fixed-point). */
    public float getEuPerRecipe()  { return data.get(4) / 10f; }

    /** Cook progress as 0–100 float for the progressbar JSON element. */
    public float getCookProgressScaled() {
        int max = getCookTime();
        return max > 0 ? getCookProgress() * 100f / max : 0f;
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
                player, OmniTechBlocks.ELECTRIC_FURNACE.get());
    }

    /** Output slot — players cannot place items into it. */
    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) { return false; }
    }
}
