/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import com.dev1lroot.mcmods.omnitech.gui.SolderingStationMenu;
import com.dev1lroot.mcmods.omnitech.items.ResistorItem;
import com.dev1lroot.mcmods.omnitech.pcb.PartKey;
import com.dev1lroot.mcmods.omnitech.pcb.PartSpec;
import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import com.dev1lroot.mcmods.omnitech.pcb.ResistorCode;
import com.dev1lroot.mcmods.omnitech.pcb.PlacedPart;
import com.dev1lroot.mcmods.omnitech.pcb.mc.PcbCodecs;
import com.dev1lroot.mcmods.omnitech.pcb.mc.TestBenchLoader;
import com.dev1lroot.mcmods.omnitech.pcb.sim.TestBenchRunner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Soldering Station: parts are placed on an etched board's pads, then soldered.
 * The finished board is run through every circuit test bench; the first one it
 * passes decides what it becomes (e.g. a Power Stabilizer Circuit), otherwise it
 * stays a generic assembled board.
 *
 * <p>Placements are remembered per board geometry, so a batch of identical boards
 * reuses one plan.
 */
public class SolderingStationBlockEntity extends BaseContainerBlockEntity implements PcbStationBlock.Station {

    public static final int SLOT_BOARD      = 0;
    public static final int SLOT_SOLDER     = 1;
    public static final int PARTS_START     = 2;
    public static final int PARTS_SLOTS     = 9;
    public static final int SLOT_OUTPUT     = PARTS_START + PARTS_SLOTS;
    public static final int SLOT_COUNT      = SLOT_OUTPUT + 1;

    /** One tin wire solders this many joints. */
    public static final int JOINTS_PER_SOLDER = 6;
    public static final int MAX_PLANS = 32;

    private static final Identifier TIN_WIRE = Identifier.fromNamespaceAndPath("omnitech", "tin_wire");

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final LinkedHashMap<Integer, List<PlacedPart>> plans = new LinkedHashMap<>();

    public SolderingStationBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.SOLDERING_STATION.get(), pos, state);
    }

    @Override protected Component getDefaultName() { return Component.translatable("container.omnitech.soldering_station"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new SolderingStationMenu(id, inv, this, plans);
    }

    @Override
    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        SolderingStationMenu.writePlans(buf, plans);
    }

    public static Item tinWire() { return BuiltInRegistries.ITEM.getValue(TIN_WIRE); }

    public @Nullable PcbDesign boardDesign() {
        return items.get(SLOT_BOARD).get(OmniTechDataComponents.PCB_DESIGN.get());
    }

    /** Stores the plan for the board in the slot, keeping only parts that physically fit. */
    public void setPlan(List<PlacedPart> parts) {
        PcbDesign d = boardDesign();
        if (d == null) return;
        plans.remove(d.geometryHash());
        plans.put(d.geometryHash(), validPlan(d, parts));
        while (plans.size() > MAX_PLANS) plans.remove(plans.keySet().iterator().next());
        setChanged();
    }

    public static List<PlacedPart> validPlan(PcbDesign d, List<PlacedPart> parts) {
        List<PlacedPart> ok = new ArrayList<>();
        for (PlacedPart p : parts) {
            if (ok.size() >= PcbCodecs.MAX_PARTS) break;
            if (PlacedPart.canPlace(d, ok, p)) ok.add(p);
        }
        return ok;
    }

    /** The stored plan for this board, else the reference placement a ready-made board carries. */
    public List<PlacedPart> planFor(PcbDesign d, ItemStack board) {
        List<PlacedPart> stored = plans.get(d.geometryHash());
        if (stored != null) return stored;
        return referencePlan(d, board);
    }

    public static List<PlacedPart> referencePlan(PcbDesign d, ItemStack board) {
        List<PlacedPart> ref = board.get(OmniTechDataComponents.PCB_PARTS.get());
        return ref == null ? List.of() : validPlan(d, ref);
    }

    public static int solderNeeded(List<PlacedPart> parts) {
        int joints = 0;
        for (PlacedPart p : parts) joints += p.pinCells().size();
        return (joints + JOINTS_PER_SOLDER - 1) / JOINTS_PER_SOLDER;
    }

    /** Parts needed by a plan, per kind (item + resistor colour code). */
    public static Map<PartKey, Integer> billOfMaterials(List<PlacedPart> parts) {
        Map<PartKey, Integer> bom = new LinkedHashMap<>();
        for (PlacedPart p : parts) bom.merge(PartKey.of(p), 1, Integer::sum);
        return bom;
    }

    /** True when the stack is a part of this kind (same item; a resistor also needs the same bands). */
    public static boolean matches(PartKey key, ItemStack stack) {
        if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(key.itemId())) return false;
        return ResistorItem.getBands(stack).equals(key.bands());
    }

    /** A stack of {@code count} parts of this kind (a resistor carries its bands). */
    public static ItemStack stackOf(PartKey key, int count) {
        ItemStack s = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(key.itemId())), count);
        if (!key.bands().isEmpty()) ResistorItem.withBands(s, key.bands());
        return s;
    }

    /** The kind of part a stack is, or null if it can't be soldered (incl. an unpainted resistor). */
    public static @Nullable PartKey keyOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (PartSpec.of(id).isEmpty()) return null;
        List<Integer> bands = ResistorItem.getBands(stack);
        if (id.equals(PartSpec.RESISTOR) && ResistorCode.decodeCodes(bands) == null) return null;
        return new PartKey(id, bands);
    }

    // ── Soldering ─────────────────────────────────────────────────────────────

    public void solder(ServerPlayer player, boolean batch) {
        PcbDesign d = boardDesign();
        if (d == null) {
            player.sendOverlayMessage(Component.translatable("pcb.omnitech.solder.no_board"));
            return;
        }
        List<PlacedPart> plan = planFor(d, items.get(SLOT_BOARD));
        if (plan.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("pcb.omnitech.solder.no_parts"));
            return;
        }

        List<TestBenchRunner.Outcome> outcomes = TestBenchLoader.test(d, plan);
        TestBenchRunner.Outcome pass = TestBenchRunner.firstPass(outcomes);
        Item resultItem = pass != null
                ? BuiltInRegistries.ITEM.getValue(Identifier.parse(pass.bench().resultItem()))
                : OmniTechItems.ASSEMBLED_CIRCUIT_BOARD.get();
        ItemStack result = new ItemStack(resultItem);
        result.set(OmniTechDataComponents.PCB_DESIGN.get(), d);
        result.set(OmniTechDataComponents.PCB_PARTS.get(), List.copyOf(plan));

        Map<PartKey, Integer> bom = billOfMaterials(plan);
        int solder = solderNeeded(plan);
        int made = 0, limit = batch ? 64 : 1;
        while (made < limit && canOutput(result) && hasMaterials(bom, solder)) {
            items.get(SLOT_BOARD).shrink(1);
            items.get(SLOT_SOLDER).shrink(solder);
            bom.forEach(this::take);
            ItemStack out = items.get(SLOT_OUTPUT);
            if (out.isEmpty()) items.set(SLOT_OUTPUT, result.copy());
            else out.grow(1);
            made++;
        }
        if (made == 0) {
            player.sendOverlayMessage(Component.translatable(canOutput(result)
                    ? "pcb.omnitech.solder.missing" : "pcb.omnitech.output_full"));
            return;
        }
        setChanged();
        player.sendOverlayMessage(pass != null
                ? Component.translatable("pcb.omnitech.solder.passed", made, result.getHoverName())
                : Component.translatable("pcb.omnitech.solder.untested", made));
        if (level != null) level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 1.8f);
    }

    private boolean hasMaterials(Map<PartKey, Integer> bom, int solder) {
        if (items.get(SLOT_BOARD).isEmpty()) return false;
        if (!items.get(SLOT_SOLDER).is(tinWire()) || items.get(SLOT_SOLDER).getCount() < solder) return false;
        for (Map.Entry<PartKey, Integer> e : bom.entrySet()) if (count(e.getKey()) < e.getValue()) return false;
        return true;
    }

    public int count(PartKey key) {
        int n = 0;
        for (int i = PARTS_START; i < SLOT_OUTPUT; i++) if (matches(key, items.get(i))) n += items.get(i).getCount();
        return n;
    }

    private void take(PartKey key, int amount) {
        for (int i = PARTS_START; i < SLOT_OUTPUT && amount > 0; i++) {
            ItemStack s = items.get(i);
            if (!matches(key, s)) continue;
            int n = Math.min(amount, s.getCount());
            s.shrink(n);
            amount -= n;
        }
    }

    private boolean canOutput(ItemStack result) {
        ItemStack out = items.get(SLOT_OUTPUT);
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, result) && out.getCount() < out.getMaxStackSize();
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        if (index == SLOT_BOARD) return stack.is(OmniTechItems.PRINTED_CIRCUIT_BOARD.get());
        if (index == SLOT_SOLDER) return stack.is(tinWire());
        if (index == SLOT_OUTPUT) return false;
        // any solderable part; a resistor only once its bands are painted
        return keyOf(stack) != null;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        plans.clear();
        input.childrenListOrEmpty("Plans").stream().forEach(c ->
                plans.put(c.getIntOr("Board", 0), c.read("Parts", PcbCodecs.PARTS).orElse(List.of())));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        ValueOutput.ValueOutputList list = output.childrenList("Plans");
        plans.forEach((board, parts) -> {
            ValueOutput c = list.addChild();
            c.putInt("Board", board);
            c.store("Parts", PcbCodecs.PARTS, parts);
        });
    }
}
