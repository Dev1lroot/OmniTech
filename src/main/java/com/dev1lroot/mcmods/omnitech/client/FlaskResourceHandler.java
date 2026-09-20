/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.items.FlaskItem;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Exposes the fluid stored inside a {@link FlaskItem} as a
 * {@code ResourceHandler<FluidResource>}, rejecting fluids {@link FlaskItem#canHold}
 * disallows (only molten metals / anything solid at ambient conditions).
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
        FluidStack fluid = FlaskItem.getFluid(accessResource.toStack(1));
        return fluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(fluid);
    }

    @Override
    protected int getAmountFrom(ItemResource accessResource, int index) {
        return FlaskItem.getAmount(accessResource.toStack(1));
    }

    @Override
    protected ItemResource update(ItemResource accessResource, int index,
            FluidResource newResource, int newAmount) {
        var stack = accessResource.toStack(1);
        FlaskItem.setFluid(stack,
                newResource.isEmpty() || newAmount <= 0
                        ? FluidStack.EMPTY
                        : newResource.toStack(newAmount));
        return ItemResource.of(stack);
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return FlaskItem.CAPACITY;
    }
}
