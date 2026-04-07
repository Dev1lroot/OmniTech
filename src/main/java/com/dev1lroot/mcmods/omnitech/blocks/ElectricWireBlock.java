package com.dev1lroot.mcmods.omnitech.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * Electric Wire — an omnidirectional conductor for the electric network.
 *
 * <p>The wire has no block entity. The {@link com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil}
 * BFS detects this block type by instanceof check and traverses through it in
 * all 6 directions, acting as a passive connector between EU producers
 * and consumers.
 *
 * <p>The wire renders as a thin 4×4×16 cross-bar shape (matching the
 * {@code electric_wire} block model).
 */
public class ElectricWireBlock extends Block {
    public static final MapCodec<ElectricWireBlock> CODEC = simpleCodec(ElectricWireBlock::new);

    private static final VoxelShape SHAPE = Block.box(6, 6, 0, 10, 10, 16);

    public ElectricWireBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }
}
