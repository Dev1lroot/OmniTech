package com.dev1lroot.mcmods.omnitech.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class LogicGateTemplateItem extends Item {

    private final String gateId;

    public LogicGateTemplateItem(String gateId, Properties props) {
        super(props);
        this.gateId = gateId;
    }

    public String getGateId() {
        return gateId;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.omnitech.gate_template_" + gateId + ".desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.omnitech.gate_template.tooltip.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
