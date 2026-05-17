package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorCellType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public abstract class ReactorRodItem extends Item {

    public ReactorRodItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public abstract ReactorCellType getCellType();

    // ── Data component helpers ────────────────────────────────────────────────

    public static int getTemperature(ItemStack stack) {
        Integer t = stack.get(OmniTechDataComponents.ROD_TEMPERATURE.get());
        return t != null ? t : -1;
    }

    public static void setTemperature(ItemStack stack, int celsius) {
        stack.set(OmniTechDataComponents.ROD_TEMPERATURE.get(), celsius);
    }

    /** True when the rod is too hot to eject (≥ 100 °C). */
    public static boolean isHot(ItemStack stack) {
        Integer t = stack.get(OmniTechDataComponents.ROD_TEMPERATURE.get());
        return t != null && t >= 100;
    }

    /** Strip all reactor-only tags so the item is safe to carry in inventory. */
    public static void removeReactorTags(ItemStack stack) {
        stack.remove(OmniTechDataComponents.ROD_TEMPERATURE.get());
        stack.remove(OmniTechDataComponents.ROD_CONTROL.get());
    }
}
