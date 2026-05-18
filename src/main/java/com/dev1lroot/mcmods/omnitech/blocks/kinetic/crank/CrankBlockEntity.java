/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.kinetic.crank;

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
import org.jetbrains.annotations.Nullable;

public class CrankBlockEntity extends BlockEntity {
    public static final int SPIN_DURATION = 20;

    private int spinCooldown = 0;
    /** Client-side only: previous tick's cooldown value for smooth interpolation. */
    public int prevSpinCooldown = 0;

    public CrankBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.CRANK.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrankBlockEntity be) {
        if (be.spinCooldown > 0) {
            be.spinCooldown--;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CrankBlockEntity be) {
        be.prevSpinCooldown = be.spinCooldown;
    }

    public boolean isBusy() {
        return spinCooldown > 0;
    }

    public void startSpin() {
        this.spinCooldown = SPIN_DURATION;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getSpinCooldown() { return spinCooldown; }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SpinCooldown", spinCooldown);
        return tag;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.spinCooldown = input.getIntOr("SpinCooldown", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("SpinCooldown", this.spinCooldown);
    }
}
