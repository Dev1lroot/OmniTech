package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlock;
import com.dev1lroot.mcmods.omnitech.client.ConveyorBeltRenderer;
import com.dev1lroot.mcmods.omnitech.client.CrankBlockEntityRenderer;
import com.dev1lroot.mcmods.omnitech.client.FluidPipeRenderer;
import com.dev1lroot.mcmods.omnitech.client.FluidTankRenderer;
import com.dev1lroot.mcmods.omnitech.client.KineticPipeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import java.util.List;

@Mod(value = OmniTech.MODID, dist = Dist.CLIENT)
public class OmniTechClient
{
    public OmniTechClient(ModContainer container, IEventBus modEventBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::registerScreens);
        modEventBus.addListener(this::registerBlockEntityRenderers);
        modEventBus.addListener(OmniTechClient::onRegisterBlockColors);
        modEventBus.register(OmniTechClient.class);
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
    }

    static void onRegisterBlockColors(RegisterColorHandlersEvent.BlockTintSources event) {
        // Tint layer 0: maps COLOR block-state (1-16) to the corresponding DyeColor.
        BlockTintSource pipeTrimTint = state -> {
            int colorIndex = state.getValue(FluidPipeBlock.COLOR);
            if (colorIndex == 0) return -1; // white / no tint
            DyeColor dye = DyeColor.values()[Math.clamp(colorIndex - 1, 0, DyeColor.values().length - 1)];
            return dye.getTextureDiffuseColor();
        };
        event.register(List.of(pipeTrimTint), OmniTechBlocks.FLUID_PIPE_TRIM.get());
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
    }
}
