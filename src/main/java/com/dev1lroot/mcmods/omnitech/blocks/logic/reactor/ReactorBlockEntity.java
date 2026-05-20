/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.reactor;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.OmniTechMobEffects;
import com.dev1lroot.mcmods.omnitech.gui.ReactorMenu;
import com.dev1lroot.mcmods.omnitech.items.ReactorControlRodItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorFuelRodItem;
import com.dev1lroot.mcmods.omnitech.items.ReactorRodItem;
import com.dev1lroot.mcmods.omnitech.util.FluidNetworkUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReactorBlockEntity extends BlockEntity implements MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ── Temperature simulation constants ─────────────────────────────────────
    private static final int HEAT_TICK_INTERVAL      = 20;
    public  static final int MAX_TEMPERATURE         = 2000;
    public  static final int MAX_COOLANT_TEMP        = 350;
    public  static final int MAX_PRESSURE            = 1000;
    private static final int AMBIENT_TEMP            = 20;
    private static final int TEMP_HEAT_THRESHOLD     = 300;
    private static final int TEMP_MELTDOWN_THRESHOLD = 1200;
    private static final int RAD_INTERVAL            = 100;
    private static final int RAD_OUTER_RADIUS        = 10;

    // Cardinal grid direction offsets [dx, dz] in local cell coordinates
    private static final int[][] GRID_DIRS = {{1,0},{-1,0},{0,1},{0,-1}};

    // ── Structure state ───────────────────────────────────────────────────────
    private boolean formed = false;
    private ReactorStructure structure = null;

    // ── Coolant fluid tank ────────────────────────────────────────────────────
    FluidStack coolantTank        = FluidStack.EMPTY;
    int        tankCapacity       = 0;
    int        coolantTemperature = AMBIENT_TEMP;
    int        pressure           = 0;
    final CoolantHandler coolantHandler = new CoolantHandler();

    // ── Neutron flow per cell (0..100, updated each physics tick) ────────────
    private int @Nullable [] cellNeutronFlowPct = null;

    // ── Tick counters / persistence ───────────────────────────────────────────
    private boolean pendingRevalidation = false;
    private int savedWidth     = 3;
    private int savedDepth     = 3;
    private int savedCellCount = 0;

    private int     revalidateTick  = 0;
    private int     heatTick        = 0;
    private int     radTick         = 0;
    private int     persistTick     = 0;
    private boolean tempDirty       = false;
    private int     coreTemperature = 0;
    private boolean hasExploded     = false;

    public int     getCoreTemperature()    { return coreTemperature; }
    public int     getCoolantTemperature() { return coolantTemperature; }
    public int     getPressure()           { return pressure; }
    public boolean hasExploded()           { return hasExploded; }
    public void    markExploded()          { hasExploded = true; }

    /** Returns the per-cell neutron flow percentages (0..100), or null if not yet computed. */
    public int @Nullable [] getCellNeutronFlowPct() { return cellNeutronFlowPct; }

    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.REACTOR.get(), pos, state);
    }

    // ── MenuProvider ──────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.omnitech.reactor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new ReactorMenu(containerId, inv, this);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide()) {
            lv.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    // ── Client sync ───────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(output);
            return output.buildResult();
        }
    }

    @Override
    public void onDataPacket(Connection net, ValueInput valueInput) {
        super.onDataPacket(net, valueInput);
        Level lv = getLevel();
        if (formed && lv != null && lv.isClientSide()) {
            ReactorStructure.detect(lv, getBlockPos()).ifPresent(s -> structure = s);
        }
    }

    // ── Fluid accessors ───────────────────────────────────────────────────────

    public FluidStack getCoolantTank()                            { return coolantTank; }
    public int        getCoolantAmount()                          { return coolantTank.getAmount(); }
    public int        getTankCapacity()                           { return tankCapacity; }
    public ResourceHandler<FluidResource> getCoolantHandler()     { return coolantHandler; }

    public static @Nullable ResourceHandler<FluidResource> findCoolantHandler(Level level, BlockPos fromPos) {
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int dx = -(ReactorStructure.MAX_SIZE - 1); dx <= 0; dx++) {
            for (int dy = -(ReactorStructure.HEIGHT - 1); dy <= 0; dy++) {
                for (int dz = -(ReactorStructure.MAX_SIZE - 1); dz <= 0; dz++) {
                    mpos.set(fromPos.getX() + dx, fromPos.getY() + dy, fromPos.getZ() + dz);
                    if (level.getBlockEntity(mpos) instanceof ReactorBlockEntity master
                            && master.isFormed()) {
                        return master.coolantHandler;
                    }
                }
            }
        }
        return null;
    }

    // ── Structure ─────────────────────────────────────────────────────────────

    public boolean isFormed()              { return formed; }
    public ReactorStructure getStructure() { return structure; }

    public void form(ReactorStructure s) {
        formed    = true;
        structure = s;
        tankCapacity = ReactorStructure.cavityBlocks(s.width, s.depth) * 1000;
        setChanged();
    }

    @Override
    public void setRemoved() {
        Level lv = getLevel();
        if (formed && lv != null && !lv.isClientSide()) {
            BlockPos pos = getBlockPos();
            LevelChunk chunk = ((ServerLevel) lv).getChunkSource().getChunkNow(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
            if (chunk != null && !(chunk.getBlockState(pos).getBlock() instanceof ReactorBlock)) {
                resetCellStates(lv);
            }
        }
        super.setRemoved();
    }

    public void invalidate() {
        if (!formed) return;
        Level lv = getLevel();
        if (lv != null && !lv.isClientSide()) {
            resetCellStates(lv);
        }
        formed            = false;
        structure         = null;
        tankCapacity      = 0;
        cellNeutronFlowPct = null;
        setChanged();
    }

    private void resetCellStates(Level lv) {
        if (structure == null) return;
        for (BlockPos cellPos : structure.cells) {
            BlockState cs = lv.getBlockState(cellPos);
            if (cs.getBlock() instanceof ReactorCell) {
                lv.setBlock(cellPos, cs
                        .setValue(ReactorCell.CELL_STATE, ReactorCellState.COOL)
                        .setValue(ReactorCell.CELL_TYPE,  ReactorCellType.EMPTY), 3);
            }
        }
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReactorBlockEntity be) {
        if (be.pendingRevalidation) {
            be.pendingRevalidation = false;
            var detected = ReactorStructure.detect(level, pos);
            if (detected.isPresent()
                    && detected.get().width == be.savedWidth
                    && detected.get().depth == be.savedDepth
                    && detected.get().cells.size() == be.savedCellCount) {
                be.structure = detected.get();
            } else {
                be.invalidate();
            }
            return;
        }

        if (be.formed && be.structure != null) {
            if (++be.revalidateTick >= 20) {
                be.revalidateTick = 0;
                if (!be.structure.isStillValid(level)) {
                    be.invalidate();
                    return;
                }
            }

            if (++be.heatTick >= HEAT_TICK_INTERVAL) {
                be.heatTick = 0;
                be.tickTemperature(level);
            }

            if (++be.radTick >= RAD_INTERVAL) {
                be.radTick = 0;
                if (be.coreTemperature > 0) be.tickReactorRadiation(level);
            }

            if (be.tempDirty && ++be.persistTick >= 200) {
                be.persistTick = 0;
                be.tempDirty   = false;
                be.setChanged();
            }
        }
    }

    // ── Active-reactor radiation ──────────────────────────────────────────────

    private void tickReactorRadiation(Level level) {
        BlockPos o = structure.origin;

        AABB interiorBox = new AABB(
                o.getX() + 1,                          o.getY() + 1,                           o.getZ() + 1,
                o.getX() + structure.width  - 1,       o.getY() + ReactorStructure.HEIGHT - 1, o.getZ() + structure.depth - 1);

        AABB searchBox = new AABB(
                o.getX(), o.getY(), o.getZ(),
                o.getX() + structure.width, o.getY() + ReactorStructure.HEIGHT, o.getZ() + structure.depth)
                .inflate(RAD_OUTER_RADIUS);

        Holder<MobEffect> effect = OmniTechMobEffects.RADIATION;

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (entity instanceof Player p && p.isCreative()) continue;

            int amplifier = interiorBox.contains(entity.getX(), entity.getY(), entity.getZ()) ? 2 : 0;

            MobEffectInstance existing = entity.getEffect(effect);
            if (existing == null || existing.getAmplifier() < amplifier) {
                entity.addEffect(new MobEffectInstance(effect, 200, amplifier, false, true));
            } else if (existing.getAmplifier() == amplifier) {
                entity.addEffect(new MobEffectInstance(effect,
                        Math.max(existing.getDuration(), 200), amplifier, false, true));
            }
        }
    }

    // ── Temperature simulation ────────────────────────────────────────────────
    //
    // Area-based neutron flow model:
    // Each fuel rod emits into rings by Chebyshev distance:
    //   d=1 (3x3)  → 100% base flux
    //   d=2 (5x5)  → 75%  base flux
    //   d=3 (7x7)  → 50%  base flux
    //   d=4 (9x9)  → 25%  base flux
    // All flux is scaled by coolant fill fraction (0 = no reaction).
    // Control rods along the path attenuate flux by their insertion %.
    // Reflectors (OTHER) bounce neutrons back to the emitting fuel rod.
    // Temperature cycle (every HEAT_TICK_INTERVAL ticks):
    //   1. receivedFlow > 0.25 → rod temp +1°C
    //   2. coolant temp = average of all fuel rod temps
    //   3. rod temp – coolant temp ≥ 5 → rod temp –1°C
    //   4. coolant temp –1°C (decay)

    private void tickTemperature(Level level) {
        List<BlockPos> cells = structure.cells;
        int count = cells.size();
        if (count == 0) return;

        // Gather cell BEs, stacks, and local positions
        ReactorCellBlockEntity[] cellBEs = new ReactorCellBlockEntity[count];
        ItemStack[]              stacks  = new ItemStack[count];
        int[]                    lxArr   = new int[count];
        int[]                    lzArr   = new int[count];

        for (int i = 0; i < count; i++) {
            BlockPos wp = cells.get(i);
            lxArr[i] = wp.getX() - structure.origin.getX();
            lzArr[i] = wp.getZ() - structure.origin.getZ();
            if (level.getBlockEntity(wp) instanceof ReactorCellBlockEntity cbe) {
                cellBEs[i] = cbe;
                stacks[i]  = cbe.getItem(0);
            } else {
                stacks[i] = ItemStack.EMPTY;
            }
        }

        // Build local-pos → index map for fast path-tracing lookups
        Map<Long, Integer> posMap = new HashMap<>(count * 2);
        for (int i = 0; i < count; i++) posMap.put(localKey(lxArr[i], lzArr[i]), i);

        // Scale factor: coolant fill fraction (0 = no reaction, 1 = full reaction)
        float coolantFill = getModerationFactor();

        // Phase 1: compute received neutron flow for each cell ─────────────────
        float[] receivedFlow = new float[count];

        if (coolantFill >= 0.001f) {
            for (int src = 0; src < count; src++) {
                if (stacks[src].isEmpty() || !(stacks[src].getItem() instanceof ReactorFuelRodItem)) continue;

                int sx = lxArr[src], sz = lzArr[src];

                for (int tgt = 0; tgt < count; tgt++) {
                    if (tgt == src) continue;
                    int dx = lxArr[tgt] - sx, dz = lzArr[tgt] - sz;
                    int d  = Math.max(Math.abs(dx), Math.abs(dz));
                    if (d < 1 || d > 4) continue;

                    float baseFlux = switch (d) {
                        case 1  -> 1.00f;
                        case 2  -> 0.75f;
                        case 3  -> 0.50f;
                        default -> 0.25f;
                    } * coolantFill;

                    // Trace from src to tgt, checking intermediate cells
                    float att = 1.0f;
                    boolean blocked = false;

                    for (int step = 1; step < d && !blocked; step++) {
                        int cx = sx + Math.round((float)dx * step / d);
                        int cz = sz + Math.round((float)dz * step / d);
                        Integer mid = posMap.get(localKey(cx, cz));
                        if (mid == null) continue;

                        ItemStack ms = stacks[mid];
                        if (ms.isEmpty() || !(ms.getItem() instanceof ReactorRodItem mr)) continue;

                        switch (mr.getCellType()) {
                            case CONTROL -> {
                                att *= 1.0f - ReactorControlRodItem.getControl(ms) / 100.0f;
                                if (att < 0.01f) blocked = true;
                            }
                            case OTHER -> {
                                // Reflector: bounce neutrons back to source fuel rod
                                receivedFlow[src] += baseFlux * att * 0.5f;
                                blocked = true;
                            }
                            case FUEL -> blocked = true; // intermediate fuel rod absorbs
                            default -> {}
                        }
                    }

                    if (!blocked && att >= 0.01f) {
                        receivedFlow[tgt] += baseFlux * att;
                    }
                }
            }
        }

        // Phase 2: fuel rod temperature update (flow > 25% → +1°C) ───────────
        boolean changed = false;
        int fuelCount = 0;
        long fuelTempSum = 0;

        for (int i = 0; i < count; i++) {
            ItemStack stack = stacks[i];
            ReactorCellBlockEntity cbe = cellBEs[i];
            if (cbe == null || stack.isEmpty() || !(stack.getItem() instanceof ReactorFuelRodItem)) continue;

            int temp = Math.max(0, ReactorRodItem.getTemperature(stack));
            if (receivedFlow[i] > 0.25f) temp = Math.min(MAX_TEMPERATURE, temp + 1);
            else                          temp = Math.max(0, temp - 1);
            ReactorRodItem.setTemperature(stack, temp);
            cbe.setChanged();
            changed = true;
            fuelTempSum += temp;
            fuelCount++;
        }

        // Phase 3: damage active fuel rods ────────────────────────────────────
        for (int i = 0; i < count; i++) {
            ItemStack stack = stacks[i];
            ReactorCellBlockEntity cbe = cellBEs[i];
            if (cbe == null || stack.isEmpty() || !(stack.getItem() instanceof ReactorFuelRodItem)) continue;
            if (receivedFlow[i] > 0.25f && hurtRod(stack)) {
                cbe.setItem(0, ItemStack.EMPTY);
                resetCell(level, cells.get(i));
                stacks[i] = ItemStack.EMPTY;
                changed = true;
            }
        }

        // Phase 4: damage inserted control rods adjacent to active fuel rods ──
        for (int i = 0; i < count; i++) {
            ItemStack stack = stacks[i];
            ReactorCellBlockEntity cbe = cellBEs[i];
            if (cbe == null || stack.isEmpty() || !(stack.getItem() instanceof ReactorControlRodItem)) continue;
            if (ReactorControlRodItem.getControl(stack) <= 0) continue;

            boolean hasAdjActive = false;
            for (int[] dir : GRID_DIRS) {
                Integer ni = posMap.get(localKey(lxArr[i] + dir[0], lzArr[i] + dir[1]));
                if (ni != null && !stacks[ni].isEmpty()
                        && stacks[ni].getItem() instanceof ReactorFuelRodItem
                        && receivedFlow[ni] > 0.25f) {
                    hasAdjActive = true;
                    break;
                }
            }

            if (hasAdjActive) {
                if (hurtRod(stack)) {
                    cbe.setItem(0, ItemStack.EMPTY);
                    resetCell(level, cells.get(i));
                    stacks[i] = ItemStack.EMPTY;
                } else {
                    cbe.setChanged();
                }
                changed = true;
            }
        }

        // Phase 5: coolant receives average fuel rod temperature ───────────────
        if (fuelCount > 0) {
            coolantTemperature = Math.max(AMBIENT_TEMP, (int)(fuelTempSum / fuelCount));
        }

        // Phase 6: rod cooling — rod is 5+ degrees hotter than coolant → rod –1°C
        for (int i = 0; i < count; i++) {
            ItemStack stack = stacks[i];
            ReactorCellBlockEntity cbe = cellBEs[i];
            if (cbe == null || stack.isEmpty() || !(stack.getItem() instanceof ReactorFuelRodItem)) continue;
            int temp = ReactorRodItem.getTemperature(stack);
            if (temp - coolantTemperature >= 5) {
                ReactorRodItem.setTemperature(stack, temp - 1);
                cbe.setChanged();
                changed = true;
            }
        }

        // Phase 7: coolant temperature decay ──────────────────────────────────
        coolantTemperature = Math.max(AMBIENT_TEMP, coolantTemperature - 1);
        updatePressure();

        // Phase 8: update cell visual states and core temperature ─────────────
        int maxCoreTemp = 0;
        for (int i = 0; i < count; i++) {
            ItemStack stack = stacks[i];
            boolean hasRod = cellBEs[i] != null && !stack.isEmpty() && stack.getItem() instanceof ReactorRodItem;
            int temp = hasRod ? ReactorRodItem.getTemperature(stack) : 0;
            if (temp > maxCoreTemp) maxCoreTemp = temp;
            updateCellState(level, cells.get(i), temp, hasRod);
        }
        coreTemperature = maxCoreTemp;

        // Phase 9: update neutron flow percentages for GUI (max-relative: highest cell = 100)
        if (cellNeutronFlowPct == null || cellNeutronFlowPct.length != count)
            cellNeutronFlowPct = new int[count];
        float maxFlow = 0f;
        for (float f : receivedFlow) if (f > maxFlow) maxFlow = f;
        for (int i = 0; i < count; i++)
            cellNeutronFlowPct[i] = (maxFlow > 0f) ? Math.round(receivedFlow[i] * 100f / maxFlow) : 0;

        // Phase 10: meltdown check ─────────────────────────────────────────────
        if (!hasExploded && maxCoreTemp >= TEMP_MELTDOWN_THRESHOLD && level instanceof ServerLevel sl) {
            hasExploded = true;
            com.dev1lroot.mcmods.omnitech.radiation.NuclearExplosion.trigger(sl, getBlockPos());
            invalidate();
        }

        if (changed) tempDirty = true;
    }

    // ── Flush coolant through connected reactor_port pipes ────────────────────

    public void flush() {
        if (coolantTank.isEmpty() || structure == null) return;
        Level lv = getLevel();
        if (lv == null || lv.isClientSide()) return;

        FluidResource fluidResource = coolantHandler.getResource(0);
        if (fluidResource.isEmpty()) return;

        int minX = structure.origin.getX(), maxX = minX + structure.width  - 1;
        int minY = structure.origin.getY(), maxY = minY + ReactorStructure.HEIGHT - 1;
        int minZ = structure.origin.getZ(), maxZ = minZ + structure.depth  - 1;

        outer:
        for (int x = minX; x <= maxX && !coolantTank.isEmpty(); x++) {
            for (int y = minY; y <= maxY && !coolantTank.isEmpty(); y++) {
                for (int z = minZ; z <= maxZ && !coolantTank.isEmpty(); z++) {
                    boolean onShell = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                    if (!onShell) continue;
                    if (!(lv.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof ReactorPort)) continue;

                    BlockPos portPos = new BlockPos(x, y, z);
                    for (Direction dir : Direction.values()) {
                        if (coolantTank.isEmpty()) break outer;
                        BlockPos outside = portPos.relative(dir);
                        if (outside.getX() >= minX && outside.getX() <= maxX
                                && outside.getY() >= minY && outside.getY() <= maxY
                                && outside.getZ() >= minZ && outside.getZ() <= maxZ) continue;

                        ResourceHandler<FluidResource> outputHandler =
                                FluidNetworkUtil.findOutputTarget(lv, outside, fluidResource);
                        if (outputHandler == null) continue;

                        int amount = coolantTank.getAmount();
                        int toTransfer;
                        try (Transaction simTx = Transaction.openRoot()) {
                            int simExtracted = coolantHandler.extract(0, fluidResource, amount, simTx);
                            if (simExtracted <= 0) continue;
                            toTransfer = outputHandler.insert(fluidResource, simExtracted, simTx);
                        }
                        if (toTransfer <= 0) continue;

                        try (Transaction execTx = Transaction.openRoot()) {
                            coolantHandler.extract(0, fluidResource, toTransfer, execTx);
                            outputHandler.insert(fluidResource, toTransfer, execTx);
                            execTx.commit();
                        }
                    }
                }
            }
        }

        tempDirty = true;
        setChanged();
    }

    // ── Local-position key helper ─────────────────────────────────────────────

    private static long localKey(int lx, int lz) {
        return (long)lx * 32 + lz;
    }

    // ── Pressure and moderation helpers ──────────────────────────────────────

    private boolean isSteamCoolant() {
        if (coolantTank.isEmpty()) return false;
        var fo = OmniTechFluids.get("steam");
        return fo != null && coolantTank.getFluid() == fo.source.get();
    }

    private float getModerationFactor() {
        if (coolantTank.isEmpty() || tankCapacity == 0) return 0f;
        float fillFraction = (float) coolantTank.getAmount() / tankCapacity;
        return isSteamCoolant() ? 0.1f * fillFraction : fillFraction;
    }

    private void updatePressure() {
        if (coolantTank.isEmpty() || isSteamCoolant()) {
            pressure = 0;
            return;
        }
        int fillComp    = tankCapacity > 0 ? coolantTank.getAmount() * 500 / tankCapacity : 0;
        int thermalComp = coolantTemperature > AMBIENT_TEMP
                ? Math.min(500, (coolantTemperature - AMBIENT_TEMP) * 500 / (MAX_COOLANT_TEMP - AMBIENT_TEMP))
                : 0;
        pressure = Math.min(MAX_PRESSURE, fillComp + thermalComp);
    }

    private static boolean hurtRod(ItemStack stack) {
        if (!stack.isDamageableItem()) return false;
        int dmg = stack.getDamageValue() + 1;
        if (dmg >= stack.getMaxDamage()) {
            stack.shrink(1);
            return true;
        }
        stack.setDamageValue(dmg);
        return false;
    }

    private static void resetCell(Level level, BlockPos cellPos) {
        BlockState state = level.getBlockState(cellPos);
        if (!(state.getBlock() instanceof ReactorCell)) return;
        level.setBlock(cellPos, state
                .setValue(ReactorCell.CELL_STATE, ReactorCellState.COOL)
                .setValue(ReactorCell.CELL_TYPE,  ReactorCellType.EMPTY), 3);
    }

    private static void updateCellState(Level level, BlockPos cellPos, int temp, boolean hasRod) {
        BlockState state = level.getBlockState(cellPos);
        if (!(state.getBlock() instanceof ReactorCell)) return;

        ReactorCellState next;
        if (!hasRod || temp < TEMP_HEAT_THRESHOLD) {
            next = ReactorCellState.COOL;
        } else if (temp < TEMP_MELTDOWN_THRESHOLD) {
            next = ReactorCellState.HEAT;
        } else {
            next = ReactorCellState.MELTDOWN;
        }

        if (state.getValue(ReactorCell.CELL_STATE) != next) {
            level.setBlock(cellPos, state.setValue(ReactorCell.CELL_STATE, next), 3);
        }
    }

    // ── Coolant fluid handler ─────────────────────────────────────────────────

    private class CoolantHandler extends SnapshotJournal<FluidStack>
            implements ResourceHandler<FluidResource> {

        private static boolean isDistilledWater(FluidResource resource) {
            if (resource.isEmpty()) return false;
            var fo = OmniTechFluids.get("distilled_water");
            if (fo == null) return false;
            return resource.toStack(1).getFluid() == fo.source.get();
        }

        @Override protected FluidStack createSnapshot()              { return coolantTank; }
        @Override protected void revertToSnapshot(FluidStack snap)   { coolantTank = snap; }
        @Override protected void onRootCommit(FluidStack orig)       { tempDirty = true; }

        @Override public int size() { return 1; }

        @Override
        public FluidResource getResource(int i) {
            if (i != 0 || coolantTank.isEmpty()) return FluidResource.EMPTY;
            FluidStack stamped = coolantTank.copy();
            stamped.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), coolantTemperature);
            return FluidResource.of(stamped);
        }

        @Override public long getAmountAsLong(int i)                       { return i == 0 ? coolantTank.getAmount() : 0L; }
        @Override public long getCapacityAsLong(int i, FluidResource r)    { return i == 0 && formed ? tankCapacity : 0L; }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            if (index != 0 || !formed) return false;
            return isDistilledWater(resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || !formed || !isDistilledWater(resource) || amount <= 0) return 0;
            int space    = tankCapacity - coolantTank.getAmount();
            int toInsert = Math.min(amount, space);
            if (toInsert <= 0) return 0;

            FluidStack incoming = resource.toStack(1);
            Integer incomingTempBox = incoming.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
            int incomingTemp = incomingTempBox != null ? incomingTempBox : AMBIENT_TEMP;
            int existing = coolantTank.getAmount();
            int blended = existing > 0
                    ? (coolantTemperature * existing + incomingTemp * toInsert) / (existing + toInsert)
                    : incomingTemp;

            updateSnapshots(tx);
            coolantTemperature = Math.max(AMBIENT_TEMP, Math.min(MAX_COOLANT_TEMP, blended));
            coolantTank = coolantTank.isEmpty() ? resource.toStack(toInsert)
                    : coolantTank.copyWithAmount(coolantTank.getAmount() + toInsert);
            return toInsert;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
            if (index != 0 || coolantTank.isEmpty() || amount <= 0) return 0;
            if (resource.isEmpty() || resource.toStack(1).getFluid() != coolantTank.getFluid()) return 0;
            int toExtract = Math.min(amount, coolantTank.getAmount());
            updateSnapshots(tx);
            coolantTank = coolantTank.copyWithAmount(coolantTank.getAmount() - toExtract);
            if (coolantTank.getAmount() <= 0) coolantTank = FluidStack.EMPTY;
            return toExtract;
        }
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Formed", formed ? 1 : 0);
        if (formed && structure != null) {
            output.putInt("StructWidth",  structure.width);
            output.putInt("StructDepth",  structure.depth);
            output.putInt("CellCount",    structure.cells.size());
        }
        output.putInt("TankCapacity",        tankCapacity);
        output.putInt("CoolantTemperature", coolantTemperature);
        output.putInt("Pressure",           pressure);
        output.store("Coolant", FluidStack.OPTIONAL_CODEC, coolantTank);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        formed = input.getIntOr("Formed", 0) != 0;
        if (formed) {
            savedWidth          = input.getIntOr("StructWidth", 3);
            savedDepth          = input.getIntOr("StructDepth", 3);
            savedCellCount      = input.getIntOr("CellCount", 0);
            pendingRevalidation = true;
        }
        tankCapacity       = input.getIntOr("TankCapacity", 0);
        coolantTemperature = input.getIntOr("CoolantTemperature", AMBIENT_TEMP);
        pressure           = input.getIntOr("Pressure", 0);
        coolantTank        = input.read("Coolant", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
    }
}
