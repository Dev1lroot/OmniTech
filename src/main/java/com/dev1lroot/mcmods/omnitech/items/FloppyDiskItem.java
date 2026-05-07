package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class FloppyDiskItem extends Item {

    public static final int CAPACITY    = 1_474_560; // 1.44 MB
    public static final int SECTOR_SIZE = 512;
    public static final int SECTOR_COUNT = CAPACITY / SECTOR_SIZE; // 2880

    public static final int DEFAULT_COLOR = 0x222222;

    public FloppyDiskItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        boolean hasData = stack.get(OmniTechDataComponents.FLOPPY_DATA.get()) != null;
        tooltip.accept(Component.literal(hasData ? "Data present" : "Empty")
                .withStyle(s -> s.withColor(0xFF88AAFF)));
//        if (flag.isAdvanced()) {
//            int color = DyedItemColor.getOrDefault(stack, DEFAULT_COLOR);
//            tooltip.accept(Component.literal(String.format("Color: #%06X", color & 0xFFFFFF))
//                    .withStyle(s -> s.withColor(0xFFAAAAAA)));
//        }
    }
}