/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.kinetic;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class KineticReductorBlockEntity extends BlockEntity {
    public static final int   POWERED_DECAY_TICKS = 20;
    public static final float ROTATION_SPEED_BASE = 200.0F;
    public static final float ROTATION_SPEED_MAX  = ROTATION_SPEED_BASE * 5f;

    private int   poweredTimer   = 0;
    float         currentKfUnits = 0;

    public KineticReductorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.KF_REDUCTOR.get(), pos, state);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            KineticReductorBlockEntity be) {
        if (be.poweredTimer > 0) {
            be.poweredTimer--;
            if (be.poweredTimer == 0) {
                if (state.getValue(KineticReductorBlock.POWERED)) {
                    level.setBlock(pos, state.setValue(KineticReductorBlock.POWERED, false), 3);
                }
                be.currentKfUnits = 0;
                be.setChanged();
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public void refreshPoweredTimer(Level level, BlockPos pos, BlockState state, float kfUnits) {
        if (state.getValue(KineticReductorBlock.SIGNALED)) {
            return;
        }

        poweredTimer   = POWERED_DECAY_TICKS;
        currentKfUnits = kfUnits;

        if (!state.getValue(KineticReductorBlock.POWERED)) {
            level.setBlock(pos, state.setValue(KineticReductorBlock.POWERED, true), 3);
            state = level.getBlockState(pos);
        }

        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    public float getRotationSpeed() {
        if (currentKfUnits <= 0) return 0f;
        return Math.min(ROTATION_SPEED_BASE * currentKfUnits / 10f, ROTATION_SPEED_MAX);
    }

    // ── Client sync ───────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("KfUnits", currentKfUnits);
        return tag;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        poweredTimer   = input.getIntOr("PoweredTimer", 0);
        currentKfUnits = input.getFloatOr("KfUnits", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("PoweredTimer", poweredTimer);
        output.putFloat("KfUnits", currentKfUnits);
    }
}
