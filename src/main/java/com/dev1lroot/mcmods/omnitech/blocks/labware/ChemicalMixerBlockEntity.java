/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.labware;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.ChemicalMixerMenu;
import com.dev1lroot.mcmods.omnitech.util.FluidMixing;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
import com.dev1lroot.mcmods.omnitech.util.SolutionFluids;
import com.dev1lroot.mcmods.omnitech.util.SolutionPhases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
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

/**
 * Chemical Mixer — blends two fluid streams into one at a fixed, player-set ratio.
 *
 * <p>Every server tick the mixer takes {@code ratioA} mB from input A and {@code ratioB} mB from
 * input B and adds {@code ratioA + ratioB} mB of the blend to the output (5 : 1 gives +6 mB per
 * tick). It only runs while both inputs hold enough, the output has room, and the fluids are
 * miscible ({@link FluidMixing}). The blend is volume-weighted in temperature, pressure and
 * composition ({@link FluidNetworkUtil#blendInto}), so it comes out as an {@code omnitech:solution}
 * unless both inputs are the same plain fluid.
 *
 * <p>Faces: front = output, left (seen from the front) = input A, right = input B, back = closed.
 */
public class ChemicalMixerBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUT_CAPACITY  = 4_000;
    public static final int OUTPUT_CAPACITY = 8_000;
    public static final int MIN_RATIO       = 1;
    public static final int MAX_RATIO       = 64;

    /** Menu button ids: 0-3 adjust A by {@link #STEPS}, 4-7 adjust B. */
    public static final int[] STEPS = {-5, -1, 1, 5};
    public static final int BUTTON_COUNT = STEPS.length * 2;

    private static final int TANK_A = 0, TANK_B = 1, TANK_OUT = 2;
    private static final int SYNC_INTERVAL = 4;

    private final FluidStack[] tanks = { FluidStack.EMPTY, FluidStack.EMPTY, FluidStack.EMPTY };
    private int ratioA = 1;
    private int ratioB = 1;
    private boolean syncPending = false;

    public final ResourceHandler<FluidResource> inputAHandler  = new TankHandler(TANK_A,   INPUT_CAPACITY,  true);
    public final ResourceHandler<FluidResource> inputBHandler  = new TankHandler(TANK_B,   INPUT_CAPACITY,  true);
    public final ResourceHandler<FluidResource> outputHandler  = new TankHandler(TANK_OUT, OUTPUT_CAPACITY, false);

    // [0]=ratioA [1]=ratioB  [2]=inA amt [3]=inA cap  [4]=inB amt [5]=inB cap  [6]=out amt [7]=out cap
    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> ratioA;
                case 1 -> ratioB;
                case 2 -> tanks[TANK_A].getAmount();
                case 3 -> INPUT_CAPACITY;
                case 4 -> tanks[TANK_B].getAmount();
                case 5 -> INPUT_CAPACITY;
                case 6 -> tanks[TANK_OUT].getAmount();
                case 7 -> OUTPUT_CAPACITY;
                default -> 0;
            };
        }
        @Override public void set(int i, int value) {}
        @Override public int getCount() { return 8; }
    };

    public ChemicalMixerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.CHEMICAL_MIXER.get(), pos, state);
    }

    @Override public Component getDisplayName() { return Component.translatable("container.omnitech.chemical_mixer"); }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new ChemicalMixerMenu(containerId, inv, this, dataAccess);
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var reporter = new ProblemReporter.ScopedCollector(this.problemPath(), com.mojang.logging.LogUtils.getLogger());
        try (reporter) {
            var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(out);
            return out.buildResult();
        }
    }

    // ── Ratio ─────────────────────────────────────────────────────────────────

    /** Handles a menu button press; returns false for an unknown id. */
    public boolean adjustRatio(int id) {
        if (id < 0 || id >= BUTTON_COUNT) return false;
        int step = STEPS[id % STEPS.length];
        if (id < STEPS.length) ratioA = Mth.clamp(ratioA + step, MIN_RATIO, MAX_RATIO);
        else                   ratioB = Mth.clamp(ratioB + step, MIN_RATIO, MAX_RATIO);
        setChanged();
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChemicalMixerBlockEntity be) {
        boolean changed = false;
        Direction front = state.getValue(ChemicalMixerBlock.FACING);
        Direction sideA = ChemicalMixerBlock.inputADirection(state);
        Direction sideB = ChemicalMixerBlock.inputBDirection(state);

        changed |= be.pull(level, pos, sideA, TANK_A, be.inputAHandler);
        changed |= be.pull(level, pos, sideB, TANK_B, be.inputBHandler);

        if (be.canMix()) {
            be.mix();
            changed = true;
        }

        if (!be.tanks[TANK_OUT].isEmpty()) {
            var neighbor = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(front), front.getOpposite());
            if (neighbor != null) {
                if (SolutionFluids.isMixture(be.tanks[TANK_OUT])) {
                    // A mixture can hold several phases: condensed part and gas part leave separately.
                    FluidStack before = be.tanks[TANK_OUT];
                    be.tanks[TANK_OUT] = SolutionPhases.pushByPhase(before, neighbor, 1000);
                    changed |= be.tanks[TANK_OUT] != before;
                } else {
                    changed |= FluidNetworkUtil.tryPushFluid(be.outputHandler, neighbor);
                }
            }
        }

        if (changed) { be.setChanged(); be.syncPending = true; }
        // The mixer changes every tick while running; sending the block every tick would flood clients.
        if (be.syncPending && level.getGameTime() % SYNC_INTERVAL == 0) {
            be.syncPending = false;
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private boolean pull(Level level, BlockPos pos, Direction side, int tank, ResourceHandler<FluidResource> handler) {
        if (tanks[tank].getAmount() >= INPUT_CAPACITY) return false;
        var src = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(side), side.getOpposite());
        return src != null && FluidNetworkUtil.tryPullFluid(src, handler);
    }

    // ── Mixing ────────────────────────────────────────────────────────────────

    private boolean canMix() {
        FluidStack a = tanks[TANK_A], b = tanks[TANK_B], out = tanks[TANK_OUT];
        if (a.getAmount() < ratioA || b.getAmount() < ratioB) return false;
        if (OUTPUT_CAPACITY - out.getAmount() < ratioA + ratioB) return false;
        FluidResource resA = FluidResource.of(a), resB = FluidResource.of(b);
        // The inputs must blend with each other, and both with whatever the output already holds.
        return FluidMixing.canBlend(a, resB)
                && FluidMixing.canBlend(out, resA)
                && FluidMixing.canBlend(out, resB);
    }

    private void mix() {
        int total = ratioA + ratioB;
        FluidStack a = tanks[TANK_A], b = tanks[TANK_B];

        // Both inputs first, so the output only has to blend one stream at the combined T / P.
        FluidStack mixed = FluidNetworkUtil.blendInto(a.copyWithAmount(ratioA), FluidResource.of(b), ratioB);
        tanks[TANK_OUT] = FluidNetworkUtil.blendInto(tanks[TANK_OUT], FluidResource.of(mixed), total);

        a.shrink(ratioA);
        b.shrink(ratioB);
        if (a.getAmount() <= 0) tanks[TANK_A] = FluidStack.EMPTY;
        if (b.getAmount() <= 0) tanks[TANK_B] = FluidStack.EMPTY;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FluidStack getFluidA()   { return tanks[TANK_A]; }
    public FluidStack getFluidB()   { return tanks[TANK_B]; }
    public FluidStack getOutput()   { return tanks[TANK_OUT]; }
    public int getRatioA()          { return ratioA; }
    public int getRatioB()          { return ratioB; }

    // ── Tank handler ──────────────────────────────────────────────────────────

    /** An input tank accepts anything that blends with its contents; the output tank only gives. */
    private class TankHandler extends SnapshotJournal<FluidStack> implements ResourceHandler<FluidResource> {
        private final int tank;
        private final int capacity;
        private final boolean input;

        TankHandler(int tank, int capacity, boolean input) {
            this.tank = tank;
            this.capacity = capacity;
            this.input = input;
        }

        @Override protected FluidStack createSnapshot()         { return tanks[tank].copy(); }
        @Override protected void revertToSnapshot(FluidStack s) { tanks[tank] = s; }
        @Override public int size()                             { return 1; }
        @Override public FluidResource getResource(int i)      { return tanks[tank].isEmpty() ? FluidResource.EMPTY : FluidResource.of(tanks[tank]); }
        @Override public long getAmountAsLong(int i)           { return tanks[tank].getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r) { return capacity; }
        @Override public boolean isValid(int i, FluidResource r)        { return input && FluidMixing.canBlend(tanks[tank], r); }

        @Override public int insert(int i, FluidResource resource, int amount, TransactionContext tx) {
            if (!input || resource.isEmpty() || !FluidMixing.canBlend(tanks[tank], resource)) return 0;
            int toFill = Math.min(amount, capacity - tanks[tank].getAmount());
            if (toFill <= 0) return 0;
            updateSnapshots(tx);
            tanks[tank] = FluidNetworkUtil.blendInto(tanks[tank], resource, toFill);
            return toFill;
        }

        @Override public int extract(int i, FluidResource resource, int amount, TransactionContext tx) {
            if (input || tanks[tank].isEmpty() || !resource.matches(tanks[tank])) return 0;
            int toExt = Math.min(amount, tanks[tank].getAmount());
            if (toExt <= 0) return 0;
            updateSnapshots(tx);
            tanks[tank] = tanks[tank].copyWithAmount(tanks[tank].getAmount() - toExt);
            if (tanks[tank].getAmount() <= 0) tanks[tank] = FluidStack.EMPTY;
            return toExt;
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tanks[TANK_A]   = input.read("FluidA",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        tanks[TANK_B]   = input.read("FluidB",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        tanks[TANK_OUT] = input.read("Output",  FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        ratioA = Mth.clamp(input.getIntOr("RatioA", 1), MIN_RATIO, MAX_RATIO);
        ratioB = Mth.clamp(input.getIntOr("RatioB", 1), MIN_RATIO, MAX_RATIO);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("FluidA", FluidStack.OPTIONAL_CODEC, tanks[TANK_A]);
        output.store("FluidB", FluidStack.OPTIONAL_CODEC, tanks[TANK_B]);
        output.store("Output", FluidStack.OPTIONAL_CODEC, tanks[TANK_OUT]);
        output.putInt("RatioA", ratioA);
        output.putInt("RatioB", ratioB);
    }
}
