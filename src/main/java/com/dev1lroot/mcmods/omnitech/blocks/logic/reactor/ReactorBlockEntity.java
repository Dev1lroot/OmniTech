package com.dev1lroot.mcmods.omnitech.blocks.logic.reactor;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ReactorMenu;
import com.dev1lroot.mcmods.omnitech.items.ReactorRodItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ReactorBlockEntity extends BaseContainerBlockEntity {

    private boolean formed = false;
    private ReactorStructure structure = null;
    private NonNullList<ItemStack> items = NonNullList.create();

    // Pending re-validation after world load
    private boolean pendingRevalidation = false;
    private int savedWidth     = 3;
    private int savedDepth     = 3;
    private int savedCellCount = 0;

    private int revalidateTick = 0;

    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.REACTOR.get(), pos, state);
    }

    // ── BaseContainerBlockEntity ──────────────────────────────────────────────

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.reactor");
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> newItems) { this.items = newItems; }

    @Override
    public int getContainerSize() { return items.size(); }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ReactorMenu(containerId, inv, this);
    }

    // ── Structure ─────────────────────────────────────────────────────────────

    public boolean isFormed() { return formed; }

    public ReactorStructure getStructure() { return structure; }

    public void form(ReactorStructure s) {
        formed    = true;
        structure = s;
        int newCount = s.cells.size();
        if (items.size() != newCount) {
            NonNullList<ItemStack> resized = NonNullList.withSize(newCount, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(items.size(), newCount); i++) {
                resized.set(i, items.get(i));
            }
            items = resized;
        }
        setChanged();
    }

    @Override
    public void setRemoved() {
        Level lv = getLevel();
        if (formed && lv != null && !lv.isClientSide()) {
            BlockPos pos = getBlockPos();
            if (!(lv.getBlockState(pos).getBlock() instanceof ReactorBlock)) {
                dropAndCleanCells(lv, pos);
            }
        }
        super.setRemoved();
    }

    public void invalidate() {
        if (!formed) return;
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide()) {
            dropAndCleanCells(lv, getBlockPos());
        }
        formed    = false;
        structure = null;
        items     = NonNullList.create();
        setChanged();
    }

    private void dropAndCleanCells(Level lv, BlockPos dropAt) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                ReactorRodItem.removeReactorTags(stack);
                Containers.dropItemStack(lv, dropAt.getX(), dropAt.getY(), dropAt.getZ(), stack);
            }
        }
        if (structure != null) {
            for (BlockPos cellPos : structure.cells) {
                BlockState cs = lv.getBlockState(cellPos);
                if (cs.getBlock() instanceof ReactorCell) {
                    lv.setBlock(cellPos, cs
                            .setValue(ReactorCell.CELL_STATE, ReactorCellState.COOL)
                            .setValue(ReactorCell.CELL_TYPE,  ReactorCellType.EMPTY), 3);
                }
            }
        }
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReactorBlockEntity be) {
        if (be.pendingRevalidation) {
            be.pendingRevalidation = false;
            var detected = ReactorStructure.detect(level, pos);
            if (detected.isPresent()
                    && detected.get().width == be.savedWidth
                    && detected.get().depth == be.savedDepth
                    && detected.get().cells.size() == be.savedCellCount) {
                be.structure = detected.get();
            } else {
                be.invalidate();
            }
            return;
        }

        if (be.formed) {
            if (++be.revalidateTick >= 20) {
                be.revalidateTick = 0;
                if (!be.structure.isStillValid(level)) be.invalidate();
            }
        }
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Formed", formed ? 1 : 0);
        if (formed && structure != null) {
            output.putInt("StructWidth",  structure.width);
            output.putInt("StructDepth",  structure.depth);
            output.putInt("CellCount",    items.size());
            ContainerHelper.saveAllItems(output, items);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        formed = input.getIntOr("Formed", 0) != 0;
        if (formed) {
            savedWidth     = input.getIntOr("StructWidth", 3);
            savedDepth     = input.getIntOr("StructDepth", 3);
            savedCellCount = input.getIntOr("CellCount", 0);
            pendingRevalidation = true;
            if (savedCellCount > 0) {
                items = NonNullList.withSize(savedCellCount, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(input, items);
            } else {
                items = NonNullList.create();
            }
        } else {
            items = NonNullList.create();
        }
    }
}
