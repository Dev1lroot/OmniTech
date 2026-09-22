/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.items.BlueprintItem;
import com.dev1lroot.mcmods.omnitech.items.BoreItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorControlRodItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorFuelRodItem;
import com.dev1lroot.mcmods.omnitech.items.DepletedReactorFuelRodItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorNeutronReflectorItem;
import com.dev1lroot.mcmods.omnitech.items.FloppyDiskItem;
import com.dev1lroot.mcmods.omnitech.items.GuidebookItem;
import com.dev1lroot.mcmods.omnitech.items.FluidCanisterItem;
import com.dev1lroot.mcmods.omnitech.items.LogicGateTemplateItem;
import com.dev1lroot.mcmods.omnitech.items.MicrocontrollerItem;
import com.dev1lroot.mcmods.omnitech.items.RomItem;
import com.dev1lroot.mcmods.omnitech.items.RadioLocatorItem;
import com.dev1lroot.mcmods.omnitech.items.RamCardItem;
import com.dev1lroot.mcmods.omnitech.items.HazmatSuitItem;
import com.dev1lroot.mcmods.omnitech.items.SpaceSuitItem;
import com.dev1lroot.mcmods.omnitech.items.TruthTableItem;
import com.dev1lroot.mcmods.omnitech.util.LogicGate;
import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OmniTechItems
{
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(OmniTech.MODID);

    // ── Spawn eggs ────────────────────────────────────────────────────────────

    /** Spawn egg for {@link com.dev1lroot.mcmods.omnitech.entities.PenguinEntity}. */
    public static final DeferredItem<SpawnEggItem> PENGUIN_EGG =
            REGISTRY.registerItem("penguin_egg",
                    SpawnEggItem::new,
                    props -> props.spawnEgg(OmniTechEntities.PENGUIN.get()));

    // Kuiper Belt block items
    public static final DeferredItem<BlockItem> ASTEROID_BLOCK_ITEM = REGISTRY.registerSimpleBlockItem(
            "asteroid_block", OmniTechBlocks.ASTEROID_BLOCK);

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

    public static final DeferredItem<BlockItem> GLASS_BLOWING_STATION_ITEM = REGISTRY.registerSimpleBlockItem(
            "glass_blowing_station", OmniTechBlocks.GLASS_BLOWING_STATION);

    public static final DeferredItem<BlockItem> FOUNDRY_ITEM = REGISTRY.registerSimpleBlockItem(
            "foundry", OmniTechBlocks.FOUNDRY);

    public static final DeferredItem<BlockItem> ELECTRIC_ENGINE_ITEM = REGISTRY.registerSimpleBlockItem(
            "electric_engine", OmniTechBlocks.ELECTRIC_ENGINE);
    public static final DeferredItem<BlockItem> POWER_RELAY_ITEM = REGISTRY.registerSimpleBlockItem(
            "power_relay", OmniTechBlocks.POWER_RELAY);
    public static final DeferredItem<BlockItem> ELECTRIC_CAPACITOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "electric_capacitor", OmniTechBlocks.ELECTRIC_CAPACITOR);
    public static final DeferredItem<BlockItem> ELECTRIC_WIRE_ITEM = REGISTRY.registerSimpleBlockItem(
            "electric_wire", OmniTechBlocks.ELECTRIC_WIRE);
    public static final DeferredItem<BlockItem> ASSEMBLER_ITEM = REGISTRY.registerSimpleBlockItem(
            "assembler", OmniTechBlocks.ASSEMBLER);

    public static final DeferredItem<BlockItem> REACTOR_BLOCK_ITEM = REGISTRY.registerSimpleBlockItem(
            "reactor_block", OmniTechBlocks.REACTOR_BLOCK);
    public static final DeferredItem<BlockItem> REACTOR_PORT_ITEM  = REGISTRY.registerSimpleBlockItem(
            "reactor_port", OmniTechBlocks.REACTOR_PORT);
    public static final DeferredItem<BlockItem> REACTOR_CELL_ITEM  = REGISTRY.registerSimpleBlockItem(
            "reactor_cell", OmniTechBlocks.REACTOR_CELL);

    public static final DeferredItem<BlockItem> NUCLEAR_BOMB_ITEM = REGISTRY.registerSimpleBlockItem(
            "nuclear_bomb", OmniTechBlocks.NUCLEAR_BOMB);

    public static final DeferredItem<BlockItem> ORRERY_ITEM = REGISTRY.registerSimpleBlockItem(
            "orrery", OmniTechBlocks.ORRERY);

    public static final DeferredItem<BlockItem> GRAVITATION_SOURCE_ITEM = REGISTRY.registerSimpleBlockItem(
            "gravitation_source", OmniTechBlocks.GRAVITATION_SOURCE);

    public static final DeferredItem<ReactorFuelRodItem> REACTOR_FUEL_ROD =
            REGISTRY.registerItem("reactor_fuel_rod", ReactorFuelRodItem::new);
    public static final DeferredItem<ReactorControlRodItem> REACTOR_CONTROL_ROD =
            REGISTRY.registerItem("reactor_control_rod", ReactorControlRodItem::new);
    public static final DeferredItem<ReactorNeutronReflectorItem> REACTOR_NEUTRON_REFLECTOR =
            REGISTRY.registerItem("reactor_neutron_reflector", ReactorNeutronReflectorItem::new);
    public static final DeferredItem<DepletedReactorFuelRodItem> DEPLETED_REACTOR_FUEL_ROD =
            REGISTRY.registerItem("depleted_reactor_fuel_rod", DepletedReactorFuelRodItem::new);

    public static final DeferredItem<BlockItem> ELECTRIC_FURNACE_ITEM = REGISTRY.registerSimpleBlockItem(
            "electric_furnace", OmniTechBlocks.ELECTRIC_FURNACE);
    public static final DeferredItem<BlockItem> SOLAR_PANEL_ITEM = REGISTRY.registerSimpleBlockItem(
            "solar_panel", OmniTechBlocks.SOLAR_PANEL);

    public static final DeferredItem<BlockItem> SOLVATION_MACHINE_ITEM = REGISTRY.registerSimpleBlockItem(
            "solvation_machine", OmniTechBlocks.SOLVATION_MACHINE);

    public static final DeferredItem<BlockItem> FILTER_PRESS_ITEM = REGISTRY.registerSimpleBlockItem(
            "filter_press", OmniTechBlocks.FILTER_PRESS);

    public static final DeferredItem<BlockItem> FERMENTER_ITEM = REGISTRY.registerSimpleBlockItem(
            "fermenter", OmniTechBlocks.FERMENTER);

    public static final DeferredItem<BlockItem> STRUCTURE_TABLE_ITEM = REGISTRY.registerSimpleBlockItem(
            "structure_table", OmniTechBlocks.STRUCTURE_TABLE);

    public static final DeferredItem<BlockItem> ELECTROLYSIS_MACHINE_ITEM = REGISTRY.registerSimpleBlockItem(
            "electrolysis_machine", OmniTechBlocks.ELECTROLYSIS_MACHINE);

    public static final DeferredItem<BlockItem> ROTARY_COMPRESSOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "rotary_compressor", OmniTechBlocks.ROTARY_COMPRESSOR);

    public static final DeferredItem<BlockItem> FLUID_COLLECTOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "fluid_collector", OmniTechBlocks.FLUID_COLLECTOR);

    public static final DeferredItem<BlockItem> HEAT_EXCHANGER_ITEM = REGISTRY.registerSimpleBlockItem(
            "heat_exchanger", OmniTechBlocks.HEAT_EXCHANGER);

    public static final DeferredItem<BlockItem> CHEMICAL_MIXER_ITEM = REGISTRY.registerSimpleBlockItem(
            "chemical_mixer", OmniTechBlocks.CHEMICAL_MIXER);

    public static final DeferredItem<BlockItem> DECOMPRESSOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "decompressor", OmniTechBlocks.DECOMPRESSOR);

    public static final DeferredItem<BlockItem> FRACTIONAL_DISTILLER_ITEM = REGISTRY.registerSimpleBlockItem(
            "fractional_distiller", OmniTechBlocks.FRACTIONAL_DISTILLER);

    public static final DeferredItem<BlockItem> CHEMICAL_REACTOR_ITEM = REGISTRY.registerSimpleBlockItem(
            "chemical_reactor", OmniTechBlocks.CHEMICAL_REACTOR);

    public static final DeferredItem<BlockItem> ROCKET_CONTROLLER_ITEM = REGISTRY.registerSimpleBlockItem(
            "rocket_controller", OmniTechBlocks.ROCKET_CONTROLLER);

    public static final DeferredItem<BlockItem> FLUID_FILLER_ITEM = REGISTRY.registerSimpleBlockItem(
            "fluid_filler", OmniTechBlocks.FLUID_FILLER);

    public static final DeferredItem<FluidCanisterItem> FLUID_CANISTER =
            REGISTRY.registerItem("fluid_canister", FluidCanisterItem::new);

    public static final DeferredItem<com.dev1lroot.mcmods.omnitech.items.FlaskItem> FLASK =
            REGISTRY.registerItem("flask", com.dev1lroot.mcmods.omnitech.items.FlaskItem::new);

    public static final DeferredItem<com.dev1lroot.mcmods.omnitech.items.MixtureDustItem> MIXTURE_DUST =
            REGISTRY.registerItem("mixture_dust", com.dev1lroot.mcmods.omnitech.items.MixtureDustItem::new);

    public static final DeferredItem<com.dev1lroot.mcmods.omnitech.items.PipetteItem> PIPETTE =
            REGISTRY.registerItem("pipette", com.dev1lroot.mcmods.omnitech.items.PipetteItem::new);

    public static final DeferredItem<com.dev1lroot.mcmods.omnitech.items.ChemicalFormulaItem> CHEMICAL_FORMULA =
            REGISTRY.registerItem("chemical_formula", com.dev1lroot.mcmods.omnitech.items.ChemicalFormulaItem::new);

    public static final DeferredItem<com.dev1lroot.mcmods.omnitech.items.ChemicalCompoundDustItem> CHEMICAL_COMPOUND_DUST =
            REGISTRY.registerItem("chemical_compound_dust", com.dev1lroot.mcmods.omnitech.items.ChemicalCompoundDustItem::new);

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

    // ── Botany ────────────────────────────────────────────────────────────────
    public static final DeferredItem<BlockItem> VANILLA_VINE_ITEM =
            REGISTRY.registerSimpleBlockItem("vanilla_vine", OmniTechBlocks.VANILLA_VINE);

    /** Logs and stripped logs are valid furnace fuel, same as every other wood species. */
    public static final DeferredItem<BlockItem> CINNAMON_LOG_ITEM =
            REGISTRY.registerItem("cinnamon_log",
                    p -> new BlockItem(OmniTechBlocks.CINNAMON_LOG.get(), p),
                    p -> p.cookingFuel(net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));
    public static final DeferredItem<BlockItem> STRIPPED_CINNAMON_LOG_ITEM =
            REGISTRY.registerItem("stripped_cinnamon_log",
                    p -> new BlockItem(OmniTechBlocks.STRIPPED_CINNAMON_LOG.get(), p),
                    p -> p.cookingFuel(net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));

    public static final DeferredItem<BlockItem> CINNAMON_LEAVES_ITEM =
            REGISTRY.registerSimpleBlockItem("cinnamon_leaves", OmniTechBlocks.CINNAMON_LEAVES);
    public static final DeferredItem<BlockItem> CINNAMON_SAPLING_ITEM =
            REGISTRY.registerSimpleBlockItem("cinnamon_sapling", OmniTechBlocks.CINNAMON_SAPLING);
    public static final DeferredItem<BlockItem> NETTLE_ITEM =
            REGISTRY.registerSimpleBlockItem("nettle", OmniTechBlocks.NETTLE);

    /** Harvested from a cinnamon log's bark when it's stripped with an axe. */
    public static final DeferredItem<Item> CINNAMON =
            REGISTRY.registerItem("cinnamon", Item::new);

    /**
     * Dropped by breaking any segment of {@link OmniTechBlocks#CORN_PLANT} — craft
     * into {@link #CORN_SEEDS} to replant. Deliberately <b>not</b> food: raw corn
     * needs to be cooked first (no cooked variant/recipe yet).
     */
    public static final DeferredItem<Item> CORN =
            REGISTRY.registerItem("corn", Item::new);
    /** Macerated wheat / corn — dissolves into the Fermenter's "flour" component. */
    public static final DeferredItem<Item> WHEAT_FLOUR =
            REGISTRY.registerItem("wheat_flour", Item::new);
    public static final DeferredItem<Item> CORN_FLOUR =
            REGISTRY.registerItem("corn_flour", Item::new);
    /**
     * A {@link BlockItem} for {@link OmniTechBlocks#CORN_PLANT} registered under its
     * own name — the only way a corn plant ever enters the world, since that block
     * has no BlockItem of its own (see {@link com.dev1lroot.mcmods.omnitech.blocks.plants.CornBlock}).
     * {@code useItemDescriptionPrefix()} makes it show its own name ("Corn Seeds")
     * instead of inheriting {@code corn_plant}'s — the same trick vanilla uses for
     * {@code wheat_seeds} (a {@code BlockItem(Blocks.WHEAT, ...)}).
     */
    public static final DeferredItem<BlockItem> CORN_SEEDS =
            REGISTRY.registerItem("corn_seeds",
                    p -> new BlockItem(OmniTechBlocks.CORN_PLANT.get(), p.useItemDescriptionPrefix()));

    /**
     * A {@link BlockItem} for {@link OmniTechBlocks#TOMATO} that's also food and a
     * throwable projectile — see {@link com.dev1lroot.mcmods.omnitech.items.TomatoItem}.
     */
    public static final DeferredItem<com.dev1lroot.mcmods.omnitech.items.TomatoItem> TOMATO =
            REGISTRY.registerItem("tomato",
                    p -> new com.dev1lroot.mcmods.omnitech.items.TomatoItem(OmniTechBlocks.TOMATO.get(), p),
                    p -> p.useItemDescriptionPrefix().food(
                            new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build()));

    /**
     * A {@link BlockItem} for {@link OmniTechBlocks#JALAPENO} that's also food —
     * see {@link com.dev1lroot.mcmods.omnitech.items.JalapenoItem} for the fire-on-eat effect.
     */
    public static final DeferredItem<com.dev1lroot.mcmods.omnitech.items.JalapenoItem> JALAPENO =
            REGISTRY.registerItem("jalapeno",
                    p -> new com.dev1lroot.mcmods.omnitech.items.JalapenoItem(OmniTechBlocks.JALAPENO.get(), p),
                    p -> p.useItemDescriptionPrefix().food(
                            new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).build()));

    // ── Research ──────────────────────────────────────────────────────────────
    public static final DeferredItem<BlueprintItem> BLUEPRINT =
            REGISTRY.registerItem("blueprint", BlueprintItem::new);
    public static final DeferredItem<BlockItem> RESEARCH_TABLE_ITEM =
            REGISTRY.registerSimpleBlockItem("research_table", OmniTechBlocks.RESEARCH_TABLE);

    // ── Logic / GPIO ──────────────────────────────────────────────────────────
    public static final DeferredItem<BlockItem> PROGRAMMING_STATION_ITEM =
            REGISTRY.registerSimpleBlockItem("programming_station", OmniTechBlocks.PROGRAMMING_STATION);
    public static final DeferredItem<BlockItem> LOGIC_MACHINE_ITEM =
            REGISTRY.registerSimpleBlockItem("logic_machine", OmniTechBlocks.LOGIC_MACHINE);
    public static final DeferredItem<BlockItem> LOGIC_CABLE_ITEM =
            REGISTRY.registerSimpleBlockItem("logic_cable", OmniTechBlocks.LOGIC_CABLE);
    public static final DeferredItem<BlockItem> LOGIC_GATE_BLOCK_ITEM =
            REGISTRY.registerSimpleBlockItem("logic_gate_block", OmniTechBlocks.LOGIC_GATE_BLOCK);
    public static final DeferredItem<BlockItem> GPIO_PORT_ITEM =
            REGISTRY.registerSimpleBlockItem("gpio_port", OmniTechBlocks.GPIO_PORT);

    public static final DeferredItem<BlockItem> DISPLAY_ITEM =
            REGISTRY.registerSimpleBlockItem("display", OmniTechBlocks.DISPLAY);

    public static final DeferredItem<BlockItem> DISPLAY_MK2_ITEM =
            REGISTRY.registerSimpleBlockItem("display_mk2", OmniTechBlocks.DISPLAY_MK2);

    public static final DeferredItem<BlockItem> DISPLAY_MK3_ITEM =
            REGISTRY.registerSimpleBlockItem("display_mk3", OmniTechBlocks.DISPLAY_MK3);

    public static final DeferredItem<BlockItem> KEYBOARD_ITEM =
            REGISTRY.registerSimpleBlockItem("keyboard", OmniTechBlocks.KEYBOARD);

    public static final DeferredItem<BlockItem> FLOPPY_DRIVE_ITEM =
            REGISTRY.registerSimpleBlockItem("floppy_drive", OmniTechBlocks.FLOPPY_DRIVE);

    public static final DeferredItem<BlockItem> EXPANSION_SLOT_ITEM =
            REGISTRY.registerSimpleBlockItem("expansion_slot", OmniTechBlocks.EXPANSION_SLOT);

    public static final DeferredItem<FloppyDiskItem> FLOPPY_DISK =
            REGISTRY.registerItem("floppy_disk", p -> new FloppyDiskItem(
                    p.stacksTo(1).component(DataComponents.DYED_COLOR,
                            new DyedItemColor(FloppyDiskItem.DEFAULT_COLOR))));

    public static final DeferredItem<RamCardItem> RAM_CARD =
            REGISTRY.registerItem("ram_card", p -> new RamCardItem(p.stacksTo(1)));

    public static final DeferredItem<MicrocontrollerItem> MICROCONTROLLER =
            REGISTRY.registerItem("microcontroller",
                    p -> new MicrocontrollerItem(p.stacksTo(1)));

    public static final DeferredItem<RomItem> LINUX_ROM =
            REGISTRY.registerItem("linux_rom",
                    p -> new RomItem(RomItem.TYPE_LINUX, p.stacksTo(1)));

    public static final DeferredItem<RomItem> FIRMWARE_ROM =
            REGISTRY.registerItem("firmware_rom",
                    p -> new RomItem(RomItem.TYPE_FIRMWARE, p.stacksTo(1)));

    public static final DeferredItem<RadioLocatorItem> RADIO_LOCATOR =
            REGISTRY.registerItem("radio_locator", RadioLocatorItem::new);

    public static final DeferredItem<GuidebookItem> GUIDEBOOK =
            REGISTRY.registerItem("guidebook", p -> new GuidebookItem(p.stacksTo(1)));

    // ── Truth Table & Logic Gate Templates ────────────────────────────────────

    public static final DeferredItem<TruthTableItem> TRUTH_TABLE =
            REGISTRY.registerItem("truth_table", p -> new TruthTableItem(p.stacksTo(1)));

    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_AND =
            REGISTRY.registerItem("gate_template_and",
                    p -> new LogicGateTemplateItem("and", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_OR =
            REGISTRY.registerItem("gate_template_or",
                    p -> new LogicGateTemplateItem("or", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_NAND =
            REGISTRY.registerItem("gate_template_nand",
                    p -> new LogicGateTemplateItem("nand", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_NOR =
            REGISTRY.registerItem("gate_template_nor",
                    p -> new LogicGateTemplateItem("nor", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_XOR =
            REGISTRY.registerItem("gate_template_xor",
                    p -> new LogicGateTemplateItem("xor", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_XNOR =
            REGISTRY.registerItem("gate_template_xnor",
                    p -> new LogicGateTemplateItem("xnor", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_BUFFER_A =
            REGISTRY.registerItem("gate_template_buffer_a",
                    p -> new LogicGateTemplateItem("buffer_a", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_NOT_A =
            REGISTRY.registerItem("gate_template_not_a",
                    p -> new LogicGateTemplateItem("not_a", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_BUFFER_B =
            REGISTRY.registerItem("gate_template_buffer_b",
                    p -> new LogicGateTemplateItem("buffer_b", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_NOT_B =
            REGISTRY.registerItem("gate_template_not_b",
                    p -> new LogicGateTemplateItem("not_b", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_ALWAYS_ON =
            REGISTRY.registerItem("gate_template_always_on",
                    p -> new LogicGateTemplateItem("always_on", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_ALWAYS_OFF =
            REGISTRY.registerItem("gate_template_always_off",
                    p -> new LogicGateTemplateItem("always_off", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_IMPLY =
            REGISTRY.registerItem("gate_template_imply",
                    p -> new LogicGateTemplateItem("imply", p.stacksTo(1)));
    public static final DeferredItem<LogicGateTemplateItem> GATE_TEMPLATE_NIMPLY =
            REGISTRY.registerItem("gate_template_nimply",
                    p -> new LogicGateTemplateItem("nimply", p.stacksTo(1)));

    /** Returns a new ItemStack for the gate template matching the given gate, or empty if unrecognised. */
    public static ItemStack gateTemplateFor(LogicGate gate) {
        return new ItemStack(switch (gate) {
            case AND        -> GATE_TEMPLATE_AND.get();
            case OR         -> GATE_TEMPLATE_OR.get();
            case NAND       -> GATE_TEMPLATE_NAND.get();
            case NOR        -> GATE_TEMPLATE_NOR.get();
            case XOR        -> GATE_TEMPLATE_XOR.get();
            case XNOR       -> GATE_TEMPLATE_XNOR.get();
            case BUFFER_A   -> GATE_TEMPLATE_BUFFER_A.get();
            case NOT_A      -> GATE_TEMPLATE_NOT_A.get();
            case BUFFER_B   -> GATE_TEMPLATE_BUFFER_B.get();
            case NOT_B      -> GATE_TEMPLATE_NOT_B.get();
            case ALWAYS_ON  -> GATE_TEMPLATE_ALWAYS_ON.get();
            case ALWAYS_OFF -> GATE_TEMPLATE_ALWAYS_OFF.get();
            case IMPLY      -> GATE_TEMPLATE_IMPLY.get();
            case NIMPLY     -> GATE_TEMPLATE_NIMPLY.get();
        });
    }

    public static final DeferredItem<BlockItem> REDSTONE_INTERSECTION_BLOCK_ITEM =
            REGISTRY.registerSimpleBlockItem("redstone_intersection_block", OmniTechBlocks.REDSTONE_INTERSECTION);

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

    // ── Hazmat Suit ────────────────────────────────────────────────────────────

    private static final ResourceKey<EquipmentAsset> HAZMAT_SUIT_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID,
                    Identifier.fromNamespaceAndPath(OmniTech.MODID, "hazmat_suit"));

    /**
     * Hazmat Suit material — zero physical defence, fragile (20 durability per
     * piece enforced via {@link Item.Properties#durability}).  Its only purpose
     * is radiation shielding when the full set is worn.
     */
    public static final ArmorMaterial HAZMAT_SUIT_MATERIAL = new ArmorMaterial(
            1,    // multiplier (actual durability overridden to 20 per piece below)
            Maps.newEnumMap(Map.of(
                    ArmorType.BOOTS,      0,
                    ArmorType.LEGGINGS,   0,
                    ArmorType.CHESTPLATE, 0,
                    ArmorType.HELMET,     0,
                    ArmorType.BODY,       0
            )),
            1,    // not enchantable
            SoundEvents.ARMOR_EQUIP_LEATHER,
            0.0f, // no toughness
            0.0f, // no knockback resistance
            ItemTags.REPAIRS_LEATHER_ARMOR,
            HAZMAT_SUIT_ASSET
    );

    public static final DeferredItem<HazmatSuitItem> HAZMAT_SUIT_HELMET =
            REGISTRY.registerItem("hazmat_suit_helmet",
                    props -> new HazmatSuitItem(ArmorType.HELMET, props),
                    props -> props.humanoidArmor(HAZMAT_SUIT_MATERIAL, ArmorType.HELMET).durability(20));

    public static final DeferredItem<HazmatSuitItem> HAZMAT_SUIT_CHESTPLATE =
            REGISTRY.registerItem("hazmat_suit_chestplate",
                    props -> new HazmatSuitItem(ArmorType.CHESTPLATE, props),
                    props -> props.humanoidArmor(HAZMAT_SUIT_MATERIAL, ArmorType.CHESTPLATE).durability(20));

    public static final DeferredItem<HazmatSuitItem> HAZMAT_SUIT_LEGGINGS =
            REGISTRY.registerItem("hazmat_suit_leggings",
                    props -> new HazmatSuitItem(ArmorType.LEGGINGS, props),
                    props -> props.humanoidArmor(HAZMAT_SUIT_MATERIAL, ArmorType.LEGGINGS).durability(20));

    public static final DeferredItem<HazmatSuitItem> HAZMAT_SUIT_BOOTS =
            REGISTRY.registerItem("hazmat_suit_boots",
                    props -> new HazmatSuitItem(ArmorType.BOOTS, props),
                    props -> props.humanoidArmor(HAZMAT_SUIT_MATERIAL, ArmorType.BOOTS).durability(20));

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
