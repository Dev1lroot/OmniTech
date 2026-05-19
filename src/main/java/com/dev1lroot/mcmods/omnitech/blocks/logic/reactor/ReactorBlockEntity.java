/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.reactor;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.OmniTechMobEffects;
import com.dev1lroot.mcmods.omnitech.gui.ReactorMenu;
import com.dev1lroot.mcmods.omnitech.items.ReactorControlRodItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorFuelRodItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorRodItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
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
    private static final int REFLECTOR_HEAT_RATE     = 2;
    private static final int CONTROL_HEAT_RATE       = 2;
    private static final int CONTROL_HEAT_THRESHOLD  = 600;
    public  static final int MAX_TEMPERATURE         = 2000;
    public  static final int MAX_COOLANT_TEMP        = 350;
    public  static final int MAX_PRESSURE            = 1000;
    private static final int AMBIENT_TEMP            = 20;
    private static final int TEMP_HEAT_THRESHOLD     = 300;
    private static final int TEMP_MELTDOWN_THRESHOLD = 1200;
    private static final int RAD_INTERVAL            = 100; // ticks between radiation sweeps (5 s)
    private static final int RAD_OUTER_RADIUS        = 10;  // blocks outside walls for Radiation I

    private static final Direction[] XZ_DIRS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    // ── Structure state ───────────────────────────────────────────────────────
    private boolean formed = false;
    private ReactorStructure structure = null;

    // ── Coolant fluid tank ────────────────────────────────────────────────────
    FluidStack coolantTank        = FluidStack.EMPTY;
    int        tankCapacity       = 0;
    int        coolantTemperature = AMBIENT_TEMP;
    int        pressure           = 0;
    final CoolantHandler coolantHandler = new CoolantHandler();

    // ── Tick counters / persistence ───────────────────────────────────────────
    private boolean pendingRevalidation = false;
    private int savedWidth     = 3;
    private int savedDepth     = 3;
    private int savedCellCount = 0;

    private int     revalidateTick  = 0;
    private int     heatTick        = 0;
    private int     radTick         = 0;
    private int     persistTick     = 0;
    private boolean tempDirty       = false;
    private int     coreTemperature = 0;
    private boolean hasExploded     = false;

    public int     getCoreTemperature()    { return coreTemperature; }
    public int     getCoolantTemperature() { return coolantTemperature; }
    public int     getPressure()           { return pressure; }
    public boolean hasExploded()           { return hasExploded; }
    public void    markExploded()          { hasExploded = true; }

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

            if (++be.radTick >= RAD_INTERVAL) {
                be.radTick = 0;
                if (be.coreTemperature > 0) be.tickReactorRadiation(level);
            }

            if (be.tempDirty && ++be.persistTick >= 200) {
                be.persistTick = 0;
                be.tempDirty   = false;
                be.setChanged();
            }
        }
    }

    // ── Active-reactor radiation ──────────────────────────────────────────────

    private void tickReactorRadiation(Level level) {
        BlockPos o = structure.origin;

        // Interior cavity: origin+(1,1,1) to origin+(w−1, H−1, d−1) [exclusive max per AABB]
        AABB interiorBox = new AABB(
                o.getX() + 1,                          o.getY() + 1,                           o.getZ() + 1,
                o.getX() + structure.width  - 1,       o.getY() + ReactorStructure.HEIGHT - 1, o.getZ() + structure.depth - 1);

        // Search volume: full reactor shell inflated by RAD_OUTER_RADIUS on every side
        AABB searchBox = new AABB(
                o.getX(), o.getY(), o.getZ(),
                o.getX() + structure.width, o.getY() + ReactorStructure.HEIGHT, o.getZ() + structure.depth)
                .inflate(RAD_OUTER_RADIUS);

        Holder<MobEffect> effect = OmniTechMobEffects.RADIATION;

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (entity instanceof Player p && p.isCreative()) continue;

            // Inside the cavity → Radiation III; anywhere else in search volume → Radiation I
            int amplifier = interiorBox.contains(entity.getX(), entity.getY(), entity.getZ()) ? 2 : 0;

            MobEffectInstance existing = entity.getEffect(effect);
            if (existing == null || existing.getAmplifier() < amplifier) {
                entity.addEffect(new MobEffectInstance(effect, 200, amplifier, false, true));
            } else if (existing.getAmplifier() == amplifier) {
                // Refresh duration without downgrading
                entity.addEffect(new MobEffectInstance(effect,
                        Math.max(existing.getDuration(), 200), amplifier, false, true));
            }
        }
    }

    // ── Temperature simulation ────────────────────────────────────────────────

    private void tickTemperature(Level level) {
        List<BlockPos> cells = structure.cells;
        int count = cells.size();
        if (count == 0) return;

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

        boolean hasCoolant = !coolantTank.isEmpty();
        // moderationFactor encodes both fluid type and fill level:
        //   liquid water → 1.0 × fillFraction  (good moderator)
        //   steam        → 0.1 × fillFraction  (poor moderator: low density)
        //   no coolant   → 0.0                 (no moderation)
        float moderationFactor = getModerationFactor();
        // Thermal effectiveness of the coolant decreases as it heats up
        float thermalEff = hasCoolant
                ? Math.max(0.1f, 1f - (coolantTemperature - AMBIENT_TEMP) / (float)(MAX_COOLANT_TEMP - AMBIENT_TEMP))
                : 0f;
        float coolingFactor = thermalEff * moderationFactor;

        // ── Phase 1: neutron physics — compute raw heat per rod ───────────────
        //
        // The coolant is the neutron moderator (water/steam slows neutrons).
        // Steam is a much weaker moderator than liquid water (low density).
        // Control rods are pure absorbers/blockers — not graphite moderators.
        // Pulse counts are scaled by moderationFactor before converting to heat.
        int[] rawHeat         = new int[count];
        int[] effectivePulses = new int[count];

        if (moderationFactor < 0.01f) {
            // No effective moderation → chain reaction cannot sustain; rawHeat stays zero.
        } else for (int i = 0; i < count; i++) {
            ItemStack stack = stacks[i];
            if (stack.isEmpty() || !(stack.getItem() instanceof ReactorRodItem rod)) continue;
            BlockPos pos = cells.get(i);

            switch (rod.getCellType()) {
                case FUEL -> {
                    float totalPulses = 0.0f;
                    dirScan:
                    for (Direction dir : XZ_DIRS) {
                        float flux = 1.0f;
                        for (int step = 1; step <= ReactorStructure.MAX_SIZE; step++) {
                            Integer ni = posToIndex.get(pos.relative(dir, step));
                            if (ni == null) break;                              // outside reactor
                            ItemStack ns = stacks[ni];
                            if (ns.isEmpty() || !(ns.getItem() instanceof ReactorRodItem nr)) break;
                            switch (nr.getCellType()) {
                                case FUEL:    totalPulses += flux;         continue dirScan;
                                case CONTROL: {
                                    // Control rod attenuates flux; fully inserted = full block
                                    int ins = ReactorControlRodItem.getControl(ns);
                                    flux *= (1.0f - ins / 100.0f);
                                    if (flux < 0.01f) continue dirScan;    // path blocked
                                    break;                                  // continue scanning
                                }
                                case OTHER:   totalPulses += flux * 0.5f; continue dirScan;
                                default:                                   continue dirScan;
                            }
                        }
                    }
                    // Scale by moderationFactor: steam reduces effective pulses
                    int pulses = Math.round(totalPulses * moderationFactor);
                    effectivePulses[i] = pulses;
                    rawHeat[i]         = HEAT_PER_PULSE * pulses;
                }
                case CONTROL -> {
                    int insertion = ReactorControlRodItem.getControl(stack);
                    int adjFuel   = 0;
                    for (Direction dir : XZ_DIRS) {
                        Integer ni = posToIndex.get(pos.relative(dir));
                        if (ni == null) continue;
                        ItemStack ns = stacks[ni];
                        if (!ns.isEmpty() && ns.getItem() instanceof ReactorRodItem nr
                                && nr.getCellType() == ReactorCellType.FUEL) adjFuel++;
                    }
                    rawHeat[i] = Math.round(CONTROL_HEAT_RATE * (insertion / 100.0f) * adjFuel);
                }
                case OTHER -> {
                    int adjFuel = 0;
                    for (Direction dir : XZ_DIRS) {
                        Integer ni = posToIndex.get(pos.relative(dir));
                        if (ni == null) continue;
                        ItemStack ns = stacks[ni];
                        if (!ns.isEmpty() && ns.getItem() instanceof ReactorRodItem nr
                                && nr.getCellType() == ReactorCellType.FUEL) adjFuel++;
                    }
                    rawHeat[i] = REFLECTOR_HEAT_RATE * adjFuel;
                }
                default -> {}
            }
        }

        // ── Phase 2: split heat between rods and coolant ──────────────────────
        //
        // Water absorbs a fraction of each rod's raw heat (50 % × coolingFactor).
        // Passive cooling and the water share reduce the rod's net temperature delta.
        // Using rawHeat (not afterPassive) ensures coolant heats up even for small rods
        // and avoids the decay-cancellation that plagued the old waterCoolingMax=2 cap.
        int[] delta = new int[count];
        int   coolantHeatAbsorbed = 0;

        for (int i = 0; i < count; i++) {
            if (stacks[i].isEmpty() || !(stacks[i].getItem() instanceof ReactorRodItem)) continue;
            int waterCooling = (hasCoolant && rawHeat[i] > 0)
                    ? Math.max(0, (int)(rawHeat[i] * coolingFactor * 0.5f))
                    : 0;
            delta[i]             = rawHeat[i] - PASSIVE_COOLING - waterCooling;
            coolantHeatAbsorbed += waterCooling;
        }

        // ── Phase 3: apply deltas, damage rods, update cell states ────────────
        boolean changed     = false;
        int     maxCoreTemp = 0;

        for (int i = 0; i < count; i++) {
            ItemStack stack = stacks[i];
            ReactorCellBlockEntity cbe = cellBEs[i];
            boolean hasRod = cbe != null && !stack.isEmpty() && stack.getItem() instanceof ReactorRodItem;
            int temp = 0;

            if (hasRod) {
                int cur  = ReactorRodItem.getTemperature(stack);
                if (cur < 0) cur = 0;
                int next = Math.max(0, Math.min(MAX_TEMPERATURE, cur + delta[i]));
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

        // ── Coolant heating and evaporation ───────────────────────────────────
        //
        // Temperature rise uses float arithmetic to avoid integer-division truncation
        // when many buckets are present.  Liquid water does not evaporate while
        // pressurized; steam evaporates at a rate proportional to temperature.
        if (hasCoolant && coolantTank.getAmount() > 0) {
            if (coolantHeatAbsorbed > 0) {
                float buckets = Math.max(1f, coolantTank.getAmount() / 1000f);
                int rise = Math.max(1, (int)(coolantHeatAbsorbed / buckets));
                coolantTemperature = Math.min(MAX_COOLANT_TEMP, coolantTemperature + rise);
                // Only liquid water is consumed by heat; steam evaporates separately below
                if (!isSteamCoolant()) {
                    int consumed  = Math.max(1, coolantHeatAbsorbed / 20);
                    int newAmount = Math.max(0, coolantTank.getAmount() - consumed);
                    coolantTank   = newAmount > 0 ? coolantTank.copyWithAmount(newAmount) : FluidStack.EMPTY;
                }
                changed = true;
            }
            // Steam dissipates from the reactor (low-density fluid escapes)
            if (isSteamCoolant()) {
                int evapRate  = Math.max(10, coolantTemperature * 2);
                int newAmount = Math.max(0, coolantTank.getAmount() - evapRate);
                coolantTank   = newAmount > 0 ? coolantTank.copyWithAmount(newAmount) : FluidStack.EMPTY;
                if (newAmount == 0) coolantTemperature = AMBIENT_TEMP;
                changed = true;
            }
        } else if (!hasCoolant && coolantTemperature > AMBIENT_TEMP) {
            coolantTemperature = Math.max(AMBIENT_TEMP, coolantTemperature - 1);
        }

        updatePressure();

        // ── Meltdown ──────────────────────────────────────────────────────────
        if (!hasExploded && maxCoreTemp >= TEMP_MELTDOWN_THRESHOLD && level instanceof ServerLevel sl) {
            hasExploded = true;
            com.dev1lroot.mcmods.omnitech.radiation.NuclearExplosion.trigger(sl, getBlockPos());
            invalidate();
        }

        if (changed) tempDirty = true;
    }

    // ── Pressure and moderation helpers ──────────────────────────────────────

    private boolean isSteamCoolant() {
        if (coolantTank.isEmpty()) return false;
        var fo = OmniTechFluids.get("steam");
        return fo != null && coolantTank.getFluid() == fo.source.get();
    }

    /**
     * Returns a 0..1 moderation factor for neutron physics and heat transfer.
     * Liquid water = 1.0 × fill fraction; steam = 0.1 × fill fraction (low density);
     * empty tank = 0.0 (no moderation → no chain reaction).
     */
    private float getModerationFactor() {
        if (coolantTank.isEmpty() || tankCapacity == 0) return 0f;
        float fillFraction = (float) coolantTank.getAmount() / tankCapacity;
        return isSteamCoolant() ? 0.1f * fillFraction : fillFraction;
    }

    /**
     * Recomputes {@link #pressure} from the current coolant state.
     * Pressure = volume component (0..500) + thermal component (0..500).
     * Steam has pressure 0 — it represents a vented, depressurized state.
     */
    private void updatePressure() {
        if (coolantTank.isEmpty() || isSteamCoolant()) {
            pressure = 0;
            return;
        }
        int fillComp    = tankCapacity > 0 ? coolantTank.getAmount() * 500 / tankCapacity : 0;
        int thermalComp = coolantTemperature > AMBIENT_TEMP
                ? Math.min(500, (coolantTemperature - AMBIENT_TEMP) * 500 / (MAX_COOLANT_TEMP - AMBIENT_TEMP))
                : 0;
        pressure = Math.min(MAX_PRESSURE, fillComp + thermalComp);
    }

    /**
     * Depressurizes the reactor: converts liquid coolant to steam 1:1 if the
     * coolant temperature is at or above 100 °C.  Below that threshold the water
     * would not flash, so nothing happens.  Calling this on steam is a no-op.
     */
    public void depressurize() {
        if (coolantTank.isEmpty() || isSteamCoolant()) return;
        if (coolantTemperature < 100) return; // too cold to flash
        var steamFo = OmniTechFluids.get("steam");
        if (steamFo == null) return;
        int amount = coolantTank.getAmount();
        FluidStack steam = new FluidStack(steamFo.source.get(), amount);
        steam.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), coolantTemperature);
        coolantTank = steam;
        pressure    = 0;
        tempDirty   = true;
        setChanged();
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

        // Type-only check: accepts any distilled water regardless of data components (e.g. temperature)
        private static boolean isDistilledWater(FluidResource resource) {
            if (resource.isEmpty()) return false;
            var fo = OmniTechFluids.get("distilled_water");
            if (fo == null) return false;
            return resource.toStack(1).getFluid() == fo.source.get();
        }

        @Override protected FluidStack createSnapshot()              { return coolantTank; }
        @Override protected void revertToSnapshot(FluidStack snap)   { coolantTank = snap; }
        @Override protected void onRootCommit(FluidStack orig)       { tempDirty = true; }

        @Override public int size() { return 1; }

        /** Returns the resource with the current coolant temperature stamped on it. */
        @Override
        public FluidResource getResource(int i) {
            if (i != 0 || coolantTank.isEmpty()) return FluidResource.EMPTY;
            FluidStack stamped = coolantTank.copy();
            stamped.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), coolantTemperature);
            return FluidResource.of(stamped);
        }

        @Override public long getAmountAsLong(int i)                       { return i == 0 ? coolantTank.getAmount() : 0L; }
        @Override public long getCapacityAsLong(int i, FluidResource r)    { return i == 0 && formed ? tankCapacity : 0L; }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            if (index != 0 || !formed) return false;
            return isDistilledWater(resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || !formed || !isDistilledWater(resource) || amount <= 0) return 0;
            int space    = tankCapacity - coolantTank.getAmount();
            int toInsert = Math.min(amount, space);
            if (toInsert <= 0) return 0;

            // Blend temperatures: incoming fluid may carry a FLUID_TEMPERATURE component
            FluidStack incoming = resource.toStack(1);
            Integer incomingTempBox = incoming.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
            int incomingTemp = incomingTempBox != null ? incomingTempBox : AMBIENT_TEMP;
            int existing = coolantTank.getAmount();
            int blended = existing > 0
                    ? (coolantTemperature * existing + incomingTemp * toInsert) / (existing + toInsert)
                    : incomingTemp;

            updateSnapshots(tx);
            coolantTemperature = Math.max(AMBIENT_TEMP, Math.min(MAX_COOLANT_TEMP, blended));
            coolantTank = coolantTank.isEmpty() ? resource.toStack(toInsert)
                    : coolantTank.copyWithAmount(coolantTank.getAmount() + toInsert);
            return toInsert;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || coolantTank.isEmpty() || amount <= 0) return 0;
            // Allow extracting whatever fluid is currently in the tank (water or steam)
            if (resource.isEmpty() || resource.toStack(1).getFluid() != coolantTank.getFluid()) return 0;
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
        output.putInt("TankCapacity",        tankCapacity);
        output.putInt("CoolantTemperature", coolantTemperature);
        output.putInt("Pressure",           pressure);
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
        tankCapacity       = input.getIntOr("TankCapacity", 0);
        coolantTemperature = input.getIntOr("CoolantTemperature", AMBIENT_TEMP);
        pressure           = input.getIntOr("Pressure", 0);
        coolantTank        = input.read("Coolant", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }
}
