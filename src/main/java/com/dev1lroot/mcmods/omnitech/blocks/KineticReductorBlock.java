package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;

/**
 * Kinetic Reductor — an omnidirectional kinetic-force junction block.
 *
 * <p>Unlike {@link KineticPipeBlock}, the reductor has no axis or rotation:
 * it accepts KF from any face and forwards it to all other faces regardless
 * of placement direction.  This lets pipes of different axes connect through
 * a single reductor without any alignment constraint.
 *
 * <p>While KF is flowing through it the {@code POWERED} blockstate is true,
 * which causes the block model to switch to an animated texture variant.
 * The animation is driven purely by Minecraft's built-in .mcmeta texture
 * animation system — no custom BlockEntityRenderer is needed.
 */
public class KineticReductorBlock extends BaseEntityBlock
{
    public static final MapCodec<KineticReductorBlock> CODEC = simpleCodec(KineticReductorBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty SIGNALED = BooleanProperty.create("signaled");

    public KineticReductorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(POWERED, false)
                .setValue(SIGNALED, false)
        );
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean moving) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, moving);

        // Проверяем, есть ли сигнал
        boolean hasSignal = level.hasNeighborSignal(pos);

        // Сравниваем с текущим состоянием SIGNALED, чтобы не спамить обновлениями блока
        if (state.getValue(SIGNALED) != hasSignal) {
            level.setBlock(pos, state.setValue(SIGNALED, hasSignal), 3);
        }
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return this.defaultBlockState().setValue(SIGNALED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    /** Use the standard block-model pipeline so .mcmeta texture animation works. */
    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KineticReductorBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, OmniTechBlockEntities.KF_REDUCTOR.get(),
                KineticReductorBlockEntity::serverTick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, SIGNALED);
    }
}
