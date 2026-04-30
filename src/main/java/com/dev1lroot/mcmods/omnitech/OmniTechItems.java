package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.items.BoreItem;
import com.dev1lroot.mcmods.omnitech.items.FluidCanisterItem;
import com.dev1lroot.mcmods.omnitech.items.RadioLocatorItem;
import com.dev1lroot.mcmods.omnitech.items.SpaceSuitItem;
import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OmniTechItems
{
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(OmniTech.MODID);

    // Europa terrain block items
    public static final DeferredItem<BlockItem> EUROPA_STONE_ITEM = REGISTRY.registerSimpleBlockItem(
            "europa_stone", OmniTechBlocks.EUROPA_STONE);
    public static final DeferredItem<BlockItem> EUROPA_ICE_ITEM = REGISTRY.registerSimpleBlockItem(
            "europa_ice", OmniTechBlocks.EUROPA_ICE);
    public static final DeferredItem<BlockItem> CRACKED_ICE_ITEM = REGISTRY.registerSimpleBlockItem(
            "cracked_ice", OmniTechBlocks.CRACKED_ICE);

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

    public static final DeferredItem<BlockItem> VALVE_ITEM = REGISTRY.registerSimpleBlockItem(
            "valve", OmniTechBlocks.VALVE);

    public static final DeferredItem<BlockItem> SMELTER_ITEM = REGISTRY.registerSimpleBlockItem(
            "smelter", OmniTechBlocks.SMELTER);

    public static final DeferredItem<BlockItem> FOUNDRY_ITEM = REGISTRY.registerSimpleBlockItem(
            "foundry", OmniTechBlocks.FOUNDRY);

    public static final DeferredItem<BlockItem> ELECTRIC_ENGINE_ITEM = REGISTRY.registerSimpleBlockItem(
            "electric_engine", OmniTechBlocks.ELECTRIC_ENGINE);
    public static final DeferredItem<BlockItem> ELECTRIC_CAPACITOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "electric_capacitor", OmniTechBlocks.ELECTRIC_CAPACITOR);
    public static final DeferredItem<BlockItem> ELECTRIC_WIRE_ITEM = REGISTRY.registerSimpleBlockItem(
            "electric_wire", OmniTechBlocks.ELECTRIC_WIRE);
    public static final DeferredItem<BlockItem> ELECTRIC_FURNACE_ITEM = REGISTRY.registerSimpleBlockItem(
            "electric_furnace", OmniTechBlocks.ELECTRIC_FURNACE);
    public static final DeferredItem<BlockItem> SOLAR_PANEL_ITEM = REGISTRY.registerSimpleBlockItem(
            "solar_panel", OmniTechBlocks.SOLAR_PANEL);

    public static final DeferredItem<BlockItem> SOLVATION_MACHINE_ITEM = REGISTRY.registerSimpleBlockItem(
            "solvation_machine", OmniTechBlocks.SOLVATION_MACHINE);

    public static final DeferredItem<BlockItem> ELECTROLYSIS_MACHINE_ITEM = REGISTRY.registerSimpleBlockItem(
            "electrolysis_machine", OmniTechBlocks.ELECTROLYSIS_MACHINE);

    public static final DeferredItem<BlockItem> ROTARY_COMPRESSOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "rotary_compressor", OmniTechBlocks.ROTARY_COMPRESSOR);

    public static final DeferredItem<BlockItem> FLUID_COLLECTOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "fluid_collector", OmniTechBlocks.FLUID_COLLECTOR);

    public static final DeferredItem<BlockItem> HEAT_EXCHANGER_ITEM = REGISTRY.registerSimpleBlockItem(
            "heat_exchanger", OmniTechBlocks.HEAT_EXCHANGER);

    public static final DeferredItem<BlockItem> DECOMPRESSOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "decompressor", OmniTechBlocks.DECOMPRESSOR);

    public static final DeferredItem<BlockItem> FRACTIONAL_DISTILLER_ITEM = REGISTRY.registerSimpleBlockItem(
            "fractional_distiller", OmniTechBlocks.FRACTIONAL_DISTILLER);

    public static final DeferredItem<BlockItem> CHEMICAL_REACTOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "chemical_reactor", OmniTechBlocks.CHEMICAL_REACTOR);

    public static final DeferredItem<BlockItem> FLUID_FILLER_ITEM = REGISTRY.registerSimpleBlockItem(
            "fluid_filler", OmniTechBlocks.FLUID_FILLER);

    public static final DeferredItem<FluidCanisterItem> FLUID_CANISTER =
            REGISTRY.registerItem("fluid_canister", FluidCanisterItem::new);

    // ── Electric Charger block item ───────────────────────────────────────────

    public static final DeferredItem<BlockItem> ELECTRIC_CHARGER_ITEM =
            REGISTRY.registerSimpleBlockItem("electric_charger", OmniTechBlocks.ELECTRIC_CHARGER);

    public static final DeferredItem<BlockItem> COKE_BRICK_ITEM =
            REGISTRY.registerSimpleBlockItem("coke_brick", OmniTechBlocks.COKE_BRICK);

    public static final DeferredItem<BlockItem> CHEMICAL_INFUSER_ITEM =
            REGISTRY.registerSimpleBlockItem("chemical_infuser", OmniTechBlocks.CHEMICAL_INFUSER);

    public static final DeferredItem<BlockItem> EXTRACTOR_ITEM =
            REGISTRY.registerSimpleBlockItem("extractor", OmniTechBlocks.EXTRACTOR);

    public static final DeferredItem<BlockItem> RADIATOR_ITEM =
            REGISTRY.registerSimpleBlockItem("radiator", OmniTechBlocks.RADIATOR);

    public static final DeferredItem<BlockItem> THERMAL_CONDUCTOR_ITEM =
            REGISTRY.registerSimpleBlockItem("thermal_conductor", OmniTechBlocks.THERMAL_CONDUCTOR);

    public static final DeferredItem<BlockItem> ELECTRIC_HEATER_ITEM =
            REGISTRY.registerSimpleBlockItem("electric_heater", OmniTechBlocks.ELECTRIC_HEATER);

    public static final DeferredItem<BlockItem> RADIO_TRANSMITTER_ITEM =
            REGISTRY.registerSimpleBlockItem("radio_transmitter", OmniTechBlocks.RADIO_TRANSMITTER);

    public static final DeferredItem<BlockItem> RADIO_RECEIVER_ITEM =
            REGISTRY.registerSimpleBlockItem("radio_receiver", OmniTechBlocks.RADIO_RECEIVER);

    public static final DeferredItem<BlockItem> RADIO_SCANNER_ITEM =
            REGISTRY.registerSimpleBlockItem("radio_scanner", OmniTechBlocks.RADIO_SCANNER);

    public static final DeferredItem<BlockItem> ANALOG_CABLE_ITEM =
            REGISTRY.registerSimpleBlockItem("analog_cable", OmniTechBlocks.ANALOG_CABLE);

    public static final DeferredItem<BlockItem> MICROPHONE_ITEM =
            REGISTRY.registerSimpleBlockItem("microphone", OmniTechBlocks.MICROPHONE);

    public static final DeferredItem<BlockItem> SPEAKER_ITEM =
            REGISTRY.registerSimpleBlockItem("speaker", OmniTechBlocks.SPEAKER);

    public static final DeferredItem<RadioLocatorItem> RADIO_LOCATOR =
            REGISTRY.registerItem("radio_locator", RadioLocatorItem::new);

    // ── Bore Tools ────────────────────────────────────────────────────────────

    private static final TagKey<Block> INCORRECT_FOR_INDUSTRIAL_BORE =
            BlockTags.create(Identifier.fromNamespaceAndPath(OmniTech.MODID, "incorrect_for_industrial_bore"));

    private static final ToolMaterial BASIC_BORE_MATERIAL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_IRON_TOOL, 10000, 8.0f, 1.0f, 14,
            ItemTags.create(Identifier.fromNamespaceAndPath(OmniTech.MODID, "repairs_bore")));

    private static final ToolMaterial ADVANCED_BORE_MATERIAL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 10000, 12.0f, 1.0f, 14,
            ItemTags.create(Identifier.fromNamespaceAndPath(OmniTech.MODID, "repairs_bore")));

    private static final ToolMaterial INDUSTRIAL_BORE_MATERIAL = new ToolMaterial(
            INCORRECT_FOR_INDUSTRIAL_BORE, 10000, 18.0f, 1.0f, 14,
            ItemTags.create(Identifier.fromNamespaceAndPath(OmniTech.MODID, "repairs_bore")));

    /** Iron-tier bore: 2000 EU capacity, 5 EU/block, 8× speed. */
    public static final DeferredItem<BoreItem> BASIC_BORE =
            REGISTRY.registerItem("basic_bore",
                    props -> new BoreItem(BASIC_BORE_MATERIAL, 2000, 5, props));

    /** Diamond-tier bore: 10 000 EU capacity, 3 EU/block, 12× speed. */
    public static final DeferredItem<BoreItem> ADVANCED_BORE =
            REGISTRY.registerItem("advanced_bore",
                    props -> new BoreItem(ADVANCED_BORE_MATERIAL, 10000, 3, props));

    /** Ultimate-tier bore: 50 000 EU capacity, 2 EU/block, 18× speed. */
    public static final DeferredItem<BoreItem> INDUSTRIAL_BORE =
            REGISTRY.registerItem("industrial_bore",
                    props -> new BoreItem(INDUSTRIAL_BORE_MATERIAL, 50000, 2, props));

    // Vanilla Material Additions and templates are registered via ItemLoader
    // from data/omnitech/item/*.json — see ItemLoader.loadAll()

    // ── Space Suit ─────────────────────────────────────────────────────────────

    private static final ResourceKey<EquipmentAsset> SPACE_SUIT_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID,
                    Identifier.fromNamespaceAndPath(OmniTech.MODID, "space_suit"));

    /**
     * Space Suit armour material — iron-tier protection, designed for use in
     * vacuum / extreme-temperature environments.  Repaired with iron ingots.
     */
    public static final ArmorMaterial SPACE_SUIT_MATERIAL = new ArmorMaterial(
            30,   // durability multiplier
            Maps.newEnumMap(Map.of(
                    ArmorType.BOOTS,      2,
                    ArmorType.LEGGINGS,   5,
                    ArmorType.CHESTPLATE, 6,
                    ArmorType.HELMET,     3,
                    ArmorType.BODY,       5
            )),
            9,    // enchantability (iron-tier)
            SoundEvents.ARMOR_EQUIP_IRON,
            0.0f, // toughness
            0.0f, // knockback resistance
            ItemTags.REPAIRS_IRON_ARMOR,
            SPACE_SUIT_ASSET
    );

    public static final DeferredItem<SpaceSuitItem> SPACE_SUIT_HELMET =
            REGISTRY.registerItem("space_suit_helmet",
                    props -> new SpaceSuitItem(ArmorType.HELMET, props),
                    props -> props.humanoidArmor(SPACE_SUIT_MATERIAL, ArmorType.HELMET));

    public static final DeferredItem<SpaceSuitItem> SPACE_SUIT_CHESTPLATE =
            REGISTRY.registerItem("space_suit_chestplate",
                    props -> new SpaceSuitItem(ArmorType.CHESTPLATE, props),
                    props -> props.humanoidArmor(SPACE_SUIT_MATERIAL, ArmorType.CHESTPLATE));

    public static final DeferredItem<SpaceSuitItem> SPACE_SUIT_LEGGINGS =
            REGISTRY.registerItem("space_suit_leggings",
                    props -> new SpaceSuitItem(ArmorType.LEGGINGS, props),
                    props -> props.humanoidArmor(SPACE_SUIT_MATERIAL, ArmorType.LEGGINGS));

    public static final DeferredItem<SpaceSuitItem> SPACE_SUIT_BOOTS =
            REGISTRY.registerItem("space_suit_boots",
                    props -> new SpaceSuitItem(ArmorType.BOOTS, props),
                    props -> props.humanoidArmor(SPACE_SUIT_MATERIAL, ArmorType.BOOTS));

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
