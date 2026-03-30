package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.AlloyFurnaceBlockEntity;
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

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
