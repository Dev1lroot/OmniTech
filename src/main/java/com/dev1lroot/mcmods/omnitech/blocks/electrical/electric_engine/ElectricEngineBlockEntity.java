package com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_engine;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import com.dev1lroot.mcmods.omnitech.io.IElectricSupplier;
import com.dev1lroot.mcmods.omnitech.io.IKineticReceiver;
import com.dev1lroot.mcmods.omnitech.io.IKineticSupplier;
import com.dev1lroot.mcmods.omnitech.gui.ElectricEngineMenu;
import com.dev1lroot.mcmods.omnitech.util.ElectricNetworkUtil;
import com.dev1lroot.mcmods.omnitech.util.KineticNetworkUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for the Electric Engine — bidirectional converter.
 *
 * <h3>Forward mode (KF → EU, default)</h3>
 * <ul>
 *   <li>Accepts KF from the {@code FACING} face (IKineticReceiver, demand 1.0 KF).</li>
 *   <li>Pushes {@value #EU_OUTPUT} EU/tick to the 5 non-facing electric faces.</li>
 * </ul>
 *
 * <h3>Reverse mode (EU → KF)</h3>
 * <ul>
 *   <li>Accepts EU from the 5 non-facing electric faces (IElectricReceiver).</li>
 *   <li>Buffers EU; while buffer ≥ {@value #EU_CONSUME} per tick, consumes it
 *       and pushes {@value #KF_OUTPUT} KF to the {@code FACING} face (IKineticSupplier).</li>
 * </ul>
 *
 * <h3>ContainerData layout (4 slots)</h3>
 * <ul>
 *   <li>0 – powered (1 = active, 0 = idle)</li>
 *   <li>1 – forward: lastKfAmount × 100; reverse: euBuffer × 10</li>
 *   <li>2 – forward: EU_OUTPUT × 10 when active; reverse: KF_OUTPUT × 100 when active</li>
 *   <li>3 – mode (0 = forward KF→EU, 1 = reverse EU→KF)</li>
 * </ul>
 */
public class ElectricEngineBlockEntity extends BaseContainerBlockEntity
        implements IKineticReceiver, IElectricSupplier, IElectricReceiver, IKineticSupplier {

    /** EU produced per tick in forward mode (KF → EU). 10 % conversion loss. */
    public static final float EU_OUTPUT = 0.9f;

    /** EU consumed per tick in reverse mode (EU → KF). */
    public static final float EU_CONSUME = 1.0f;

    /** KF produced per tick in reverse mode (EU → KF). 10 % conversion loss. */
    public static final float KF_OUTPUT = 0.9f;

    /** Max EU the engine can buffer in reverse mode (10 ticks). */
    public static final float MAX_EU_BUFFER = 10f;

    /** Propagate electricity every N ticks to reduce BFS overhead. */
    private static final int CLOCK_INTERVAL = 5;

    /** Ticks before the engine powers down without fresh KF (forward mode). */
    private static final int POWERED_DECAY_TICKS = 3;

    private int poweredTimer = 0;
    private int clockCounter = 0;

    /** KF units received last network clock (forward mode display). */
    private float lastKfAmount = 0f;

    /** EU buffered for conversion (reverse mode). */
    private float euBuffer = 0f;

    /** true = reverse mode (EU→KF); false = forward mode (KF→EU, default). */
    private boolean reverseMode = false;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> isPowered() ? 1 : 0;
                case 1 -> reverseMode
                        ? (int)(euBuffer * 10f)
                        : (int)(lastKfAmount * 100f);
                case 2 -> isPowered()
                        ? (reverseMode ? (int)(KF_OUTPUT * 100f) : (int)(EU_OUTPUT * 10f))
                        : 0;
                case 3 -> reverseMode ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> {}
                case 1 -> {
                    if (reverseMode) euBuffer = value / 10f;
                    else lastKfAmount = value / 100f;
                }
                case 3 -> reverseMode = value == 1;
            }
        }
        @Override public int getCount() { return 4; }
    };

    public ElectricEngineBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.ELECTRIC_ENGINE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.electric_engine");
    }

    @Override protected NonNullList<ItemStack> getItems() { return NonNullList.create(); }
    @Override protected void setItems(NonNullList<ItemStack> items) {}
    @Override public int getContainerSize() { return 0; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ElectricEngineMenu(containerId, inv, this, dataAccess);
    }

    // ── IKineticReceiver (forward mode only) ──────────────────────────────────

    @Override
    public boolean addKineticForce(float amount) {
        if (reverseMode) return false;   // reject KF in reverse mode
        poweredTimer = POWERED_DECAY_TICKS;
        lastKfAmount = amount;
        setChanged();
        return true;
    }

    @Override
    public float getKfDemand() {
        return reverseMode ? 0f : 1.0F;
    }

    // ── IElectricSupplier (forward mode only) ─────────────────────────────────

    @Override
    public float getEuSupply() {
        return (!reverseMode && isPowered()) ? EU_OUTPUT : 0f;
    }

    // ── IElectricReceiver (reverse mode only) ─────────────────────────────────

    @Override
    public float addElectricity(float amount) {
        if (!reverseMode) return 0f;   // reject EU in forward mode
        float space = MAX_EU_BUFFER - euBuffer;
        if (space <= 0f) return 0f;
        float accepted = Math.min(amount, space);
        euBuffer += accepted;
        setChanged();
        return accepted;
    }

    // ── IKineticSupplier (reverse mode only) ──────────────────────────────────

    @Override
    public float getKfSupply() {
        return (reverseMode && isPowered()) ? KF_OUTPUT : 0f;
    }

    // ── Mode toggle (called by menu on server side) ───────────────────────────

    public void toggleMode() {
        reverseMode = !reverseMode;
        // Reset transient state so stale data from the old mode doesn't leak
        poweredTimer = 0;
        euBuffer     = 0f;
        lastKfAmount = 0f;
        clockCounter = 0;
        setChanged();
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            ElectricEngineBlockEntity be) {
        if (be.reverseMode) {
            tickReverse(level, pos, state, be);
        } else {
            tickForward(level, pos, state, be);
        }
    }

    /** Forward mode: receive KF from FACING face, push EU to the other 5 faces. */
    private static void tickForward(Level level, BlockPos pos, BlockState state,
            ElectricEngineBlockEntity be) {

        boolean wasLit = state.getValue(ElectricEngineBlock.LIT);

        if (be.poweredTimer > 0) {
            be.poweredTimer--;
        } else {
            if (be.lastKfAmount != 0f) {
                be.lastKfAmount = 0f;
                be.setChanged();
            }
        }

        boolean isLit = be.isPowered();
        if (wasLit != isLit) {
            level.setBlock(pos, state.setValue(ElectricEngineBlock.LIT, isLit), 3);
        }

        if (!isLit) {
            be.clockCounter = 0;
            return;
        }

        be.clockCounter++;
        if (be.clockCounter >= CLOCK_INTERVAL) {
            be.clockCounter = 0;
            Direction front = state.getValue(ElectricEngineBlock.FACING);
            ElectricNetworkUtil.propagateElectricity(
                    level, pos, EU_OUTPUT * CLOCK_INTERVAL, outputFaces(front));
        }
    }

    /** Reverse mode: consume EU from electric network, push KF to the FACING face. */
    private static void tickReverse(Level level, BlockPos pos, BlockState state,
            ElectricEngineBlockEntity be) {

        boolean wasLit = state.getValue(ElectricEngineBlock.LIT);

        // Consume 1 EU worth of buffer per tick; power down if buffer runs dry
        boolean active = false;
        if (be.euBuffer >= EU_CONSUME) {
            be.euBuffer -= EU_CONSUME;
            be.setChanged();
            active = true;
        }

        // Map "active" to the powered-timer so isPowered() works uniformly
        be.poweredTimer = active ? POWERED_DECAY_TICKS : Math.max(0, be.poweredTimer - 1);

        boolean isLit = be.isPowered();
        if (wasLit != isLit) {
            level.setBlock(pos, state.setValue(ElectricEngineBlock.LIT, isLit), 3);
        }

        // Propagate KF every tick when active (same cadence as other KF generators)
        if (active) {
            KineticNetworkUtil.propagateKineticForce(level, pos, KF_OUTPUT);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isPowered()      { return poweredTimer > 0; }
    public boolean isReverseMode()  { return reverseMode; }
    public ContainerData getContainerData() { return dataAccess; }

    /** Returns the 5 faces that are NOT the KF/front face. */
    private static Direction[] outputFaces(Direction front) {
        Direction[] dirs = new Direction[5];
        int i = 0;
        for (Direction d : Direction.values()) {
            if (d != front) dirs[i++] = d;
        }
        return dirs;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        poweredTimer = input.getIntOr("PoweredTimer", 0);
        lastKfAmount = input.getFloatOr("LastKfAmount", 0f);
        euBuffer     = input.getFloatOr("EuBuffer", 0f);
        reverseMode  = input.getBooleanOr("ReverseMode", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("PoweredTimer",  poweredTimer);
        output.putFloat("LastKfAmount", lastKfAmount);
        output.putFloat("EuBuffer",     euBuffer);
        output.putBoolean("ReverseMode", reverseMode);
    }
}
