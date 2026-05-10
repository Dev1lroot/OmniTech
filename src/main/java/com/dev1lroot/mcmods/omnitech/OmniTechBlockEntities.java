package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.thermal.alloy_furnace.AlloyFurnaceBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.processing.centrifuge.ManualCentrifugeBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticGeneratorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticPipeBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticReductorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.labware.ElectrolysisMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.labware.FractionalDistillerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.labware.SolvationMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logistic.SorterBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.processing.macerator.ManualMaceratorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.*;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.boiler.BoilerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.labware.chemical_reactor.ChemicalReactorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logistic.conveyor_belt.ConveyorBeltBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.crank.CrankBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.pressure.decompressor.DecompressorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_capacitor.ElectricCapacitorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_charger.ElectricChargerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.labware.chemical_infuser.ChemicalInfuserBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.labware.extractor.ExtractorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.electric_heater.ElectricHeaterBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.radiator.RadiatorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_transmitter.RadioTransmitterBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_receiver.RadioReceiverBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_scanner.RadioScannerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.analog.microphone.MicrophoneBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.analog.speaker.SpeakerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockEntityMk2;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockEntityMk3;
import com.dev1lroot.mcmods.omnitech.blocks.logic.gpio_port.GPIOPortBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.programming_station.ProgrammingStationBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.research_table.ResearchTableBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_gate.LogicGateBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.LogicMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.thermal_conductor.ThermalConductorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_engine.ElectricEngineBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_furnace.ElectricFurnaceBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.*;
import com.dev1lroot.mcmods.omnitech.blocks.pressure.RotaryCompressorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.solar_panel.SolarPanelBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class OmniTechBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, OmniTech.MODID);

    public static final Supplier<BlockEntityType<AlloyFurnaceBlockEntity>> ALLOY_FURNACE =
            REGISTRY.register("alloy_furnace",
                    () -> new BlockEntityType<>(AlloyFurnaceBlockEntity::new, OmniTechBlocks.ALLOY_FURNACE.get()));

    public static final Supplier<BlockEntityType<ManualMaceratorBlockEntity>> MANUAL_MACERATOR =
            REGISTRY.register("manual_macerator",
                    () -> new BlockEntityType<>(ManualMaceratorBlockEntity::new, OmniTechBlocks.MANUAL_MACERATOR.get()));

    public static final Supplier<BlockEntityType<ManualCentrifugeBlockEntity>> MANUAL_CENTRIFUGE =
            REGISTRY.register("manual_centrifuge",
                    () -> new BlockEntityType<>(ManualCentrifugeBlockEntity::new, OmniTechBlocks.MANUAL_CENTRIFUGE.get()));

    public static final Supplier<BlockEntityType<CrankBlockEntity>> CRANK =
            REGISTRY.register("crank",
                    () -> new BlockEntityType<>(CrankBlockEntity::new, OmniTechBlocks.CRANK.get()));

    public static final Supplier<BlockEntityType<KineticGeneratorBlockEntity>> KF_GENERATOR =
            REGISTRY.register("kf_generator",
                    () -> new BlockEntityType<>(KineticGeneratorBlockEntity::new,
                            OmniTechBlocks.KF_GENERATOR.get()));

    public static final Supplier<BlockEntityType<KineticPipeBlockEntity>> KF_PIPE =
            REGISTRY.register("kf_pipe",
                    () -> new BlockEntityType<>(KineticPipeBlockEntity::new,
                            OmniTechBlocks.KF_PIPE.get()));

    public static final Supplier<BlockEntityType<KineticReductorBlockEntity>> KF_REDUCTOR =
            REGISTRY.register("kf_reductor",
                    () -> new BlockEntityType<>(KineticReductorBlockEntity::new,
                            OmniTechBlocks.KF_REDUCTOR.get()));

    public static final Supplier<BlockEntityType<HeaterBlockEntity>> HEATER =
            REGISTRY.register("heater",
                    () -> new BlockEntityType<>(HeaterBlockEntity::new,
                            OmniTechBlocks.HEATER.get()));

    public static final Supplier<BlockEntityType<StirlingEngineBlockEntity>> STIRLING_ENGINE =
            REGISTRY.register("stirling_engine",
                    () -> new BlockEntityType<>(StirlingEngineBlockEntity::new,
                            OmniTechBlocks.STIRLING_ENGINE.get()));

    public static final Supplier<BlockEntityType<ConveyorBeltBlockEntity>> CONVEYOR_BELT =
            REGISTRY.register("conveyor_belt",
                    () -> new BlockEntityType<>(ConveyorBeltBlockEntity::new,
                            OmniTechBlocks.CONVEYOR_BELT.get()));

    public static final Supplier<BlockEntityType<FluidPipeBlockEntity>> FLUID_PIPE =
            REGISTRY.register("fluid_pipe",
                    () -> new BlockEntityType<>(FluidPipeBlockEntity::new,
                            OmniTechBlocks.FLUID_PIPE.get()));

    public static final Supplier<BlockEntityType<PumpBlockEntity>> PUMP =
            REGISTRY.register("pump",
                    () -> new BlockEntityType<>(PumpBlockEntity::new,
                            OmniTechBlocks.PUMP.get()));

    public static final Supplier<BlockEntityType<FluidTankBlockEntity>> FLUID_TANK =
            REGISTRY.register("fluid_tank",
                    () -> new BlockEntityType<>(FluidTankBlockEntity::new,
                            OmniTechBlocks.FLUID_TANK.get()));

    public static final Supplier<BlockEntityType<BoilerBlockEntity>> BOILER =
            REGISTRY.register("boiler",
                    () -> new BlockEntityType<>(BoilerBlockEntity::new,
                            OmniTechBlocks.BOILER.get()));

    public static final Supplier<BlockEntityType<ValveBlockEntity>> VALVE =
            REGISTRY.register("valve",
                    () -> new BlockEntityType<>(ValveBlockEntity::new,
                            OmniTechBlocks.VALVE.get()));

    public static final Supplier<BlockEntityType<SorterBlockEntity>> SORTER =
            REGISTRY.register("sorter",
                    () -> new BlockEntityType<>(SorterBlockEntity::new,
                            OmniTechBlocks.SORTER.get()));

    public static final Supplier<BlockEntityType<SmelterBlockEntity>> SMELTER =
            REGISTRY.register("smelter",
                    () -> new BlockEntityType<>(SmelterBlockEntity::new,
                            OmniTechBlocks.SMELTER.get()));

    public static final Supplier<BlockEntityType<FoundryBlockEntity>> FOUNDRY =
            REGISTRY.register("foundry",
                    () -> new BlockEntityType<>(FoundryBlockEntity::new,
                            OmniTechBlocks.FOUNDRY.get()));

    public static final Supplier<BlockEntityType<ElectricEngineBlockEntity>> ELECTRIC_ENGINE =
            REGISTRY.register("electric_engine",
                    () -> new BlockEntityType<>(ElectricEngineBlockEntity::new,
                            OmniTechBlocks.ELECTRIC_ENGINE.get()));

    public static final Supplier<BlockEntityType<ElectricCapacitorBlockEntity>> ELECTRIC_CAPACITOR =
            REGISTRY.register("electric_capacitor",
                    () -> new BlockEntityType<>(ElectricCapacitorBlockEntity::new,
                            OmniTechBlocks.ELECTRIC_CAPACITOR.get()));

    public static final Supplier<BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE =
            REGISTRY.register("electric_furnace",
                    () -> new BlockEntityType<>(ElectricFurnaceBlockEntity::new,
                            OmniTechBlocks.ELECTRIC_FURNACE.get()));

    public static final Supplier<BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
            REGISTRY.register("solar_panel",
                    () -> new BlockEntityType<>(SolarPanelBlockEntity::new,
                            OmniTechBlocks.SOLAR_PANEL.get()));

    public static final Supplier<BlockEntityType<SolvationMachineBlockEntity>> SOLVATION_MACHINE =
            REGISTRY.register("solvation_machine",
                    () -> new BlockEntityType<>(SolvationMachineBlockEntity::new,
                            OmniTechBlocks.SOLVATION_MACHINE.get()));

    public static final Supplier<BlockEntityType<ElectrolysisMachineBlockEntity>> ELECTROLYSIS_MACHINE =
            REGISTRY.register("electrolysis_machine",
                    () -> new BlockEntityType<>(ElectrolysisMachineBlockEntity::new,
                            OmniTechBlocks.ELECTROLYSIS_MACHINE.get()));

    public static final Supplier<BlockEntityType<RotaryCompressorBlockEntity>> ROTARY_COMPRESSOR =
            REGISTRY.register("rotary_compressor",
                    () -> new BlockEntityType<>(RotaryCompressorBlockEntity::new,
                            OmniTechBlocks.ROTARY_COMPRESSOR.get()));

    public static final Supplier<BlockEntityType<FluidCollectorBlockEntity>> FLUID_COLLECTOR =
            REGISTRY.register("fluid_collector",
                    () -> new BlockEntityType<>(FluidCollectorBlockEntity::new,
                            OmniTechBlocks.FLUID_COLLECTOR.get()));

    public static final Supplier<BlockEntityType<HeatExchangerBlockEntity>> HEAT_EXCHANGER =
            REGISTRY.register("heat_exchanger",
                    () -> new BlockEntityType<>(HeatExchangerBlockEntity::new,
                            OmniTechBlocks.HEAT_EXCHANGER.get()));

    public static final Supplier<BlockEntityType<DecompressorBlockEntity>> DECOMPRESSOR =
            REGISTRY.register("decompressor",
                    () -> new BlockEntityType<>(DecompressorBlockEntity::new,
                            OmniTechBlocks.DECOMPRESSOR.get()));

    public static final Supplier<BlockEntityType<FractionalDistillerBlockEntity>> FRACTIONAL_DISTILLER =
            REGISTRY.register("fractional_distiller",
                    () -> new BlockEntityType<>(FractionalDistillerBlockEntity::new,
                            OmniTechBlocks.FRACTIONAL_DISTILLER.get()));

    public static final Supplier<BlockEntityType<ChemicalReactorBlockEntity>> CHEMICAL_REACTOR =
            REGISTRY.register("chemical_reactor",
                    () -> new BlockEntityType<>(ChemicalReactorBlockEntity::new,
                            OmniTechBlocks.CHEMICAL_REACTOR.get()));

    public static final Supplier<BlockEntityType<FluidFillerBlockEntity>> FLUID_FILLER =
            REGISTRY.register("fluid_filler",
                    () -> new BlockEntityType<>(FluidFillerBlockEntity::new,
                            OmniTechBlocks.FLUID_FILLER.get()));

    public static final Supplier<BlockEntityType<ElectricChargerBlockEntity>> ELECTRIC_CHARGER =
            REGISTRY.register("electric_charger",
                    () -> new BlockEntityType<>(ElectricChargerBlockEntity::new,
                            OmniTechBlocks.ELECTRIC_CHARGER.get()));

    public static final Supplier<BlockEntityType<ChemicalInfuserBlockEntity>> CHEMICAL_INFUSER =
            REGISTRY.register("chemical_infuser",
                    () -> new BlockEntityType<>(ChemicalInfuserBlockEntity::new,
                            OmniTechBlocks.CHEMICAL_INFUSER.get()));

    public static final Supplier<BlockEntityType<ExtractorBlockEntity>> EXTRACTOR =
            REGISTRY.register("extractor",
                    () -> new BlockEntityType<>(ExtractorBlockEntity::new,
                            OmniTechBlocks.EXTRACTOR.get()));

    public static final Supplier<BlockEntityType<RadiatorBlockEntity>> RADIATOR =
            REGISTRY.register("radiator",
                    () -> new BlockEntityType<>(RadiatorBlockEntity::new,
                            OmniTechBlocks.RADIATOR.get()));

    public static final Supplier<BlockEntityType<ThermalConductorBlockEntity>> THERMAL_CONDUCTOR =
            REGISTRY.register("thermal_conductor",
                    () -> new BlockEntityType<>(ThermalConductorBlockEntity::new,
                            OmniTechBlocks.THERMAL_CONDUCTOR.get()));

    public static final Supplier<BlockEntityType<ElectricHeaterBlockEntity>> ELECTRIC_HEATER =
            REGISTRY.register("electric_heater",
                    () -> new BlockEntityType<>(ElectricHeaterBlockEntity::new,
                            OmniTechBlocks.ELECTRIC_HEATER.get()));

    public static final Supplier<BlockEntityType<RadioTransmitterBlockEntity>> RADIO_TRANSMITTER =
            REGISTRY.register("radio_transmitter",
                    () -> new BlockEntityType<>(RadioTransmitterBlockEntity::new,
                            OmniTechBlocks.RADIO_TRANSMITTER.get()));

    public static final Supplier<BlockEntityType<RadioReceiverBlockEntity>> RADIO_RECEIVER =
            REGISTRY.register("radio_receiver",
                    () -> new BlockEntityType<>(RadioReceiverBlockEntity::new,
                            OmniTechBlocks.RADIO_RECEIVER.get()));

    public static final Supplier<BlockEntityType<RadioScannerBlockEntity>> RADIO_SCANNER =
            REGISTRY.register("radio_scanner",
                    () -> new BlockEntityType<>(RadioScannerBlockEntity::new,
                            OmniTechBlocks.RADIO_SCANNER.get()));

    public static final Supplier<BlockEntityType<MicrophoneBlockEntity>> MICROPHONE =
            REGISTRY.register("microphone",
                    () -> new BlockEntityType<>(MicrophoneBlockEntity::new,
                            OmniTechBlocks.MICROPHONE.get()));

    public static final Supplier<BlockEntityType<SpeakerBlockEntity>> SPEAKER =
            REGISTRY.register("speaker",
                    () -> new BlockEntityType<>(SpeakerBlockEntity::new,
                            OmniTechBlocks.SPEAKER.get()));

    public static final Supplier<BlockEntityType<ProgrammingStationBlockEntity>> PROGRAMMING_STATION =
            REGISTRY.register("programming_station",
                    () -> new BlockEntityType<>(ProgrammingStationBlockEntity::new,
                            OmniTechBlocks.PROGRAMMING_STATION.get()));

    public static final Supplier<BlockEntityType<ResearchTableBlockEntity>> RESEARCH_TABLE =
            REGISTRY.register("research_table",
                    () -> new BlockEntityType<>(ResearchTableBlockEntity::new,
                            OmniTechBlocks.RESEARCH_TABLE.get()));

    public static final Supplier<BlockEntityType<LogicMachineBlockEntity>> LOGIC_MACHINE =
            REGISTRY.register("logic_machine",
                    () -> new BlockEntityType<>(LogicMachineBlockEntity::new,
                            OmniTechBlocks.LOGIC_MACHINE.get()));

    public static final Supplier<BlockEntityType<LogicGateBlockEntity>> LOGIC_GATE_BLOCK =
            REGISTRY.register("logic_gate_block",
                    () -> new BlockEntityType<>(LogicGateBlockEntity::new,
                            OmniTechBlocks.LOGIC_GATE_BLOCK.get()));

    public static final Supplier<BlockEntityType<GPIOPortBlockEntity>> GPIO_PORT =
            REGISTRY.register("gpio_port",
                    () -> new BlockEntityType<>(GPIOPortBlockEntity::new,
                            OmniTechBlocks.GPIO_PORT.get()));

    public static final Supplier<BlockEntityType<DisplayBlockEntity>> DISPLAY =
            REGISTRY.register("display",
                    () -> new BlockEntityType<>(DisplayBlockEntity::new,
                            OmniTechBlocks.DISPLAY.get()));

    public static final Supplier<BlockEntityType<DisplayBlockEntityMk2>> DISPLAY_MK2 =
            REGISTRY.register("display_mk2",
                    () -> new BlockEntityType<>(DisplayBlockEntityMk2::new,
                            OmniTechBlocks.DISPLAY_MK2.get()));

    public static final Supplier<BlockEntityType<DisplayBlockEntityMk3>> DISPLAY_MK3 =
            REGISTRY.register("display_mk3",
                    () -> new BlockEntityType<>(DisplayBlockEntityMk3::new,
                            OmniTechBlocks.DISPLAY_MK3.get()));

    public static final Supplier<BlockEntityType<com.dev1lroot.mcmods.omnitech.blocks.logic.floppy_drive.FloppyDriveBlockEntity>> FLOPPY_DRIVE =
            REGISTRY.register("floppy_drive",
                    () -> new BlockEntityType<>(
                            com.dev1lroot.mcmods.omnitech.blocks.logic.floppy_drive.FloppyDriveBlockEntity::new,
                            OmniTechBlocks.FLOPPY_DRIVE.get()));

    public static final Supplier<BlockEntityType<com.dev1lroot.mcmods.omnitech.blocks.logic.expansion_slot.ExpansionSlotBlockEntity>> EXPANSION_SLOT =
            REGISTRY.register("expansion_slot",
                    () -> new BlockEntityType<>(
                            com.dev1lroot.mcmods.omnitech.blocks.logic.expansion_slot.ExpansionSlotBlockEntity::new,
                            OmniTechBlocks.EXPANSION_SLOT.get()));

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
