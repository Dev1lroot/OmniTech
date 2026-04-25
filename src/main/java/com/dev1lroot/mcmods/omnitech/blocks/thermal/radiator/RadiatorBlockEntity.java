package com.dev1lroot.mcmods.omnitech.blocks.thermal.radiator;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.blocks.thermal.thermal_conductor.ThermalConductorBlockEntity;
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
 * Radiator block entity — a thermal node that passively dissipates heat/cold to ambient.
 *
 * <p>Behaves like a conductor (same {@link #CONDUCTIVITY}) but additionally sheds
 * {@link #DISSIPATION_RATE} × (temperature – ambient) per tick to the environment.
 * This limits how fast it can cool an attached machine: it can only absorb heat as
 * fast as the conductor chain delivers it, and can only dissipate as fast as the
 * temperature difference from ambient allows.
 *
 * <p>No inventory, no GUI, no IHeatReceiver/IColdReceiver — the radiator is purely
 * a passive thermal node in the diffusion network.
 */
public class RadiatorBlockEntity extends BlockEntity implements IThermalNode {

    /** Same conductivity as conductor pipes — conductors limit how fast heat arrives. */
    public static final float CONDUCTIVITY = ThermalConductorBlockEntity.CONDUCTIVITY;
    /** Fraction of temperature excess/deficit shed to ambient each tick. */
    public static final float DISSIPATION_RATE = 0.08f;
    /** Threshold for LIT blockstate toggle. */
    private static final float LIT_THRESHOLD = 5f;
    private static final float SYNC_THRESHOLD = 1f;

    private float temperature = AMBIENT_TEMP;
    private float lastSentTemp = Float.MAX_VALUE;

    public RadiatorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.RADIATOR.get(), pos, state);
    }

    // ── IThermalNode ──────────────────────────────────────────────────────────

    @Override
    public float getTemperature() { return temperature; }

    @Override
    public void applyHeat(float dT) { temperature += dT; }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            RadiatorBlockEntity be) {

        float totalDelta = 0f;

        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));

            if (neighbor instanceof ThermalConductorBlockEntity adjConductor) {
                // Passive diffusion with adjacent conductors
                totalDelta += CONDUCTIVITY * (adjConductor.getTemperature() - be.temperature);

            } else if (neighbor instanceof RadiatorBlockEntity adjRadiator) {
                // Radiator-to-radiator diffusion (chain dissipation)
                totalDelta += CONDUCTIVITY * (adjRadiator.temperature - be.temperature);

            } else if (neighbor instanceof IThermalNode node) {
                // Adjacent machine (heat/cold source): exchange and inform machine
                float delta = CONDUCTIVITY * (node.getTemperature() - be.temperature);
                totalDelta += delta;
                node.applyHeat(-delta);
            }
        }

        // Passive dissipation to ambient — the radiator's defining property
        totalDelta -= DISSIPATION_RATE * (be.temperature - AMBIENT_TEMP);

        be.temperature = Math.clamp(be.temperature + totalDelta, -500f, 2000f);

        // LIT while actively hot or cold
        boolean shouldBeLit = Math.abs(be.temperature - AMBIENT_TEMP) > LIT_THRESHOLD;
        if (state.getValue(RadiatorBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state.setValue(RadiatorBlock.LIT, shouldBeLit), 3);
        }

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
