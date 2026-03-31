package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.AlloyFurnaceBlock;
import com.dev1lroot.mcmods.omnitech.blocks.CrankBlock;
import com.dev1lroot.mcmods.omnitech.blocks.ManualCentrifugeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.ManualMaceratorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.OmniTechOreBlock;
import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig;
import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig.BiomeOverride;
import com.dev1lroot.mcmods.omnitech.worldgen.OreSpawnConfig.GenerationConfig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class OmniTechBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(OmniTech.MODID);

    /** All ore blocks — iterated by datagen to generate worldgen JSON files. */
    public static final List<DeferredBlock<OmniTechOreBlock>> ALL_ORES = new ArrayList<>();

    public static final DeferredBlock<Block> EXAMPLE_BLOCK = REGISTRY.registerSimpleBlock(
            "example_block", p -> p.mapColor(MapColor.STONE));

    public static final DeferredBlock<Block> ALLOY_FURNACE;
    public static final DeferredBlock<Block> MANUAL_MACERATOR;
    public static final DeferredBlock<Block> MANUAL_CENTRIFUGE;
    public static final DeferredBlock<Block> CRANK;
    public static final DeferredBlock<OmniTechOreBlock> TIN_ORE;

    static {
        ALLOY_FURNACE = register("alloy_furnace", AlloyFurnaceBlock::new);
        MANUAL_MACERATOR = register("manual_macerator",
                p -> new ManualMaceratorBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        MANUAL_CENTRIFUGE = register("manual_centrifuge",
                p -> new ManualCentrifugeBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        CRANK = register("crank",
                p -> new CrankBlock(p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));

        TIN_ORE = registerOre("tin_ore",
                p -> p.mapColor(MapColor.STONE).strength(3.0F).sound(SoundType.STONE).requiresCorrectToolForDrops(),
                new OreSpawnConfig(
                        new GenerationConfig(
                                "#minecraft:is_overworld", // all overworld biomes
                                12, 64,                    // minY, maxY
                                8,                         // veinSize (max blocks per cluster)
                                1, 32                      // minCount, maxCount per chunk
                        ),
                        List.of(
                                new BiomeOverride(
                                        "#minecraft:is_mountain", // mountains get extra tin
                                        12, 32,                   // slightly higher ceiling
                                        8,                        // larger clusters
                                        1, 6                      // extra 1-6 clusters per chunk
                                ),
                                new BiomeOverride(
                                        "#minecraft:is_jungle",   // jungle / tropical biomes
                                        8, 50,                    // slightly lower floor
                                        64,                        // smaller clusters
                                        1,32                      // extra 1-4 clusters per chunk
                                )
                        )
                ));
    }

    // ── Registration helpers ───────────────────────────────────────────────

    private static DeferredBlock<OmniTechOreBlock> registerOre(
            String name,
            Function<BlockBehaviour.Properties, BlockBehaviour.Properties> props,
            OreSpawnConfig config) {
        DeferredBlock<OmniTechOreBlock> block = register(name,
                p -> new OmniTechOreBlock(props.apply(p), config));
        ALL_ORES.add(block);
        return block;
    }

    private static <B extends Block> DeferredBlock<B> register(
            String name, Function<BlockBehaviour.Properties, ? extends B> supplier) {
        return REGISTRY.registerBlock(name, supplier);
    }
}
