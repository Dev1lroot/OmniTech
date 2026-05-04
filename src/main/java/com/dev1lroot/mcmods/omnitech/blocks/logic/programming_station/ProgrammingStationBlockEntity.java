package com.dev1lroot.mcmods.omnitech.blocks.logic.programming_station;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.gui.ProgrammingStationMenu;
import com.dev1lroot.mcmods.omnitech.items.MicrocontrollerItem;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class ProgrammingStationBlockEntity extends BaseContainerBlockEntity {

    public static final int MAX_PROGRAM_LEN = 1_048_576;

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private String programText = "";

    public ProgrammingStationBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.PROGRAMMING_STATION.get(), pos, state);
    }

    public String getProgramText() { return programText; }

    /** Called by the server when the player uploads a program from the GUI. */
    public void uploadProgram(String text) {
        programText = text;
        ItemStack mc = items.get(0);
        if (!mc.isEmpty() && mc.getItem() instanceof MicrocontrollerItem) {
            mc.set(OmniTechDataComponents.PROGRAM.get(), text);
        }
        setChanged();
    }

    // ── BaseContainerBlockEntity ──────────────────────────────────────────────

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.programming_station");
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        // Prefer the chip's stored program so the player can re-edit it after reinsertion
        ItemStack mc = items.get(0);
        String prog = programText;
        if (!mc.isEmpty() && mc.getItem() instanceof MicrocontrollerItem) {
            String mcProg = mc.getOrDefault(OmniTechDataComponents.PROGRAM.get(), "");
            if (!mcProg.isEmpty()) prog = mcProg;
        }
        return new ProgrammingStationMenu(containerId, playerInventory, this, prog);
    }

    @Override
    public int getContainerSize() { return 1; }

    @Override
    public boolean stillValid(Player player) { return true; }

    // ── Serialization ────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items);
        out.putString("Program", programText);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        ContainerHelper.loadAllItems(in, items);
        programText = in.getStringOr("Program", "");
    }
}
