package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechItems;
import com.dev1lroot.mcmods.omnitech.ResearchLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class BlueprintItem extends Item {

    public BlueprintItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        String researchId = stack.get(OmniTechDataComponents.RESEARCH_NAME.get());
        if (researchId != null) {
            String displayName = ResearchLoader.find(researchId)
                    .map(ResearchLoader.ResearchDefinition::displayName)
                    .orElse(researchId);
            tooltip.accept(Component.literal(displayName).withStyle(ChatFormatting.AQUA));
            tooltip.accept(Component.literal("Research Blueprint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Creates a blueprint ItemStack tagged with the given research id. */
    public static ItemStack ofResearch(String researchId) {
        ItemStack stack = new ItemStack(OmniTechItems.BLUEPRINT.get());
        stack.set(OmniTechDataComponents.RESEARCH_NAME.get(), researchId);
        return stack;
    }
}
