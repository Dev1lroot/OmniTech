package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.List;

/**
 * A portable fluid container that holds 0–{@value #CAPACITY} mB of any single fluid.
 *
 * <h3>Data storage</h3>
 * Fluid is stored in the {@link OmniTechDataComponents#FLUID_CONTENTS} component as an
 * immutable {@link ResourceStack}{@code <}{@link FluidResource}{@code >}.
 * The component is absent when the canister holds no fluid.
 *
 * <h3>Item model layers</h3>
 * The item uses a two-layer generated model:
 * <ul>
 *   <li>layer0 ({@code tintindex 0}) – fluid colour overlay; tinted by
 *       {@link com.dev1lroot.mcmods.omnitech.client.FluidCanisterTintSource}
 *       with the contained fluid's ARGB colour.</li>
 *   <li>layer1 ({@code tintindex 1}) – canister frame / outline; no tint.</li>
 * </ul>
 *
 * <h3>Fill indicator</h3>
 * The standard Minecraft item bar (normally used for durability) is repurposed to
 * show fill level: blue, 0–13 px wide, visible whenever the canister is not empty.
 */
public class FluidCanisterItem extends Item {

    public static final int CAPACITY = 1000;

    public FluidCanisterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ── Fluid access helpers ──────────────────────────────────────────────────

    /** Returns the fluid stored in this stack, or {@link FluidStack#EMPTY} if none. */
    public static FluidStack getFluid(ItemStack stack) {
        ResourceStack<FluidResource> stored = stack.get(OmniTechDataComponents.FLUID_CONTENTS.get());
        if (stored == null || stored.isEmpty()) return FluidStack.EMPTY;
        return stored.resource().toStack(stored.amount());
    }

    /** Sets or removes the fluid component. Pass {@link FluidStack#EMPTY} to clear. */
    public static void setFluid(ItemStack stack, FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) {
            stack.remove(OmniTechDataComponents.FLUID_CONTENTS.get());
        } else {
            stack.set(OmniTechDataComponents.FLUID_CONTENTS.get(),
                    new ResourceStack<>(FluidResource.of(fluid), fluid.getAmount()));
        }
    }

    public static int getAmount(ItemStack stack)  { return getFluid(stack).getAmount(); }
    public static boolean isEmpty(ItemStack stack) { return getFluid(stack).isEmpty(); }

    // ── Fill-level bar (repurposed durability bar) ────────────────────────────

    /** Show the bar whenever there is any fluid inside. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getAmount(stack) > 0;
    }

    /**
     * Bar width proportional to fill level: 0–13 px where 13 = full (1000 mB).
     */
    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * getAmount(stack) / CAPACITY);
    }

    /** Blue bar to differentiate from the red durability bar. */
    @Override
    public int getBarColor(ItemStack stack) {
        return 0x2266FF;
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        FluidStack fluid = getFluid(stack);
        if (fluid.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.omnitech.fluid_canister.empty"));
        } else {
            tooltip.add(Component.translatable("tooltip.omnitech.fluid_canister.contents",
                    fluid.getHoverName(),
                    fluid.getAmount(),
                    CAPACITY));
        }
    }
}
