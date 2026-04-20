package com.dev1lroot.mcmods.omnitech.blocks.plumbing;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Ghost block that exists solely to host the {@code fluid_pipe_trim} blockstate
 * and its tinted overlay models. It is never given an item, never appears in any
 * creative tab, and is never meant to be placed in the world directly.
 *
 * <p>All block-state properties are shared with {@link FluidPipeBlock} so the
 * blockstate JSON multipart selectors can use identical conditions.
 */
public class FluidPipeTrimBlock extends Block {

    public FluidPipeTrimBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FluidPipeBlock.NORTH, false)
                .setValue(FluidPipeBlock.SOUTH, false)
                .setValue(FluidPipeBlock.EAST,  false)
                .setValue(FluidPipeBlock.WEST,  false)
                .setValue(FluidPipeBlock.UP,    false)
                .setValue(FluidPipeBlock.DOWN,  false)
                .setValue(FluidPipeBlock.COLOR, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(
                FluidPipeBlock.NORTH, FluidPipeBlock.SOUTH,
                FluidPipeBlock.EAST,  FluidPipeBlock.WEST,
                FluidPipeBlock.UP,    FluidPipeBlock.DOWN,
                FluidPipeBlock.COLOR);
    }
}
