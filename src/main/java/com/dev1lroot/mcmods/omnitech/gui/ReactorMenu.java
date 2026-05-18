package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorBlockEntity;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ReactorMenu extends AbstractContainerMenu {

    public static final int SLOT_SIZE = 18;
    public static final int PAD       = 7;
    private static final int INV_W    = 9 * SLOT_SIZE;            // 162
    private static final int INV_H    = 3 * SLOT_SIZE + 4 + SLOT_SIZE; // 76

    public final int structWidth;
    public final int structDepth;

    /** [localX, localZ] pairs, same order as cell slots (sorted Z then X). */
    public final List<int[]> cellLocalPositions;

    public final int imageWidth;
    public final int imageHeight;
    public final int gridOffsetX;
    public final int gridOffsetY;
    public final int invOffsetX;
    public final int invOffsetY;

    private final int     cellSlotCount;
    private final BlockPos masterPos;

    // ── Server-side constructor ───────────────────────────────────────────────

    public ReactorMenu(int containerId, Inventory playerInventory, ReactorBlockEntity be) {
        super(OmniTechMenuTypes.REACTOR.get(), containerId);
        this.masterPos = be.getBlockPos();

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

        int[] layout = layout(structWidth, structDepth);
        imageWidth  = layout[0]; imageHeight  = layout[1];
        gridOffsetX = layout[2]; gridOffsetY  = layout[3];
        invOffsetX  = layout[4]; invOffsetY   = layout[5];

        ServerLevel serverLevel = (ServerLevel) be.getLevel();
        for (int i = 0; i < cellSlotCount; i++) {
            int[] lp = cellLocalPositions.get(i);
            addSlot(new ReactorCellSlot(be, i,
                    gridOffsetX + lp[0] * SLOT_SIZE + 1,
                    gridOffsetY + lp[1] * SLOT_SIZE + 1,
                    serverLevel, s.cells.get(i)));
        }
        addPlayerSlots(playerInventory, invOffsetX, invOffsetY);
    }

    // ── Client-side constructor (FriendlyByteBuf) ─────────────────────────────

    public ReactorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(OmniTechMenuTypes.REACTOR.get(), containerId);
        this.masterPos   = buf.readBlockPos();
        this.structWidth = buf.readVarInt();
        this.structDepth = buf.readVarInt();

        int cellCount = buf.readVarInt();
        cellLocalPositions = new ArrayList<>(cellCount);
        for (int i = 0; i < cellCount; i++) {
            cellLocalPositions.add(new int[]{buf.readVarInt(), buf.readVarInt()});
        }
        this.cellSlotCount = cellCount;

        int[] layout = layout(structWidth, structDepth);
        imageWidth  = layout[0]; imageHeight  = layout[1];
        gridOffsetX = layout[2]; gridOffsetY  = layout[3];
        invOffsetX  = layout[4]; invOffsetY   = layout[5];

        Container container = new SimpleContainer(cellCount);
        for (int i = 0; i < cellCount; i++) {
            int[] lp = cellLocalPositions.get(i);
            addSlot(new Slot(container, i,
                    gridOffsetX + lp[0] * SLOT_SIZE + 1,
                    gridOffsetY + lp[1] * SLOT_SIZE + 1));
        }
        addPlayerSlots(playerInventory, invOffsetX, invOffsetY);
    }

    // ── Layout maths ─────────────────────────────────────────────────────────

    private static int[] layout(int w, int d) {
        int gridW  = w * SLOT_SIZE;
        int gridH  = d * SLOT_SIZE;
        int innerW = Math.max(gridW, INV_W);
        int imgW   = innerW + 2 * PAD;
        int imgH   = PAD + gridH + 6 + INV_H + PAD;
        int gox    = PAD + (innerW - gridW) / 2;
        int iox    = PAD + (innerW - INV_W) / 2;
        int ioy    = PAD + gridH + 6;
        return new int[]{imgW, imgH, gox, PAD, iox, ioy};
    }

    // ── Slot helpers ─────────────────────────────────────────────────────────

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
            // Strip reactor tags so they don't follow the rod into the player's inventory.
            // mayPickup already blocked hot rods (≥ 100 °C), so temp here is always < 100.
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
        // Check distance from the nearest point on the reactor AABB, not just the NW corner.
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double minX = masterPos.getX(), minY = masterPos.getY(), minZ = masterPos.getZ();
        double maxX = minX + structWidth, maxY = minY + ReactorStructure.HEIGHT, maxZ = minZ + structDepth;
        double dx = Math.max(0.0, Math.max(minX - px, px - maxX));
        double dy = Math.max(0.0, Math.max(minY - py, py - maxY));
        double dz = Math.max(0.0, Math.max(minZ - pz, pz - maxZ));
        return dx * dx + dy * dy + dz * dz < 64.0;
    }
}
