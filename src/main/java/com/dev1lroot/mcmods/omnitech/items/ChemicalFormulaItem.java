/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.chemistry.IupacNamer;
import com.dev1lroot.mcmods.omnitech.chemistry.Molecule;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A printed structural-formula template — the "paper" a Structure Table's sketch gets baked
 * onto. Its tooltip always shows the molecular formula and name; right-click opens a read-only
 * viewer showing the actual structure diagram.
 */
public class ChemicalFormulaItem extends Item {

    public ChemicalFormulaItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    public static Molecule getMolecule(ItemStack stack) {
        return stack.getOrDefault(OmniTechDataComponents.MOLECULE.get(), Molecule.EMPTY);
    }

    public static void setMolecule(ItemStack stack, Molecule molecule) {
        stack.set(OmniTechDataComponents.MOLECULE.get(), molecule);
    }

    /** The resolved display name — the player's override if set, otherwise the auto-generated one. */
    public static String resolveName(ItemStack stack) {
        String custom = stack.get(OmniTechDataComponents.MOLECULE_NAME.get());
        if (custom != null && !custom.isBlank()) return custom;
        return IupacNamer.name(getMolecule(stack));
    }

    public static void setCustomName(ItemStack stack, String name) {
        if (name == null || name.isBlank()) stack.remove(OmniTechDataComponents.MOLECULE_NAME.get());
        else stack.set(OmniTechDataComponents.MOLECULE_NAME.get(), name);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(resolveName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        Molecule molecule = getMolecule(stack);
        if (molecule.isEmpty()) {
            tooltip.accept(Component.literal("Blank").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.accept(Component.literal(molecule.molecularFormula()).withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.literal("Right-click to view structure").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            openViewer(player.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    private static void openViewer(ItemStack stack) {
        Molecule molecule = getMolecule(stack);
        String name = resolveName(stack);
        net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                new com.dev1lroot.mcmods.omnitech.gui.FormulaViewerScreen(molecule, name));
    }
}
