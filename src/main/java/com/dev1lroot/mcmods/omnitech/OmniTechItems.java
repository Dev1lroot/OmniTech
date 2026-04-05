package com.dev1lroot.mcmods.omnitech;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OmniTechItems
{
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(OmniTech.MODID);

    // BlockItems of Mechanisms
    public static final DeferredItem<BlockItem> ALLOY_FURNACE_ITEM = REGISTRY.registerSimpleBlockItem(
            "alloy_furnace", OmniTechBlocks.ALLOY_FURNACE);

    public static final DeferredItem<BlockItem> MANUAL_MACERATOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "manual_macerator", OmniTechBlocks.MANUAL_MACERATOR);

    public static final DeferredItem<BlockItem> MANUAL_CENTRIFUGE_ITEM = REGISTRY.registerSimpleBlockItem(
            "manual_centrifuge", OmniTechBlocks.MANUAL_CENTRIFUGE);

    public static final DeferredItem<BlockItem> CRANK_ITEM = REGISTRY.registerSimpleBlockItem(
            "crank", OmniTechBlocks.CRANK);

    public static final DeferredItem<BlockItem> KF_GENERATOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "kf_generator", OmniTechBlocks.KF_GENERATOR);

    public static final DeferredItem<BlockItem> KF_PIPE_ITEM = REGISTRY.registerSimpleBlockItem(
            "kf_pipe", OmniTechBlocks.KF_PIPE);

    public static final DeferredItem<BlockItem> KF_REDUCTOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "kf_reductor", OmniTechBlocks.KF_REDUCTOR);

    public static final DeferredItem<BlockItem> HEATER_ITEM = REGISTRY.registerSimpleBlockItem(
            "heater", OmniTechBlocks.HEATER);

    public static final DeferredItem<BlockItem> STIRLING_ENGINE_ITEM = REGISTRY.registerSimpleBlockItem(
            "stirling_engine", OmniTechBlocks.STIRLING_ENGINE);

    public static final DeferredItem<BlockItem> CONVEYOR_BELT_ITEM = REGISTRY.registerSimpleBlockItem(
            "conveyor_belt", OmniTechBlocks.CONVEYOR_BELT);

    public static final DeferredItem<BlockItem> FLUID_PIPE_ITEM = REGISTRY.registerSimpleBlockItem(
            "fluid_pipe", OmniTechBlocks.FLUID_PIPE);

    public static final DeferredItem<BlockItem> PUMP_ITEM = REGISTRY.registerSimpleBlockItem(
            "pump", OmniTechBlocks.PUMP);

    public static final DeferredItem<BlockItem> SORTER_ITEM = REGISTRY.registerSimpleBlockItem(
            "sorter", OmniTechBlocks.SORTER);

    public static final DeferredItem<BlockItem> FLUID_TANK_ITEM = REGISTRY.registerSimpleBlockItem(
            "fluid_tank", OmniTechBlocks.FLUID_TANK);

    public static final DeferredItem<BlockItem> BOILER = REGISTRY.registerSimpleBlockItem(
            "boiler", OmniTechBlocks.BOILER);

    public static final DeferredItem<BlockItem> SMELTER_ITEM = REGISTRY.registerSimpleBlockItem(
            "smelter", OmniTechBlocks.SMELTER);

    public static final DeferredItem<BlockItem> FOUNDRY_ITEM = REGISTRY.registerSimpleBlockItem(
            "foundry", OmniTechBlocks.FOUNDRY);

    public static final DeferredItem<Item> BRASS_COG = REGISTRY.registerSimpleItem(
            "brass_cog", p -> p);

    // Real Items
    public static final DeferredItem<Item> STEEL_INGOT = REGISTRY.registerSimpleItem(
            "steel_ingot", p -> p);


    // Vanilla Material Additions
    public static final DeferredItem<Item> GRANITE_DUST = REGISTRY.registerSimpleItem(
            "granite_dust", p -> p);

    public static final DeferredItem<Item> ANDESITE_DUST = REGISTRY.registerSimpleItem(
            "andesite_dust", p -> p);

    public static final DeferredItem<Item> DIORITE_DUST = REGISTRY.registerSimpleItem(
            "diorite_dust", p -> p);

    public static final DeferredItem<Item> STONE_DUST = REGISTRY.registerSimpleItem(
            "stone_dust", p -> p);

    public static final DeferredItem<Item> HEMATITE_DUST = REGISTRY.registerSimpleItem(
            "hematite_dust", p -> p);

    public static final DeferredItem<Item> DEEPSLATE_DUST = REGISTRY.registerSimpleItem(
            "deepslate_dust", p -> p);

    public static final DeferredItem<Item> WOODEN_COG = REGISTRY.registerSimpleItem(
            "wooden_cog", p -> p);

    public static final DeferredItem<Item> WOODEN_REDUCTOR = REGISTRY.registerSimpleItem(
            "wooden_reductor", p -> p);

    public static final DeferredItem<Item> COG_TEMPLATE = REGISTRY.registerSimpleItem(
            "cog_template", p -> p);

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
