package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class MicrocontrollerItem extends Item {

    public MicrocontrollerItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        int hz = stack.getOrDefault(OmniTechDataComponents.MCU_SPEED.get(), 20);
        tooltip.accept(Component.literal("Speed: " + hz + " Hz  |  RISC-V RV32GC")
                .withStyle(s -> s.withColor(0xFF4488FF)));
        java.util.UUID binId = stack.get(OmniTechDataComponents.PROGRAM_BINARY.get());
        if (binId != null) {
            tooltip.accept(Component.literal("Binary loaded")
                    .withStyle(s -> s.withColor(0xFF44FF44)));
        } else {
            String prog = stack.get(OmniTechDataComponents.PROGRAM.get());
            if (prog != null && !prog.isBlank()) {
                tooltip.accept(Component.literal("Source loaded — not yet assembled")
                        .withStyle(s -> s.withColor(0xFFFFAA44)));
            } else {
                tooltip.accept(Component.literal("No program").withStyle(s -> s.withColor(0xFF888888)));
            }
        }
    }
}
