package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.AlloyFurnaceBlock;
import com.dev1lroot.mcmods.omnitech.blocks.CrankBlock;
import com.dev1lroot.mcmods.omnitech.blocks.ManualCentrifugeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.ManualMaceratorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.OmniTechOreBlock;
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

    static {
        ALLOY_FURNACE = register("alloy_furnace", AlloyFurnaceBlock::new);
        MANUAL_MACERATOR = register("manual_macerator",
                p -> new ManualMaceratorBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        MANUAL_CENTRIFUGE = register("manual_centrifuge",
                p -> new ManualCentrifugeBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        CRANK = register("crank",
                p -> new CrankBlock(p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));
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
