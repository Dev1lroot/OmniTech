package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.FluidFillerBlockEntity;
import com.dev1lroot.mcmods.omnitech.items.FluidCanisterItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

public class FluidFillerMenu extends AbstractContainerMenu {

    private final Container  container;
    private final ContainerData data;

    public static final int IN_CANISTER_SLOT_X  = 80;
    public static final int IN_CANISTER_SLOT_Y  = 8;
    public static final int OUT_CANISTER_SLOT_X = 80;
    public static final int OUT_CANISTER_SLOT_Y = 30;

    // Client constructor
    public FluidFillerMenu(int containerId, Inventory playerInventory,
            FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(6));
    }

    // Server constructor
    public FluidFillerMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.FLUID_FILLER.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        // Input canister slot — only accepts FluidCanisterItem
        addSlot(new Slot(container, FluidFillerBlockEntity.SLOT_INPUT_CANISTER,
                IN_CANISTER_SLOT_X, IN_CANISTER_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof FluidCanisterItem;
            }
        });

        // Output canister slot — players may take items, but not insert
        addSlot(new Slot(container, FluidFillerBlockEntity.SLOT_OUTPUT_CANISTER,
                OUT_CANISTER_SLOT_X, OUT_CANISTER_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        });

        // Player inventory (slots 2..28)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));

        // Player hotbar (slots 29..37)
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
    }

    // ── Fluid accessors (GUI) ─────────────────────────────────────────────────

    public FluidStack getInputFluid() {
        if (container instanceof FluidFillerBlockEntity be) return be.getInputFluid();
        return FluidStack.EMPTY;
    }

    public FluidStack getOutputFluid() {
        if (container instanceof FluidFillerBlockEntity be) return be.getOutputFluid();
        return FluidStack.EMPTY;
    }

    // ── ContainerData accessors ───────────────────────────────────────────────

    public int getInputFluidAmount()    { return data.get(0); }
    public int getInputFluidCapacity()  { return data.get(1); }
    public int getOutputFluidAmount()   { return data.get(2); }
    public int getOutputFluidCapacity() { return data.get(3); }
    public int getProcessTimer()        { return data.get(4); }
    public int getProcessTime()         { return data.get(5); }

    /** Progress scaled to [0, 100] for the progress bar. */
    public float getProgressScaled() {
        int max = getProcessTime();
        return max > 0 ? getProcessTimer() * 100f / max : 0f;
    }

    // ── Shift-click ───────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack slotStack = slot.getItem();
        result = slotStack.copy();

        if (index == 0 || index == 1) {
            // From machine slot → player inventory
            if (!moveItemStackTo(slotStack, 2, 38, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else if (index < 29) {
            // From player inventory → try machine input slot
            if (!moveItemStackTo(slotStack, 0, 1, false))
                if (!moveItemStackTo(slotStack, 29, 38, false)) return ItemStack.EMPTY;
        } else {
            // From hotbar → try machine input slot, then player inv
            if (!moveItemStackTo(slotStack, 0, 1, false))
                if (!moveItemStackTo(slotStack, 2, 29, false)) return ItemStack.EMPTY;
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
                player, OmniTechBlocks.FLUID_FILLER.get());
    }
}
