package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class RamCardItem extends Item {

    public RamCardItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        int capacity = stack.getOrDefault(OmniTechDataComponents.RAM_CAPACITY.get(), 1024);
        tooltip.accept(Component.literal("RAM: " + capacity + " bytes")
                .withStyle(s -> s.withColor(0xFF44FF88)));
        if (flag.isAdvanced()) {
            OmniTechDataComponents.ByteData data = stack.get(OmniTechDataComponents.RAM_DATA.get());
            int used = data != null ? data.data().length : 0;
            if (used > 0) {
                tooltip.accept(Component.literal(used + " bytes stored")
                        .withStyle(s -> s.withColor(0xFF888888)));
            }
        }
    }
}
