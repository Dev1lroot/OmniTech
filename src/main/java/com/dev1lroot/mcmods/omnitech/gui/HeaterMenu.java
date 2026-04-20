package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticGeneratorBlockEntity;
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

public class HeaterMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final int machineSlotCount;

    // Client constructor
    public HeaterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(4));
    }

    // Server constructor
    public HeaterMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.HEATER.get(), containerId);
        this.container = (Container) blockEntity;
        this.data      = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("heater");

        List<GuiElementDef> machineSlots = layout.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();

        for (GuiElementDef el : machineSlots) {
            addSlot(new FuelSlot(container, el.slot_index, el.x, el.y));
        }
        this.machineSlotCount = machineSlots.size();

        layout.addPlayerInventory(playerInventory, this::addSlot);
    }

    // ── Data accessors ─────────────────────────────────────────────────────────

    public int getBurnTime()    { return data.get(0); }
    public int getMaxBurnTime() { return data.get(1); }
    public int getStoredHeat()  { return data.get(2); }
    public int getMaxHeat()     { return data.get(3); }

    /** Flame height (0–14 px) for the burn indicator. */
    public int getFlameHeight() {
        int max = getMaxBurnTime();
        return max != 0 ? getBurnTime() * 14 / max : 0;
    }

    /** Heat gauge fill height (0–52 px) for the heat bar. */
    public int getHeatBarHeight() {
        int max = getMaxHeat();
        return max != 0 ? getStoredHeat() * 52 / max : 0;
    }

    // ── Menu logic ─────────────────────────────────────────────────────────────

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
            if (!moveItemStackTo(slotStack, playerStart, hotbarEnd, false)) return ItemStack.EMPTY;
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
                        container instanceof BlockEntity be ? be.getLevel() : null,
                        container instanceof BlockEntity be ? be.getBlockPos() : null),
                player, OmniTechBlocks.HEATER.get());
    }

    private static class FuelSlot extends Slot {
        public FuelSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return KineticGeneratorBlockEntity.getBurnDuration(stack) > 0;
        }
    }
}
