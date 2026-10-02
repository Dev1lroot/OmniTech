/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import com.dev1lroot.mcmods.omnitech.OmniTechSounds;
import com.dev1lroot.mcmods.omnitech.gui.PcbBurnerMenu;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import com.dev1lroot.mcmods.omnitech.pcb.PlacedPart;
import com.dev1lroot.mcmods.omnitech.util.PowerMeter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * PCB Burner: a UV exposure unit. The bulb shines through the photomask onto an empty
 * circuit board and burns the drawing into its photoresist, giving an exposed board for
 * the PCB Washer. The photomask is never used up; the bulb wears one point per board.
 */
public class PcbBurnerBlockEntity extends BaseContainerBlockEntity
        implements PcbStationBlock.Station, IElectricReceiver, WorldlyContainer {

    public static final int SLOT_LAMP   = 0;
    public static final int SLOT_MASK   = 1;
    public static final int SLOT_BOARD  = 2;
    public static final int SLOT_OUTPUT = 3;
    public static final int SLOT_COUNT  = 4;

    public static final int EXPOSE_TICKS   = 100;
    public static final float MAX_EU       = 1000f;
    public static final float EU_PER_BOARD = 400f;
    /** How long the station glows after a board is exposed (0.5 s). */
    public static final int FLASH_TICKS    = 10;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private float energy;
    private int progress;
    private int flashTicks;
    private final PowerMeter inputMeter = new PowerMeter();
    private final PowerMeter loadMeter = new PowerMeter();

    private final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> (int) (energy * 10f);
                case 1 -> (int) (MAX_EU * 10f);
                case 2 -> progress;
                case 3 -> EXPOSE_TICKS;
                case 4 -> (int) (EU_PER_BOARD * 10f);
                case 5 -> inputMeter.syncWatts();
                case 6 -> inputMeter.syncDeciVolts();
                case 7 -> loadMeter.syncWatts();
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return 8; }
    };

    public PcbBurnerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.PCB_BURNER.get(), pos, state);
    }

    @Override protected Component getDefaultName() { return Component.translatable("container.omnitech.pcb_burner"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new PcbBurnerMenu(id, inv, this, data);
    }

    @Override
    public void writeOpenData(RegistryFriendlyByteBuf buf) { buf.writeBlockPos(worldPosition); }

    private @Nullable PcbDesign mask() {
        ItemStack mask = items.get(SLOT_MASK);
        return mask.is(OmniTechItems.PHOTOMASK.get()) ? mask.get(OmniTechDataComponents.PCB_DESIGN.get()) : null;
    }

    // ── Electricity ───────────────────────────────────────────────────────────

    @Override
    public float addElectricity(float amount, float volts) {
        float accepted = Math.max(0f, Math.min(amount, MAX_EU - energy));
        inputMeter.add(accepted, volts);
        if (accepted <= 0f) return 0f;
        energy += accepted;
        setChanged();
        return accepted;
    }

    // ── Process ───────────────────────────────────────────────────────────────

    @Override
    public void serverTick() {
        inputMeter.tick();
        loadMeter.tick();
        if (flashTicks > 0 && --flashTicks == 0 && level != null) PcbBurnerBlock.setLit(level, worldPosition, false);

        PcbDesign d = mask();
        ItemStack result = d == null ? ItemStack.EMPTY
                : exposed(d, items.get(SLOT_MASK).get(OmniTechDataComponents.PCB_PARTS.get()));
        boolean ready = d != null && d.padCount() > 0
                && items.get(SLOT_LAMP).is(OmniTechItems.UV_BULB.get())
                && items.get(SLOT_BOARD).is(OmniTechItems.EMPTY_CIRCUIT_BOARD.get())
                && canOutput(result);
        if (!ready) {
            // a half-burnt pattern is lost when anything is pulled out
            if (progress != 0) { progress = 0; setChanged(); }
            return;
        }
        float perTick = EU_PER_BOARD / EXPOSE_TICKS;
        if (energy < perTick) return;
        energy -= perTick;
        loadMeter.add(perTick);
        if (++progress >= EXPOSE_TICKS) {
            progress = 0;
            wearLamp();
            flash();
            items.get(SLOT_BOARD).shrink(1);
            ItemStack out = items.get(SLOT_OUTPUT);
            if (out.isEmpty()) items.set(SLOT_OUTPUT, result);
            else out.grow(1);
        }
        setChanged();
    }

    private void flash() {
        if (level == null) return;
        flashTicks = FLASH_TICKS;
        PcbBurnerBlock.setLit(level, worldPosition, true);
        level.playSound(null, worldPosition, OmniTechSounds.PCB_BURNER_FLASH.get(), SoundSource.BLOCKS, 1f, 1f);
    }

    private void wearLamp() {
        ItemStack lamp = items.get(SLOT_LAMP);
        if (!lamp.isDamageableItem()) return;
        lamp.setDamageValue(lamp.getDamageValue() + 1);
        if (lamp.getDamageValue() >= lamp.getMaxDamage()) items.set(SLOT_LAMP, ItemStack.EMPTY);
    }

    /** An exposed board; a ready-made blueprint's reference placement rides along to the soldering station. */
    private static ItemStack exposed(PcbDesign d, @Nullable List<PlacedPart> reference) {
        ItemStack s = new ItemStack(OmniTechItems.EXPOSED_CIRCUIT_BOARD.get());
        s.set(OmniTechDataComponents.PCB_DESIGN.get(), d);
        if (reference != null) s.set(OmniTechDataComponents.PCB_PARTS.get(), reference);
        return s;
    }

    private boolean canOutput(ItemStack result) {
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, result) && out.getCount() < out.getMaxStackSize();
    }

    // ── Slots ─────────────────────────────────────────────────────────────────

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return switch (index) {
            case SLOT_LAMP  -> stack.is(OmniTechItems.UV_BULB.get());
            case SLOT_MASK  -> stack.is(OmniTechItems.PHOTOMASK.get());
            case SLOT_BOARD -> stack.is(OmniTechItems.EMPTY_CIRCUIT_BOARD.get());
            default -> false;
        };
    }

    private static final int[] SIDE_SLOTS = {SLOT_LAMP, SLOT_BOARD, SLOT_OUTPUT};

    @Override public int[] getSlotsForFace(Direction side) { return SIDE_SLOTS; }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction side) {
        return (index == SLOT_LAMP || index == SLOT_BOARD) && canPlaceItem(index, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction side) {
        return index == SLOT_OUTPUT;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energy = input.getFloatOr("Energy", 0f);
        progress = input.getIntOr("Progress", 0);
        flashTicks = input.getIntOr("Flash", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putFloat("Energy", energy);
        output.putInt("Progress", progress);
        output.putInt("Flash", flashTicks);
    }
}
