package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Block entity for {@link PumpBlock}.
 *
 * <p>Each server tick while powered by Kinetic Force, attempts to transfer up to
 * {@link #TRANSFER_RATE} mb from the block on the input side
 * ({@code FACING.getOpposite()}) to the block on the output side ({@code FACING}).
 *
 * <p>Uses a simulate-then-execute transaction pair so the transfer is always
 * atomic: nothing is lost if the output is full or contains an incompatible fluid.
 */
public class PumpBlockEntity extends BlockEntity implements IKineticReceiver {

    /** Kinetic ticks before POWERED turns off after the last KF pulse. */
    public static final int POWERED_DECAY_TICKS = 3;
    /** Maximum fluid moved per tick, in millibuckets. */
    public static final int TRANSFER_RATE = 100;

    private int poweredTimer = 0;

    public PumpBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.PUMP.get(), pos, state);
    }

    // ── IKineticReceiver ──────────────────────────────────────────────────────

    @Override
    public boolean addKineticForce(int amount) {
        poweredTimer = POWERED_DECAY_TICKS;
        return true;
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            PumpBlockEntity be) {
        // ── Powered timer decay ────────────────────────────────────────────────
        boolean wasPowered = state.getValue(PumpBlock.POWERED);
        if (be.poweredTimer > 0) be.poweredTimer--;
        boolean isPowered = be.poweredTimer > 0;

        if (wasPowered != isPowered) {
            level.setBlock(pos, state.setValue(PumpBlock.POWERED, isPowered), 3);
            state = level.getBlockState(pos);
        }

        if (!isPowered) return;

        // ── Fluid transfer ─────────────────────────────────────────────────────
        Direction outputDir = state.getValue(PumpBlock.FACING);
        Direction inputDir  = outputDir.getOpposite();

        // Query fluid handlers on each side.
        // The context direction is the face of the NEIGHBOR that faces this pump.
        ResourceHandler<FluidResource> inputHandler  = level.getCapability(
                Capabilities.Fluid.BLOCK, pos.relative(inputDir),  outputDir);
        ResourceHandler<FluidResource> outputHandler = level.getCapability(
                Capabilities.Fluid.BLOCK, pos.relative(outputDir), inputDir);

        if (inputHandler == null || outputHandler == null) return;

        // Find the first non-empty slot in the input handler
        FluidResource available = FluidResource.EMPTY;
        int availableAmount = 0;
        for (int i = 0; i < inputHandler.size(); i++) {
            FluidResource res = inputHandler.getResource(i);
            if (!res.isEmpty()) {
                available = res;
                availableAmount = Math.min(TRANSFER_RATE, inputHandler.getAmountAsInt(i));
                break;
            }
        }
        if (available.isEmpty() || availableAmount <= 0) return;

        // Simulate: determine how much can actually be transferred this tick
        int toTransfer;
        try (Transaction simTx = Transaction.openRoot()) {
            int simExtracted = inputHandler.extract(available, availableAmount, simTx);
            if (simExtracted <= 0) return;
            toTransfer = outputHandler.insert(available, simExtracted, simTx);
            // simTx closes without commit → everything rolled back
        }

        if (toTransfer <= 0) return;

        // Execute: perform the actual transfer
        try (Transaction execTx = Transaction.openRoot()) {
            inputHandler.extract(available, toTransfer, execTx);
            outputHandler.insert(available, toTransfer, execTx);
            execTx.commit();
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        poweredTimer = input.getIntOr("PoweredTimer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("PoweredTimer", poweredTimer);
    }
}
