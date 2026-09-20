/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.items.FlaskItem;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

/**
 * Exposes a {@link FlaskItem}'s contents as a {@code ResourceHandler<FluidResource>} for
 * generic external interactions (right-clicking a Fluid Tank, an automated pipe, etc.) and
 * for {@code DynamicFluidContainerModel}, which reads this capability to know what fluid to
 * render.
 *
 * <p>This capability path only ever reports/accepts a <em>single</em> fluid — it's built to
 * mirror {@code FluidCanisterItem}'s old single-fluid auto-fill behaviour so an external pipe
 * can't silently start mixing fluids nobody asked to mix. It fills an empty flask, or tops up
 * one already holding just that same fluid; it refuses anything that would introduce a second
 * fluid. Deliberately blending several fluids into a real {@link Solution} only happens through
 * the Fluid Filler's manual amount-select + Inject action, or a filled {@link
 * com.dev1lroot.mcmods.omnitech.items.PipetteItem} combined with the flask on a crafting table —
 * neither of which goes through this handler.
 *
 * <p>Extraction is always rejected — flasks can't be drained this way (for now).
 */
public final class FlaskResourceHandler extends ItemAccessResourceHandler<FluidResource> {

    public FlaskResourceHandler(ItemAccess access) {
        super(access, 1);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return resource.isEmpty() || FlaskItem.canHold(resource.toStack(1).getFluid());
    }

    @Override
    protected FluidResource getResourceFrom(ItemResource accessResource, int index) {
        Solution solution = FlaskItem.getSolution(accessResource.toStack(1));
        ResourceStack<FluidResource> dominant = solution.dominant();
        return dominant == null ? FluidResource.EMPTY : dominant.resource();
    }

    @Override
    protected int getAmountFrom(ItemResource accessResource, int index) {
        return FlaskItem.getTotalAmount(accessResource.toStack(1));
    }

    @Override
    protected ItemResource update(ItemResource accessResource, int index,
            FluidResource newResource, int newAmount) {
        var stack = accessResource.toStack(1);
        Solution solution = FlaskItem.getSolution(stack);
        int current = solution.totalAmount();
        int delta = newAmount - current;

        boolean singleFluidOrEmpty = solution.isEmpty()
                || (solution.components().size() == 1 && solution.components().get(0).resource().equals(newResource));

        if (delta > 0 && !newResource.isEmpty() && singleFluidOrEmpty) {
            FlaskItem.addFluid(stack, newResource.toStack(1).getFluid(), delta);
        }
        // delta <= 0 (extraction) or a foreign fluid on top of an existing one: no-op.
        return ItemResource.of(stack);
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return FlaskItem.CAPACITY;
    }
}
