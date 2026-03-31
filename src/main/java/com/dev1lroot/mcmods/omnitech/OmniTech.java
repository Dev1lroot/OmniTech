package com.dev1lroot.mcmods.omnitech;

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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.Profiler;
import com.dev1lroot.mcmods.omnitech.datagen.OmniTechDatagen;
import com.dev1lroot.mcmods.omnitech.recipes.AlloyFurnaceRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ManualCentrifugeRecipeManager;
import com.dev1lroot.mcmods.omnitech.recipes.ManualMaceratorRecipeManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mod(OmniTech.MODID)
public class OmniTech {
    public static final String MODID = "omnitech";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OmniTech(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(OmniTechDatagen::gatherData);

        OmniTechBlocks.REGISTRY.register(modEventBus);
        OmniTechBlockEntities.REGISTRY.register(modEventBus);
        OmniTechItems.REGISTRY.register(modEventBus);
        OmniTechMenuTypes.REGISTRY.register(modEventBus);
        OmniTechGUI.REGISTRY.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
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
        LOGGER.info("HELLO from server starting");
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
    }
}
