/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.util.ElectricUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Rechargeable battery pack — plugs into a {@link BoreItem} and powers it. The charge lives
 * on the battery ({@link OmniTechDataComponents#EU_STORED}), so a pack keeps its charge when
 * swapped between bores or charged on its own in the Electric Charger.
 *
 * <p>Registered from {@code data/omnitech/battery/*.json} by
 * {@link com.dev1lroot.mcmods.omnitech.BoreLoader}. Packs are wired to the nearest whole
 * number of cells in series for {@link #PACK_TARGET_VOLTAGE}.
 */
public class BatteryItem extends Item implements ChargeableItem {

    /** Nominal voltage the cells are stacked towards. */
    public static final float PACK_TARGET_VOLTAGE = 48f;

    public final String chemistry;
    public final String anode;
    public final String cathode;
    public final float cellVoltage;
    public final int cells;
    /** Pack capacity, kJ. */
    public final int capacityKj;

    public BatteryItem(String chemistry, String anode, String cathode, float cellVoltage,
            int capacityKj, Properties properties) {
        super(properties.stacksTo(1));
        this.chemistry = chemistry;
        this.anode = anode;
        this.cathode = cathode;
        this.cellVoltage = cellVoltage;
        this.cells = Math.max(1, Math.round(PACK_TARGET_VOLTAGE / cellVoltage));
        this.capacityKj = capacityKj;
    }

    public float packVoltage() { return cells * cellVoltage; }

    // ── ChargeableItem ────────────────────────────────────────────────────────

    @Override
    public int getEnergy(ItemStack stack) {
        return stack.getOrDefault(OmniTechDataComponents.EU_STORED.get(), 0);
    }

    @Override
    public int getCapacity(ItemStack stack) { return capacityKj; }

    @Override
    public void setEnergy(ItemStack stack, int kj) {
        int v = Math.min(kj, capacityKj);
        if (v <= 0) stack.remove(OmniTechDataComponents.EU_STORED.get());
        else stack.set(OmniTechDataComponents.EU_STORED.get(), v);
    }

    // ── Charge bar ────────────────────────────────────────────────────────────

    @Override public boolean isBarVisible(ItemStack stack) { return true; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * getEnergy(stack) / capacityKj);
    }

    @Override public int getBarColor(ItemStack stack) { return 0x44AAFF; }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        int eu = getEnergy(stack);
        tooltip.accept(Component.literal(ElectricUnits.formatEnergy(eu) + " / "
                        + ElectricUnits.formatEnergy(capacityKj)
                        + " (" + Math.round(eu * 100f / capacityKj) + "%)")
                .withStyle(ChatFormatting.GRAY));
        appendPackInfo(tooltip);
    }

    /** Chemistry and electrical ratings — shared with the bore tooltip. */
    public void appendPackInfo(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.omnitech.battery.electrodes", anode, cathode)
                .withStyle(ChatFormatting.DARK_GRAY));
        // Charge Q = E / U, shown in amp-hours (1 Ah = 3600 C)
        double ampHours = ElectricUnits.toJoules(capacityKj) / packVoltage() / 3600.0;
        tooltip.accept(Component.translatable("tooltip.omnitech.battery.pack",
                        cells, ElectricUnits.formatVoltage(cellVoltage),
                        ElectricUnits.formatVoltage(packVoltage()),
                        ElectricUnits.formatSi(ampHours, "Ah"), ElectricUnits.formatKwh(capacityKj))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
