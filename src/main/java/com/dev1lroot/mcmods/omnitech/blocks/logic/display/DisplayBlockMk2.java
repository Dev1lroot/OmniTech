package com.dev1lroot.mcmods.omnitech.blocks.logic.display;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DisplayBlockMk2 extends DisplayBlock {

    public static final MapCodec<DisplayBlockMk2> CODEC = simpleCodec(DisplayBlockMk2::new);

    public DisplayBlockMk2(Properties props) { super(props); }

    @Override
    protected MapCodec<? extends DisplayBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayBlockEntityMk2(pos, state);
    }
}
