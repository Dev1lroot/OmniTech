package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Block entity for {@link ValveBlock}.
 *
 * <p>Every server tick while LEVEL > 0, the valve transfers fluid from the
 * fuller port neighbor to the emptier one, capped at {@link ValveBlock#getFlowRate}
 * mb/tick. Uses a simulate-then-execute transaction pair (same as {@link PumpBlockEntity}).
 *
 * <p>Because the valve is not a valid node in {@code FluidNetworkUtil.isValidNode()},
 * pipe networks on either side remain isolated and do not equalize through the valve
 * on their own — only this block entity bridges them, controlled by LEVEL.
 */
public class ValveBlockEntity extends BlockEntity
{
    public ValveBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.VALVE.get(), pos, state);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ValveBlockEntity be) {

        int flowRate = ValveBlock.getFlowRate(state);
        if (flowRate <= 0) return;

        Direction[] ports = ValveBlock.getPorts(state);
        Direction portA = ports[0];
        Direction portB = ports[1];

        // Query fluid handlers on both port sides.
        // Context = the face of the neighbor that faces this valve.
        ResourceHandler<FluidResource> handlerA = level.getCapability(
                Capabilities.Fluid.BLOCK, pos.relative(portA), portA.getOpposite());
        ResourceHandler<FluidResource> handlerB = level.getCapability(
                Capabilities.Fluid.BLOCK, pos.relative(portB), portB.getOpposite());

        if (handlerA == null || handlerB == null) return;

        // Scan both sides: record both the actual stored amount (for direction comparison)
        // and the transfer-capped amount (for the transaction).
        FluidResource resA = FluidResource.EMPTY;
        int rawA = 0, cappedA = 0;
        for (int i = 0; i < handlerA.size(); i++) {
            FluidResource r = handlerA.getResource(i);
            if (!r.isEmpty()) {
                resA  = r;
                rawA  = handlerA.getAmountAsInt(i);          // actual stored amount
                cappedA = Math.min(flowRate, rawA);           // amount to transfer
                break;
            }
        }

        FluidResource resB = FluidResource.EMPTY;
        int rawB = 0, cappedB = 0;
        for (int i = 0; i < handlerB.size(); i++) {
            FluidResource r = handlerB.getResource(i);
            if (!r.isEmpty()) {
                resB  = r;
                rawB  = handlerB.getAmountAsInt(i);
                cappedB = Math.min(flowRate, rawB);
                break;
            }
        }

        // Pick source→dest: push from the side with MORE actual fluid.
        // Using rawA/rawB (not capped) ensures we pick the truly fuller side
        // even when both exceed flowRate.
        ResourceHandler<FluidResource> source, dest;
        FluidResource resource;
        int available;

        if (!resA.isEmpty() && (resB.isEmpty() || rawA >= rawB)) {
            source    = handlerA;
            dest      = handlerB;
            resource  = resA;
            available = cappedA;
        } else if (!resB.isEmpty()) {
            source    = handlerB;
            dest      = handlerA;
            resource  = resB;
            available = cappedB;
        } else {
            return; // both sides empty
        }

        // ── Simulate → execute ────────────────────────────────────────────────
        int toTransfer;
        try (Transaction simTx = Transaction.openRoot()) {
            int extracted = source.extract(resource, available, simTx);
            if (extracted <= 0) return;
            toTransfer = dest.insert(resource, extracted, simTx);
            // simTx closes without commit → fully rolled back
        }
        if (toTransfer <= 0) return;

        try (Transaction execTx = Transaction.openRoot()) {
            source.extract(resource, toTransfer, execTx);
            dest.insert(resource, toTransfer, execTx);
            execTx.commit();
        }

        // Notify both affected block entities so changes persist and sync to clients.
        // FluidNetworkUtil.syncNetwork() will also handle this on the next pipe tick,
        // but notifying immediately prevents a 1-tick visual delay.
        BlockPos srcPos = (source == handlerA) ? pos.relative(portA) : pos.relative(portB);
        BlockPos dstPos = (source == handlerA) ? pos.relative(portB) : pos.relative(portA);
        notifyPipe(level, srcPos);
        notifyPipe(level, dstPos);
    }

    private static void notifyPipe(Level level, BlockPos pipePos) {
        BlockEntity be = level.getBlockEntity(pipePos);
        if (be == null) return;
        be.setChanged();
        BlockState state = be.getBlockState();
        level.sendBlockUpdated(pipePos, state, state, 3);
    }
}
