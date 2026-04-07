package com.dev1lroot.mcmods.omnitech.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Ghost block used only as a model host for the valve's spinning wheel overlay.
 * Never placed in the world directly — no item, no loot table.
 *
 * <p>The block-state properties mirror {@link ValveBlock} so the BER can build
 * an identically-shaped state and feed it to {@code MovingBlockRenderState}.
 */
public class ValveWheelBlock extends Block
{
    public ValveWheelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(ValveBlock.FACING,   net.minecraft.core.Direction.NORTH)
                .setValue(ValveBlock.VERTICAL, false)
                .setValue(ValveBlock.LEVEL,    0));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ValveBlock.FACING, ValveBlock.VERTICAL, ValveBlock.LEVEL);
    }
}
