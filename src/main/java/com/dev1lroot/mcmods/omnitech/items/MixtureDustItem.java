/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The dry leftovers of a solution: a powder made of every solid ingredient that was suspended in
 * it, in the proportions it was suspended in. The composition is a {@link Mixture} (ratios, so
 * equal powders stack); one item stands for {@value #UNIT} mB of solids. Its colour is the
 * ingredients' colours blended by share — see
 * {@link com.dev1lroot.mcmods.omnitech.client.MixtureDustTintSource}.
 */
public class MixtureDustItem extends Item {

    /** mB of undissolved solids that make one item of dust. */
    public static final int UNIT = 100;

    public MixtureDustItem(Properties properties) {
        super(properties);
    }

    /** One dust item holding {@code solids} in its own ratios (every part is stored as undissolved). */
    public static ItemStack of(Solution solids) {
        ItemStack stack = new ItemStack(OmniTechItems.MIXTURE_DUST.get());
        Solution dry = Solution.EMPTY;
        for (Solution.Part p : solids.components()) dry = dry.plus(p.fluid(), p.amount(), false);
        stack.set(OmniTechDataComponents.MIXTURE.get(), Mixture.of(dry));
        return stack;
    }

    /** The composition of {@code stack}, empty if it carries none. */
    public static Mixture getMixture(ItemStack stack) {
        Mixture m = stack.get(OmniTechDataComponents.MIXTURE.get());
        return m == null ? new Mixture(java.util.List.of()) : m;
    }

    /** The solids {@code stack} stands for: {@value #UNIT} mB per item, in its own ratios. */
    public static Solution solidsOf(ItemStack stack) {
        return getMixture(stack).toSolution(stack.getCount() * UNIT);
    }

    /** The set of ingredients in {@code stack} — two dust piles blend only if these match. */
    private static Set<Fluid> ingredients(ItemStack stack) {
        Set<Fluid> set = new HashSet<>();
        for (Mixture.Entry e : getMixture(stack).entries()) set.add(e.fluid());
        return set;
    }

    /**
     * True if {@code dust} can be put onto {@code slot}: an empty slot, an identical stack, or a pile
     * of mixture dust made of the same ingredients in other ratios (the powders simply blend) —
     * as long as the stack limit allows.
     */
    public static boolean canMerge(ItemStack slot, ItemStack dust) {
        if (slot.isEmpty()) return true;
        if (slot.getCount() + dust.getCount() > slot.getMaxStackSize()) return false;
        if (ItemStack.isSameItemSameComponents(slot, dust)) return true;
        return slot.getItem() instanceof MixtureDustItem && dust.getItem() instanceof MixtureDustItem
                && ingredients(slot).equals(ingredients(dust));
    }

    /** {@code dust} put onto {@code slot} (see {@link #canMerge}); blended piles share one ratio. */
    public static ItemStack merge(ItemStack slot, ItemStack dust) {
        if (slot.isEmpty()) return dust.copy();
        if (ItemStack.isSameItemSameComponents(slot, dust)) return slot.copyWithCount(slot.getCount() + dust.getCount());
        return of(solidsOf(slot).plus(solidsOf(dust))).copyWithCount(slot.getCount() + dust.getCount());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        Mixture mixture = getMixture(stack);
        if (mixture.entries().isEmpty()) {
            tooltip.accept(Component.literal("Plain dust").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (Mixture.Entry e : mixture.entries()) {
            Component name = FluidResource.of(e.fluid()).toStack(1).getHoverName();
            String share = String.format("%.1f%%", e.ppm() * 100.0 / Mixture.TOTAL);
            tooltip.accept(Component.literal(share + " ").withStyle(ChatFormatting.GRAY).append(name));
        }
    }
}
