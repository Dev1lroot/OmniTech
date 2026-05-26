/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.thermal.boiler;

import com.dev1lroot.mcmods.omnitech.FluidPhase;
import com.dev1lroot.mcmods.omnitech.FluidPhaseUtil;
import com.dev1lroot.mcmods.omnitech.FluidPhysicsRegistry;
import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.io.IColdReceiver;
import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
import com.dev1lroot.mcmods.omnitech.blocks.ThermalState;
import com.dev1lroot.mcmods.omnitech.gui.BoilerMenu;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.slf4j.Logger;

public class BoilerBlockEntity extends BlockEntity implements MenuProvider, IHeatReceiver, IColdReceiver {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int MAX_FLUID     = 8000;
    public static final int TRANSFER_RATE = 100;
    public static final int MIN_BOIL_HEAT = 100;
    public static final int MAX_HEAT      =  500;
    public static final int MIN_HEAT      = -500;

    private static final int AMBIENT_TEMPERATURE = 15;
    private static final int DECAY_INTERVAL      = 20;

    // ── State ─────────────────────────────────────────────────────────────────

    private int storedHeat = 0;
    private int decayTimer = 0;
    private FluidStack fluidTank = FluidStack.EMPTY;

    // ── Fluid capability ──────────────────────────────────────────────────────

    public final ResourceHandler<FluidResource> fluidHandler = new InternalTank();

    // ── ContainerData (synced to GUI) ─────────────────────────────────────────
    // Index 0 = storedHeat, 1 = fluidAmount

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> storedHeat;
                case 1 -> fluidTank.getAmount();
                default -> 0;
            };
        }
        @Override public void set(int i, int value) {
            if (i == 0) storedHeat = value;
        }
        @Override public int getCount() { return 2; }
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public BoilerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.BOILER.get(), pos, state);
    }

    // ── MenuProvider ──────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.omnitech.boiler");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BoilerMenu(containerId, playerInventory, this, dataAccess);
    }

    // ── IHeatReceiver / IColdReceiver ─────────────────────────────────────────

    @Override
    public int addHeat(int celsius) {
        if (storedHeat >= MAX_HEAT) return 0;
        int absorbed = Math.min(celsius, MAX_HEAT - storedHeat);
        storedHeat += absorbed;
        return absorbed;
    }

    @Override
    public int addCold(int celsius) {
        if (storedHeat <= MIN_HEAT) return 0;
        int absorbed = Math.min(celsius, storedHeat - MIN_HEAT);
        storedHeat -= absorbed;
        return absorbed;
    }

    // ── Network sync ──────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, registries);
            this.saveAdditional(output);
            return output.buildResult();
        }
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoilerBlockEntity be) {
        boolean dirty = false;

        // 1. Pull fluid from below and sides only — never from above (would suck back vapour)
        for (Direction face : Direction.values()) {
            if (face == Direction.UP) continue;
            if (be.fluidTank.getAmount() >= MAX_FLUID) break;
            ResourceHandler<FluidResource> neighbor = level.getCapability(
                    Capabilities.Fluid.BLOCK, pos.relative(face), face.getOpposite());
            if (neighbor != null) {
                dirty |= FluidNetworkUtil.tryPullFluid(neighbor, be.fluidHandler,
                        be.fluidTank.isEmpty() ? null : be.fluidTank.getFluid(), TRANSFER_RATE);
            }
        }

        // 2. Stamp the boiler's heat onto the fluid's temperature component
        if (!be.fluidTank.isEmpty()) {
            Integer existing = be.fluidTank.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
            if (existing == null || existing != be.storedHeat) {
                be.fluidTank.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), be.storedHeat);
                dirty = true;
            }
        }

        // 3. Push fluid upward only when it has transitioned to a gas-like phase
        if (!be.fluidTank.isEmpty()) {
            var physics = FluidPhysicsRegistry.get(be.fluidTank.getFluid());
            Integer pressureBox = be.fluidTank.get(OmniTechDataComponents.FLUID_PRESSURE.get());
            int pressureKPa = pressureBox != null ? pressureBox : 101;
            FluidPhase phase = FluidPhaseUtil.getPhase(be.storedHeat, pressureKPa, physics.phaseDiagram());
            boolean gasLike = phase == FluidPhase.VAPOUR || phase == FluidPhase.GAS
                    || phase == FluidPhase.SUPERCRITICAL || phase == FluidPhase.PLASMA;
            if (gasLike) {
                ResourceHandler<FluidResource> output = level.getCapability(
                        Capabilities.Fluid.BLOCK, pos.above(), Direction.DOWN);
                if (output != null)
                    dirty |= FluidNetworkUtil.tryPushFluid(be.fluidHandler, output, TRANSFER_RATE);
            }
        }

        // 4. Ambient decay — storedHeat drifts toward AMBIENT_TEMPERATURE every DECAY_INTERVAL ticks
        if (be.storedHeat != AMBIENT_TEMPERATURE) {
            be.decayTimer++;
            if (be.decayTimer >= DECAY_INTERVAL) {
                be.decayTimer = 0;
                if (be.storedHeat > AMBIENT_TEMPERATURE) be.storedHeat--;
                else be.storedHeat++;
                dirty = true;
            }
        } else {
            be.decayTimer = 0;
        }

        // 5. Update LIT and THERMAL blockstates
        boolean isLit = !be.fluidTank.isEmpty() && be.storedHeat > MIN_BOIL_HEAT;
        ThermalState thermal = ThermalState.of(be.storedHeat);
        if (state.getValue(BoilerBlock.LIT) != isLit
                || state.getValue(BoilerBlock.THERMAL) != thermal) {
            level.setBlock(pos, state
                    .setValue(BoilerBlock.LIT, isLit)
                    .setValue(BoilerBlock.THERMAL, thermal), 3);
            dirty = true;
        }

        if (dirty) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        storedHeat = input.getIntOr("StoredHeat", 0);
        decayTimer = input.getIntOr("DecayTimer", 0);
        fluidTank  = input.read("FluidTank", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("StoredHeat", storedHeat);
        output.putInt("DecayTimer", decayTimer);
        output.store("FluidTank", FluidStack.OPTIONAL_CODEC, fluidTank);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FluidStack getFluidTank()     { return fluidTank; }
    public int getStoredHeat()           { return storedHeat; }
    public ContainerData getContainerData() { return dataAccess; }

    // ── InternalTank ──────────────────────────────────────────────────────────

    private class InternalTank extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        @Override protected FluidStack createSnapshot()          { return fluidTank; }
        @Override protected void revertToSnapshot(FluidStack s)  { fluidTank = s; }

        @Override public int size() { return 1; }

        @Override public FluidResource getResource(int i) {
            return fluidTank.isEmpty() ? FluidResource.EMPTY : FluidResource.of(fluidTank);
        }

        @Override public long getAmountAsLong(int i) { return fluidTank.getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource res) { return MAX_FLUID; }
        @Override public boolean isValid(int i, FluidResource res) { return true; }

        @Override
        public int insert(int i, FluidResource res, int amount, TransactionContext tx) {
            if (!fluidTank.isEmpty() && !res.matches(fluidTank)) return 0;
            int space = MAX_FLUID - fluidTank.getAmount();
            int toInsert = Math.min(amount, space);
            if (toInsert <= 0) return 0;
            updateSnapshots(tx);
            fluidTank = FluidNetworkUtil.blendInto(fluidTank, res, toInsert);
            return toInsert;
        }

        @Override
        public int extract(int i, FluidResource res, int amount, TransactionContext tx) {
            if (fluidTank.isEmpty() || !res.matches(fluidTank)) return 0;
            int toExt = Math.min(amount, fluidTank.getAmount());
            if (toExt <= 0) return 0;
            updateSnapshots(tx);
            fluidTank = fluidTank.copyWithAmount(fluidTank.getAmount() - toExt);
            if (fluidTank.getAmount() <= 0) fluidTank = FluidStack.EMPTY;
            return toExt;
        }
    }
}
