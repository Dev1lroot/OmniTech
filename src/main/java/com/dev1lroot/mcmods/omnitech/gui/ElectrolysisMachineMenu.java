package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechBlocks;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.ElectrolysisMachineBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

public class ElectrolysisMachineMenu extends AbstractContainerMenu {

    private final Container  container;
    private final ContainerData data;

    /** Anode item slot (nickel rod etc.) */
    public static final int ANODE_SLOT_X   = 44;
    public static final int ANODE_SLOT_Y   = 26;
    /** Cathode item slot (graphite rod etc.) */
    public static final int CATHODE_SLOT_X = 108;
    public static final int CATHODE_SLOT_Y = 26;

    // Client constructor
    public ElectrolysisMachineMenu(int containerId, Inventory playerInventory,
            FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(13));
    }

    // Server constructor
    public ElectrolysisMachineMenu(int containerId, Inventory playerInventory,
            BlockEntity blockEntity, ContainerData data) {
        super(OmniTechMenuTypes.ELECTROLYSIS_MACHINE.get(), containerId);
        this.container = (Container) blockEntity;
        this.data = data;

        addDataSlots(data);

        addSlot(new Slot(container, ElectrolysisMachineBlockEntity.SLOT_ANODE,
                ANODE_SLOT_X, ANODE_SLOT_Y));
        addSlot(new Slot(container, ElectrolysisMachineBlockEntity.SLOT_CATHODE,
                CATHODE_SLOT_X, CATHODE_SLOT_Y));

        // Player inventory (slots 2..28)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));

        // Player hotbar (slots 29..37)
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
    }

    // ── Fluid accessors ───────────────────────────────────────────────────────

    public FluidStack getInputFluid() {
        if (container instanceof ElectrolysisMachineBlockEntity be) return be.getInputFluid();
        return FluidStack.EMPTY;
    }
    public FluidStack getAnodeFluid() {
        if (container instanceof ElectrolysisMachineBlockEntity be) return be.getAnodeFluid();
        return FluidStack.EMPTY;
    }
    public FluidStack getCathodeFluid() {
        if (container instanceof ElectrolysisMachineBlockEntity be) return be.getCathodeFluid();
        return FluidStack.EMPTY;
    }
    public FluidStack getSolutionFluid() {
        if (container instanceof ElectrolysisMachineBlockEntity be) return be.getSolutionFluid();
        return FluidStack.EMPTY;
    }

    // ── ContainerData accessors ───────────────────────────────────────────────

    public float getEnergyStored()      { return data.get(0) / 10f; }
    public float getMaxEu()             { return data.get(1) / 10f; }
    public int   getCookProgress()      { return data.get(2); }
    public int   getCookTime()          { return data.get(3); }
    public float getEuPerRecipe()       { return data.get(4) / 10f; }

    public int getInputFluidAmount()    { return data.get(5); }
    public int getInputFluidCapacity()  { return data.get(6); }
    public int getAnodeFluidAmount()    { return data.get(7); }
    public int getAnodeFluidCapacity()  { return data.get(8); }
    public int getCathodeFluidAmount()  { return data.get(9); }
    public int getCathodeFluidCapacity(){ return data.get(10); }
    public int getSolutionFluidAmount() { return data.get(11); }
    public int getSolutionFluidCapacity(){ return data.get(12); }

    /** Cook progress scaled to [0, 100] for the progress bar. */
    public float getCookProgressScaled() {
        int max = getCookTime();
        return max > 0 ? getCookProgress() * 100f / max : 0f;
    }

    /** Energy bar fill width (0..24 px). */
    public int getEnergyBarWidth() {
        float max = getMaxEu();
        return max > 0f ? (int)(getEnergyStored() * 24f / max) : 0;
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
            // From machine slot → player
            if (!moveItemStackTo(slotStack, 2, 38, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slotStack, result);
        } else if (index < 29) {
            // From player inventory → try machine slots
            if (!moveItemStackTo(slotStack, 0, 2, false))
                if (!moveItemStackTo(slotStack, 29, 38, false)) return ItemStack.EMPTY;
        } else {
            // From hotbar → try machine slots, then player inv
            if (!moveItemStackTo(slotStack, 0, 2, false))
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
                player, OmniTechBlocks.ELECTROLYSIS_MACHINE.get());
    }
}
