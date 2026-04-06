package com.dev1lroot.mcmods.omnitech.blocks;

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

/**
 * Block entity for the Kinetic Pipe.
 *
 * <p>The pipe does NOT forward KF by itself. The generator walks the pipe network
 * via BFS each tick and calls {@link #refreshPoweredTimer} on every pipe it
 * reaches. This avoids recursion and works for arbitrarily long chains.
 *
 * <p>The {@code poweredTimer} counts down each server tick. When it reaches zero
 * the POWERED blockstate is cleared. As long as the generator is running, it
 * refreshes the timer every tick, keeping the pipe visually spinning.
 *
 * <p>The rotation speed scales with the total KF flowing through the network:
 * {@link #ROTATION_SPEED_BASE} degrees/tick at 1 KF (10 units), proportionally
 * faster at higher KF levels.
 */
public class KineticPipeBlockEntity extends BlockEntity {
    /** Ticks before POWERED turns off after the last refresh. */
    public static final int   POWERED_DECAY_TICKS  = 20;
    /** Degrees per tick at 1 KF (10 fixed-point units) — scales linearly with KF. */
    public static final float ROTATION_SPEED_BASE  = 200.0F;
    /** Maximum rotation speed: 5× the base (= 45 °/tick). */
    public static final float ROTATION_SPEED_MAX   = ROTATION_SPEED_BASE * 5f;

    private int poweredTimer   = 0;
    /** Fixed-point KF units received this network clock (10 = 1 KF). Synced to client. */
    float currentKfUnits = 0;

    public KineticPipeBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.KF_PIPE.get(), pos, state);
    }

    // ── Ticks ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            KineticPipeBlockEntity be) {
        if (be.poweredTimer > 0) {
            be.poweredTimer--;
            if (be.poweredTimer == 0) {
                if (state.getValue(KineticPipeBlock.POWERED)) {
                    level.setBlock(pos, state.setValue(KineticPipeBlock.POWERED, false), 3);
                }
                be.currentKfUnits = 0;
                be.setChanged();
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Called by the generator's BFS propagation each tick while KF flows through
     * this pipe. Resets the decay timer, stores the current KF level for the
     * renderer, and ensures the POWERED blockstate is set.
     *
     * @param kfUnits fixed-point KF units flowing in the network (10 = 1 KF);
     *                pass 0 when the network is stalled (supply &lt; demand) so
     *                the pipe still spins but machines stay off
     */
    public void refreshPoweredTimer(Level level, BlockPos pos, BlockState state, float kfUnits) {
        poweredTimer   = POWERED_DECAY_TICKS;
        currentKfUnits = kfUnits;

        if (!state.getValue(KineticPipeBlock.POWERED)) {
            level.setBlock(pos, state.setValue(KineticPipeBlock.POWERED, true), 3);
            state = level.getBlockState(pos);
        }

        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    /**
     * Rotation speed in degrees per tick based on the current network KF level.
     * Returns 0 when the pipe is unpowered or the network is stalled.
     */
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
        currentKfUnits = input.getFloatOr("KfUnits",      0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("PoweredTimer", poweredTimer);
        output.putFloat("KfUnits",      currentKfUnits);
    }
}
