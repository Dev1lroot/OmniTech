/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.rocket;

import com.dev1lroot.mcmods.omnitech.OmniTechEntities;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import com.dev1lroot.mcmods.omnitech.rocket.RocketStructureDef;
import com.dev1lroot.mcmods.omnitech.rocket.RocketStructureLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Placed as the anchor block of a rocket multiblock template (see
 * {@code data/omnitech/rocket_structure/*.json}). Structure shape, block
 * types, and this controller's own position within the shape are entirely
 * JSON-defined — the shape need not be a cuboid.
 *
 * <p>Assembly is attempted automatically right after this block is placed
 * (the normal "place the controller last" flow), and again on right-click so
 * players who build in a different order can retry once the rest of the
 * structure is finished. On a match, every block belonging to the matched
 * shape is removed (no drops) and replaced with a single {@link RocketEntity}.
 */
public class RocketControllerBlock extends Block {

    public RocketControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (!level.isClientSide()) {
            tryAssemble((ServerLevel) level, pos, by instanceof Player player ? player : null);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        tryAssemble((ServerLevel) level, pos, player);
        return InteractionResult.CONSUME;
    }

    /** Tries every loaded template whose controller resolves to this block. */
    private void tryAssemble(ServerLevel level, BlockPos pos, @Nullable Player player) {
        for (RocketStructureDef def : RocketStructureLoader.forController(this)) {
            Optional<RocketStructureDef.Match> matched = def.match(level, pos);
            if (matched.isEmpty()) continue;

            launch(level, matched.get());
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.omnitech.rocket_assembled"));
            }
            return;
        }

        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.omnitech.rocket_incomplete"));
        }
    }

    private void launch(ServerLevel level, RocketStructureDef.Match match) {
        for (BlockPos p : match.positions()) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }

        Vec3 spawn = match.spawnPos();
        RocketEntity rocket = new RocketEntity(OmniTechEntities.ROCKET.get(), level);
        rocket.setPos(spawn.x, spawn.y, spawn.z);
        level.addFreshEntity(rocket);
    }
}
