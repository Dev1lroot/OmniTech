package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for the Kinetic Reductor.
 *
 * <p>The generator's BFS calls {@link #refreshPoweredTimer} on every reductor it
 * traverses each tick.  The timer decays on every server tick; when it reaches
 * zero the {@code POWERED} blockstate is cleared and the texture animation stops.
 *
 * <p>This mirrors the design of {@link KineticPipeBlockEntity} — only the
 * property name on the owning block differs.
 */
public class KineticReductorBlockEntity extends BlockEntity {
    /** Ticks before POWERED turns off after the last BFS refresh. */
    public static final int POWERED_DECAY_TICKS = 20;

    private int poweredTimer = 0;

    public KineticReductorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.KF_REDUCTOR.get(), pos, state);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            KineticReductorBlockEntity be) {
        if (be.poweredTimer > 0) {
            be.poweredTimer--;
            if (be.poweredTimer == 0 && state.getValue(KineticReductorBlock.POWERED)) {
                level.setBlock(pos, state.setValue(KineticReductorBlock.POWERED, false), 3);
            }
        }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Called by the generator's BFS propagation each tick while KF flows
     * through this reductor.  Resets the decay timer and ensures the POWERED
     * blockstate (and therefore the animated texture) is active.
     */
    public void refreshPoweredTimer(Level level, BlockPos pos, BlockState state)
    {
        if (state.getValue(KineticReductorBlock.SIGNALED)) {
            // TODO: Feature to block Kinetic Force passage while reductor is redstone signaled
            return;
        }

        poweredTimer = POWERED_DECAY_TICKS;

        if (!state.getValue(KineticReductorBlock.POWERED)) {
            level.setBlock(pos, state.setValue(KineticReductorBlock.POWERED, true), 3);
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
