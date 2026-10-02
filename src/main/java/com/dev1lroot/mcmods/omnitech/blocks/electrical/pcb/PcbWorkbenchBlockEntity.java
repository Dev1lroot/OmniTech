/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import com.dev1lroot.mcmods.omnitech.gui.PcbWorkbenchMenu;
import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import com.dev1lroot.mcmods.omnitech.pcb.mc.PcbCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * PCB Workbench: a drawing board. Holds the design being edited and prints it onto
 * paper as a PCB Blueprint (the photomask the Fabricator exposes boards with).
 */
public class PcbWorkbenchBlockEntity extends BaseContainerBlockEntity implements PcbStationBlock.Station {

    public static final int SLOT_PAPER  = 0;
    /** A blueprint to load back into the editor; never consumed. */
    public static final int SLOT_SOURCE = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT  = 3;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private PcbDesign design = PcbDesign.DEFAULT;

    public PcbWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.PCB_WORKBENCH.get(), pos, state);
    }

    @Override protected Component getDefaultName() { return Component.translatable("container.omnitech.pcb_workbench"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new PcbWorkbenchMenu(id, inv, this, design);
    }

    @Override
    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        PcbCodecs.DESIGN_STREAM.encode(buf, design);
    }

    public PcbDesign getDesign() { return design; }

    public void setDesign(PcbDesign d) {
        design = d;
        setChanged();
    }

    /** Prints the current design on one sheet of paper. */
    public void print(ServerPlayer player) {
        if (design.padCount() == 0) {
            player.sendOverlayMessage(Component.translatable("pcb.omnitech.workbench.no_pads"));
            return;
        }
        ItemStack paper = items.get(SLOT_PAPER);
        if (!paper.is(Items.PAPER)) {
            player.sendOverlayMessage(Component.translatable("pcb.omnitech.workbench.no_paper"));
            return;
        }
        ItemStack print = new ItemStack(OmniTechItems.PCB_BLUEPRINT.get());
        print.set(OmniTechDataComponents.PCB_DESIGN.get(), design);
        // Copying a ready-made blueprint keeps its reference placement if the geometry is untouched
        ItemStack source = items.get(SLOT_SOURCE);
        PcbDesign sourceDesign = source.get(OmniTechDataComponents.PCB_DESIGN.get());
        var reference = source.get(OmniTechDataComponents.PCB_PARTS.get());
        if (reference != null && sourceDesign != null && sourceDesign.geometryHash() == design.geometryHash()) {
            print.set(OmniTechDataComponents.PCB_PARTS.get(), reference);
        }
        ItemStack out = items.get(SLOT_OUTPUT);
        if (!out.isEmpty() && (!ItemStack.isSameItemSameComponents(out, print) || out.getCount() >= out.getMaxStackSize())) {
            player.sendOverlayMessage(Component.translatable("pcb.omnitech.output_full"));
            return;
        }
        paper.shrink(1);
        if (out.isEmpty()) items.set(SLOT_OUTPUT, print);
        else out.grow(1);
        setChanged();
        if (level != null) level.playSound(null, worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return switch (index) {
            case SLOT_PAPER  -> stack.is(Items.PAPER);
            case SLOT_SOURCE -> stack.has(OmniTechDataComponents.PCB_DESIGN.get());
            default -> false;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        design = input.read("Design", PcbCodecs.DESIGN).orElse(PcbDesign.DEFAULT);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("Design", PcbCodecs.DESIGN, design);
    }
}
