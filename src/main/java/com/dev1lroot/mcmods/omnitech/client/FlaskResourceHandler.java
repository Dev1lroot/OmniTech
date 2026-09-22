/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.items.FlaskItem;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import com.dev1lroot.mcmods.omnitech.util.SolutionFluids;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Exposes a {@link FlaskItem}'s contents as a {@code ResourceHandler<FluidResource>} for generic
 * external interactions (right-clicking a Fluid Tank, an automated pipe, etc.) and for
 * {@code DynamicFluidContainerModel}, which reads this capability to know what fluid to render.
 *
 * <p>The flask reports <em>all</em> of its contents as one resource: the plain fluid if it holds a
 * single dissolved fluid, otherwise a {@code omnitech:solution} mixture carrying every component's
 * ratio and dissolved flag, plus the flask's temperature and pressure. That is what lets a flask of
 * mixture be emptied into a tank (or any machine that takes fluids) with the composition intact, and
 * be filled from one.
 *
 * <p>Filling keeps the old rule that nothing silently blends: a flask accepts fluid only while it is
 * empty or when the incoming resource is exactly what it already holds. Deliberately mixing
 * different fluids into a flask still happens only through the Fluid Filler's Inject action, the
 * pipette recipes, or sampling a mixture. Glass limits still apply — every component must pass
 * {@link FlaskItem#canHold}.
 */
public final class FlaskResourceHandler extends ItemAccessResourceHandler<FluidResource> {

    public FlaskResourceHandler(ItemAccess access) {
        super(access, 1);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) return true;
        for (Solution.Part part : SolutionFluids.toSolution(resource, 1000).components()) {
            if (!FlaskItem.canHold(part.fluid())) return false;
        }
        return true;
    }

    @Override
    protected FluidResource getResourceFrom(ItemResource accessResource, int index) {
        var stack = FlaskItem.toFluidStack(accessResource.toStack(1));
        return stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(stack);
    }

    @Override
    protected int getAmountFrom(ItemResource accessResource, int index) {
        return FlaskItem.getTotalAmount(accessResource.toStack(1));
    }

    @Override
    protected ItemResource update(ItemResource accessResource, int index,
            FluidResource newResource, int newAmount) {
        var stack = accessResource.toStack(1);
        Solution current = FlaskItem.getSolution(stack);
        int currentTotal = current.totalAmount();

        if (newAmount <= 0 || newResource.isEmpty()) {
            FlaskItem.setSolution(stack, Solution.EMPTY);            // drained completely
        } else if (newAmount < currentTotal) {
            FlaskItem.setSolution(stack, current.scaledTo(newAmount)); // drained partly: same ratios
        } else if (newAmount > currentTotal) {
            // Filled. The base handler only lets this happen into an empty flask or with the very
            // resource already inside, so the incoming stack carries the composition to store.
            var incoming = newResource.toStack(newAmount - currentTotal);
            if (currentTotal == 0) {
                FlaskItem.addFluidStack(stack, incoming);
            } else {
                Solution add = SolutionFluids.toSolution(incoming);
                FlaskItem.setSolution(stack, current.plus(add));
            }
        }
        return ItemResource.of(stack);
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return FlaskItem.CAPACITY;
    }
}
