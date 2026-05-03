package com.dev1lroot.mcmods.omnitech.blocks.logic;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Passive connector block that the LogicMachine uses to find GPIO ports via BFS. */
public class LogicCableBlock extends Block {
    public LogicCableBlock(BlockBehaviour.Properties props) { super(props); }
}
