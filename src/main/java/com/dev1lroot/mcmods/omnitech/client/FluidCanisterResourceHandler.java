package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.items.FluidCanisterItem;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Exposes the fluid stored inside a {@link FluidCanisterItem} as a
 * {@code ResourceHandler<FluidResource>} so that the NeoForge capability system
 * (and therefore {@code FluidUtil.getFirstStackContained}) can read and write it.
 *
 * <p>This is the bridge that allows {@link FluidCanisterItemModel} (which wraps
 * {@code DynamicFluidContainerModel}) to discover which fluid is in the canister
 * at render time.
 */
public final class FluidCanisterResourceHandler extends ItemAccessResourceHandler<FluidResource> {

    public FluidCanisterResourceHandler(ItemAccess access) {
        super(access, 1);
    }

    @Override
    protected FluidResource getResourceFrom(ItemResource accessResource, int index) {
        FluidStack fluid = FluidCanisterItem.getFluid(accessResource.toStack(1));
        return fluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(fluid);
    }

    @Override
    protected int getAmountFrom(ItemResource accessResource, int index) {
        return FluidCanisterItem.getAmount(accessResource.toStack(1));
    }

    @Override
    protected ItemResource update(ItemResource accessResource, int index,
            FluidResource newResource, int newAmount) {
        var stack = accessResource.toStack(1);
        FluidCanisterItem.setFluid(stack,
                newResource.isEmpty() || newAmount <= 0
                        ? FluidStack.EMPTY
                        : newResource.toStack(newAmount));
        return ItemResource.of(stack);
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return FluidCanisterItem.CAPACITY;
    }
}
