/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.client.*;
import com.dev1lroot.mcmods.omnitech.client.GravityFieldManager;
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
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
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
import com.mojang.blaze3d.platform.InputConstants;
@Mod(value = OmniTech.MODID, dist = Dist.CLIENT)
public class OmniTechClient
{
    public static KeyMapping OPEN_ROCKET_GUI;
    public static KeyMapping PUSH_TO_TALK;
    /** Held to roll the view left / right around the look axis while floating free in zero-g. */
    public static KeyMapping ROLL_LEFT;
    public static KeyMapping ROLL_RIGHT;

    /** Zero-g roll speed while a roll key is held. */
    private static final float ROLL_DEGREES_PER_SECOND = 90f;
    private static long lastCameraFrameNanos = 0L;

    /** Smoothly-interpolated gravity rotation quaternion applied to the camera each frame. */
    private static final org.joml.Quaternionf cameraGravityQ = new org.joml.Quaternionf();

    public OmniTechClient(ModContainer container, IEventBus modEventBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::registerScreens);
        modEventBus.addListener(this::registerBlockEntityRenderers);
        modEventBus.addListener(this::registerLayerDefinitions);
        modEventBus.addListener(this::registerCustomEnvironmentRenderers);
        modEventBus.addListener(this::registerRenderPipelines);
        modEventBus.addListener(this::registerKeys);
        modEventBus.addListener(this::registerRenderStateModifiers);
        modEventBus.addListener(this::onAddClientReloadListeners);
        modEventBus.addListener(this::registerItemColors);
        modEventBus.addListener(this::registerBlockColors);
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
        NeoForge.EVENT_BUS.addListener(OmniTechClient::onComputeCameraAngles);
        NeoForge.EVENT_BUS.addListener(OmniTechClient::onScreenMouseScroll);
    }

    void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(PhaseDiagramTooltipData.class, PhaseDiagramClientTooltipComponent::new);
        event.register(com.dev1lroot.mcmods.omnitech.gui.tooltip.MoleculeStructureTooltipData.class,
                com.dev1lroot.mcmods.omnitech.gui.tooltip.MoleculeStructureClientTooltipComponent::new);
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
        event.registerEntityRenderer(OmniTechEntities.THROWN_TOMATO.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
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
        event.register(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(OmniTech.MODID, "mixture_dust_tint"),
                MixtureDustTintSource.MAP_CODEC);
    }

    /**
     * Biome-tints our foliage exactly like their vanilla counterparts:
     * {@code VANILLA_VINE}/{@code CINNAMON_LEAVES} use the same foliage-color
     * source as {@code Blocks.VINE}/{@code Blocks.OAK_LEAVES} etc., and
     * {@code NETTLE} uses the same double-tall-grass source as
     * {@code Blocks.TALL_GRASS}/{@code Blocks.LARGE_FERN}. Without this the
     * blocks render with their raw (grayish, pre-tint) textures.
     */
    void registerBlockColors(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(
                java.util.List.of(net.minecraft.client.color.block.BlockTintSources.foliage()),
                OmniTechBlocks.VANILLA_VINE.get(),
                OmniTechBlocks.CINNAMON_LEAVES.get(),
                OmniTechBlocks.LEMON_LEAVES.get());
        event.register(
                java.util.List.of(net.minecraft.client.color.block.BlockTintSources.doubleTallGrass()),
                OmniTechBlocks.NETTLE.get());
    }

    void registerItemModels(RegisterItemModelsEvent event) {
        event.register(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, "fluid_canister"),
                FluidCanisterItemModel.Unbaked.MAP_CODEC);
        event.register(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, "flask"),
                com.dev1lroot.mcmods.omnitech.client.FlaskItemModel.Unbaked.MAP_CODEC);
        event.register(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, "reaction_flask"),
                com.dev1lroot.mcmods.omnitech.client.ReactionFlaskItemModel.Unbaked.MAP_CODEC);
        event.register(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, "drinking_bottle"),
                com.dev1lroot.mcmods.omnitech.client.DrinkingBottleItemModel.Unbaked.MAP_CODEC);
        event.register(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, "pipette"),
                com.dev1lroot.mcmods.omnitech.client.PipetteItemModel.Unbaked.MAP_CODEC);
    }

    void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(SpaceMapSkyboxRenderer.SKY_BODY_PIPELINE);
    }

    /** Hands each player's gravity frame to their render state (see GravityPose). */
    void registerRenderStateModifiers(net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier() {
            @Override
            public <T extends net.minecraft.world.entity.Avatar & net.minecraft.client.entity.ClientAvatarEntity> void accept(
                    T avatar, net.minecraft.client.renderer.entity.state.AvatarRenderState state) {
                if (avatar instanceof net.minecraft.world.entity.player.Player player) {
                    com.dev1lroot.mcmods.omnitech.client.GravityPose.extract(player, state);
                }
            }
        });
    }

    void registerKeys(RegisterKeyMappingsEvent event) {
        KeyMapping.Category category = new KeyMapping.Category(
                Identifier.fromNamespaceAndPath(OmniTech.MODID, "categories"));
        event.registerCategory(category);
        OPEN_ROCKET_GUI = new KeyMapping(
                "key.omnitech.open_rocket_gui",
                InputConstants.KEY_G,
                category);
        event.register(OPEN_ROCKET_GUI);
        PUSH_TO_TALK = new KeyMapping(
                "key.omnitech.push_to_talk",
                InputConstants.UNKNOWN.getValue(),
                category);
        event.register(PUSH_TO_TALK);
        ROLL_LEFT = new KeyMapping(
                "key.omnitech.roll_left",
                InputConstants.KEY_Z,
                category);
        event.register(ROLL_LEFT);
        ROLL_RIGHT = new KeyMapping(
                "key.omnitech.roll_right",
                InputConstants.KEY_C,
                category);
        event.register(ROLL_RIGHT);
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
                    Minecraft.getInstance().gui.setScreen(new SpaceNavigationScreen());
                    return 1;
                })
        );
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("smiles")
                .then(net.minecraft.commands.Commands.argument("code",
                                com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String code = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "code");
                            try {
                                Minecraft.getInstance().gui.setScreen(
                                        com.dev1lroot.mcmods.omnitech.gui.FormulaViewerScreen.fromCode(code));
                            } catch (com.dev1lroot.mcmods.omnitech.chemistry.SmilesParser.ParseException e) {
                                if (Minecraft.getInstance().player != null) {
                                    Minecraft.getInstance().player.sendSystemMessage(
                                            net.minecraft.network.chat.Component.literal("Couldn't read that structural code: " + e.getMessage())
                                                    .withStyle(net.minecraft.ChatFormatting.RED));
                                }
                            }
                            return 1;
                        }))
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
        GravityFieldManager.clear();
        cameraGravityQ.identity();
        com.dev1lroot.mcmods.omnitech.util.PlayerFrames.clearClient();
        com.dev1lroot.mcmods.omnitech.client.GravityPose.clear();
        lastSentFrame = null;
    }

    /**
     * Scroll wheel while hovering a {@link com.dev1lroot.mcmods.omnitech.items.PipetteItem} in
     * any open inventory slot (the player's own inventory, a chest, a machine GUI, ...) adjusts
     * its draw amount by ±1 per notch instead of scrolling that slot's stack size — the same
     * mechanic as scrolling over a control rod slot in the Reactor screen to change its
     * insertion (see {@link com.dev1lroot.mcmods.omnitech.network.SetControlRodPacket}).
     */
    public static void onScreenMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> containerScreen)) return;

        net.minecraft.world.inventory.Slot hovered = containerScreen.getHoveredSlot();
        if (hovered == null || !hovered.hasItem()) return;

        net.minecraft.world.item.ItemStack stack = hovered.getItem();
        if (!(stack.getItem() instanceof com.dev1lroot.mcmods.omnitech.items.PipetteItem)) return;

        int delta = event.getScrollDeltaY() > 0 ? 1 : -1;
        com.dev1lroot.mcmods.omnitech.items.PipetteItem.setTargetAmount(stack,
                com.dev1lroot.mcmods.omnitech.items.PipetteItem.getTargetAmount(stack) + delta); // instant local feedback
        ClientPacketDistributor.sendToServer(
                new com.dev1lroot.mcmods.omnitech.network.SetPipetteAmountSlotPacket(
                        containerScreen.getMenu().containerId, hovered.index, delta));
        event.setCanceled(true);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // Guard covers both the "no world loaded" and "disconnecting" states.
        // Audio cleanup is handled by onLevelUnload; this return just keeps the
        // rest of the tick logic from running without a valid player/level.
        if (mc.player == null || mc.level == null) return;

        sendFrameToServer();

        // Deactivate keyboard capture if window loses focus
        if (KeyboardCaptureManager.isActive() && !mc.isWindowActive()) {
            KeyboardCaptureManager.deactivate();
        }

        if (mc.gui.screen() != null) {
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

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long now = System.nanoTime();
        float frameSeconds = lastCameraFrameNanos == 0L ? 0f : Math.min(0.1f, (now - lastCameraFrameNanos) / 1e9f);
        lastCameraFrameNanos = now;

        // Sample the field where the physics and the body pivot do: at the body's centre
        Vec3 feet = mc.player.getPosition(event.getPartialTick());
        Vec3 gv = GravityFieldManager.computeGravityVec(feet.x, feet.y + mc.player.getBbHeight() * 0.5, feet.z);
        double gLen = gv.length();
        boolean ignoresGravity = mc.player.isCreative() || mc.player.isSpectator();
        boolean zeroGDim = com.dev1lroot.mcmods.omnitech.util.GravityUtil.isZeroGravityDimension(mc.level);

        // Zero-g, outside every field: nothing defines "up", so the player keeps whatever
        // orientation they have (no easing back to upright) and may roll it around the view axis.
        boolean freeFloating = !ignoresGravity && zeroGDim && gLen < 0.01;
        GravityFieldManager.setFreeFloating(freeFloating);
        if (freeFloating) {
            int roll = (ROLL_RIGHT != null && ROLL_RIGHT.isDown() ? 1 : 0)
                     - (ROLL_LEFT  != null && ROLL_LEFT.isDown()  ? 1 : 0);
            if (roll != 0 && frameSeconds > 0f && mc.gui.screen() == null) {
                // The look direction is already in the rolled frame (EntityViewVectorGravityMixin).
                // Rotating the frame about it by +θ tips the camera's up toward its right: roll right.
                Vec3 look = mc.player.getViewVector(event.getPartialTick());
                Vector3f axis = new Vector3f((float) look.x, (float) look.y, (float) look.z).normalize();
                float angle = (float) Math.toRadians(ROLL_DEGREES_PER_SECOND * frameSeconds * roll);
                cameraGravityQ.premul(new org.joml.Quaternionf().rotationAxis(angle, axis)).normalize();
            }
            publishFrame(mc);
            return;
        }

        // Where the frame's "up" should point. In a field: away from the attractor — in a normal
        // dimension blended with world-up by the field strength α (the fringe tilts only partly);
        // in zero-g there is no world-up to blend with, so a weak field just aligns more slowly.
        // No field (or creative/spectator): world-up.
        Vector3f worldUp = new Vector3f(0f, 1f, 0f);
        Vector3f targetUp = new Vector3f(worldUp);
        float alpha = (float) Math.min(1.0, gLen);
        float rate = 1f;
        boolean backToUpright = ignoresGravity || gLen < 0.01;
        if (!backToUpright) {
            Vector3f gravUp = new Vector3f((float) (-gv.x / gLen), (float) (-gv.y / gLen), (float) (-gv.z / gLen));
            if (zeroGDim) {
                targetUp.set(gravUp);
                rate = alpha;
            } else {
                Vector3f blend = new Vector3f(worldUp).lerp(gravUp, alpha);
                targetUp.set(blend.lengthSquared() > 1e-4f ? blend.normalize() : gravUp);
            }
        }

        // Turn the current frame by the *smallest* rotation that brings its up onto the target.
        // Rebuilding the frame from world-up every frame instead (rotationTo(worldUp, gravUp))
        // flips unpredictably under an attractor (the axis of a 180° turn is undefined) and twists
        // the view's heading as the player walks around an asteroid.
        // Smoothing is per second, not per frame: 15%/frame at 60 fps, whatever the frame rate.
        Vector3f currentUp = cameraGravityQ.transform(new Vector3f(worldUp));
        float blendNow = 1f - (float) Math.pow(0.85, frameSeconds * 60f * rate);
        org.joml.Quaternionf step = new org.joml.Quaternionf().rotationTo(currentUp, targetUp);
        cameraGravityQ.premul(new org.joml.Quaternionf().slerp(step, blendNow)).normalize();

        // Upright again: whatever is left of the frame is a turn about world-up (heading picked
        // up walking around an attractor). Hand it to the player's yaw and drop the frame, so the
        // player is plain vanilla again — the view, WASD, the server and other players all agree.
        if (backToUpright && !com.dev1lroot.mcmods.omnitech.util.PlayerFrames.isUpright(cameraGravityQ)
                && cameraGravityQ.transform(new Vector3f(worldUp)).y > 0.99995f) {
            float twistDegrees = (float) Math.toDegrees(2.0 * Math.atan2(cameraGravityQ.y, cameraGravityQ.w));
            mc.player.setYRot(mc.player.getYRot() - twistDegrees);
            mc.player.yRotO -= twistDegrees;
            mc.player.setYHeadRot(mc.player.getYHeadRot() - twistDegrees);
            cameraGravityQ.identity();
        }
        publishFrame(mc);
    }

    /**
     * Shares the local player's frame: the camera mixins read it via
     * {@link GravityFieldManager#getGravityQ()}, rendering / aiming via
     * {@link com.dev1lroot.mcmods.omnitech.util.PlayerFrames}.
     */
    private static void publishFrame(Minecraft mc) {
        GravityFieldManager.setGravityQ(cameraGravityQ);
        com.dev1lroot.mcmods.omnitech.util.PlayerFrames.setLocal(mc.player.getId(), cameraGravityQ);
    }

    /** Last frame reported to the server, and ticks since, see {@link #sendFrameToServer}. */
    private static org.joml.Quaternionf lastSentFrame = null;
    private static int ticksSinceFrameSent = 0;

    /**
     * Reports the local player's frame to the server whenever it has turned by more than half a
     * degree (at most once a tick), plus a heartbeat every two seconds while not upright.
     */
    private static void sendFrameToServer() {
        org.joml.Quaternionf frame = GravityFieldManager.getGravityQ();
        ticksSinceFrameSent++;
        boolean turned = lastSentFrame == null
                || 2.0 * Math.acos(Math.min(1.0, Math.abs(frame.dot(lastSentFrame)))) > Math.toRadians(0.5);
        boolean heartbeat = ticksSinceFrameSent >= 40 && !com.dev1lroot.mcmods.omnitech.util.PlayerFrames.isUpright(frame);
        if (!turned && !heartbeat) return;
        ClientPacketDistributor.sendToServer(com.dev1lroot.mcmods.omnitech.network.PlayerFramePacket.of(frame));
        lastSentFrame = frame;
        ticksSinceFrameSent = 0;
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
        var stack = event.getItemStack();
        String formula = stack.get(OmniTechDataComponents.FORMULA.get());
        if (formula != null) {
            event.getToolTip().add(
                    Component.literal(formula).withStyle(ChatFormatting.DARK_GRAY));
        }

        net.minecraft.resources.Identifier id =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean shift = Minecraft.getInstance().hasShiftDown();
        boolean shiftGatedContent = false;

        // Machine blurb: any item with a "tooltip.omnitech.<path>.desc" lang key gets a
        // one-line description on shift. Which items show this is controlled entirely by which
        // lang keys exist — no separate registry to keep in sync.
        if (id != null && id.getNamespace().equals(OmniTech.MODID)) {
            String descKey = "tooltip.omnitech." + id.getPath() + ".desc";
            if (net.minecraft.locale.Language.getInstance().has(descKey)) {
                shiftGatedContent = true;
                if (shift) {
                    event.getToolTip().add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
                }
            }
        }

        // Real-world chemical composition, data-driven from data/omnitech/chemistry/*.json —
        // see ChemistryCompositionLoader. Applies to vanilla and modded items alike.
        List<ItemChemistryRegistry.CompoundAmount> compounds =
                id == null ? List.of() : ItemChemistryRegistry.get(id.toString());
        if (!compounds.isEmpty()) {
            shiftGatedContent = true;
            if (shift) {
                event.getToolTip().add(Component.translatable("tooltip.omnitech.composition")
                        .withStyle(ChatFormatting.GOLD));
                for (var c : compounds) {
                    event.getToolTip().add(Component.literal(
                                    "  " + c.displayName() + " — " + formatMmol(c.amountMmol()) + " mmol")
                            .withStyle(ChatFormatting.GREEN));
                }
            }
        }

        if (shiftGatedContent && !shift) {
            event.getToolTip().add(Component.translatable("tooltip.omnitech.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /** {@code 12500.0} → {@code "12,500"}; keeps a couple of decimals for non-whole amounts. */
    private static String formatMmol(double amount) {
        if (amount == Math.rint(amount) && Math.abs(amount) < 1.0e7) {
            return String.format("%,d", (long) amount);
        }
        return String.format("%,.2f", amount);
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
