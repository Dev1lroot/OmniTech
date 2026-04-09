package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlock;
import com.dev1lroot.mcmods.omnitech.client.ConveyorBeltRenderer;
import com.dev1lroot.mcmods.omnitech.client.CrankBlockEntityRenderer;
import com.dev1lroot.mcmods.omnitech.client.FluidPipeRenderer;
import com.dev1lroot.mcmods.omnitech.client.FluidTankRenderer;
import com.dev1lroot.mcmods.omnitech.client.KineticPipeRenderer;
import com.dev1lroot.mcmods.omnitech.client.ValveRenderer;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntityRenderer;
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
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
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
        modEventBus.addListener(this::registerKeys);
        modEventBus.register(OmniTechClient.class);
        NeoForge.EVENT_BUS.addListener(OmniTechClient::onClientTick);
    }

    void onClientSetup(FMLClientSetupEvent event) {
        OmniTech.LOGGER.info("HELLO FROM CLIENT SETUP");
        OmniTech.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    void registerScreens(RegisterMenuScreensEvent event) {
        OmniTechGUI.registerScreens(event);
    }

    void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(OmniTechBlockEntities.CRANK.get(), CrankBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.KF_PIPE.get(), KineticPipeRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.CONVEYOR_BELT.get(), ConveyorBeltRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.FLUID_TANK.get(), FluidTankRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.FLUID_PIPE.get(), FluidPipeRenderer::new);
        event.registerBlockEntityRenderer(OmniTechBlockEntities.VALVE.get(), ValveRenderer::new);
        event.registerEntityRenderer(OmniTechEntities.ROCKET.get(), RocketEntityRenderer::new);
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

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!(mc.player.getVehicle() instanceof RocketEntity)) return;

        while (OPEN_ROCKET_GUI != null && OPEN_ROCKET_GUI.consumeClick()) {
            ClientPacketDistributor.sendToServer(new OpenRocketGuiPacket());
        }
    }

    @SubscribeEvent
    public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        // Для пара
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/steam_still")),
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/steam_flow")),
                null,
                null
        ), OmniTechFluids.STEAM.source.get(), OmniTechFluids.STEAM.flowing.get());

        // Для латуни
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/molten_brass_still")),
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/molten_brass_flow")),
                null,
                null
        ), OmniTechFluids.MOLTEN_BRASS.source.get(), OmniTechFluids.MOLTEN_BRASS.flowing.get());

        // Для латуни
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/distilled_water_still")),
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/distilled_water_flow")),
                null,
                null
        ), OmniTechFluids.DISTILLED_WATER.source.get(), OmniTechFluids.DISTILLED_WATER.flowing.get());
        // Для латуни
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/distilled_water_still")),
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/distilled_water_flow")),
                null,
                null
        ), OmniTechFluids.AIR.source.get(), OmniTechFluids.AIR.flowing.get());
        // Для латуни
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/distilled_water_still")),
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/distilled_water_flow")),
                null,
                null
        ), OmniTechFluids.COMPRESSED_HEATED_AIR.source.get(), OmniTechFluids.COMPRESSED_HEATED_AIR.flowing.get());
        // Для латуни
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/distilled_water_still")),
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/distilled_water_flow")),
                null,
                null
        ), OmniTechFluids.COMPRESSED_AIR.source.get(), OmniTechFluids.COMPRESSED_AIR.flowing.get());

        // Для латуни
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/brine_still")),
                new Material(Identifier.fromNamespaceAndPath("omnitech", "block/fluid/brine_flow")),
                null,
                null
        ), OmniTechFluids.BRINE.source.get(), OmniTechFluids.BRINE.flowing.get());
    }
}
