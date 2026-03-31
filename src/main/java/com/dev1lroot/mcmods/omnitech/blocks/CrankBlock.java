package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CrankBlock extends BaseEntityBlock {
    public static final MapCodec<CrankBlock> CODEC = simpleCodec(CrankBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Small flat shape — just the crank sitting on top of the macerator
    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 4, 13);

    public CrankBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // All rendering is handled by the BlockEntityRenderer
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrankBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, OmniTechBlockEntities.CRANK.get(), CrankBlockEntity::clientTick);
        }
        return createTickerHelper(type, OmniTechBlockEntities.CRANK.get(), CrankBlockEntity::serverTick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrankBlockEntity crank)) return InteractionResult.PASS;

        if (crank.isBusy()) {
            // Still spinning — ignore click
            return InteractionResult.SUCCESS;
        }

        // Try to give kinetic force to the macerator directly below
        BlockPos maceratorPos = pos.below();
        BlockEntity below = level.getBlockEntity(maceratorPos);
        if (below instanceof ManualMaceratorBlockEntity macerator) {
            boolean accepted = macerator.addKineticForce(1);
            if (accepted) {
                crank.startSpin();
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.9f + level.getRandom().nextFloat() * 0.2f);
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof ManualMaceratorBlock;
    }
}
