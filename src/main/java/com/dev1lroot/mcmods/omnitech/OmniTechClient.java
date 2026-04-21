package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.client.FluidCanisterItemModel;
import com.dev1lroot.mcmods.omnitech.client.FluidCanisterTintSource;
import com.dev1lroot.mcmods.omnitech.client.ConveyorBeltRenderer;
import com.dev1lroot.mcmods.omnitech.client.CrankBlockEntityRenderer;
import com.dev1lroot.mcmods.omnitech.client.ManualCentrifugeRenderer;
import com.dev1lroot.mcmods.omnitech.client.FluidPipeRenderer;
import com.dev1lroot.mcmods.omnitech.client.FluidTankRenderer;
import com.dev1lroot.mcmods.omnitech.client.FoundryRenderer;
import com.dev1lroot.mcmods.omnitech.client.KineticPipeRenderer;
import com.dev1lroot.mcmods.omnitech.client.ValveRenderer;
import com.dev1lroot.mcmods.omnitech.client.SpaceMapSkyboxRenderer;
import com.dev1lroot.mcmods.omnitech.client.SpaceSuitHudOverlay;
import com.dev1lroot.mcmods.omnitech.entities.AbyssalEelRenderer;
import com.dev1lroot.mcmods.omnitech.entities.CokeOvenEntityRenderer;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntityRenderer;
import com.dev1lroot.mcmods.omnitech.models.RocketModel;
import com.dev1lroot.mcmods.omnitech.gui.SpaceNavigationScreen;
import com.dev1lroot.mcmods.omnitech.network.OpenRocketGuiPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
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

    public OmniTechClient(ModContainer container, IEventBus modEventBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::registerScreens);
        modEventBus.addListener(this::registerBlockEntityRenderers);
        modEventBus.addListener(this::registerLayerDefinitions);
        modEventBus.addListener(this::registerCustomEnvironmentRenderers);
        modEventBus.addListener(this::registerRenderPipelines);
        modEventBus.addListener(this::registerKeys);
        modEventBus.addListener(this::registerItemColors);
        modEventBus.addListener(this::registerItemModels);
        modEventBus.register(OmniTechClient.class);
        NeoForge.EVENT_BUS.addListener(OmniTechClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(OmniTechClient::registerClientCommands);
        NeoForge.EVENT_BUS.addListener(SpaceSuitHudOverlay::onRenderGui);
        NeoForge.EVENT_BUS.addListener(OmniTechClient::onItemTooltip);
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
        event.registerBlockEntityRenderer(OmniTechBlockEntities.CRANK.get(), CrankBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.KF_PIPE.get(), KineticPipeRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.CONVEYOR_BELT.get(), ConveyorBeltRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.FLUID_TANK.get(), FluidTankRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.FOUNDRY.get(), FoundryRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.FLUID_PIPE.get(), FluidPipeRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.VALVE.get(), ValveRenderer::new);
        event.registerEntityRenderer(OmniTechEntities.ROCKET.get(), RocketEntityRenderer::new);
        event.registerEntityRenderer(OmniTechEntities.ABYSSAL_EEL.get(), AbyssalEelRenderer::new);
        event.registerEntityRenderer(OmniTechEntities.COKE_OVEN.get(), CokeOvenEntityRenderer::new);
    }

    void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RocketModel.LAYER_LOCATION, RocketModel::createBodyLayer);
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

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        // Rocket inventory key — opens the rocket's container GUI.
        // Handled server-side; only send while the player is mounted in the rocket
        // and below orbit altitude (in ORBIT the space map is used instead).
        if (mc.player.getVehicle() instanceof RocketEntity
                && OPEN_ROCKET_GUI != null) {
            while (OPEN_ROCKET_GUI.consumeClick()) {
                ClientPacketDistributor.sendToServer(new OpenRocketGuiPacket());
            }
        }
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        String formula = event.getItemStack().get(OmniTechDataComponents.FORMULA.get());
        if (formula != null) {
            event.getToolTip().add(
                    Component.literal(formula).withStyle(ChatFormatting.DARK_GRAY));
        }
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
