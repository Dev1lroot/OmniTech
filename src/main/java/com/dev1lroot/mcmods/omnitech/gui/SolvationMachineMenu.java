package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.SolvationMachineBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

public class SolvationMachineMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;

    /** Item input slot position (GUI-relative). */
    public static final int SLOT_X = 80;
    public static final int SLOT_Y = 35;

    // Client constructor
    public SolvationMachineMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(6));
    }

    // Server constructor
    public SolvationMachineMenu(int containerId, Inventory playerInventory,
                                BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.SOLVATION_MACHINE.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        // Single item input slot
        addSlot(new Slot(container, SolvationMachineBlockEntity.SLOT_INPUT, SLOT_X, SLOT_Y));

        // Player inventory (slots 1–27)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar (slots 28–36)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    // ── Fluid accessors (cast to BE on server; return EMPTY on client) ─────────

    public FluidStack getInputFluid() {
        if (container instanceof SolvationMachineBlockEntity be) return be.getInputFluid();
        return FluidStack.EMPTY;
    }

    public FluidStack getOutputFluid() {
        if (container instanceof SolvationMachineBlockEntity be) return be.getOutputFluid();
        return FluidStack.EMPTY;
    }

    // ── ContainerData accessors ────────────────────────────────────────────────

    /** Current accumulated KF (fixed-point ÷100). */
    public float getKineticForce()         { return data.get(0) / 100f; }

    /** KF required for current recipe (fixed-point ÷100). */
    public float getRequiredKineticForce() { return data.get(1) / 100f; }

    public int getInputFluidAmount()       { return data.get(2); }
    public int getInputFluidCapacity()     { return data.get(3); }
    public int getOutputFluidAmount()      { return data.get(4); }
    public int getOutputFluidCapacity()    { return data.get(5); }

    /**
     * Returns KF progress as a value in [0, 100], suitable for
     * {@code GuiUtil.renderProgressBar}.
     */
    public float getKfProgressScaled() {
        float required = getRequiredKineticForce();
        if (required <= 0f) return 0f;
        return Math.min(100f, getKineticForce() / required * 100f);
    }

    // ── Shift-click ───────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        // Slot 0 is the machine input; slots 1–36 are player
        if (index == 0) {
            if (!this.moveItemStackTo(slotStack, 1, 37, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else {
            if (!this.moveItemStackTo(slotStack, 0, 1, false)) {
                // Move between hotbar and inventory
                if (index < 28) {
                    if (!this.moveItemStackTo(slotStack, 28, 37, false)) return ItemStack.EMPTY;
                } else {
                    if (!this.moveItemStackTo(slotStack, 1, 28, false)) return ItemStack.EMPTY;
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
                player, OmniTechBlocks.SOLVATION_MACHINE.get());
    }
}
