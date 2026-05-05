package com.dev1lroot.mcmods.omnitech.blocks.logic.display;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class DisplayBlockEntityMk2 extends DisplayBlockEntity {

    public DisplayBlockEntityMk2(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.DISPLAY_MK2.get(), pos, state, 64);
    }

    @Override
    protected String getContainerName() { return "container.omnitech.display_mk2"; }
}
