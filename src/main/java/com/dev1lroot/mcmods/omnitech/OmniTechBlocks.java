package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.*;
import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class OmniTechBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(OmniTech.MODID);

    /**
     * All ore entries — iterated by datagen to generate worldgen JSON files.
     * Each entry pairs a block reference with its spawn configuration.
     * Populated by {@link #registerOre} and by {@link MaterialSet} for MaterialSet-based ores.
     */
    public static final List<OreEntry> ALL_ORES = new ArrayList<>();

    /** Pairs a block with its world-generation spawn configuration. */
    public record OreEntry(DeferredBlock<? extends Block> block, OreSpawnConfig config) {
        public String name() { return block.getId().getPath(); }
    }

    public static final DeferredBlock<Block> EXAMPLE_BLOCK = REGISTRY.registerSimpleBlock(
            "example_block", p -> p.mapColor(MapColor.STONE));

    public static final DeferredBlock<Block> ALLOY_FURNACE;
    public static final DeferredBlock<Block> MANUAL_MACERATOR;
    public static final DeferredBlock<Block> MANUAL_CENTRIFUGE;
    public static final DeferredBlock<Block> CRANK;

    public static final DeferredBlock<Block> KF_GENERATOR;
    public static final DeferredBlock<Block> KF_PIPE;
    public static final DeferredBlock<Block> KF_REDUCTOR;

    public static final DeferredBlock<Block> HEATER;
    public static final DeferredBlock<Block> STIRLING_ENGINE;

    public static final DeferredBlock<Block> CONVEYOR_BELT;

    public static final DeferredBlock<Block> FLUID_PIPE;
    public static final DeferredBlock<Block> PUMP;
    public static final DeferredBlock<Block> FLUID_TANK;
    public static final DeferredBlock<Block> BOILER;
    public static final DeferredBlock<Block> SORTER;
    public static final DeferredBlock<Block> SMELTER;

    static {
        ALLOY_FURNACE = register("alloy_furnace", AlloyFurnaceBlock::new);
        MANUAL_MACERATOR = register("manual_macerator",
                p -> new ManualMaceratorBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        MANUAL_CENTRIFUGE = register("manual_centrifuge",
                p -> new ManualCentrifugeBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        CRANK = register("crank",
                p -> new CrankBlock(p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));

        KF_GENERATOR = register("kf_generator",
                p -> new KineticGeneratorBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        KF_PIPE = register("kf_pipe",
                p -> new KineticPipeBlock(p.mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).noOcclusion()));
        KF_REDUCTOR = register("kf_reductor",
                p -> new KineticReductorBlock(p
                        .mapColor(MapColor.METAL)
                        .strength(3.5F)
                        .sound(SoundType.METAL)));

        HEATER = register("heater",
                p -> new HeaterBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        STIRLING_ENGINE = register("stirling_engine",
                p -> new StirlingEngineBlock(p.mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL)));

        CONVEYOR_BELT = register("conveyor_belt",
                p -> new ConveyorBeltBlock(p.mapColor(MapColor.METAL).strength(2.5F)
                        .sound(SoundType.METAL).noOcclusion()));

        FLUID_PIPE = register("fluid_pipe",
                p -> new FluidPipeBlock(p.mapColor(MapColor.METAL).strength(2.0F)
                        .sound(SoundType.METAL).noOcclusion()));
        PUMP = register("pump",
                p -> new PumpBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL).noOcclusion()));
        FLUID_TANK = register("fluid_tank",
                p -> new FluidTankBlock(p
                        .mapColor(MapColor.METAL)
                        .strength(3.0F)
                        .sound(SoundType.METAL)
                        .noOcclusion()
                        .isViewBlocking((state, level, pos) -> false)
                ));
        BOILER = register("boiler",
                p -> new BoilerBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL).noOcclusion()));
        SORTER = register("sorter",
                p -> new SorterBlock(p.mapColor(MapColor.METAL).strength(3.0F)
                        .sound(SoundType.METAL)));
        SMELTER = register("smelter",
                p -> new SmelterBlock(p.mapColor(MapColor.METAL).strength(4.0F)
                        .sound(SoundType.METAL)));
    }

    // ── Registration helpers ───────────────────────────────────────────────

    /**
     * Register an ore block backed by {@link OmniTechOreBlock} and enqueue it for worldgen datagen.
     * The config is stored in both the block instance (for legacy access) and the {@link OreEntry}.
     */
    public static DeferredBlock<OmniTechOreBlock> registerOre(
            String name,
            Function<BlockBehaviour.Properties, BlockBehaviour.Properties> props,
            OreSpawnConfig config) {
        DeferredBlock<OmniTechOreBlock> block = register(name,
                p -> new OmniTechOreBlock(props.apply(p), config));
        ALL_ORES.add(new OreEntry(block, config));
        return block;
    }

    static <B extends Block> DeferredBlock<B> register(
            String name, Function<BlockBehaviour.Properties, ? extends B> supplier) {
        return REGISTRY.registerBlock(name, supplier);
    }
}
