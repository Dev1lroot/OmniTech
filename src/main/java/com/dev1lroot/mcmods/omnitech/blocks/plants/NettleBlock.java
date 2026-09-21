/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Stinging nettle — a two-block-tall plant exactly like
 * {@link net.minecraft.world.level.block.Blocks#TALL_GRASS} /
 * {@code LARGE_FERN} (both are plain {@link DoublePlantBlock} instances with
 * no shape override; nettle follows the same convention here). Pushing
 * through it while moving hurts the entity — the exact speed-threshold check
 * and damage source {@link net.minecraft.world.level.block.SweetBerryBushBlock}
 * uses, minus its age/growth stages since nettle has none.
 */
public class NettleBlock extends DoublePlantBlock {

    private static final float HURT_SPEED_THRESHOLD = 0.003F;

    public NettleBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(
            BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity instanceof LivingEntity) {
            entity.makeStuckInBlock(state, new Vec3(0.8F, 0.75, 0.8F));
            if (level instanceof ServerLevel serverLevel) {
                Vec3 movement = entity.isClientAuthoritative()
                        ? entity.getKnownMovement()
                        : entity.oldPosition().subtract(entity.position());
                if (movement.horizontalDistanceSqr() > 0.0) {
                    double xs = Math.abs(movement.x());
                    double zs = Math.abs(movement.z());
                    if (xs >= HURT_SPEED_THRESHOLD || zs >= HURT_SPEED_THRESHOLD) {
                        entity.hurtServer(serverLevel, level.damageSources().sweetBerryBush(), 1.0F);
                    }
                }
            }
        }
    }
}
