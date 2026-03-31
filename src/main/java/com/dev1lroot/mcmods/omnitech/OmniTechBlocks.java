package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.AlloyFurnaceBlock;
import com.dev1lroot.mcmods.omnitech.blocks.CrankBlock;
import com.dev1lroot.mcmods.omnitech.blocks.ManualMaceratorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

public class OmniTechBlocks {
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(OmniTech.MODID);

    public static final DeferredBlock<Block> EXAMPLE_BLOCK = REGISTRY.registerSimpleBlock(
            "example_block", p -> p.mapColor(MapColor.STONE));

    public static final DeferredBlock<Block> ALLOY_FURNACE;
    public static final DeferredBlock<Block> MANUAL_MACERATOR;
    public static final DeferredBlock<Block> CRANK;

    static {
        ALLOY_FURNACE = register("alloy_furnace", AlloyFurnaceBlock::new);
        MANUAL_MACERATOR = register("manual_macerator",
                p -> new ManualMaceratorBlock(p.mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE)));
        CRANK = register("crank",
                p -> new CrankBlock(p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));
    }

    private static <B extends Block> DeferredBlock<B> register(String name, Function<BlockBehaviour.Properties, ? extends B> supplier) {
        return REGISTRY.registerBlock(name, supplier);
    }
}
