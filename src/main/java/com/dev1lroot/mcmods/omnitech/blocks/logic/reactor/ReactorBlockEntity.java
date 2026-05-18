/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.reactor;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.gui.ReactorMenu;
import com.dev1lroot.mcmods.omnitech.items.ReactorControlRodItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorFuelRodItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorRodItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReactorBlockEntity extends BlockEntity implements MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ── Temperature simulation constants ─────────────────────────────────────
    private static final int HEAT_TICK_INTERVAL      = 10;
    private static final int HEAT_PER_PULSE          = 3;
    private static final int PASSIVE_COOLING         = 1;
    private static final int WATER_EXTRA_COOLING     = 2;
    private static final int REFLECTOR_HEAT_RATE     = 2;
    private static final int CONTROL_HEAT_RATE       = 2;
    private static final int CONTROL_HEAT_THRESHOLD  = 600;
    public  static final int MAX_TEMPERATURE         = 2000;
    private static final int TEMP_HEAT_THRESHOLD     = 300;
    private static final int TEMP_MELTDOWN_THRESHOLD = 1200;

    private static final Direction[] XZ_DIRS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    // ── Structure state ───────────────────────────────────────────────────────
    private boolean formed = false;
    private ReactorStructure structure = null;

    // ── Coolant fluid tank ────────────────────────────────────────────────────
    FluidStack coolantTank  = FluidStack.EMPTY;
    int        tankCapacity = 0;
    final CoolantHandler coolantHandler = new CoolantHandler();

    // ── Tick counters / persistence ───────────────────────────────────────────
    private boolean pendingRevalidation = false;
    private int savedWidth     = 3;
    private int savedDepth     = 3;
    private int savedCellCount = 0;

    private int     revalidateTick  = 0;
    private int     heatTick        = 0;
    private int     persistTick     = 0;
    private boolean tempDirty       = false;
    private int     coreTemperature = 0;
    private boolean hasExploded     = false;

    public int     getCoreTemperature() { return coreTemperature; }
    public boolean hasExploded()        { return hasExploded; }
    public void    markExploded()       { hasExploded = true; }

    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.REACTOR.get(), pos, state);
    }

    // ── MenuProvider ──────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.omnitech.reactor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new ReactorMenu(containerId, inv, this);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide()) {
            lv.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    // ── Client sync ───────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(output);
            return output.buildResult();
        }
    }

    @Override
    public void onDataPacket(Connection net, ValueInput valueInput) {
        super.onDataPacket(net, valueInput);
        Level lv = getLevel();
        if (formed && lv != null && lv.isClientSide()) {
            ReactorStructure.detect(lv, getBlockPos()).ifPresent(s -> structure = s);
        }
    }

    // ── Fluid accessors ───────────────────────────────────────────────────────

    public FluidStack getCoolantTank()                            { return coolantTank; }
    public int        getCoolantAmount()                          { return coolantTank.getAmount(); }
    public int        getTankCapacity()                           { return tankCapacity; }
    public ResourceHandler<FluidResource> getCoolantHandler()     { return coolantHandler; }

    public static @Nullable ResourceHandler<FluidResource> findCoolantHandler(Level level, BlockPos fromPos) {
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int dx = -(ReactorStructure.MAX_SIZE - 1); dx <= 0; dx++) {
            for (int dy = -(ReactorStructure.HEIGHT - 1); dy <= 0; dy++) {
                for (int dz = -(ReactorStructure.MAX_SIZE - 1); dz <= 0; dz++) {
                    mpos.set(fromPos.getX() + dx, fromPos.getY() + dy, fromPos.getZ() + dz);
                    if (level.getBlockEntity(mpos) instanceof ReactorBlockEntity master
                            && master.isFormed()) {
                        return master.coolantHandler;
                    }
                }
            }
        }
        return null;
    }

    // ── Structure ─────────────────────────────────────────────────────────────

    public boolean isFormed()              { return formed; }
    public ReactorStructure getStructure() { return structure; }

    public void form(ReactorStructure s) {
        formed    = true;
        structure = s;
        tankCapacity = ReactorStructure.cavityBlocks(s.width, s.depth) * 1000;
        setChanged();
    }

    @Override
    public void setRemoved() {
        Level lv = getLevel();
        if (formed && lv != null && !lv.isClientSide()) {
            BlockPos pos = getBlockPos();
            LevelChunk chunk = ((ServerLevel) lv).getChunkSource().getChunkNow(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
            if (chunk != null && !(chunk.getBlockState(pos).getBlock() instanceof ReactorBlock)) {
                resetCellStates(lv);
            }
        }
        super.setRemoved();
    }

    public void invalidate() {
        if (!formed) return;
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide()) {
            resetCellStates(lv);
        }
        formed       = false;
        structure    = null;
        tankCapacity = 0;
        setChanged();
    }

    /** Resets block-state visual on all cells (items stay in their cell BEs). */
    private void resetCellStates(Level lv) {
        if (structure == null) return;
        for (BlockPos cellPos : structure.cells) {
            BlockState cs = lv.getBlockState(cellPos);
            if (cs.getBlock() instanceof ReactorCell) {
                lv.setBlock(cellPos, cs
                        .setValue(ReactorCell.CELL_STATE, ReactorCellState.COOL)
                        .setValue(ReactorCell.CELL_TYPE,  ReactorCellType.EMPTY), 3);
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

        if (be.formed && be.structure != null) {
            if (++be.revalidateTick >= 20) {
                be.revalidateTick = 0;
                if (!be.structure.isStillValid(level)) {
                    be.invalidate();
                    return;
                }
            }

            if (++be.heatTick >= HEAT_TICK_INTERVAL) {
                be.heatTick = 0;
                be.tickTemperature(level);
            }

            if (be.tempDirty && ++be.persistTick >= 200) {
                be.persistTick = 0;
                be.tempDirty   = false;
                be.setChanged();
            }
        }
    }

    // ── Temperature simulation ────────────────────────────────────────────────

    private void tickTemperature(Level level) {
        List<BlockPos> cells = structure.cells;
        int count = cells.size();
        if (count == 0) return;

        // Collect cell BEs and their current items up front
        ReactorCellBlockEntity[] cellBEs = new ReactorCellBlockEntity[count];
        ItemStack[]              stacks  = new ItemStack[count];
        for (int i = 0; i < count; i++) {
            if (level.getBlockEntity(cells.get(i)) instanceof ReactorCellBlockEntity cbe) {
                cellBEs[i] = cbe;
                stacks[i]  = cbe.getItem(0);
            } else {
                stacks[i] = ItemStack.EMPTY;
            }
        }

        Map<BlockPos, Integer> posToIndex = new HashMap<>(count * 2);
        for (int i = 0; i < count; i++) posToIndex.put(cells.get(i), i);

        boolean hasWater = !coolantTank.isEmpty();
        int cooling = PASSIVE_COOLING + (hasWater ? WATER_EXTRA_COOLING : 0);

        int[] delta           = new int[count];
        int[] effectivePulses = new int[count];

        for (int i = 0; i < count; i++) {
            ItemStack stack = stacks[i];
            if (stack.isEmpty() || !(stack.getItem() instanceof ReactorRodItem rod)) continue;

            BlockPos pos = cells.get(i);

            switch (rod.getCellType()) {
                case FUEL -> {
                    if (!hasWater) { delta[i] = -cooling; break; }
                    int rawPulses = 1;
                    double absorbed = 0.0;
                    for (Direction dir : XZ_DIRS) {
                        Integer ni = posToIndex.get(pos.relative(dir));
                        if (ni == null) continue;
                        ItemStack ns = stacks[ni];
                        if (ns.isEmpty() || !(ns.getItem() instanceof ReactorRodItem nr)) continue;
                        switch (nr.getCellType()) {
                            case FUEL, OTHER -> rawPulses++;
                            case CONTROL     -> absorbed += ReactorControlRodItem.getControl(ns) / 100.0;
                            default          -> {}
                        }
                    }
                    int effective = (int) Math.max(0.0, rawPulses - absorbed);
                    effectivePulses[i] = effective;
                    delta[i] = HEAT_PER_PULSE * effective - cooling;
                }
                case OTHER -> {
                    int adjFuel = 0;
                    if (hasWater) {
                        for (Direction dir : XZ_DIRS) {
                            Integer ni = posToIndex.get(pos.relative(dir));
                            if (ni == null) continue;
                            ItemStack ns = stacks[ni];
                            if (!ns.isEmpty() && ns.getItem() instanceof ReactorRodItem nr
                                    && nr.getCellType() == ReactorCellType.FUEL) adjFuel++;
                        }
                    }
                    delta[i] = REFLECTOR_HEAT_RATE * adjFuel - cooling;
                }
                case CONTROL -> {
                    int insertion = ReactorControlRodItem.getControl(stack);
                    int adjFuel   = 0;
                    if (hasWater) {
                        for (Direction dir : XZ_DIRS) {
                            Integer ni = posToIndex.get(pos.relative(dir));
                            if (ni == null) continue;
                            ItemStack ns = stacks[ni];
                            if (!ns.isEmpty() && ns.getItem() instanceof ReactorRodItem nr
                                    && nr.getCellType() == ReactorCellType.FUEL) adjFuel++;
                        }
                    }
                    double totalAbsorbed = (insertion / 100.0) * adjFuel;
                    delta[i] = (int)(CONTROL_HEAT_RATE * totalAbsorbed) - cooling;
                }
                default -> {}
            }
        }

        boolean changed      = false;
        int     totalHeat    = 0;
        int     maxCoreTemp  = 0;

        for (int i = 0; i < count; i++) {
            ItemStack stack = stacks[i];
            ReactorCellBlockEntity cbe = cellBEs[i];
            boolean hasRod = cbe != null && !stack.isEmpty() && stack.getItem() instanceof ReactorRodItem;
            int temp = 0;

            if (hasRod) {
                int cur      = ReactorRodItem.getTemperature(stack);
                if (cur < 0) cur = 0;
                totalHeat   += Math.max(0, delta[i]);
                int next     = Math.max(0, Math.min(MAX_TEMPERATURE, cur + delta[i]));
                boolean cellChanged = false;

                if (next != cur) {
                    ReactorRodItem.setTemperature(stack, next);
                    cellChanged = true;
                }
                temp = next;

                if (stack.getItem() instanceof ReactorFuelRodItem && effectivePulses[i] > 0) {
                    cellChanged = true;
                    if (hurtRod(stack)) {
                        cbe.setItem(0, ItemStack.EMPTY);
                        resetCell(level, cells.get(i));
                        changed = true;
                        continue;
                    }
                }

                if (stack.getItem() instanceof ReactorControlRodItem && temp >= CONTROL_HEAT_THRESHOLD) {
                    cellChanged = true;
                    if (hurtRod(stack)) {
                        cbe.setItem(0, ItemStack.EMPTY);
                        resetCell(level, cells.get(i));
                        changed = true;
                        continue;
                    }
                }

                if (cellChanged) {
                    cbe.setChanged();
                    changed = true;
                }
            }

            if (temp > maxCoreTemp) maxCoreTemp = temp;
            updateCellState(level, cells.get(i), temp, hasRod && !stack.isEmpty());
        }

        coreTemperature = maxCoreTemp;

        // Trigger nuclear explosion on meltdown (only once)
        if (!hasExploded && maxCoreTemp >= TEMP_MELTDOWN_THRESHOLD
                && level instanceof ServerLevel sl) {
            hasExploded = true;
            com.dev1lroot.mcmods.omnitech.radiation.NuclearExplosion.trigger(sl, getBlockPos());
            invalidate();
        }

        if (hasWater && totalHeat > 0) {
            int consumed  = Math.max(1, totalHeat / 20);
            int newAmount = Math.max(0, coolantTank.getAmount() - consumed);
            coolantTank   = newAmount > 0 ? coolantTank.copyWithAmount(newAmount) : FluidStack.EMPTY;
            changed = true;
        }

        if (changed) tempDirty = true;
    }

    private static boolean hurtRod(ItemStack stack) {
        if (!stack.isDamageableItem()) return false;
        int dmg = stack.getDamageValue() + 1;
        if (dmg >= stack.getMaxDamage()) {
            stack.shrink(1);
            return true;
        }
        stack.setDamageValue(dmg);
        return false;
    }

    private static void resetCell(Level level, BlockPos cellPos) {
        BlockState state = level.getBlockState(cellPos);
        if (!(state.getBlock() instanceof ReactorCell)) return;
        level.setBlock(cellPos, state
                .setValue(ReactorCell.CELL_STATE, ReactorCellState.COOL)
                .setValue(ReactorCell.CELL_TYPE,  ReactorCellType.EMPTY), 3);
    }

    private static void updateCellState(Level level, BlockPos cellPos, int temp, boolean hasRod) {
        BlockState state = level.getBlockState(cellPos);
        if (!(state.getBlock() instanceof ReactorCell)) return;

        ReactorCellState next;
        if (!hasRod || temp < TEMP_HEAT_THRESHOLD) {
            next = ReactorCellState.COOL;
        } else if (temp < TEMP_MELTDOWN_THRESHOLD) {
            next = ReactorCellState.HEAT;
        } else {
            next = ReactorCellState.MELTDOWN;
        }

        if (state.getValue(ReactorCell.CELL_STATE) != next) {
            level.setBlock(cellPos, state.setValue(ReactorCell.CELL_STATE, next), 3);
        }
    }

    // ── Coolant fluid handler ─────────────────────────────────────────────────

    private class CoolantHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        private static boolean isDistilledWater(FluidResource resource) {
            if (resource.isEmpty()) return false;
            var fo = OmniTechFluids.get("distilled_water");
            if (fo == null) return false;
            return resource.matches(new FluidStack(fo.source.get(), 1));
        }

        @Override protected FluidStack createSnapshot()              { return coolantTank; }
        @Override protected void revertToSnapshot(FluidStack snap)   { coolantTank = snap; }
        @Override protected void onRootCommit(FluidStack orig)       { tempDirty = true; }

        @Override public int           size()                         { return 1; }
        @Override public FluidResource getResource(int i)            { return i == 0 && !coolantTank.isEmpty() ? FluidResource.of(coolantTank) : FluidResource.EMPTY; }
        @Override public long          getAmountAsLong(int i)        { return i == 0 ? coolantTank.getAmount() : 0L; }
        @Override public long          getCapacityAsLong(int i, FluidResource r) { return i == 0 && formed ? tankCapacity : 0L; }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            if (index != 0 || !formed) return false;
            if (!isDistilledWater(resource)) return false;
            return coolantTank.isEmpty() || resource.matches(coolantTank);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || !formed || !isDistilledWater(resource) || amount <= 0) return 0;
            if (!coolantTank.isEmpty() && !resource.matches(coolantTank)) return 0;
            int space    = tankCapacity - coolantTank.getAmount();
            int toInsert = Math.min(amount, space);
            if (toInsert <= 0) return 0;
            updateSnapshots(tx);
            coolantTank = coolantTank.isEmpty() ? resource.toStack(toInsert)
                    : coolantTank.copyWithAmount(coolantTank.getAmount() + toInsert);
            return toInsert;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || coolantTank.isEmpty() || amount <= 0) return 0;
            if (!resource.matches(coolantTank)) return 0;
            int toExtract = Math.min(amount, coolantTank.getAmount());
            updateSnapshots(tx);
            coolantTank = coolantTank.copyWithAmount(coolantTank.getAmount() - toExtract);
            if (coolantTank.getAmount() <= 0) coolantTank = FluidStack.EMPTY;
            return toExtract;
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
            output.putInt("CellCount",    structure.cells.size());
        }
        output.putInt("TankCapacity", tankCapacity);
        output.store("Coolant", FluidStack.OPTIONAL_CODEC, coolantTank);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        formed = input.getIntOr("Formed", 0) != 0;
        if (formed) {
            savedWidth          = input.getIntOr("StructWidth", 3);
            savedDepth          = input.getIntOr("StructDepth", 3);
            savedCellCount      = input.getIntOr("CellCount", 0);
            pendingRevalidation = true;
        }
        tankCapacity = input.getIntOr("TankCapacity", 0);
        coolantTank  = input.read("Coolant", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }
}
