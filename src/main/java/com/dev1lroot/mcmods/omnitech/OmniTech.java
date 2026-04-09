package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.BoilerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.ElectricCapacitorBlockEntity;
import net.minecraft.core.Direction;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.Profiler;
import com.dev1lroot.mcmods.omnitech.datagen.OmniTechDatagen;
import com.dev1lroot.mcmods.omnitech.blocks.SmelterBlockEntity;
import com.dev1lroot.mcmods.omnitech.recipes.BoilerRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.AlloyFurnaceRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.FoundryRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ManualCentrifugeRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ManualMaceratorRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.SmelterRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ElectrolysisRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.SolvationRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.RotaryCompressionRecipeManager;
import com.dev1lroot.mcmods.omnitech.blocks.ElectrolysisMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.ElectrolysisMachineBlock;
import com.dev1lroot.mcmods.omnitech.blocks.SolvationMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.SolvationMachineBlock;
import com.dev1lroot.mcmods.omnitech.blocks.RotaryCompressorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.RotaryCompressorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.FluidCollectorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.FluidCollectorBlock;
import com.dev1lroot.mcmods.omnitech.recipes.FluidCollectorRecipeManager;
import com.dev1lroot.mcmods.omnitech.blocks.HeatExchangerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.HeatExchangerBlock;
import com.dev1lroot.mcmods.omnitech.recipes.HeatExchangerRecipeManager;
import com.dev1lroot.mcmods.omnitech.blocks.DecompressorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.DecompressorBlock;
import com.dev1lroot.mcmods.omnitech.recipes.DecompressorRecipeManager;
import com.dev1lroot.mcmods.omnitech.blocks.FractionalDistillerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.FractionalDistillerBlock;
import com.dev1lroot.mcmods.omnitech.recipes.FractionalDistillationRecipeManager;
import com.dev1lroot.mcmods.omnitech.network.OpenRocketGuiPacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mod(OmniTech.MODID)
public class OmniTech {
    public static final String MODID = "omnitech";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OmniTech(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(OmniTechDatagen::gatherData);

        // Load material sets before registries fire so deferred entries are queued
        OmniTechMaterials.init();

        OmniTechFluids.register(modEventBus); // Добавьте это
        OmniTechEntities.register(modEventBus);
        OmniTechBlocks.REGISTRY.register(modEventBus);
        OmniTechBlockEntities.REGISTRY.register(modEventBus);
        OmniTechItems.REGISTRY.register(modEventBus);
        OmniTechMenuTypes.REGISTRY.register(modEventBus);
        OmniTechGUI.REGISTRY.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Expose ResourceHandler<FluidResource> on fluid pipes and tanks so pumps
        // and bucket interactions work via the NeoForge capability system.
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.FLUID_PIPE.get(),
                (be, side) -> be.fluidHandler);
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.FLUID_TANK.get(),
                (be, side) -> be.fluidHandler);
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.STIRLING_ENGINE.get(),
                (be, side) -> {
                    // Двигатель Стирлинга:
                    // Снизу отдаем воду (конденсат)
                    if (side == Direction.DOWN) {
                        return be.waterHandler;
                    }
                    // Со всех остальных сторон принимаем пар
                    return be.steamHandler;
                }
        );
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.BOILER.get(),
                (be, side) -> {
                    // Если труба подключена СВЕРХУ — даем доступ к баку пара
                    if (side == Direction.UP) {
                        return ((BoilerBlockEntity) be).steamHandler;
                    }
                    // Для всех остальных сторон (низ и бока) — даем доступ к баку воды
                    return ((BoilerBlockEntity) be).waterHandler;
                }
        );
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.SMELTER.get(),
                (be, side) -> ((SmelterBlockEntity) be).fluidHandler
        );
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.FOUNDRY.get(),
                (be, side) -> be.fluidHandler
        );
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.SOLVATION_MACHINE.get(),
                (be, side) -> {
                    if (side == null) return null;
                    // Determine facing from block state
                    var state = be.getLevel() != null
                            ? be.getLevel().getBlockState(be.getBlockPos())
                            : null;
                    if (state == null) return null;
                    Direction facing = state.getValue(SolvationMachineBlock.FACING);
                    if (side == facing) return ((SolvationMachineBlockEntity) be).inputFluidHandler;
                    if (side == facing.getOpposite()) return ((SolvationMachineBlockEntity) be).outputFluidHandler;
                    return null;
                }
        );
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.FLUID_COLLECTOR.get(),
                (be, side) -> {
                    if (side == null) return null;
                    var state = be.getLevel() != null
                            ? be.getLevel().getBlockState(be.getBlockPos()) : null;
                    if (state == null) return null;
                    // Only the output (FACING) face exposes the handler
                    if (side == state.getValue(FluidCollectorBlock.FACING))
                        return ((FluidCollectorBlockEntity) be).outputFluidHandler;
                    return null;
                }
        );
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.ROTARY_COMPRESSOR.get(),
                (be, side) -> {
                    if (side == null) return null;
                    var state = be.getLevel() != null
                            ? be.getLevel().getBlockState(be.getBlockPos())
                            : null;
                    if (state == null) return null;
                    Direction facing = state.getValue(RotaryCompressorBlock.FACING);
                    if (side == facing) return ((RotaryCompressorBlockEntity) be).inputFluidHandler;
                    if (side == facing.getOpposite()) return ((RotaryCompressorBlockEntity) be).outputFluidHandler;
                    return null;
                }
        );
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.ELECTROLYSIS_MACHINE.get(),
                (be, side) -> {
                    if (side == null) return null;
                    var state = be.getLevel() != null
                            ? be.getLevel().getBlockState(be.getBlockPos())
                            : null;
                    if (state == null) return null;
                    Direction facing = state.getValue(ElectrolysisMachineBlock.FACING);
                    if (side == facing)                      return ((ElectrolysisMachineBlockEntity) be).inputFluidHandler;
                    if (side == facing.getCounterClockWise()) return ((ElectrolysisMachineBlockEntity) be).anodeFluidHandler;
                    if (side == facing.getClockWise())        return ((ElectrolysisMachineBlockEntity) be).cathodeFluidHandler;
                    if (side == facing.getOpposite())         return ((ElectrolysisMachineBlockEntity) be).solutionFluidHandler;
                    return null;
                }
        );

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.HEAT_EXCHANGER.get(),
                (be, side) -> {
                    if (side == null) return null;
                    var state = be.getLevel() != null
                            ? be.getLevel().getBlockState(be.getBlockPos())
                            : null;
                    if (state == null) return null;
                    Direction facing = state.getValue(HeatExchangerBlock.FACING);
                    if (side == facing) return ((HeatExchangerBlockEntity) be).inputFluidHandler;
                    if (side == facing.getOpposite()) return ((HeatExchangerBlockEntity) be).outputFluidHandler;
                    return null;
                }
        );
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.DECOMPRESSOR.get(),
                (be, side) -> {
                    if (side == null) return null;
                    var state = be.getLevel() != null
                            ? be.getLevel().getBlockState(be.getBlockPos())
                            : null;
                    if (state == null) return null;
                    Direction facing = state.getValue(DecompressorBlock.FACING);
                    if (side == facing) return ((DecompressorBlockEntity) be).inputFluidHandler;
                    if (side == facing.getOpposite()) return ((DecompressorBlockEntity) be).outputFluidHandler;
                    return null;
                }
        );

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.FRACTIONAL_DISTILLER.get(),
                (be, side) -> {
                    if (side == null) return null;
                    var state = be.getLevel() != null
                            ? be.getLevel().getBlockState(be.getBlockPos())
                            : null;
                    if (state == null) return null;
                    FractionalDistillerBlockEntity fbe = (FractionalDistillerBlockEntity) be;
                    Direction facing = state.getValue(FractionalDistillerBlock.FACING);
                    // Front face: input, only on the bottom (master) block
                    if (side == facing && fbe.isBottomBlock()) return fbe.inputFluidHandler;
                    // Back face: output on every segment
                    if (side == facing.getOpposite()) return fbe.outputFluidHandler;
                    return null;
                }
        );

        // Electric Capacitor exposes the NeoForge EnergyHandler capability for
        // interoperability with other mods using NeoForge's energy system.
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                OmniTechBlockEntities.ELECTRIC_CAPACITOR.get(),
                (be, side) -> be.energyHandler
        );
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                OpenRocketGuiPacket.TYPE,
                OpenRocketGuiPacket.CODEC,
                OpenRocketGuiPacket::handle);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("OmniTech by Dev1lroot (c) 2026");
    }

    @SubscribeEvent
    public void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "boiler_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    BoilerRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "alloy_furnace_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    AlloyFurnaceRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "manual_macerator_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    ManualMaceratorRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "manual_centrifuge_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    ManualCentrifugeRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "smelter_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    SmelterRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "foundry_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    FoundryRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "solvation_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    SolvationRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "fluid_collector_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    FluidCollectorRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "compression_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    RotaryCompressionRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "electrolysis_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    ElectrolysisRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "heat_exchanger_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    HeatExchangerRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "decompressor_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    DecompressorRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "fractional_distiller_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    FractionalDistillationRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
    }
}
