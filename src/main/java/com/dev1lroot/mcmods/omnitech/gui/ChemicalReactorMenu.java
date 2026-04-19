package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.ChemicalReactorBlockEntity;
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
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Comparator;
import java.util.List;

public class ChemicalReactorMenu extends AbstractContainerMenu {

    private final Container  container;
    private final ContainerData data;
    private final int machineSlotCount;

    // Client constructor
    public ChemicalReactorMenu(int containerId, Inventory playerInventory,
            FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(10));
    }

    // Server constructor
    public ChemicalReactorMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.CHEMICAL_REACTOR.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("chemical_reactor");

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

    // ── Fluid accessors ───────────────────────────────────────────────────────

    public FluidStack getInputFluid1() {
        if (container instanceof ChemicalReactorBlockEntity be) return be.getInputFluid1();
        return FluidStack.EMPTY;
    }

    public FluidStack getInputFluid2() {
        if (container instanceof ChemicalReactorBlockEntity be) return be.getInputFluid2();
        return FluidStack.EMPTY;
    }

    public FluidStack getOutputFluid() {
        if (container instanceof ChemicalReactorBlockEntity be) return be.getOutputFluid();
        return FluidStack.EMPTY;
    }

    // ── ContainerData accessors ───────────────────────────────────────────────

    public int   getStoredHeat()          { return data.get(0); }
    public int   getRequiredTemperature() { return data.get(1); }
    public int   getProcessTimer()        { return data.get(2); }
    public int   getProcessTotalTime()    { return data.get(3); }
    public int   getInputFluid1Amount()   { return data.get(4); }
    public int   getInputFluid1Capacity() { return data.get(5); }
    public int   getInputFluid2Amount()   { return data.get(6); }
    public int   getInputFluid2Capacity() { return data.get(7); }
    public int   getOutputFluidAmount()   { return data.get(8); }
    public int   getOutputFluidCapacity() { return data.get(9); }

    /** Progress scaled to [0, 100] for the arrow bar. */
    public float getProgressScaled() {
        int total = getProcessTotalTime();
        return total > 0 ? getProcessTimer() * 100f / total : 0f;
    }

    // ── Shift-click ───────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        int playerStart = machineSlotCount;
        int playerEnd   = playerStart + 27;
        int hotbarEnd   = playerEnd + 9;

        if (index < machineSlotCount) {
            // Catalyst slot → player inventory
            if (!moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else if (index < playerEnd) {
            // Player inventory → catalyst slot, then hotbar
            if (!moveItemStackTo(slotStack, 0, machineSlotCount, false))
                if (!moveItemStackTo(slotStack, playerEnd, hotbarEnd, false)) return ItemStack.EMPTY;
        } else {
            // Hotbar → catalyst slot, then player inventory
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
                player, OmniTechBlocks.CHEMICAL_REACTOR.get());
    }
}
