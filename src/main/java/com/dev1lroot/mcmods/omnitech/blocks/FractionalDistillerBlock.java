package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * One segment of a Fractional Distiller multiblock.
 *
 * <p>Stack multiple instances vertically to form a taller structure.
 * The bottom block is the "master" that drives all processing.
 *
 * <ul>
 *   <li>Front face (FACING) — input fluid, bottom block only.</li>
 *   <li>Back face (FACING.opposite) — output fluid, each block outputs its own product.</li>
 *   <li>Bottom face — heat / cold input (IHeatReceiver / IColdReceiver).</li>
 * </ul>
 */
public class FractionalDistillerBlock extends BaseEntityBlock implements IFluidContainer {

    public static final MapCodec<FractionalDistillerBlock> CODEC = simpleCodec(FractionalDistillerBlock::new);

    public static final EnumProperty<Direction>  FACING  = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty          LIT     = BlockStateProperties.LIT;

    public FractionalDistillerBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? 7 : 0));
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT,    false));
    }

    // ── IFluidContainer ───────────────────────────────────────────────────────

    /** Front and back faces connect to fluid pipes. */
    @Override
    public boolean isConnectable(BlockState state, Direction face) {
        Direction facing = state.getValue(FACING);
        return face == facing || face == facing.getOpposite();
    }

    // ── Block boilerplate ─────────────────────────────────────────────────────

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    // ── Block entity ──────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FractionalDistillerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OmniTechBlockEntities.FRACTIONAL_DISTILLER.get(),
                        FractionalDistillerBlockEntity::serverTick);
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FractionalDistillerBlockEntity fbe) {
                // Always open the GUI of the bottom (master) block
                FractionalDistillerBlockEntity master =
                        fbe.isBottomBlock() ? fbe : fbe.findBottomBlock();
                if (master != null) {
                    ((ServerPlayer) player).openMenu(master, master.getBlockPos());
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}
