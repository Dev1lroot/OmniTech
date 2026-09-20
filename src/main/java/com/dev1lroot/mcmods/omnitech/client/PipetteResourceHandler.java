/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.items.FlaskItem;
import com.dev1lroot.mcmods.omnitech.items.PipetteItem;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

/**
 * Exposes a {@link PipetteItem}'s contents as a {@code ResourceHandler<FluidResource>}, purely
 * so {@code DynamicFluidContainerModel} (see {@link PipetteItemModel}) has something to read the
 * current fluid + tint from when rendering. The pipette's actual fill/pour behaviour (tank
 * right-click, crafting-table recipes) goes through {@link PipetteItem} directly, not this
 * handler — it's a read-mostly reporting surface, not a real transfer path.
 */
public final class PipetteResourceHandler extends ItemAccessResourceHandler<FluidResource> {

    public PipetteResourceHandler(ItemAccess access) {
        super(access, 1);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return resource.isEmpty() || FlaskItem.canHold(resource.toStack(1).getFluid());
    }

    @Override
    protected FluidResource getResourceFrom(ItemResource accessResource, int index) {
        Solution solution = PipetteItem.getSolution(accessResource.toStack(1));
        ResourceStack<FluidResource> dominant = solution.dominant();
        return dominant == null ? FluidResource.EMPTY : dominant.resource();
    }

    @Override
    protected int getAmountFrom(ItemResource accessResource, int index) {
        return PipetteItem.getTotalAmount(accessResource.toStack(1));
    }

    @Override
    protected ItemResource update(ItemResource accessResource, int index,
            FluidResource newResource, int newAmount) {
        // Not a real transfer path — see class doc. Fill/pour always goes through PipetteItem
        // directly (tank right-click, crafting recipes), so this never mutates the stack.
        return accessResource;
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return PipetteItem.MAX_AMOUNT;
    }
}
