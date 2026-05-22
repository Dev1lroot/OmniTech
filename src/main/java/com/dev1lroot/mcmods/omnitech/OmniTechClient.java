/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.client.*;
import com.dev1lroot.mcmods.omnitech.client.NuclearExplosionRenderer;
import com.dev1lroot.mcmods.omnitech.gui.tooltip.PhaseDiagramClientTooltipComponent;
import com.dev1lroot.mcmods.omnitech.gui.tooltip.PhaseDiagramTooltipData;
import com.dev1lroot.mcmods.omnitech.client.KeyboardCaptureManager;
import com.dev1lroot.mcmods.omnitech.blocks.analog.microphone.MicrophoneBlock;
import com.dev1lroot.mcmods.omnitech.blocks.analog.microphone.MicrophoneBlockEntity;
import com.dev1lroot.mcmods.omnitech.network.MicrophoneAudioPacket;
import com.dev1lroot.mcmods.omnitech.network.VoiceChatSendPacket;
import com.dev1lroot.mcmods.omnitech.entities.AbyssalEelRenderer;
import com.dev1lroot.mcmods.omnitech.entities.PenguinRenderer;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;
import com.dev1lroot.mcmods.omnitech.entities.CokeOvenEntityRenderer;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntityRenderer;
import com.dev1lroot.mcmods.omnitech.models.PenguinModel;
import com.dev1lroot.mcmods.omnitech.models.RocketModel;
import com.dev1lroot.mcmods.omnitech.gui.SpaceNavigationScreen;
import com.dev1lroot.mcmods.omnitech.gui.guidebook.GuidebookLoader;
import com.dev1lroot.mcmods.omnitech.network.OpenRocketGuiPacket;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;
@Mod(value = OmniTech.MODID, dist = Dist.CLIENT)
public class OmniTechClient
{
    public static KeyMapping OPEN_ROCKET_GUI;
    public static KeyMapping PUSH_TO_TALK;

    public OmniTechClient(ModContainer container, IEventBus modEventBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::registerScreens);
        modEventBus.addListener(this::registerBlockEntityRenderers);
        modEventBus.addListener(this::registerLayerDefinitions);
        modEventBus.addListener(this::registerCustomEnvironmentRenderers);
        modEventBus.addListener(this::registerRenderPipelines);
        modEventBus.addListener(this::registerKeys);
        modEventBus.addListener(this::onAddClientReloadListeners);
        modEventBus.addListener(this::registerItemColors);
        modEventBus.addListener(this::registerItemModels);
        modEventBus.addListener(this::registerGuiLayers);
        modEventBus.addListener(this::registerTooltipComponents);
        modEventBus.register(OmniTechClient.class);
        NeoForge.EVENT_BUS.addListener(OmniTechClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(KeyboardCaptureManager::onKeyInput);
        NeoForge.EVENT_BUS.addListener(OmniTechClient::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(OmniTechClient::registerClientCommands);
        NeoForge.EVENT_BUS.addListener(OmniTechClient::onSoundOptionsOpening);
        NeoForge.EVENT_BUS.addListener(SpaceSuitHudOverlay::onRenderGui);
        NeoForge.EVENT_BUS.addListener(RadioLocatorHudOverlay::onRenderGui);
        NeoForge.EVENT_BUS.addListener(OmniTechClient::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(NuclearExplosionRenderer::onSubmitGeometry);
        NeoForge.EVENT_BUS.addListener(NuclearExplosionRenderer::onClientTick);
    }

    void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(PhaseDiagramTooltipData.class, PhaseDiagramClientTooltipComponent::new);
    }

    void onClientSetup(FMLClientSetupEvent event) {
        OmniTech.LOGGER.info("HELLO FROM CLIENT SETUP");
        OmniTech.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    void registerScreens(RegisterMenuScreensEvent event) {
        OmniTechGUI.registerScreens(event);
    }

    void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(OmniTechBlockEntities.MANUAL_CENTRIFUGE.get(), ManualCentrifugeRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.ELECTRIC_ENGINE.get(), ElectricEngineRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.CRANK.get(), CrankBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.KF_PIPE.get(), KineticPipeRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.KF_REDUCTOR.get(), KineticReductorRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.CONVEYOR_BELT.get(), ConveyorBeltRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.FLUID_TANK.get(), FluidTankRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.FOUNDRY.get(), FoundryRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.LOGIC_GATE_BLOCK.get(), LogicGateRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.EXPANSION_SLOT.get(), ExpansionSlotRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.FLUID_PIPE.get(), FluidPipeRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.VALVE.get(), ValveRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.THERMAL_CONDUCTOR.get(), ThermalConductorBER::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.RADIATOR.get(), RadiatorBER::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.REACTOR.get(), ReactorBER::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.REACTOR_CELL.get(), ReactorCellBER::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.DISPLAY.get(), DisplayBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.DISPLAY_MK2.get(), DisplayBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.DISPLAY_MK3.get(), DisplayBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.ORRERY.get(), OrreryBlockEntityRenderer::new);
        event.registerEntityRenderer(OmniTechEntities.ROCKET.get(), RocketEntityRenderer::new);
        event.registerEntityRenderer(OmniTechEntities.PENGUIN.get(), PenguinRenderer::new);
        event.registerEntityRenderer(OmniTechEntities.ABYSSAL_EEL.get(), AbyssalEelRenderer::new);
        event.registerEntityRenderer(OmniTechEntities.COKE_OVEN.get(), CokeOvenEntityRenderer::new);
    }

    void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RocketModel.LAYER_LOCATION, RocketModel::createBodyLayer);
        event.registerLayerDefinition(PenguinModel.LAYER_LOCATION, PenguinModel::createBodyLayer);
    }

    void registerCustomEnvironmentRenderers(RegisterCustomEnvironmentEffectRendererEvent event) {
        event.registerSkyboxRenderer(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, "space_sky"),
                new SpaceMapSkyboxRenderer()
        );
    }

    void registerItemColors(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(OmniTech.MODID, "fluid_canister_tint"),
                FluidCanisterTintSource.MAP_CODEC);
    }

    void registerItemModels(RegisterItemModelsEvent event) {
        event.register(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, "fluid_canister"),
                FluidCanisterItemModel.Unbaked.MAP_CODEC);
    }

    void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(SpaceMapSkyboxRenderer.SKY_BODY_PIPELINE);
    }

    void registerKeys(RegisterKeyMappingsEvent event) {
        KeyMapping.Category category = new KeyMapping.Category(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, "categories"));
        event.registerCategory(category);
        OPEN_ROCKET_GUI = new KeyMapping(
                "key.omnitech.open_rocket_gui",
                GLFW.GLFW_KEY_G,
                category);
        event.register(OPEN_ROCKET_GUI);
        PUSH_TO_TALK = new KeyMapping(
                "key.omnitech.push_to_talk",
                GLFW.GLFW_KEY_UNKNOWN,
                category);
        event.register(PUSH_TO_TALK);
    }

    public static void onSoundOptionsOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen().getClass() == SoundOptionsScreen.class) {
            event.setNewScreen(new MicrophoneSoundOptionsScreen(
                    event.getCurrentScreen(),
                    Minecraft.getInstance().options));
        }
    }

    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("spacemap")
                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    Minecraft.getInstance().setScreen(new SpaceNavigationScreen());
                    return 1;
                })
        );
    }

    /**
     * Fires the instant the client level starts unloading (before the SoundEngine
     * destroys the OpenAL context).  Releasing the TargetDataLine here avoids a
     * deadlock where the OS refuses to hand the audio device to OpenAL while
     * JavaSound still holds it.  All three stop/closeAll methods are idempotent.
     */
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        KeyboardCaptureManager.deactivateLocal();
        MicrophoneCapture.stop();
        SpeakerAudioManager.closeAll();
        SpeakerToneManager.closeAll();
        VoiceAudioManager.closeAll();
        DisplayBlockEntityRenderer.cleanupAll();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // Guard covers both the "no world loaded" and "disconnecting" states.
        // Audio cleanup is handled by onLevelUnload; this return just keeps the
        // rest of the tick logic from running without a valid player/level.
        if (mc.player == null || mc.level == null) return;

        // Deactivate keyboard capture if window loses focus
        if (KeyboardCaptureManager.isActive() && !mc.isWindowActive()) {
            KeyboardCaptureManager.deactivate();
        }

        if (mc.screen != null) {
            // Any open screen (pause menu, inventory, etc.) stops mic capture immediately.
            // This releases the ALSA device well before the level unloads, avoiding a
            // race where our closer daemon thread still holds the device when Minecraft's
            // sound engine tries to destroy the OpenAL context.
            MicrophoneCapture.stop();
            return;
        }

        // Rocket inventory key — only send while the player is mounted in the rocket
        // and below orbit altitude (in ORBIT the space map is used instead).
        if (mc.player.getVehicle() instanceof RocketEntity && OPEN_ROCKET_GUI != null) {
            while (OPEN_ROCKET_GUI.consumeClick()) {
                ClientPacketDistributor.sendToServer(new OpenRocketGuiPacket());
            }
        }

        ReactorCoolantOverlay.tick(mc);

        long gameTime = mc.level.getGameTime();
        // Microphone block and voice chat capture — runs every 4 ticks
        if (gameTime % 4 == 0) tickMicrophoneCapture(mc);
        // Expire silent audio sources
        SpeakerAudioManager.tick(gameTime);
        SpeakerToneManager.tick(gameTime);
        VoiceAudioManager.tick(gameTime);
    }

    private static void tickMicrophoneCapture(Minecraft mc) {
        MicrophoneMode mode = MicrophoneConfig.getMode();
        List<BlockPos> nearbyMics = findNearbyMicrophones(mc);

        // Capture only while at least one Microphone block is loaded and in range.
        // Walking away from all blocks (or chunk unloading) stops the audio device.
        boolean captureActive = !nearbyMics.isEmpty()
                && mode != MicrophoneMode.DISABLED
                && (mode != MicrophoneMode.PUSH_TO_TALK || (PUSH_TO_TALK != null && PUSH_TO_TALK.isDown()));

        if (!captureActive) {
            MicrophoneCapture.stop();
            return;
        }

        if (!MicrophoneCapture.isRunning()) MicrophoneCapture.start();

        byte[] samples = MicrophoneCapture.drainSamples();
        if (samples.length == 0) return;

        for (BlockPos micPos : nearbyMics) {
            ClientPacketDistributor.sendToServer(new MicrophoneAudioPacket(micPos, samples));
        }

        ClientPacketDistributor.sendToServer(new VoiceChatSendPacket(samples));
    }

    /** Scans the area around the player for Microphone blocks within MAX_RANGE. */
    private static List<BlockPos> findNearbyMicrophones(Minecraft mc) {
        List<BlockPos> result = new ArrayList<>();
        int range = (int) MicrophoneBlockEntity.MAX_RANGE;
        BlockPos origin = mc.player.blockPosition();
        double rangeSq = (double) range * range;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range / 2; dy <= range / 2; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos check = origin.offset(dx, dy, dz);
                    if (mc.player.distanceToSqr(
                            check.getX() + 0.5, check.getY() + 0.5, check.getZ() + 0.5) > rangeSq)
                        continue;
                    if (mc.level.getBlockState(check).getBlock() instanceof MicrophoneBlock) {
                        result.add(check.immutable());
                    }
                }
            }
        }
        return result;
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        String formula = event.getItemStack().get(OmniTechDataComponents.FORMULA.get());
        if (formula != null) {
            event.getToolTip().add(
                    Component.literal(formula).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(OmniTech.MODID, "radiation_overlay"),
                new RadiationOverlay());
        event.registerAboveAll(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(OmniTech.MODID, "reactor_coolant_overlay"),
                new ReactorCoolantOverlay());
    }

    private void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(OmniTech.MODID, "guidebook"),
                new GuidebookLoader());
    }

    @SubscribeEvent
    public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        OmniTechFluids.all().forEach((name, fluid) ->
            event.register(new FluidModel.Unbaked(
                    new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/" + name + "_still")),
                    new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/" + name + "_flow")),
                    null,
                    null
            ), fluid.source.get(), fluid.flowing.get())
        );
    }
}
