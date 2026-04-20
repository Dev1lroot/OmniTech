package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.OmniTechMenuTypes;
import com.dev1lroot.mcmods.omnitech.entities.CokeOvenEntity;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Comparator;
import java.util.List;

public class CokeOvenMenu extends AbstractContainerMenu {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("coke_oven");

    private final Container container;
    private final ContainerData data;
    private final CokeOvenEntity entity;
    private final int machineSlotCount;

    // Client constructor
    public CokeOvenMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
                (CokeOvenEntity) playerInventory.player.level().getEntity(buf.readInt()),
                new SimpleContainerData(4));
    }

    // Server constructor
    public CokeOvenMenu(int containerId, Inventory playerInventory, CokeOvenEntity entity) {
        this(containerId, playerInventory, entity, new SimpleContainerData(4) {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> entity.isLit() ? 1 : 0;
                    case 1 -> entity.getProcessTimer();
                    case 2 -> entity.getProcessTotalTime();
                    case 3 -> entity.getCreosoteAmount();
                    default -> 0;
                };
            }
        });
    }

    private CokeOvenMenu(int containerId, Inventory playerInventory,
                         CokeOvenEntity entity, ContainerData data) {
        super(OmniTechMenuTypes.COKE_OVEN.get(), containerId);
        this.entity = entity;
        this.container = entity != null ? entity.getInventory()
                : new net.minecraft.world.SimpleContainer(CokeOvenEntity.INVENTORY_SIZE);
        this.data = data;
        addDataSlots(data);

        List<GuiElementDef> machineSlots = LAYOUT.getElementsByType("slot").stream()
                .filter(e -> e.slot_index >= 0)
                .sorted(Comparator.comparingInt(e -> e.slot_index))
                .toList();

        for (GuiElementDef el : machineSlots) {
            addSlot(createMachineSlot(el));
        }
        this.machineSlotCount = machineSlots.size();

        LAYOUT.addPlayerInventory(playerInventory, this::addSlot);
    }

    private Slot createMachineSlot(GuiElementDef el) {
        return switch (el.slot_index) {
            case CokeOvenEntity.SLOT_INPUT  -> new CoalOnlySlot(container,   el.slot_index, el.x, el.y);
            case CokeOvenEntity.SLOT_FUEL   -> new Slot(container,           el.slot_index, el.x, el.y);
            case CokeOvenEntity.SLOT_OUTPUT -> new OutputOnlySlot(container, el.slot_index, el.x, el.y);
            default -> new Slot(container, el.slot_index, el.x, el.y);
        };
    }

    // ── Data accessors ─────────────────────────────────────────────────────────

    public boolean isLit() { return data.get(0) > 0; }

    public float getProgressScaled() {
        int total = data.get(2);
        return total > 0 ? data.get(1) * 100f / total : 0f;
    }

    public FluidStack getCreosoteFluid() {
        int amount = data.get(3);
        if (amount <= 0) return FluidStack.EMPTY;
        var entry = OmniTechFluids.get("creosote");
        return entry != null ? new FluidStack(entry.source.get(), amount) : FluidStack.EMPTY;
    }

    public int getCreosoteAmount()   { return data.get(3); }
    public int getCreosoteCapacity() { return CokeOvenEntity.CREOSOTE_CAPACITY; }

    // ── Shift-click ────────────────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int playerStart = machineSlotCount;
        int playerEnd   = playerStart + 27;
        int hotbarEnd   = playerEnd + 9;

        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack slotStack = slot.getItem();
        ItemStack result = slotStack.copy();

        if (index < machineSlotCount) {
            // Machine → player inventory
            if (!this.moveItemStackTo(slotStack, playerStart, hotbarEnd, true)) return ItemStack.EMPTY;
        } else {
            // Player → machine: coal goes to input first, then fuel; other items go to fuel slot
            if (isCoal(slotStack)) {
                if (!this.moveItemStackTo(slotStack, CokeOvenEntity.SLOT_INPUT,
                        CokeOvenEntity.SLOT_INPUT + 1, false)
                        && !this.moveItemStackTo(slotStack, CokeOvenEntity.SLOT_FUEL,
                        CokeOvenEntity.SLOT_FUEL + 1, false)) {
                    if (index < playerEnd) {
                        if (!this.moveItemStackTo(slotStack, playerEnd, hotbarEnd, false))
                            return ItemStack.EMPTY;
                    } else {
                        if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, false))
                            return ItemStack.EMPTY;
                    }
                }
            } else {
                // Non-coal items (potential fuels like wood) go to the fuel slot
                if (!this.moveItemStackTo(slotStack, CokeOvenEntity.SLOT_FUEL,
                        CokeOvenEntity.SLOT_FUEL + 1, false)) {
                    if (index < playerEnd) {
                        if (!this.moveItemStackTo(slotStack, playerEnd, hotbarEnd, false))
                            return ItemStack.EMPTY;
                    } else {
                        if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, false))
                            return ItemStack.EMPTY;
                    }
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
        if (entity == null || entity.isRemoved()) return false;
        return player.distanceToSqr(entity) < 64.0;
    }

    private static boolean isCoal(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
    }

    // ── Custom slot types ──────────────────────────────────────────────────────

    private static class CoalOnlySlot extends Slot {
        CoalOnlySlot(Container c, int index, int x, int y) { super(c, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) {
            return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
        }
    }

    private static class OutputOnlySlot extends Slot {
        OutputOnlySlot(Container c, int index, int x, int y) { super(c, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
    }
}
