package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorCellType;
import net.minecraft.world.item.ItemStack;

public class ReactorControlRodItem extends ReactorRodItem {

    public ReactorControlRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public ReactorCellType getCellType() { return ReactorCellType.CONTROL; }

    // ── Control attribute (0–100, percent insertion) ──────────────────────────

    public static int getControl(ItemStack stack) {
        Integer v = stack.get(OmniTechDataComponents.ROD_CONTROL.get());
        return v != null ? v : 0;
    }

    public static void setControl(ItemStack stack, int percent) {
        stack.set(OmniTechDataComponents.ROD_CONTROL.get(),
                  Math.max(0, Math.min(100, percent)));
    }
}
