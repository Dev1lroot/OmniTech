/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorCellBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorStructure;
import com.dev1lroot.mcmods.omnitech.items.ReactorRodItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ReactorMenu extends AbstractContainerMenu {

    public static final int  SLOT_SIZE       = 18;
    public static final int  PAD             = 7;
    public static final int  TANK_BAR_W      = 12;
    public static final int  HEAT_BAR_W      = 12;
    public static final int  SCRAM_BTN_H     = 20;
    public static final int  MINI_CELL_PX    = 2;
    public static final int  MINI_GRAPH_SIZE = MINI_CELL_PX * ReactorStructure.MAX_SIZE; // 22

    private static final int TANK_MARGIN = 6;
    private static final int BAR_GAP     = 4;
    private static final int INV_W       = 9 * SLOT_SIZE;
    private static final int INV_H       = 3 * SLOT_SIZE + 4 + SLOT_SIZE;

    public final int structWidth;
    public final int structDepth;

    public final List<int[]> cellLocalPositions;
    public final int cellDisplayOffsetX;
    public final int cellDisplayOffsetY;

    public final int imageWidth;
    public final int imageHeight;
    public final int gridOffsetX;
    public final int gridOffsetY;
    public final int invOffsetX;
    public final int invOffsetY;

    public final int tankBarX;
    public final int tankBarY;
    public final int tankBarH;

    public final int heatBarX;
    public final int heatBarY;
    public final int heatBarH;

    public final int scramBtnX;
    public final int scramBtnY;
    public final int scramBtnW;

    /** Y position of the FLUSH (drain coolant) button. */
    public final int ventBtnY;

    /** Y position of the START (all rods to 0%) button. */
    public final int startBtnY;

    /** X/Y of the temperature mini-graph (MINI_GRAPH_SIZE × MINI_GRAPH_SIZE). */
    public final int tempGraphX;
    public final int tempGraphY;

    /** X/Y of the neutron flow mini-graph. */
    public final int flowGraphX;
    public final int flowGraphY;

    @Nullable public final ReactorBlockEntity reactorBE;
    private final ContainerData fluidData;

    private final int     cellSlotCount;
    private final BlockPos masterPos;

    // ── Server-side constructor ───────────────────────────────────────────────

    public ReactorMenu(int containerId, Inventory playerInventory, ReactorBlockEntity be) {
        super(OmniTechMenuTypes.REACTOR.get(), containerId);
        this.masterPos = be.getBlockPos();
        this.reactorBE = be;

        ReactorStructure s = be.getStructure();
        this.structWidth = s.width;
        this.structDepth = s.depth;

        cellLocalPositions = new ArrayList<>();
        for (BlockPos cell : s.cells) {
            cellLocalPositions.add(new int[]{
                cell.getX() - s.origin.getX(),
                cell.getZ() - s.origin.getZ()
            });
        }
        this.cellSlotCount = cellLocalPositions.size();
        this.cellDisplayOffsetX = (ReactorStructure.MAX_SIZE - structWidth)  / 2;
        this.cellDisplayOffsetY = (ReactorStructure.MAX_SIZE - structDepth) / 2;

        int[] L = computeLayout();
        imageWidth  = L[0]; imageHeight  = L[1];
        gridOffsetX = L[2]; gridOffsetY  = L[3];
        invOffsetX  = L[4]; invOffsetY   = L[5];
        tankBarX    = L[6]; tankBarY     = L[7]; tankBarH = L[8];
        scramBtnX   = L[9]; scramBtnY   = L[10]; scramBtnW = L[11];
        heatBarX    = tankBarX + TANK_BAR_W + BAR_GAP;
        heatBarY    = tankBarY;
        heatBarH    = tankBarH;
        ventBtnY    = scramBtnY - SCRAM_BTN_H - BAR_GAP;
        startBtnY   = ventBtnY  - SCRAM_BTN_H - BAR_GAP;
        tempGraphX  = heatBarX  + HEAT_BAR_W  + BAR_GAP;
        tempGraphY  = tankBarY;
        flowGraphX  = tempGraphX + MINI_GRAPH_SIZE + BAR_GAP;
        flowGraphY  = tankBarY;

        // Sync: indices 0-4 = coolant/temp/pressure, 5..5+cells-1 = neutron flow %
        final ReactorBlockEntity theBe = be;
        final int cellCount = cellSlotCount;
        this.fluidData = new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> theBe.getCoolantAmount() / 1000;
                    case 1 -> theBe.getTankCapacity()  / 1000;
                    case 2 -> theBe.getCoreTemperature();
                    case 3 -> theBe.getCoolantTemperature();
                    case 4 -> theBe.getPressure();
                    default -> {
                        int ci = i - 5;
                        int[] flows = theBe.getCellNeutronFlowPct();
                        yield (ci >= 0 && flows != null && ci < flows.length) ? flows[ci] : 0;
                    }
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return 5 + cellCount; }
        };
        addDataSlots(fluidData);

        ServerLevel serverLevel = (ServerLevel) be.getLevel();
        for (int i = 0; i < cellSlotCount; i++) {
            int[] lp = cellLocalPositions.get(i);
            BlockPos cellPos = s.cells.get(i);
            net.minecraft.world.Container cellContainer =
                    serverLevel.getBlockEntity(cellPos) instanceof ReactorCellBlockEntity cbe
                    ? cbe : new net.minecraft.world.SimpleContainer(1);
            addSlot(new ReactorCellSlot(cellContainer, 0,
                    gridOffsetX + (lp[0] + cellDisplayOffsetX) * SLOT_SIZE + 1,
                    gridOffsetY + (lp[1] + cellDisplayOffsetY) * SLOT_SIZE + 1,
                    serverLevel, cellPos));
        }
        addPlayerSlots(playerInventory, invOffsetX, invOffsetY);
    }

    // ── Client-side constructor (FriendlyByteBuf) ─────────────────────────────

    public ReactorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(OmniTechMenuTypes.REACTOR.get(), containerId);
        this.masterPos   = buf.readBlockPos();
        this.structWidth = buf.readVarInt();
        this.structDepth = buf.readVarInt();

        Level lv = playerInventory.player.level();
        ReactorBlockEntity tmpBe = null;
        if (lv.getBlockEntity(masterPos) instanceof ReactorBlockEntity rbe) tmpBe = rbe;
        this.reactorBE = tmpBe;

        int cellCount = buf.readVarInt();
        cellLocalPositions = new ArrayList<>(cellCount);
        for (int i = 0; i < cellCount; i++) {
            cellLocalPositions.add(new int[]{buf.readVarInt(), buf.readVarInt()});
        }
        this.cellSlotCount = cellCount;
        this.cellDisplayOffsetX = (ReactorStructure.MAX_SIZE - structWidth)  / 2;
        this.cellDisplayOffsetY = (ReactorStructure.MAX_SIZE - structDepth) / 2;

        int[] L = computeLayout();
        imageWidth  = L[0]; imageHeight  = L[1];
        gridOffsetX = L[2]; gridOffsetY  = L[3];
        invOffsetX  = L[4]; invOffsetY   = L[5];
        tankBarX    = L[6]; tankBarY     = L[7]; tankBarH = L[8];
        scramBtnX   = L[9]; scramBtnY   = L[10]; scramBtnW = L[11];
        heatBarX    = tankBarX + TANK_BAR_W + BAR_GAP;
        heatBarY    = tankBarY;
        heatBarH    = tankBarH;
        ventBtnY    = scramBtnY - SCRAM_BTN_H - BAR_GAP;
        startBtnY   = ventBtnY  - SCRAM_BTN_H - BAR_GAP;
        tempGraphX  = heatBarX  + HEAT_BAR_W  + BAR_GAP;
        tempGraphY  = tankBarY;
        flowGraphX  = tempGraphX + MINI_GRAPH_SIZE + BAR_GAP;
        flowGraphY  = tankBarY;

        this.fluidData = new SimpleContainerData(5 + cellCount);
        addDataSlots(fluidData);

        Container container = new SimpleContainer(cellCount);
        for (int i = 0; i < cellCount; i++) {
            int[] lp = cellLocalPositions.get(i);
            addSlot(new Slot(container, i,
                    gridOffsetX + (lp[0] + cellDisplayOffsetX) * SLOT_SIZE + 1,
                    gridOffsetY + (lp[1] + cellDisplayOffsetY) * SLOT_SIZE + 1));
        }
        addPlayerSlots(playerInventory, invOffsetX, invOffsetY);
    }

    // ── Data accessors (synced via ContainerData) ─────────────────────────────

    public int getWaterBuckets()         { return fluidData.get(0); }
    public int getWaterCapacityBuckets() { return fluidData.get(1); }
    public int getCoreTemperature()      { return fluidData.get(2); }
    public int getCoolantTemperature()   { return fluidData.get(3); }
    public int getPressure()             { return fluidData.get(4); }

    /** Neutron flow percentage (0..100) for cell at index {@code i}. */
    public int getNeutronFlowPct(int i) {
        return (i >= 0 && i < cellSlotCount) ? fluidData.get(5 + i) : 0;
    }

    public FluidStack getWaterFluid() {
        FluidStack fs = (reactorBE != null) ? reactorBE.getCoolantTank() : FluidStack.EMPTY;
        if (fs.isEmpty()) {
            var fo = OmniTechFluids.get("distilled_water");
            int water = getWaterBuckets();
            if (fo != null && water > 0) fs = new FluidStack(fo.source.get(), water * 1000);
        }
        if (!fs.isEmpty()) {
            fs = fs.copy();
            int temp = getCoolantTemperature();
            if (temp != 20) fs.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), temp);
        }
        return fs;
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    // returns [imgW, imgH, gox, goy, iox, ioy, tankX, tankY, tankH, scramX, scramY, scramW]
    // Right column: tank bar | heat bar | temp graph | flow graph
    // Three buttons at bottom: START, FLUSH, SCRAM
    private static int[] computeLayout() {
        int gridW     = ReactorStructure.MAX_SIZE * SLOT_SIZE;  // 198
        int gridH     = ReactorStructure.MAX_SIZE * SLOT_SIZE;  // 198
        int mainW     = Math.max(gridW, INV_W);                 // 198
        int rightColW = TANK_BAR_W + BAR_GAP + HEAT_BAR_W + BAR_GAP + MINI_GRAPH_SIZE + BAR_GAP + MINI_GRAPH_SIZE;
        int imgW      = 2 * PAD + mainW + TANK_MARGIN + rightColW;
        int imgH      = PAD + gridH + 6 + INV_H + PAD;
        int gox       = PAD + (mainW - gridW) / 2;
        int iox       = PAD + (mainW - INV_W) / 2;
        int ioy       = PAD + gridH + 6;
        int tankX     = PAD + mainW + TANK_MARGIN;
        // Three buttons stacked at the bottom (SCRAM at bottom, FLUSH above, START above that)
        int scramY    = imgH - PAD - SCRAM_BTN_H;
        int ventY     = scramY - SCRAM_BTN_H - BAR_GAP;
        int startY    = ventY  - SCRAM_BTN_H - BAR_GAP;
        int tankH     = startY - PAD - BAR_GAP;
        int scramW    = TANK_BAR_W + BAR_GAP + HEAT_BAR_W;
        return new int[]{imgW, imgH, gox, PAD, iox, ioy, tankX, PAD, tankH, tankX, scramY, scramW};
    }

    // ── Slot helpers ──────────────────────────────────────────────────────────

    private void addPlayerSlots(Inventory inv, int ox, int oy) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, ox + col * SLOT_SIZE, oy + row * SLOT_SIZE));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, ox + col * SLOT_SIZE, oy + 3 * SLOT_SIZE + 4));
    }

    // ── Menu logic ────────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy  = stack.copy();

        int invStart = cellSlotCount;
        int invEnd   = invStart + 27;
        int hotEnd   = invEnd + 9;

        if (index < cellSlotCount) {
            if (stack.getItem() instanceof ReactorRodItem) {
                ReactorRodItem.removeReactorTags(stack);
            }
            if (!moveItemStackTo(stack, invStart, hotEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, copy);
        } else {
            if (!moveItemStackTo(stack, 0, cellSlotCount, false)) {
                if (index < invEnd) {
                    if (!moveItemStackTo(stack, invEnd, hotEnd, false)) return ItemStack.EMPTY;
                } else {
                    if (!moveItemStackTo(stack, invStart, invEnd, false)) return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        var be = player.level().getBlockEntity(masterPos);
        if (!(be instanceof ReactorBlockEntity rbe) || !rbe.isFormed()) return false;
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double minX = masterPos.getX(), minY = masterPos.getY(), minZ = masterPos.getZ();
        double maxX = minX + structWidth, maxY = minY + ReactorStructure.HEIGHT, maxZ = minZ + structDepth;
        double dx = Math.max(0.0, Math.max(minX - px, px - maxX));
        double dy = Math.max(0.0, Math.max(minY - py, py - maxY));
        double dz = Math.max(0.0, Math.max(minZ - pz, pz - maxZ));
        return dx * dx + dy * dy + dz * dz < 64.0;
    }
}
