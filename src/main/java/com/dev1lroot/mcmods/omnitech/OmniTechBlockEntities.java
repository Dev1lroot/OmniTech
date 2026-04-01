package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.AlloyFurnaceBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.CrankBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.KineticGeneratorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.KineticPipeBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.KineticReductorBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.ManualCentrifugeBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.ManualMaceratorBlockEntity;
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

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
