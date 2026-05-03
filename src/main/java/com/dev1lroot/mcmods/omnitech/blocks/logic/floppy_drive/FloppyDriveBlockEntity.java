package com.dev1lroot.mcmods.omnitech.blocks.logic.floppy_drive;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.FloppyDriveMenu;
import com.dev1lroot.mcmods.omnitech.items.FloppyDiskItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class FloppyDriveBlockEntity extends BaseContainerBlockEntity {

    private int driveId = 0;
    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    public FloppyDriveBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.FLOPPY_DRIVE.get(), pos, state);
    }

    public int getDriveId() { return driveId; }

    public void setDriveId(int id) {
        driveId = Math.clamp(id, 0, 65535);
        setChanged();
    }

    public ItemStack getDisk() { return items.get(0); }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.floppy_drive");
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        ContainerData data = new SimpleContainerData(1);
        data.set(0, driveId);
        return new FloppyDriveMenu(id, inv, this, data);
    }

    @Override
    public int getContainerSize() { return 1; }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof FloppyDiskItem;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("DriveId", driveId);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        driveId = in.getIntOr("DriveId", 0);
    }
}
