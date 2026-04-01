package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for the Kinetic Pipe.
 *
 * <p>The pipe does NOT forward KF by itself. The generator walks the pipe network
 * via BFS each tick and calls {@link #refreshPoweredTimer()} on every pipe it
 * reaches. This avoids recursion and works for arbitrarily long chains.
 *
 * <p>The {@code poweredTimer} counts down each server tick. When it reaches zero
 * the POWERED blockstate is cleared. As long as the generator is running, it
 * refreshes the timer every tick, keeping the pipe visually spinning.
 */
public class KineticPipeBlockEntity extends BlockEntity {
    /** Ticks before POWERED turns off after the last refresh. */
    public static final int   POWERED_DECAY_TICKS = 3;
    /** Degrees per tick for the spinning animation (client-side only). */
    public static final float ROTATION_SPEED      = 9.0f;

    private int poweredTimer = 0;

    public KineticPipeBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.KF_PIPE.get(), pos, state);
    }

    // ── Ticks ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            KineticPipeBlockEntity be) {
        if (be.poweredTimer > 0) {
            be.poweredTimer--;
            if (be.poweredTimer == 0 && state.getValue(KineticPipeBlock.POWERED)) {
                level.setBlock(pos, state.setValue(KineticPipeBlock.POWERED, false), 3);
            }
        }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Called by the generator's BFS propagation each tick while KF flows through
     * this pipe. Resets the decay timer and ensures the POWERED blockstate is set.
     */
    public void refreshPoweredTimer(Level level, BlockPos pos, BlockState state) {
        poweredTimer = POWERED_DECAY_TICKS;
        if (!state.getValue(KineticPipeBlock.POWERED)) {
            level.setBlock(pos, state.setValue(KineticPipeBlock.POWERED, true), 3);
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        poweredTimer = input.getIntOr("PoweredTimer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("PoweredTimer", poweredTimer);
    }
}
