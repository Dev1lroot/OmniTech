package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.labware.extractor.ExtractorBlockEntity;
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

public class ExtractorMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;
    private final int machineSlotCount;

    // Client constructor
    public ExtractorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(4));
    }

    // Server constructor
    public ExtractorMenu(int containerId, Inventory playerInventory,
                         BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.EXTRACTOR.get(), containerId);
        this.container = (Container) blockEntity;
        this.data      = data;

        addDataSlots(data);

        GuiLayout layout = GuiLayoutLoader.load("extractor");

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

    public FluidStack getOutputFluid() {
        if (container instanceof ExtractorBlockEntity be) return be.getOutputFluid();
        return FluidStack.EMPTY;
    }

    // ── ContainerData accessors ───────────────────────────────────────────────

    public float getKineticForce()          { return data.get(0) / 100f; }
    public float getRequiredKineticForce()  { return data.get(1) / 100f; }
    public int   getOutputFluidAmount()     { return data.get(2); }
    public int   getOutputFluidCapacity()   { return data.get(3); }

    public float getKfProgressScaled() {
        float req = getRequiredKineticForce();
        if (req <= 0f) return 0f;
        return Math.min(100f, getKineticForce() / req * 100f);
    }

    // ── Shift-click ───────────────────────────────────────────────────────────

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
            if (!this.moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else {
            if (!this.moveItemStackTo(slotStack, 0, machineSlotCount, false)) {
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
                player, OmniTechBlocks.EXTRACTOR.get());
    }
}
