/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * One piece of the Hazmat Suit armour set.
 *
 * <p>Provides no physical defence. When the player wears all four pieces the
 * full set blocks the Radiation mob effect (tiers I and II fully suppressed;
 * tier III downgraded to tier I). Durability is fixed at 20 per piece.
 */
public class HazmatSuitItem extends Item {

    private final ArmorType armorType;

    public HazmatSuitItem(ArmorType type, Properties props) {
        super(props);
        this.armorType = type;
    }

    public ArmorType getArmorType() {
        return armorType;
    }

    /** {@code true} if the player is wearing all four Hazmat Suit pieces. */
    public static boolean isWearingFullSuit(Player player) {
        return isHazmat(player.getItemBySlot(EquipmentSlot.HEAD))
                && isHazmat(player.getItemBySlot(EquipmentSlot.CHEST))
                && isHazmat(player.getItemBySlot(EquipmentSlot.LEGS))
                && isHazmat(player.getItemBySlot(EquipmentSlot.FEET));
    }

    private static boolean isHazmat(ItemStack stack) {
        return stack.getItem() instanceof HazmatSuitItem;
    }
}
