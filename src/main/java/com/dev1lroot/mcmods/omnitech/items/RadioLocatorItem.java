package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioConstants;
import com.dev1lroot.mcmods.omnitech.gui.RadioLocatorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class RadioLocatorItem extends Item {

    /** Maximum range for signal detection (blocks). */
    public static final double MAX_RANGE = 200.0;

    public RadioLocatorItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            openScreen(player.getItemInHand(hand), hand);
        }
        return InteractionResult.SUCCESS;
    }

    private static void openScreen(ItemStack stack, InteractionHand hand) {
        int freq = stack.getOrDefault(OmniTechDataComponents.RADIO_LOCATOR_FREQ.get(),
                RadioConstants.FREQ_MIN_X10);
        Minecraft.getInstance().setScreen(new RadioLocatorScreen(freq, hand));
    }
}
