package com.dev1lroot.mcmods.omnitech.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Ghost block — holds the {@code manual_centrifuge_rotor} blockstate for BERI spinning rotor rendering. */
public class ManualCentrifugeRotorBlock extends Block {
    public ManualCentrifugeRotorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
}
