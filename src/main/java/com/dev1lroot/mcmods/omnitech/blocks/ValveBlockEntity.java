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
 * <p>Every server tick while LEVEL > 0, the valve equalizes flow between its
 * two port neighbors, limited to {@link ValveBlock#getFlowRate} mb/tick.
 * It finds the neighbor with more fluid and pushes from that side to the other.
 *
 * <p>Because the valve is not a valid node in {@code FluidNetworkUtil.isValidNode()},
 * pipe networks on either side remain isolated and do not equalize through the valve
 * on their own — only this block entity bridges them.
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
        // Context direction = the face of the neighbor that faces this valve.
        ResourceHandler<FluidResource> handlerA = level.getCapability(
                Capabilities.Fluid.BLOCK, pos.relative(portA), portA.getOpposite());
        ResourceHandler<FluidResource> handlerB = level.getCapability(
                Capabilities.Fluid.BLOCK, pos.relative(portB), portB.getOpposite());

        if (handlerA == null || handlerB == null) return;

        // Determine which side has fluid to offer (first non-empty slot).
        FluidResource resA = FluidResource.EMPTY;
        int amountA = 0;
        for (int i = 0; i < handlerA.size(); i++) {
            FluidResource r = handlerA.getResource(i);
            if (!r.isEmpty()) {
                resA = r;
                amountA = Math.min(flowRate, handlerA.getAmountAsInt(i));
                break;
            }
        }

        FluidResource resB = FluidResource.EMPTY;
        int amountB = 0;
        for (int i = 0; i < handlerB.size(); i++) {
            FluidResource r = handlerB.getResource(i);
            if (!r.isEmpty()) {
                resB = r;
                amountB = Math.min(flowRate, handlerB.getAmountAsInt(i));
                break;
            }
        }

        // Pick the side with more fluid to be the source.
        // If both have the same fluid type, push from the fuller side.
        // If they hold different fluids, attempt A→B first.
        ResourceHandler<FluidResource> source;
        ResourceHandler<FluidResource> dest;
        FluidResource resource;
        int available;

        if (!resA.isEmpty() && (resB.isEmpty() || amountA >= amountB)) {
            source    = handlerA;
            dest      = handlerB;
            resource  = resA;
            available = amountA;
        } else if (!resB.isEmpty()) {
            source    = handlerB;
            dest      = handlerA;
            resource  = resB;
            available = amountB;
        } else {
            return; // nothing to transfer
        }

        // Simulate → execute
        int toTransfer;
        try (Transaction simTx = Transaction.openRoot()) {
            int extracted = source.extract(resource, available, simTx);
            if (extracted <= 0) return;
            toTransfer = dest.insert(resource, extracted, simTx);
            // rolled back
        }
        if (toTransfer <= 0) return;

        try (Transaction execTx = Transaction.openRoot()) {
            source.extract(resource, toTransfer, execTx);
            dest.insert(resource, toTransfer, execTx);
            execTx.commit();
        }
    }
}
