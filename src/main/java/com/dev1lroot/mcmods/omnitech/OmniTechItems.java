package com.dev1lroot.mcmods.omnitech;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OmniTechItems {
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(OmniTech.MODID);

    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = REGISTRY.registerSimpleBlockItem(
            "example_block", OmniTechBlocks.EXAMPLE_BLOCK);

    public static final DeferredItem<Item> EXAMPLE_ITEM = REGISTRY.registerSimpleItem(
            "example_item",
            p -> p.food(new FoodProperties.Builder()
                    .alwaysEdible()
                    .nutrition(1)
                    .saturationModifier(2f)
                    .build()));

    public static final DeferredItem<BlockItem> ALLOY_FURNACE_ITEM = REGISTRY.registerSimpleBlockItem(
            "alloy_furnace", OmniTechBlocks.ALLOY_FURNACE);

    public static final DeferredItem<Item> STEEL_INGOT = REGISTRY.registerSimpleItem(
            "steel_ingot", p -> p);

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
