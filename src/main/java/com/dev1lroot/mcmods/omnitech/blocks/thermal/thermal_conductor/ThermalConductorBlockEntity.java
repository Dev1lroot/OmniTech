package com.dev1lroot.mcmods.omnitech.blocks.thermal.thermal_conductor;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IColdReceiver;
import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
import com.dev1lroot.mcmods.omnitech.io.IThermalNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Temperature-diffusion node for the thermal conductor pipe.
 *
 * <p>Each tick, heat flows from each adjacent {@link IThermalNode} neighbor proportionally to
 * the temperature difference ({@code CONDUCTIVITY * ΔT}).  This creates a natural gradient:
 * conductors near a heat source are hotter than those far away, and heat spreads at a finite
 * speed (one tile per ~10 ticks at typical temperatures).
 *
 * <p>Adjacent {@link IHeatReceiver}/{@link IColdReceiver} machines (chemical reactors etc.)
 * receive discrete heat/cold units at a rate proportional to this conductor's excess
 * temperature above/below ambient.
 *
 * <p>Adjacent {@link com.dev1lroot.mcmods.omnitech.blocks.thermal.radiator.RadiatorBlockEntity}
 * tiles are treated as passive IThermalNode neighbors — they manage their own dissipation.
 */
public class ThermalConductorBlockEntity extends BlockEntity implements IThermalNode {

    /** Fraction of temperature difference transferred per tick per adjacent node. */
    public static final float CONDUCTIVITY = 0.10f;
    /** Minimum temperature delta before treating the conductor as active for display. */
    public static final float DISPLAY_THRESHOLD = 2f;
    /** Sync threshold — don't spam packets for sub-degree changes. */
    private static final float SYNC_THRESHOLD = 1f;

    private float temperature = AMBIENT_TEMP;
    private float lastSentTemp = Float.MAX_VALUE;

    public ThermalConductorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.THERMAL_CONDUCTOR.get(), pos, state);
    }

    // ── IThermalNode ──────────────────────────────────────────────────────────

    @Override
    public float getTemperature() { return temperature; }

    @Override
    public void applyHeat(float dT) { temperature += dT; }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ThermalConductorBlockEntity be) {

        float totalDelta = 0f;

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockEntity neighbor = level.getBlockEntity(neighborPos);

            if (neighbor instanceof IThermalNode node
                    && !(neighbor instanceof ThermalConductorBlockEntity)
                    && !(neighbor instanceof com.dev1lroot.mcmods.omnitech.blocks.thermal.radiator.RadiatorBlockEntity)) {
                // Heat-source or sink machine: conductor exchanges directly and tells the machine
                float delta = CONDUCTIVITY * (node.getTemperature() - be.temperature);
                totalDelta += delta;
                node.applyHeat(-delta);

            } else if (neighbor instanceof ThermalConductorBlockEntity adjConductor) {
                // Peer conductor: read its temperature, both update independently this tick
                totalDelta += CONDUCTIVITY * (adjConductor.temperature - be.temperature);

            } else if (neighbor instanceof com.dev1lroot.mcmods.omnitech.blocks.thermal.radiator.RadiatorBlockEntity adjRadiator) {
                // Radiator: passive diffusion — radiator handles its own tick
                totalDelta += CONDUCTIVITY * (adjRadiator.getTemperature() - be.temperature);

            } else if (be.temperature > AMBIENT_TEMP + 1f && neighbor instanceof IHeatReceiver hr) {
                // Hot conductor pushes discrete heat to adjacent sink machine
                int deliver = (int)(CONDUCTIVITY * (be.temperature - AMBIENT_TEMP));
                if (deliver > 0) {
                    int absorbed = hr.addHeat(deliver);
                    totalDelta -= absorbed;
                }
            } else if (be.temperature < AMBIENT_TEMP - 1f && neighbor instanceof IColdReceiver cr) {
                // Cold conductor pushes discrete cold to adjacent sink machine
                int deliver = (int)(CONDUCTIVITY * (AMBIENT_TEMP - be.temperature));
                if (deliver > 0) {
                    int absorbed = cr.addCold(deliver);
                    totalDelta += absorbed;
                }
            }
        }

        be.temperature = Math.clamp(be.temperature + totalDelta, -500f, 5000f);

        if (Math.abs(be.temperature - be.lastSentTemp) >= SYNC_THRESHOLD) {
            be.lastSentTemp = be.temperature;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ── Client sync ───────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var reporter = new ProblemReporter.ScopedCollector(this.problemPath(),
                com.mojang.logging.LogUtils.getLogger());
        try (reporter) {
            var out = TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(out);
            return out.buildResult();
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        temperature = input.getFloatOr("Temperature", AMBIENT_TEMP);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("Temperature", temperature);
    }
}
