/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.boiler.BoilerBlockEntity;
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
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import com.dev1lroot.mcmods.omnitech.datagen.OmniTechDatagen;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.SmelterBlockEntity;
import com.dev1lroot.mcmods.omnitech.recipes.AlloyFurnaceRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.FoundryRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ManualCentrifugeRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ManualMaceratorRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.SmelterRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ElectrolysisRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.SolvationRecipeManager;
import com.dev1lroot.mcmods.omnitech.blocks.labware.ElectrolysisMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.labware.ElectrolysisMachineBlock;
import com.dev1lroot.mcmods.omnitech.blocks.labware.SolvationMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.labware.SolvationMachineBlock;
import com.dev1lroot.mcmods.omnitech.blocks.pressure.RotaryCompressorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.pressure.RotaryCompressorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidCollectorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidCollectorBlock;
import com.dev1lroot.mcmods.omnitech.recipes.FluidCollectorRecipeManager;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.HeatExchangerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.HeatExchangerBlock;
import com.dev1lroot.mcmods.omnitech.blocks.pressure.decompressor.DecompressorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.pressure.decompressor.DecompressorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.labware.FractionalDistillerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.labware.FractionalDistillerBlock;
import com.dev1lroot.mcmods.omnitech.recipes.FractionalDistillationRecipeManager;
import com.dev1lroot.mcmods.omnitech.blocks.labware.chemical_reactor.ChemicalReactorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.labware.chemical_reactor.ChemicalReactorBlock;
import com.dev1lroot.mcmods.omnitech.recipes.ChemicalReactorRecipeManager;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidFillerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidFillerBlock;
import com.dev1lroot.mcmods.omnitech.recipes.CokingRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ChemicalInfuserRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ExtractorRecipeManager;
import com.dev1lroot.mcmods.omnitech.rocket.RocketStructureLoader;
import com.dev1lroot.mcmods.omnitech.network.NuclearExplosionFxPacket;
import com.dev1lroot.mcmods.omnitech.network.GravityFieldSyncPacket;
import com.dev1lroot.mcmods.omnitech.blocks.space.gravitation_source.GravitationSourceBlockEntity;
import com.dev1lroot.mcmods.omnitech.network.RadiationSyncPacket;
import com.dev1lroot.mcmods.omnitech.radiation.RadiationSavedData;
import com.dev1lroot.mcmods.omnitech.network.OpenRocketGuiPacket;
import com.dev1lroot.mcmods.omnitech.network.RocketOrbitPacket;
import com.dev1lroot.mcmods.omnitech.network.SpaceTravelPacket;
import com.dev1lroot.mcmods.omnitech.network.SetRadioFrequencyPacket;
import com.dev1lroot.mcmods.omnitech.network.RadioScannerRowPacket;
import com.dev1lroot.mcmods.omnitech.network.MicrophoneAudioPacket;
import com.dev1lroot.mcmods.omnitech.network.SpeakerPlayPacket;
import com.dev1lroot.mcmods.omnitech.network.VoiceChatSendPacket;
import com.dev1lroot.mcmods.omnitech.network.VoiceChatReceivePacket;
import com.dev1lroot.mcmods.omnitech.network.SetRadioLocatorFreqPacket;
import com.dev1lroot.mcmods.omnitech.network.RadioLocatorSignalPacket;
import com.dev1lroot.mcmods.omnitech.network.UploadProgramPacket;
import com.dev1lroot.mcmods.omnitech.network.FlashRomPacket;
import com.dev1lroot.mcmods.omnitech.network.SetGPIOIdPacket;
import com.dev1lroot.mcmods.omnitech.network.SetDisplayIdPacket;
import com.dev1lroot.mcmods.omnitech.network.AssembleTruthTablePacket;
import com.dev1lroot.mcmods.omnitech.network.SetFloppyDriveIdPacket;
import com.dev1lroot.mcmods.omnitech.network.TerminalInputPacket;
import com.dev1lroot.mcmods.omnitech.network.KeyboardModePacket;
import com.dev1lroot.mcmods.omnitech.network.KeyboardReleasePacket;
import com.dev1lroot.mcmods.omnitech.network.MinesweeperResultPacket;
import com.dev1lroot.mcmods.omnitech.network.SpeakerTonePacket;
import com.dev1lroot.mcmods.omnitech.network.DepressurizeReactorPacket;
import com.dev1lroot.mcmods.omnitech.network.ScramReactorPacket;
import com.dev1lroot.mcmods.omnitech.network.StartReactorPacket;
import com.dev1lroot.mcmods.omnitech.network.SetMachineValuePacket;
import com.dev1lroot.mcmods.omnitech.network.SetControlRodPacket;
import com.dev1lroot.mcmods.omnitech.blocks.logic.keyboard.KeyboardBlock;
import com.dev1lroot.mcmods.omnitech.blocks.radio.FrequencyBand;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioManager;
import com.dev1lroot.mcmods.omnitech.items.RadioLocatorItem;
import com.dev1lroot.mcmods.omnitech.entities.AbyssalEelEntity;
import com.dev1lroot.mcmods.omnitech.entities.PenguinEntity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import com.dev1lroot.mcmods.omnitech.worldgen.OmniTechCarvers;
import com.dev1lroot.mcmods.omnitech.worldgen.OmniTechFeatures;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import com.dev1lroot.mcmods.omnitech.client.FluidCanisterResourceHandler;
import java.util.ArrayList;
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

        // Load blocks and ore spawn configs before registries fire so deferred entries are queued
        BlockLoader.loadAll();
        OreSpawnLoader.loadAll();   // must run after BlockLoader
        ToolSetLoader.loadAll();
        ArmorSetLoader.loadAll();
        ResearchLoader.loadAll();   // reads data/omnitech/research/*.json
        AssemblerLoader.loadAll();  // reads data/omnitech/assembler/*.json

        FluidLoader.loadAll();           // reads data/omnitech/fluid/*.json
        OmniTechFluids.register(modEventBus);
        OmniTechEntities.register(modEventBus);
        OmniTechFeatures.register(modEventBus);
        OmniTechCarvers.register(modEventBus);
        OmniTechBlocks.REGISTRY.register(modEventBus);
        OmniTechBlockEntities.REGISTRY.register(modEventBus);
        ItemLoader.loadAll();             // reads data/omnitech/item/*.json
        OmniTechItems.REGISTRY.register(modEventBus);
        OmniTechMenuTypes.REGISTRY.register(modEventBus);
        CreativeTabLoader.loadAll();     // reads data/omnitech/creative_tab/*.json
        OmniTechGUI.register(modEventBus);
        OmniTechDataComponents.register(modEventBus);
        OmniTechRecipeSerializers.register(modEventBus);
        OmniTechAttachments.register(modEventBus);
        OmniTechMobEffects.register(modEventBus);
        OmniTechSounds.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(com.dev1lroot.mcmods.omnitech.radiation.RadiationTick.class);
        NeoForge.EVENT_BUS.register(FluidHazardTick.class);
        NeoForge.EVENT_BUS.addListener(OmniTech::registerCommands);
        NeoForge.EVENT_BUS.addListener(OmniTech::onServerTick);
        NeoForge.EVENT_BUS.addListener(OmniTech::onServerStopping);
        NeoForge.EVENT_BUS.addListener(OmniTech::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(OmniTech::onPlayerLoggedOut);
        modEventBus.addListener(OmniTech::registerAttributes);
        modEventBus.addListener(OmniTechEntities::registerSpawnPlacements);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, com.dev1lroot.mcmods.omnitech.client.MicrophoneConfig.SPEC, "omnitech-microphone-client.toml");
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
                (be, side) -> ((BoilerBlockEntity) be).fluidHandler
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
                OmniTechBlockEntities.FILTER_PRESS.get(),
                (be, side) -> {
                    if (side == null) return null;
                    var state = be.getLevel() != null
                            ? be.getLevel().getBlockState(be.getBlockPos())
                            : null;
                    if (state == null) return null;
                    Direction facing = state.getValue(com.dev1lroot.mcmods.omnitech.blocks.labware.FilterPressBlock.FACING);
                    if (side == facing) return ((com.dev1lroot.mcmods.omnitech.blocks.labware.FilterPressBlockEntity) be).inputFluidHandler;
                    if (side == facing.getOpposite()) return ((com.dev1lroot.mcmods.omnitech.blocks.labware.FilterPressBlockEntity) be).outputFluidHandler;
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
                OmniTechBlockEntities.CHEMICAL_REACTOR.get(),
                (be, side) -> {
                    if (side == null) return null;
                    var state = be.getLevel() != null
                            ? be.getLevel().getBlockState(be.getBlockPos()) : null;
                    if (state == null) return null;
                    ChemicalReactorBlockEntity reactor = (ChemicalReactorBlockEntity) be;
                    Direction facing = state.getValue(ChemicalReactorBlock.FACING);
                    // Back face: output
                    if (side == facing.getOpposite()) return reactor.outputFluidHandler;
                    // Front, left, right: input (combined handler)
                    if (side == facing
                            || side == facing.getCounterClockWise()
                            || side == facing.getClockWise()) return reactor.anyInputHandler;
                    return null;
                }
        );

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.FLUID_FILLER.get(),
                (be, side) -> {
                    if (side == null) return null;
                    var state = be.getLevel() != null
                            ? be.getLevel().getBlockState(be.getBlockPos()) : null;
                    if (state == null) return null;
                    FluidFillerBlockEntity filler = (FluidFillerBlockEntity) be;
                    Direction facing = state.getValue(FluidFillerBlock.FACING);
                    if (side == facing)              return filler.inputFluidHandler;
                    if (side == facing.getOpposite()) return filler.outputFluidHandler;
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

        // Conveyor belt exposes its item slot as an ItemResource handler so that
        // capability-based machines (e.g. CokeOven) can push/pull items via the
        // NeoForge transfer API.
        event.registerBlockEntity(
                Capabilities.Item.BLOCK,
                OmniTechBlockEntities.CONVEYOR_BELT.get(),
                (be, side) -> be.getItemHandler(side)
        );

        // Electric Capacitor exposes the NeoForge EnergyHandler capability for
        // interoperability with other mods using NeoForge's energy system.
        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                OmniTechBlockEntities.ELECTRIC_CAPACITOR.get(),
                (be, side) -> be.energyHandler
        );

        // Fluid Canister exposes its stored fluid via the item capability so that
        // FluidUtil.getFirstStackContained (used by DynamicFluidContainerModel) can
        // read the fluid at render time.
        event.registerItem(
                Capabilities.Fluid.ITEM,
                (stack, access) -> new FluidCanisterResourceHandler(access),
                OmniTechItems.FLUID_CANISTER.get()
        );

        // Flask exposes its stored fluid the same way, so Fluid Tank / Fluid Filler
        // right-click filling works on it automatically with no new interaction code —
        // its ResourceHandler's isValid() rejects fluids it can't hold.
        event.registerItem(
                Capabilities.Fluid.ITEM,
                (stack, access) -> new com.dev1lroot.mcmods.omnitech.client.FlaskResourceHandler(access),
                OmniTechItems.FLASK.get()
        );

        // Pipette exposes its stored fluid the same way, purely so DynamicFluidContainerModel
        // has something to read the current fluid + tint from when rendering.
        event.registerItem(
                Capabilities.Fluid.ITEM,
                (stack, access) -> new com.dev1lroot.mcmods.omnitech.client.PipetteResourceHandler(access),
                OmniTechItems.PIPETTE.get()
        );

        // Reactor master block exposes its distilled-water coolant tank
        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.REACTOR.get(),
                (be, side) -> be.getCoolantHandler()
        );
        // Reactor ports delegate to the nearest formed master
        event.registerBlock(
                Capabilities.Fluid.BLOCK,
                (level, pos, state, be, side) -> ReactorBlockEntity.findCoolantHandler(level, pos),
                OmniTechBlocks.REACTOR_PORT.get()
        );

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.CHEMICAL_INFUSER.get(),
                (be, side) -> {
                    if (side == null) return null;
                    var state = be.getLevel() != null
                            ? be.getLevel().getBlockState(be.getBlockPos()) : null;
                    if (state == null) return null;
                    Direction facing = state.getValue(
                            com.dev1lroot.mcmods.omnitech.blocks.labware.chemical_infuser.ChemicalInfuserBlock.FACING);
                    if (side == facing) return ((com.dev1lroot.mcmods.omnitech.blocks.labware.chemical_infuser.ChemicalInfuserBlockEntity) be).inputFluidHandler;
                    return null;
                }
        );

        event.registerBlockEntity(
                Capabilities.Fluid.BLOCK,
                OmniTechBlockEntities.EXTRACTOR.get(),
                (be, side) -> {
                    if (side == null) return null;
                    var state = be.getLevel() != null
                            ? be.getLevel().getBlockState(be.getBlockPos()) : null;
                    if (state == null) return null;
                    Direction facing = state.getValue(
                            com.dev1lroot.mcmods.omnitech.blocks.labware.extractor.ExtractorBlock.FACING);
                    if (side == facing) return ((com.dev1lroot.mcmods.omnitech.blocks.labware.extractor.ExtractorBlockEntity) be).outputFluidHandler;
                    return null;
                }
        );

    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                OpenRocketGuiPacket.TYPE,
                OpenRocketGuiPacket.CODEC,
                OpenRocketGuiPacket::handle);
        event.registrar("1").playToServer(
                SpaceTravelPacket.TYPE,
                SpaceTravelPacket.CODEC,
                SpaceTravelPacket::handle);
        event.registrar("1").playToClient(
                RocketOrbitPacket.TYPE,
                RocketOrbitPacket.CODEC,
                RocketOrbitPacket::handle);
        event.registrar("1").playToServer(
                SetRadioFrequencyPacket.TYPE,
                SetRadioFrequencyPacket.CODEC,
                SetRadioFrequencyPacket::handle);
        event.registrar("1").playToClient(
                RadioScannerRowPacket.TYPE,
                RadioScannerRowPacket.CODEC,
                RadioScannerRowPacket::handle);
        event.registrar("1").playToServer(
                MicrophoneAudioPacket.TYPE,
                MicrophoneAudioPacket.CODEC,
                MicrophoneAudioPacket::handle);
        event.registrar("1").playToClient(
                SpeakerPlayPacket.TYPE,
                SpeakerPlayPacket.CODEC,
                SpeakerPlayPacket::handle);
        event.registrar("1").playToServer(
                VoiceChatSendPacket.TYPE,
                VoiceChatSendPacket.CODEC,
                VoiceChatSendPacket::handle);
        event.registrar("1").playToClient(
                VoiceChatReceivePacket.TYPE,
                VoiceChatReceivePacket.CODEC,
                VoiceChatReceivePacket::handle);
        event.registrar("1").playToServer(
                SetRadioLocatorFreqPacket.TYPE,
                SetRadioLocatorFreqPacket.CODEC,
                SetRadioLocatorFreqPacket::handle);
        event.registrar("1").playToClient(
                RadioLocatorSignalPacket.TYPE,
                RadioLocatorSignalPacket.CODEC,
                RadioLocatorSignalPacket::handle);
        event.registrar("1").playToServer(
                UploadProgramPacket.TYPE,
                UploadProgramPacket.CODEC,
                UploadProgramPacket::handle);
        event.registrar("1").playToServer(
                SetGPIOIdPacket.TYPE,
                SetGPIOIdPacket.CODEC,
                SetGPIOIdPacket::handle);
        event.registrar("1").playToServer(
                SetDisplayIdPacket.TYPE,
                SetDisplayIdPacket.CODEC,
                SetDisplayIdPacket::handle);
        event.registrar("1").playToServer(
                SetFloppyDriveIdPacket.TYPE,
                SetFloppyDriveIdPacket.CODEC,
                SetFloppyDriveIdPacket::handle);
        event.registrar("1").playToServer(
                AssembleTruthTablePacket.TYPE,
                AssembleTruthTablePacket.CODEC,
                AssembleTruthTablePacket::handle);
        event.registrar("1").playToServer(
                TerminalInputPacket.TYPE,
                TerminalInputPacket.CODEC,
                TerminalInputPacket::handle);
        event.registrar("1").playToServer(
                FlashRomPacket.TYPE,
                FlashRomPacket.CODEC,
                FlashRomPacket::handle);
        event.registrar("1").playToClient(
                KeyboardModePacket.TYPE,
                KeyboardModePacket.CODEC,
                KeyboardModePacket::handle);
        event.registrar("1").playToServer(
                KeyboardReleasePacket.TYPE,
                KeyboardReleasePacket.CODEC,
                KeyboardReleasePacket::handle);
        event.registrar("1").playToServer(
                MinesweeperResultPacket.TYPE,
                MinesweeperResultPacket.CODEC,
                MinesweeperResultPacket::handle);
        event.registrar("1").playToClient(
                SpeakerTonePacket.TYPE,
                SpeakerTonePacket.CODEC,
                SpeakerTonePacket::handle);
        event.registrar("1").playToServer(
                SetControlRodPacket.TYPE,
                SetControlRodPacket.CODEC,
                SetControlRodPacket::handle);
        event.registrar("1").playToServer(
                ScramReactorPacket.TYPE,
                ScramReactorPacket.CODEC,
                ScramReactorPacket::handle);
        event.registrar("1").playToServer(
                DepressurizeReactorPacket.TYPE,
                DepressurizeReactorPacket.CODEC,
                DepressurizeReactorPacket::handle);
        event.registrar("1").playToServer(
                StartReactorPacket.TYPE,
                StartReactorPacket.CODEC,
                StartReactorPacket::handle);
        event.registrar("1").playToServer(
                SetMachineValuePacket.TYPE,
                SetMachineValuePacket.CODEC,
                SetMachineValuePacket::handle);
        event.registrar("1").playToServer(
                com.dev1lroot.mcmods.omnitech.network.FluidFillerInjectPacket.TYPE,
                com.dev1lroot.mcmods.omnitech.network.FluidFillerInjectPacket.CODEC,
                com.dev1lroot.mcmods.omnitech.network.FluidFillerInjectPacket::handle);
        event.registrar("1").playToServer(
                com.dev1lroot.mcmods.omnitech.network.SetPipetteAmountPacket.TYPE,
                com.dev1lroot.mcmods.omnitech.network.SetPipetteAmountPacket.CODEC,
                com.dev1lroot.mcmods.omnitech.network.SetPipetteAmountPacket::handle);
        event.registrar("1").playToServer(
                com.dev1lroot.mcmods.omnitech.network.SetPipetteAmountSlotPacket.TYPE,
                com.dev1lroot.mcmods.omnitech.network.SetPipetteAmountSlotPacket.CODEC,
                com.dev1lroot.mcmods.omnitech.network.SetPipetteAmountSlotPacket::handle);
        event.registrar("1").playToServer(
                com.dev1lroot.mcmods.omnitech.network.PrintFormulaPacket.TYPE,
                com.dev1lroot.mcmods.omnitech.network.PrintFormulaPacket.CODEC,
                com.dev1lroot.mcmods.omnitech.network.PrintFormulaPacket::handle);
        event.registrar("1").playToClient(
                NuclearExplosionFxPacket.TYPE,
                NuclearExplosionFxPacket.CODEC,
                NuclearExplosionFxPacket::handle);
        event.registrar("1").playToClient(
                RadiationSyncPacket.TYPE,
                RadiationSyncPacket.CODEC,
                RadiationSyncPacket::handle);
        event.registrar("1").playToClient(
                GravityFieldSyncPacket.TYPE,
                GravityFieldSyncPacket.CODEC,
                GravityFieldSyncPacket::handle);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    /**
     * Registers {@code /warpjump <dimension>} — an operator shorthand for
     * {@code /execute in <dimension> run tp @s ~ ~ ~}.
     *
     * <p>Teleports the executing player to the same XYZ position in the target
     * dimension. Requires permission level 2 (gamemaster / op).  The dimension
     * argument is validated by {@link DimensionArgument} and provides tab
     * completion for all dimensions registered on the server.
     */
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(OmniTechEntities.PENGUIN.get(),     PenguinEntity.createAttributes().build());
        event.put(OmniTechEntities.ABYSSAL_EEL.get(), AbyssalEelEntity.createAttributes().build());
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("warpjump")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        ServerLevel  target = DimensionArgument.getDimension(ctx, "dimension");
                        Vec3         pos    = player.position();

                        player.teleport(new TeleportTransition(
                                target,
                                pos,
                                Vec3.ZERO,
                                player.getYRot(),
                                player.getXRot(),
                                TeleportTransition.DO_NOTHING
                        ));
                        return 1;
                    })
                )
        );
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        long tick = event.getServer().getTickCount();

        // Send gravity field data to clients once per second
        if (tick % 20 == 0) {
            for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
                ArrayList<BlockPos> posList     = new ArrayList<>();
                ArrayList<Integer>  outerRadii  = new ArrayList<>();
                ArrayList<Integer>  innerRadii  = new ArrayList<>();
                double px = sp.getX(), py = sp.getY(), pz = sp.getZ();
                double maxRangeSq = 150.0 * 150.0;
                for (var entry : GravitationSourceBlockEntity.SERVER_ACTIVE_SOURCES.entrySet()) {
                    if (!entry.getValue().dimension().equals(sp.level().dimension())) continue;
                    BlockPos bp = entry.getKey();
                    double dx = bp.getX() + 0.5 - px;
                    double dy = bp.getY() + 0.5 - py;
                    double dz = bp.getZ() + 0.5 - pz;
                    if (dx*dx + dy*dy + dz*dz <= maxRangeSq) {
                        posList.add(bp);
                        outerRadii.add(entry.getValue().outerRadius());
                        innerRadii.add(entry.getValue().innerRadius());
                    }
                }
                PacketDistributor.sendToPlayer(sp, new GravityFieldSyncPacket(posList, outerRadii, innerRadii));
            }
        }

        if (tick % 10 != 0) return;

        for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
            ItemStack stack = null;
            InteractionHand hand = null;
            ItemStack main = sp.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack off  = sp.getItemInHand(InteractionHand.OFF_HAND);
            if (main.getItem() instanceof RadioLocatorItem) { stack = main; hand = InteractionHand.MAIN_HAND; }
            else if (off.getItem() instanceof RadioLocatorItem) { stack = off; hand = InteractionHand.OFF_HAND; }
            if (stack == null) continue;

            int globalKey = stack.getOrDefault(OmniTechDataComponents.RADIO_LOCATOR_FREQ.get(),
                    FrequencyBand.VHF.globalKey(0));

            var positions = RadioManager.getTransmitterPositions(sp.level().dimension(), globalKey);
            float signal = 0f;
            double rangeSq = RadioLocatorItem.MAX_RANGE * RadioLocatorItem.MAX_RANGE;
            double px = sp.getX(), py = sp.getY(), pz = sp.getZ();
            for (BlockPos txPos : positions) {
                double dx = txPos.getX() + 0.5 - px;
                double dy = txPos.getY() + 0.5 - py;
                double dz = txPos.getZ() + 0.5 - pz;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > rangeSq) continue;
                float s = (float) (15.0 * (1.0 - Math.sqrt(distSq) / RadioLocatorItem.MAX_RANGE));
                signal = Math.max(signal, s);
            }
            PacketDistributor.sendToPlayer(sp, new RadioLocatorSignalPacket(signal));
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            KeyboardBlock.clearSession(sp.getUUID());
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!sp.getData(OmniTechAttachments.GUIDEBOOK_GIVEN)) {
            sp.getInventory().add(new ItemStack(OmniTechItems.GUIDEBOOK.get()));
            sp.setData(OmniTechAttachments.GUIDEBOOK_GIVEN, true);
        }
        RadiationSavedData radData = RadiationSavedData.get((ServerLevel) sp.level());
        if (!radData.getCenters().isEmpty()) {
            PacketDistributor.sendToPlayer(sp, new RadiationSyncPacket(new ArrayList<>(radData.getCenters())));
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[OmniTech/server] ServerStoppingEvent – server is shutting down, starting watchdog");
        Thread watchdog = new Thread(() -> {
            for (int attempt = 1; attempt <= 5; attempt++) {
                try { Thread.sleep(10_000); } catch (InterruptedException e) { return; }
                LOGGER.error("[OmniTech/server] *** SERVER SHUTDOWN HANGING ({}0 s elapsed) – dumping all thread stacks ***", attempt);
                Thread.getAllStackTraces().forEach((t, stack) -> {
                    LOGGER.error("  Thread '{}' daemon={} state={}", t.getName(), t.isDaemon(), t.getState());
                    for (StackTraceElement frame : stack)
                        LOGGER.error("    at {}", frame);
                });
            }
        }, "omnitech-server-stop-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("OmniTech by Dev1lroot (c) 2026");
        RadioManager.clearAll();
    }

    @SubscribeEvent
    public void onAddReloadListener(AddServerReloadListenersEvent event) {
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
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "filter_press_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    com.dev1lroot.mcmods.omnitech.recipes.FilterPressRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "glass_blowing_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    com.dev1lroot.mcmods.omnitech.recipes.GlassBlowingRecipeManager.loadRecipes(sharedState.resourceManager());
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
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "electrolysis_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    ElectrolysisRecipeManager.loadRecipes(sharedState.resourceManager());
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
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "chemical_reactor_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    ChemicalReactorRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "coking_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    CokingRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "chemical_infuser_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    ChemicalInfuserRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "extractor_recipes"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    ExtractorRecipeManager.loadRecipes(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
        event.addListener(Identifier.fromNamespaceAndPath(MODID, "rocket_structures"), new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState sharedState, Executor taskExecutor,
                    PreparationBarrier barrier, Executor reloadExecutor) {
                return CompletableFuture.runAsync(() -> {
                    RocketStructureLoader.loadStructures(sharedState.resourceManager());
                }, taskExecutor).thenCompose(barrier::wait);
            }
        });
    }
}
