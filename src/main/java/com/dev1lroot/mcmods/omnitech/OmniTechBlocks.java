/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.*;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.alloy_furnace.AlloyFurnaceBlock;
import com.dev1lroot.mcmods.omnitech.blocks.processing.centrifuge.ManualCentrifugeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.processing.centrifuge.ManualCentrifugeRotorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_engine.ElectricEngineStatorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.power_relay.PowerRelayBlock;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticGeneratorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticPipeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticReductorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.labware.ElectrolysisMachineBlock;
import com.dev1lroot.mcmods.omnitech.blocks.labware.FractionalDistillerBlock;
import com.dev1lroot.mcmods.omnitech.blocks.labware.SolvationMachineBlock;
import com.dev1lroot.mcmods.omnitech.blocks.labware.chemical_infuser.ChemicalInfuserBlock;
import com.dev1lroot.mcmods.omnitech.blocks.labware.extractor.ExtractorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.electric_heater.ElectricHeaterBlock;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.radiator.RadiatorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_transmitter.RadioTransmitterBlock;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_receiver.RadioReceiverBlock;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_scanner.RadioScannerBlock;
import com.dev1lroot.mcmods.omnitech.blocks.analog.AnalogCableBlock;
import com.dev1lroot.mcmods.omnitech.blocks.analog.microphone.MicrophoneBlock;
import com.dev1lroot.mcmods.omnitech.blocks.analog.speaker.SpeakerBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.LogicCableBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.RedstoneIntersectionBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_gate.LogicGateBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockMk2;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockMk3;
import com.dev1lroot.mcmods.omnitech.blocks.logic.floppy_drive.FloppyDriveBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.expansion_slot.ExpansionSlotBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.gpio_port.GPIOPortBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.programming_station.ProgrammingStationBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.research_table.ResearchTableBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.keyboard.KeyboardBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorPort;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorCell;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.LogicMachineBlock;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.thermal_conductor.ThermalConductorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logistic.SorterBlock;
import com.dev1lroot.mcmods.omnitech.blocks.processing.macerator.ManualMaceratorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.*;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.boiler.BoilerBlock;
import com.dev1lroot.mcmods.omnitech.blocks.labware.chemical_reactor.ChemicalReactorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logistic.conveyor_belt.ConveyorBeltBlock;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.crank.CrankBlock;
import com.dev1lroot.mcmods.omnitech.blocks.pressure.decompressor.DecompressorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_capacitor.ElectricCapacitorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_charger.ElectricChargerBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_engine.ElectricEngineBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.assembler.AssemblerBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_furnace.ElectricFurnaceBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_wire.ElectricWireBlock;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.*;
import com.dev1lroot.mcmods.omnitech.blocks.pressure.RotaryCompressorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.solar_panel.SolarPanelBlock;
import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class OmniTechBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(OmniTech.MODID);

    /**
     * All ore entries — iterated by datagen to generate worldgen JSON files.
     * Each entry pairs a block reference with its spawn configuration.
     * Populated by {@link #registerOre} and by {@link MineralSet} for MineralSet-based ores.
     */
    public static final List<OreEntry> ALL_ORES = new ArrayList<>();

    /** Pairs a block with its world-generation spawn configuration. */
    public record OreEntry(DeferredBlock<? extends Block> block, OreSpawnConfig config) {
        public String name() { return block.getId().getPath(); }
    }

    public static final DeferredBlock<Block> EXAMPLE_BLOCK = REGISTRY.registerSimpleBlock(
            "example_block", p -> p.mapColor(MapColor.STONE));

    // ── Europa blocks ─────────────────────────────────────────────────────────
    /** Deep rocky basement layer (Y=3–22) beneath the Europa ocean. */
    public static final DeferredBlock<Block> EUROPA_STONE = REGISTRY.registerSimpleBlock(
            "europa_stone", p -> p.mapColor(MapColor.STONE).strength(3.0F).sound(SoundType.STONE));
    /** Main ice body (Y=103–122) forming Europa's frozen crust. */
    public static final DeferredBlock<Block> EUROPA_ICE = REGISTRY.registerSimpleBlock(
            "europa_ice", p -> p.mapColor(MapColor.ICE).strength(1.5F).sound(SoundType.GLASS).friction(0.98F));
    /** Topmost cracked-surface ice layer and the material of downward ice spikes. */
    public static final DeferredBlock<Block> CRACKED_ICE = REGISTRY.registerSimpleBlock(
            "cracked_ice", p -> p.mapColor(MapColor.ICE).strength(1.2F).sound(SoundType.GLASS).friction(0.96F));

    public static final DeferredBlock<Block> ALLOY_FURNACE;
    public static final DeferredBlock<Block> MANUAL_MACERATOR;
    public static final DeferredBlock<Block> MANUAL_CENTRIFUGE;
    /** Ghost block – holds the {@code manual_centrifuge_rotor} blockstate for BERI spinning rotor rendering. */
    public static final DeferredBlock<Block> MANUAL_CENTRIFUGE_ROTOR;
    public static final DeferredBlock<Block> CRANK;

    public static final DeferredBlock<Block> KF_GENERATOR;
    public static final DeferredBlock<Block> KF_PIPE;
    public static final DeferredBlock<Block> KF_REDUCTOR;

    public static final DeferredBlock<Block> HEATER;
    public static final DeferredBlock<Block> STIRLING_ENGINE;

    public static final DeferredBlock<Block> CONVEYOR_BELT;

    public static final DeferredBlock<Block> FLUID_PIPE;
    /** Ghost block – holds the {@code fluid_pipe_trim} blockstate for BER tinted overlay rendering. */
    public static final DeferredBlock<Block> FLUID_PIPE_TRIM;
    public static final DeferredBlock<Block> PUMP;
    public static final DeferredBlock<Block> FLUID_TANK;
    public static final DeferredBlock<Block> BOILER;
    public static final DeferredBlock<Block> VALVE;
    /** Ghost block – holds the {@code valve_wheel} blockstate for BER spinning wheel rendering. */
    public static final DeferredBlock<Block> VALVE_WHEEL;
    public static final DeferredBlock<Block> SORTER;
    public static final DeferredBlock<Block> SMELTER;
    public static final DeferredBlock<Block> FOUNDRY;

    public static final DeferredBlock<Block> ASSEMBLER;

    public static final DeferredBlock<Block> ELECTRIC_ENGINE;
    public static final DeferredBlock<Block> ELECTRIC_ENGINE_STATOR;
    public static final DeferredBlock<Block> POWER_RELAY;
    public static final DeferredBlock<Block> ELECTRIC_CAPACITOR;
    public static final DeferredBlock<Block> ELECTRIC_WIRE;
    public static final DeferredBlock<Block> ELECTRIC_FURNACE;
    public static final DeferredBlock<Block> SOLAR_PANEL;
    public static final DeferredBlock<Block> SOLVATION_MACHINE;
    public static final DeferredBlock<Block> ELECTROLYSIS_MACHINE;
    public static final DeferredBlock<Block> ROTARY_COMPRESSOR;
    public static final DeferredBlock<Block> FLUID_COLLECTOR;
    public static final DeferredBlock<Block> HEAT_EXCHANGER;
    public static final DeferredBlock<Block> DECOMPRESSOR;
    public static final DeferredBlock<Block> FRACTIONAL_DISTILLER;
    public static final DeferredBlock<Block> CHEMICAL_REACTOR;
    public static final DeferredBlock<Block> FLUID_FILLER;
    public static final DeferredBlock<Block> ELECTRIC_CHARGER;
    public static final DeferredBlock<Block> COKE_BRICK;
    public static final DeferredBlock<Block> CHEMICAL_INFUSER;
    public static final DeferredBlock<Block> EXTRACTOR;
    public static final DeferredBlock<Block> RADIATOR;
    public static final DeferredBlock<Block> THERMAL_CONDUCTOR;
    public static final DeferredBlock<Block> ELECTRIC_HEATER;

    public static final DeferredBlock<Block> RADIO_TRANSMITTER;
    public static final DeferredBlock<Block> RADIO_RECEIVER;
    public static final DeferredBlock<Block> RADIO_SCANNER;

    public static final DeferredBlock<Block> ANALOG_CABLE;
    public static final DeferredBlock<Block> MICROPHONE;
    public static final DeferredBlock<Block> SPEAKER;

    // ── Logic / GPIO / Display ────────────────────────────────────────────────
    public static final DeferredBlock<Block> PROGRAMMING_STATION;
    public static final DeferredBlock<Block> RESEARCH_TABLE;
    public static final DeferredBlock<Block> LOGIC_MACHINE;
    public static final DeferredBlock<Block> LOGIC_CABLE;
    public static final DeferredBlock<Block> LOGIC_GATE_BLOCK;
    public static final DeferredBlock<Block> GPIO_PORT;
    public static final DeferredBlock<Block> DISPLAY;
    public static final DeferredBlock<Block> DISPLAY_MK2;
    public static final DeferredBlock<Block> DISPLAY_MK3;
    public static final DeferredBlock<Block> FLOPPY_DRIVE;
    public static final DeferredBlock<Block> EXPANSION_SLOT;
    public static final DeferredBlock<Block> REDSTONE_INTERSECTION;
    public static final DeferredBlock<Block> KEYBOARD;

    // ── Reactor multiblock ────────────────────────────────────────────────────
    public static final DeferredBlock<Block> REACTOR_BLOCK;
    public static final DeferredBlock<Block> REACTOR_PORT;
    public static final DeferredBlock<Block> REACTOR_CELL;

    static {
        ALLOY_FURNACE = register("alloy_furnace", AlloyFurnaceBlock::new);
        MANUAL_MACERATOR = register("manual_macerator",
                p -> new ManualMaceratorBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        MANUAL_CENTRIFUGE = register("manual_centrifuge",
                p -> new ManualCentrifugeBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        MANUAL_CENTRIFUGE_ROTOR = register("manual_centrifuge_rotor",
                p -> new ManualCentrifugeRotorBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE).noLootTable()));
        CRANK = register("crank",
                p -> new CrankBlock(p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));

        KF_GENERATOR = register("kf_generator",
                p -> new KineticGeneratorBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        KF_PIPE = register("kf_pipe",
                p -> new KineticPipeBlock(p
                        .mapColor(MapColor.METAL)
                        .strength(3.0F)
                        .sound(SoundType.METAL)
                        .noOcclusion()
                        .pushReaction(PushReaction.NORMAL)
                ));
        KF_REDUCTOR = register("kf_reductor",
                p -> new KineticReductorBlock(p
                        .mapColor(MapColor.METAL)
                        .strength(3.5F)
                        .sound(SoundType.METAL)));

        HEATER = register("heater",
                p -> new HeaterBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        STIRLING_ENGINE = register("stirling_engine",
                p -> new StirlingEngineBlock(p.mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL)));

        CONVEYOR_BELT = register("conveyor_belt",
                p -> new ConveyorBeltBlock(p.mapColor(MapColor.METAL).strength(2.5F)
                        .sound(SoundType.METAL).noOcclusion()));

        FLUID_PIPE = register("fluid_pipe",
                p -> new FluidPipeBlock(p
                        .mapColor(MapColor.METAL)
                        .strength(2.0F)
                        .sound(SoundType.METAL)
                        .noOcclusion()
                        .pushReaction(PushReaction.NORMAL)
                ));
        FLUID_PIPE_TRIM = register("fluid_pipe_trim",
                p -> new FluidPipeTrimBlock(p
                        .mapColor(MapColor.METAL)
                        .strength(2.0F)
                        .sound(SoundType.METAL)
                        .noOcclusion()
                        .noLootTable()
                ));
        PUMP = register("pump",
                p -> new PumpBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL).noOcclusion()));
        FLUID_TANK = register("fluid_tank",
                p -> new FluidTankBlock(p
                        .mapColor(MapColor.METAL)
                        .strength(3.0F)
                        .sound(SoundType.METAL)
                        .noOcclusion()
                        .isViewBlocking((state, level, pos) -> false)
                ));
        BOILER = register("boiler",
                p -> new BoilerBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL).noOcclusion()));
        VALVE = register("valve",
                p -> new ValveBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL).noOcclusion()));
        VALVE_WHEEL = register("valve_wheel",
                p -> new ValveWheelBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL).noOcclusion().noLootTable()));
        SORTER = register("sorter",
                p -> new SorterBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL)));
        SMELTER = register("smelter",
                p -> new SmelterBlock(p.mapColor(MapColor.METAL).strength(4.0F)
                        .sound(SoundType.METAL)));
        FOUNDRY = register("foundry",
                p -> new FoundryBlock(p.mapColor(MapColor.METAL).strength(4.0F)
                        .sound(SoundType.METAL)));

        ASSEMBLER = register("assembler",
                p -> new AssemblerBlock(p.mapColor(MapColor.METAL).strength(3.5F)
                        .sound(SoundType.METAL)));

        ELECTRIC_ENGINE = register("electric_engine",
                p -> new ElectricEngineBlock(p.mapColor(MapColor.METAL).strength(3.5F).noOcclusion()
                        .sound(SoundType.METAL)));
        ELECTRIC_ENGINE_STATOR = register("electric_engine_stator",
                p -> new ElectricEngineStatorBlock(p.mapColor(MapColor.METAL).strength(3.5F).noOcclusion()
                        .sound(SoundType.METAL).noLootTable()));
        POWER_RELAY = register("power_relay",
                p -> new PowerRelayBlock(p.mapColor(MapColor.METAL).strength(2.0F)
                        .sound(SoundType.METAL).noOcclusion()));
        ELECTRIC_CAPACITOR = register("electric_capacitor",
                p -> new ElectricCapacitorBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL)));
        ELECTRIC_WIRE = register("electric_wire",
                p -> new ElectricWireBlock(p.mapColor(MapColor.METAL).strength(1.5F)
                        .sound(SoundType.METAL).noOcclusion()
                        .pushReaction(net.minecraft.world.level.material.PushReaction.NORMAL)));
        ELECTRIC_FURNACE = register("electric_furnace",
                p -> new ElectricFurnaceBlock(p.mapColor(MapColor.METAL).strength(3.5F)
                        .sound(SoundType.METAL)));
        SOLAR_PANEL = register("solar_panel",
                p -> new SolarPanelBlock(p.mapColor(MapColor.METAL).strength(2.0F)
                        .sound(SoundType.METAL).noOcclusion()));
        SOLVATION_MACHINE = register("solvation_machine",
                p -> new SolvationMachineBlock(p.mapColor(MapColor.METAL).strength(3.5F)
                        .sound(SoundType.METAL)));
        ELECTROLYSIS_MACHINE = register("electrolysis_machine",
                p -> new ElectrolysisMachineBlock(p.mapColor(MapColor.METAL).strength(3.5F)
                        .sound(SoundType.METAL)));
        ROTARY_COMPRESSOR = register("rotary_compressor",
                p -> new RotaryCompressorBlock(p.mapColor(MapColor.METAL).strength(3.5F).noOcclusion()
                        .sound(SoundType.METAL)));
        FLUID_COLLECTOR = register("fluid_collector",
                p -> new FluidCollectorBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL).noOcclusion()));
        HEAT_EXCHANGER = register("heat_exchanger",
                p -> new HeatExchangerBlock(p.mapColor(MapColor.METAL).strength(3.5F).noOcclusion()
                        .sound(SoundType.METAL)));
        DECOMPRESSOR = register("decompressor",
                p -> new DecompressorBlock(p.mapColor(MapColor.METAL).strength(3.5F).noOcclusion()
                        .sound(SoundType.METAL)));
        FRACTIONAL_DISTILLER = register("fractional_distiller",
                p -> new FractionalDistillerBlock(p.mapColor(MapColor.METAL).strength(3.5F)
                        .sound(SoundType.METAL)));
        CHEMICAL_REACTOR = register("chemical_reactor",
                p -> new ChemicalReactorBlock(p.mapColor(MapColor.METAL).strength(3.5F)
                        .sound(SoundType.METAL)));
        FLUID_FILLER = register("fluid_filler",
                p -> new FluidFillerBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL)));
        ELECTRIC_CHARGER = register("electric_charger",
                p -> new ElectricChargerBlock(p.mapColor(MapColor.METAL).strength(3.5F)
                        .sound(SoundType.METAL)));
        COKE_BRICK = register("coke_brick",
                p -> new CokeBrickBlock(p.mapColor(MapColor.STONE).strength(3.5F)
                        .sound(SoundType.STONE)));
        CHEMICAL_INFUSER = register("chemical_infuser",
                p -> new ChemicalInfuserBlock(p.mapColor(MapColor.METAL).strength(3.5F)
                        .sound(SoundType.METAL)));
        EXTRACTOR = register("extractor",
                p -> new ExtractorBlock(p.mapColor(MapColor.METAL).strength(3.5F)
                        .sound(SoundType.METAL)));
        RADIATOR = register("radiator",
                p -> new RadiatorBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL).noOcclusion()));
        THERMAL_CONDUCTOR = register("thermal_conductor",
                p -> new ThermalConductorBlock(p.mapColor(MapColor.METAL).strength(2.0F)
                        .sound(SoundType.METAL).noOcclusion()
                        .pushReaction(net.minecraft.world.level.material.PushReaction.NORMAL)));
        ELECTRIC_HEATER = register("electric_heater",
                p -> new ElectricHeaterBlock(p.mapColor(MapColor.METAL).strength(3.5F)
                        .sound(SoundType.METAL)));

        RADIO_TRANSMITTER = register("radio_transmitter",
                p -> new RadioTransmitterBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL)));
        RADIO_RECEIVER = register("radio_receiver",
                p -> new RadioReceiverBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL)));
        RADIO_SCANNER = register("radio_scanner",
                p -> new RadioScannerBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL)));

        ANALOG_CABLE = register("analog_cable",
                p -> new AnalogCableBlock(p.mapColor(MapColor.COLOR_YELLOW).strength(1.0F)
                        .sound(SoundType.METAL).noOcclusion()
                        .pushReaction(net.minecraft.world.level.material.PushReaction.NORMAL)));
        MICROPHONE = register("microphone",
                p -> new MicrophoneBlock(p.mapColor(MapColor.METAL).strength(2.5F)
                        .sound(SoundType.METAL)));
        SPEAKER = register("speaker",
                p -> new SpeakerBlock(p.mapColor(MapColor.METAL).strength(2.5F)
                        .sound(SoundType.METAL)));

        PROGRAMMING_STATION = register("programming_station",
                p -> new ProgrammingStationBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL)));
        RESEARCH_TABLE = register("research_table",
                p -> new ResearchTableBlock(p.mapColor(MapColor.WOOD).strength(2.5F)
                        .sound(SoundType.WOOD)));
        LOGIC_MACHINE = register("logic_machine",
                p -> new LogicMachineBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL)));
        LOGIC_CABLE = register("logic_cable",
                p -> new LogicCableBlock(p.mapColor(MapColor.COLOR_GREEN).strength(1.0F)
                        .sound(SoundType.METAL).noOcclusion()
                        .pushReaction(net.minecraft.world.level.material.PushReaction.NORMAL)));
        LOGIC_GATE_BLOCK = register("logic_gate_block",
                p -> new LogicGateBlock(p.mapColor(MapColor.METAL).strength(2.5F)
                        .sound(SoundType.METAL)
                        .isRedstoneConductor((s, l, pos2) -> false)));
        GPIO_PORT = register("gpio_port",
                p -> new GPIOPortBlock(p.mapColor(MapColor.COLOR_BLUE).strength(2.0F)
                        .sound(SoundType.METAL)));
        DISPLAY = register("display",
                p -> new DisplayBlock(p.mapColor(MapColor.COLOR_BLACK).strength(2.0F)
                        .sound(SoundType.METAL).noOcclusion()));
        DISPLAY_MK2 = register("display_mk2",
                p -> new DisplayBlockMk2(p.mapColor(MapColor.COLOR_BLACK).strength(2.5F)
                        .sound(SoundType.METAL).noOcclusion()));
        DISPLAY_MK3 = register("display_mk3",
                p -> new DisplayBlockMk3(p.mapColor(MapColor.COLOR_BLACK).strength(3.0F)
                        .sound(SoundType.METAL).noOcclusion()));
        FLOPPY_DRIVE = register("floppy_drive",
                p -> new FloppyDriveBlock(p.mapColor(MapColor.COLOR_GRAY).strength(2.5F)
                        .sound(SoundType.METAL)));
        EXPANSION_SLOT = register("expansion_slot",
                p -> new ExpansionSlotBlock(p.mapColor(MapColor.COLOR_GRAY).strength(2.5F).noOcclusion()
                        .sound(SoundType.METAL)));
        REDSTONE_INTERSECTION = register("redstone_intersection_block",
                p -> new RedstoneIntersectionBlock(p.mapColor(MapColor.METAL).strength(2.5F)
                        .sound(SoundType.METAL)
                        .isRedstoneConductor((s, l, pos2) -> false)));
        KEYBOARD = register("keyboard",
                p -> new KeyboardBlock(p.mapColor(MapColor.METAL).strength(2.5F)
                        .sound(SoundType.METAL)));

        REACTOR_BLOCK = register("reactor_block",
                p -> new ReactorBlock(p.mapColor(MapColor.METAL).strength(5.0F)
                        .sound(SoundType.METAL)));
        REACTOR_PORT  = register("reactor_port",
                p -> new ReactorPort(p.mapColor(MapColor.METAL).strength(5.0F)
                        .sound(SoundType.METAL)));
        REACTOR_CELL  = register("reactor_cell",
                p -> new ReactorCell(p.mapColor(MapColor.METAL).strength(5.0F).noOcclusion()
                        .sound(SoundType.METAL)));
    }

    // ── Registration helpers ───────────────────────────────────────────────

    /**
     * Register an ore block backed by {@link OmniTechOreBlock} and enqueue it for worldgen datagen.
     * The config is stored in both the block instance (for legacy access) and the {@link OreEntry}.
     */
    public static DeferredBlock<OmniTechOreBlock> registerOre(
            String name,
            Function<BlockBehaviour.Properties, BlockBehaviour.Properties> props,
            OreSpawnConfig config) {
        DeferredBlock<OmniTechOreBlock> block = register(name,
                p -> new OmniTechOreBlock(props.apply(p)));
        ALL_ORES.add(new OreEntry(block, config));
        return block;
    }

    static <B extends Block> DeferredBlock<B> register(
            String name, Function<BlockBehaviour.Properties, ? extends B> supplier) {
        return REGISTRY.registerBlock(name, supplier);
    }
}
