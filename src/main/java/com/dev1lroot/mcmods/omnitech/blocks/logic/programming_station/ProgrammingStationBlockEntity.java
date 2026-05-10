package com.dev1lroot.mcmods.omnitech.blocks.logic.programming_station;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.gui.ProgrammingStationMenu;
import com.dev1lroot.mcmods.omnitech.items.RomItem;
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

    public static final int MAX_ROM_SIZE = 1_048_576;

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    public ProgrammingStationBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.PROGRAMMING_STATION.get(), pos, state);
    }

    /** Returns the binary data from the currently inserted ROM, or an empty array if none. */
    public byte[] getRomData() {
        ItemStack rom = items.get(0);
        if (!rom.isEmpty() && rom.getItem() instanceof RomItem) {
            OmniTechDataComponents.ByteData data = rom.get(OmniTechDataComponents.ROM_DATA.get());
            if (data != null) return data.data();
        }
        return new byte[0];
    }

    /** Called by the server when the player flashes edited data from the GUI. */
    public void flashRom(byte[] data) {
        ItemStack rom = items.get(0);
        if (!rom.isEmpty() && rom.getItem() instanceof RomItem r
                && RomItem.TYPE_FIRMWARE.equals(r.getRomType())) {
            rom.set(OmniTechDataComponents.ROM_DATA.get(),
                    new OmniTechDataComponents.ByteData(data.clone()));
            setChanged();
        }
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
        return new ProgrammingStationMenu(containerId, playerInventory, this, getRomData());
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
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        ContainerHelper.loadAllItems(in, items);
    }
}
