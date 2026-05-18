/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * One piece of the Space Suit armour set.
 *
 * <p>Stored as a plain {@link Item} with {@code humanoidArmor()} properties
 * (the recommended pattern in MC 26.1).  Helper methods check whether the
 * player is wearing the full set or just the helmet, which drives the HUD
 * overlay display.
 */
public class SpaceSuitItem extends Item {

    private final ArmorType armorType;

    public SpaceSuitItem(ArmorType type, Properties props) {
        super(props);
        this.armorType = type;
    }

    public ArmorType getArmorType() {
        return armorType;
    }

    // ── Static helpers used by the HUD overlay ────────────────────────────────

    /** Returns {@code true} if the player has a Space Suit helmet equipped. */
    public static boolean isWearingHelmet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SpaceSuitItem;
    }

    /** Returns {@code true} if the player is wearing all four Space Suit pieces. */
    public static boolean isWearingFullSuit(Player player) {
        return isSpaceSuit(player.getItemBySlot(EquipmentSlot.HEAD))
                && isSpaceSuit(player.getItemBySlot(EquipmentSlot.CHEST))
                && isSpaceSuit(player.getItemBySlot(EquipmentSlot.LEGS))
                && isSpaceSuit(player.getItemBySlot(EquipmentSlot.FEET));
    }

    private static boolean isSpaceSuit(ItemStack stack) {
        return stack.getItem() instanceof SpaceSuitItem;
    }
}
