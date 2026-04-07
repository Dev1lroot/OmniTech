package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.*;
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

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
