package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
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
 * <p>Each server tick while powered by Kinetic Force, pulls up to
 * {@link #TRANSFER_RATE} mb from the input side ({@code FACING.getOpposite()})
 * and routes it to the first node in the output network that has available space.
 *
 * <p>The output target is found by BFS through connected pipes and tanks starting
 * from the block directly in front of the pump. This allows fluid to bypass a full
 * adjacent pipe and reach any container further along the network — enabling upward
 * flow, downward gas flow, or any path regardless of gravity.
 *
 * <p>Uses a simulate-then-execute transaction pair so the transfer is atomic.
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
    public boolean addKineticForce(float amount) {
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

        // Query the input side via the NeoForge capability system.
        // The context direction is the face of the neighbour that faces this pump.
        BlockPos inputPos = pos.relative(inputDir);
        ResourceHandler<FluidResource> inputHandler = level.getCapability(
                Capabilities.Fluid.BLOCK, inputPos, outputDir);

        // Determine what fluid is available on the direct input side
        FluidResource available = FluidResource.EMPTY;
        int availableAmount = 0;
        if (inputHandler != null) {
            for (int i = 0; i < inputHandler.size(); i++) {
                FluidResource res = inputHandler.getResource(i);
                if (!res.isEmpty()) {
                    available = res;
                    availableAmount = Math.min(TRANSFER_RATE, inputHandler.getAmountAsInt(i));
                    break;
                }
            }
        }

        // If the direct neighbour is empty, BFS through the entire input-side
        // fluid network to find the closest non-empty container.
        if (available.isEmpty() || availableAmount <= 0) {
            inputHandler = FluidNetworkUtil.findInputSource(level, inputPos, inputDir);
            if (inputHandler == null) return;
            available = FluidResource.EMPTY;
            availableAmount = 0;
            for (int i = 0; i < inputHandler.size(); i++) {
                FluidResource res = inputHandler.getResource(i);
                if (!res.isEmpty()) {
                    available = res;
                    availableAmount = Math.min(TRANSFER_RATE, inputHandler.getAmountAsInt(i));
                    break;
                }
            }
            if (available.isEmpty() || availableAmount <= 0) return;
        }

        // ── Find the output target via BFS ─────────────────────────────────────
        // Walk the output network (pipes + tanks, stopping at other pumps) and
        // return the handler of the first node that has room for this fluid.
        // This lets the pump route past a full adjacent pipe to reach empty space
        // further along, enabling fluid to travel against gravity.
        ResourceHandler<FluidResource> outputHandler =
                FluidNetworkUtil.findOutputTarget(level, pos.relative(outputDir), available);
        if (outputHandler == null) return;

        // ── Simulate then execute ──────────────────────────────────────────────
        int toTransfer;
        try (Transaction simTx = Transaction.openRoot()) {
            int simExtracted = inputHandler.extract(available, availableAmount, simTx);
            if (simExtracted <= 0) return;
            toTransfer = outputHandler.insert(available, simExtracted, simTx);
            // simTx closes without commit → rolled back
        }
        if (toTransfer <= 0) return;

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
