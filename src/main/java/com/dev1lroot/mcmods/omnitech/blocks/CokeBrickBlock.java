package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.entities.CokeOvenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CokeBrickBlock extends Block {

    public static final BooleanProperty FORMED  = BooleanProperty.create("formed");
    /** Never set on world blocks — exists only so the entity renderer can resolve the display model. */
    public static final BooleanProperty DISPLAY = BooleanProperty.create("display");

    public CokeBrickBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FORMED, false)
                .setValue(DISPLAY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, DISPLAY);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            Direction facing = placer != null
                    ? placer.getDirection().getOpposite()
                    : Direction.NORTH;
            tryFormStructure(level, pos, facing);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(FORMED)) {
            AABB searchBox = AABB.ofSize(Vec3.atCenterOf(pos), 8, 8, 8);
            List<CokeOvenEntity> entities = level.getEntitiesOfClass(CokeOvenEntity.class, searchBox);
            for (CokeOvenEntity entity : entities) {
                if (entity.isStructurePos(pos)) {
                    entity.onStructureBroken();
                    break;
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (state.getValue(FORMED) && !level.isClientSide()) {
            AABB searchBox = AABB.ofSize(Vec3.atCenterOf(pos), 8, 8, 8);
            List<CokeOvenEntity> entities = level.getEntitiesOfClass(CokeOvenEntity.class, searchBox);
            for (CokeOvenEntity entity : entities) {
                if (entity.isStructurePos(pos)) {
                    if (player instanceof ServerPlayer sp) {
                        entity.openMenu(sp);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }

    // ── Structure detection ───────────────────────────────────────────────────

    private void tryFormStructure(Level level, BlockPos newPos, Direction facing) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                for (int dz = 0; dz < 3; dz++) {
                    // The newly placed block occupies offset (dx, dy, dz) from corner
                    if (dx == 1 && dy == 1 && dz == 1) continue; // center must be empty
                    BlockPos corner = newPos.offset(-dx, -dy, -dz);
                    if (checkStructure(serverLevel, corner)) {
                        formStructure(serverLevel, corner, facing);
                        return;
                    }
                }
            }
        }
    }

    public static boolean checkStructure(Level level, BlockPos corner) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos p = corner.offset(x, y, z);
                    if (x == 1 && y == 1 && z == 1) {
                        // Center must be air or non-coke-brick
                        if (level.getBlockState(p).getBlock() instanceof CokeBrickBlock) return false;
                    } else {
                        BlockState bs = level.getBlockState(p);
                        if (!(bs.getBlock() instanceof CokeBrickBlock)) return false;
                        if (bs.getValue(FORMED)) return false; // already part of another structure
                    }
                }
            }
        }
        return true;
    }

    private static void formStructure(ServerLevel level, BlockPos corner, Direction facing) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    if (x == 1 && y == 1 && z == 1) continue;
                    BlockPos p = corner.offset(x, y, z);
                    BlockState bs = level.getBlockState(p);
                    if (bs.getBlock() instanceof CokeBrickBlock) {
                        level.setBlock(p, bs.setValue(FORMED, true), Block.UPDATE_ALL);
                    }
                }
            }
        }

        CokeOvenEntity entity = new CokeOvenEntity(level, corner, facing);
        level.addFreshEntity(entity);
    }
}
