package com.dev1lroot.mcmods.omnitech.blocks.logic.display;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.DisplayMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DisplayBlockEntity extends BlockEntity implements MenuProvider {

    protected final int size;
    private int portId = 0;
    public final int[] pixels; // 0x00RRGGBB per pixel, row-major

    // Cluster state — null masterPos means this block is the master (or standalone)
    @Nullable private BlockPos masterPos = null;
    private int clusterCols = 1;
    private int clusterRows = 1;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        this(OmniTechBlockEntities.DISPLAY.get(), pos, state, 16);
    }

    protected DisplayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
        super(type, pos, state);
        this.size = size;
        this.pixels = new int[size * size];
    }

    public int getSize() { return size; }

    public int getPortId() { return portId; }

    public void setPortId(int id) {
        portId = Math.clamp(id, 0, 65535);
        setChanged();
        sync();
        if (level != null && !level.isClientSide()) {
            detectAndUpdateCluster(level, worldPosition);
        }
    }

    /** True when this block is the cluster master (or a standalone single block). */
    public boolean isMaster() { return masterPos == null; }

    public int getClusterCols() { return clusterCols; }
    public int getClusterRows() { return clusterRows; }

    @Nullable
    public BlockPos getMasterPos() { return masterPos; }

    /** Returns this if master, or the master block entity if this is a slave. */
    @Nullable
    public DisplayBlockEntity getMasterEntity(Level level) {
        if (masterPos == null) return this;
        BlockEntity be = level.getBlockEntity(masterPos);
        return be instanceof DisplayBlockEntity d ? d : null;
    }

    /** Called by cluster detection to assign this block's role. */
    public void setCluster(@Nullable BlockPos master, int cols, int rows) {
        this.masterPos = master;
        this.clusterCols = cols;
        this.clusterRows = rows;
        setChanged();
        sync();
    }

    // ── Pixel API (called by LogicMachine on the master) ─────────────────────

    /**
     * Set pixel (x, y) in the virtual canvas. x and y span the whole cluster:
     *   x ∈ [0, clusterCols*16), y ∈ [0, clusterRows*16).
     * Call only on the master block.
     */
    public void setPixel(int x, int y, int color) {
        if (x < 0 || x >= clusterCols * size || y < 0 || y >= clusterRows * size) return;
        int blockCol = x / size;
        int blockRow = y / size;
        int localX   = x % size;
        int localY   = y % size;

        if (blockCol == 0 && blockRow == 0) {
            setLocalPixel(localX, localY, color);
        } else if (level != null) {
            Direction facing = getBlockState().getValue(DisplayBlock.FACING);
            BlockPos targetPos = clusterMemberPos(facing, worldPosition, blockCol, blockRow);
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target instanceof DisplayBlockEntity slave) {
                slave.setLocalPixel(localX, localY, color);
            }
        }
    }

    /** Write a single pixel into this block's own size×size buffer. */
    public void setLocalPixel(int x, int y, int color) {
        int c = color & 0xFFFFFF;
        if (pixels[y * size + x] == c) return;
        pixels[y * size + x] = c;
        setChanged();
        sync();
    }

    /** Clear all pixels in the entire cluster. Call only on the master block. */
    public void resetPixels() {
        resetLocalPixels();
        if (clusterCols == 1 && clusterRows == 1 || level == null) return;
        Direction facing = getBlockState().getValue(DisplayBlock.FACING);
        for (int col = 0; col < clusterCols; col++) {
            for (int row = 0; row < clusterRows; row++) {
                if (col == 0 && row == 0) continue;
                BlockPos targetPos = clusterMemberPos(facing, worldPosition, col, row);
                BlockEntity target = level.getBlockEntity(targetPos);
                if (target instanceof DisplayBlockEntity slave) {
                    slave.resetLocalPixels();
                }
            }
        }
    }

    /** Clear this block's own 16×16 buffer. */
    public void resetLocalPixels() {
        boolean changed = false;
        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] != 0) { pixels[i] = 0; changed = true; }
        }
        if (changed) { setChanged(); sync(); }
    }

    // ── Display dimensions ────────────────────────────────────────────────────

    public int getDisplayWidth()  { return clusterCols * size; }
    public int getDisplayHeight() { return clusterRows * size; }

    // ── Batch drawing (GDIM / LINE / RECT / BLIT) ─────────────────────────────

    /** Bresenham line across the full cluster canvas. */
    public void drawLine(int x1, int y1, int x2, int y2, int color) {
        Set<DisplayBlockEntity> dirty = new HashSet<>();
        int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1), sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        for (;;) {
            writePixelBatched(x1, y1, color, dirty);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 >= dy) { if (x1 == x2) break; err += dy; x1 += sx; }
            if (e2 <= dx) { if (y1 == y2) break; err += dx; y1 += sy; }
        }
        flushDirty(dirty);
    }

    /** Fill a solid rectangle on the cluster canvas. */
    public void fillRect(int x, int y, int w, int h, int color) {
        Set<DisplayBlockEntity> dirty = new HashSet<>();
        for (int dy = 0; dy < h; dy++)
            for (int dx = 0; dx < w; dx++)
                writePixelBatched(x + dx, y + dy, color, dirty);
        flushDirty(dirty);
    }

    /**
     * Copy a w×h region from a packed pixel array to the cluster canvas at (x, y).
     * pixels[row * w + col] = 0x00RRGGBB.
     */
    public void blit(int x, int y, int w, int h, int[] pixels) {
        Set<DisplayBlockEntity> dirty = new HashSet<>();
        for (int row = 0; row < h; row++)
            for (int col = 0; col < w; col++)
                writePixelBatched(x + col, y + row, pixels[row * w + col], dirty);
        flushDirty(dirty);
    }

    // Write pixel without per-pixel sync; collect touched block entities.
    private void writePixelBatched(int x, int y, int color, Set<DisplayBlockEntity> dirty) {
        if (x < 0 || x >= clusterCols * size || y < 0 || y >= clusterRows * size) return;
        DisplayBlockEntity target = resolveTarget(x / size, y / size);
        if (target == null) return;
        int lx = x % size, ly = y % size;
        int c = color & 0xFFFFFF;
        if (target.pixels[ly * size + lx] == c) return;
        target.pixels[ly * size + lx] = c;
        dirty.add(target);
    }

    private DisplayBlockEntity resolveTarget(int blockCol, int blockRow) {
        if (blockCol == 0 && blockRow == 0) return this;
        if (level == null) return null;
        Direction facing = getBlockState().getValue(DisplayBlock.FACING);
        BlockPos p = clusterMemberPos(facing, worldPosition, blockCol, blockRow);
        BlockEntity be = level.getBlockEntity(p);
        return be instanceof DisplayBlockEntity d ? d : null;
    }

    private static void flushDirty(Set<DisplayBlockEntity> dirty) {
        for (DisplayBlockEntity be : dirty) { be.setChanged(); be.sync(); }
    }

    // ── Cluster coordinate helpers ────────────────────────────────────────────

    /**
     * Given the master's world position and a (col, row) index in the cluster,
     * returns the world BlockPos of that member block.
     *
     * Coordinate conventions (pixel (0,0) = viewer's top-left):
     *   NORTH  col++ → west  (−X), row++ → down (−Y)
     *   SOUTH  col++ → east  (+X), row++ → down (−Y)
     *   EAST   col++ → north (−Z), row++ → down (−Y)
     *   WEST   col++ → south (+Z), row++ → down (−Y)
     */
    public static BlockPos clusterMemberPos(Direction facing, BlockPos master, int col, int row) {
        return switch (facing) {
            case NORTH -> master.offset(-col, -row,  0);
            case SOUTH -> master.offset( col, -row,  0);
            case EAST  -> master.offset(  0, -row, -col);
            case WEST  -> master.offset(  0, -row,  col);
            default    -> master;
        };
    }

    // ── Cluster detection (server-side) ───────────────────────────────────────

    /**
     * Re-evaluates the cluster for the display block at {@code pos} and all its
     * connected same-portId / same-facing neighbours.  Call on block place,
     * remove, or portId change.
     */
    public static void detectAndUpdateCluster(Level level, BlockPos pos) {
        detectAndUpdateCluster(level, pos, false);
    }

    public static void detectAndUpdateCluster(Level level, BlockPos pos, boolean showFormationParticles) {
        if (level.isClientSide()) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DisplayBlockEntity d)) return;

        Direction facing = d.getBlockState().getValue(DisplayBlock.FACING);
        int portId       = d.portId;
        int size         = d.size;
        int orthogonal   = getOrthogonalCoord(pos, facing);

        // Flood-fill in the display plane (same Z for N/S, same X for E/W)
        Set<BlockPos> cluster = new HashSet<>();
        floodFill(level, pos, facing, portId, size, orthogonal, cluster);

        if (cluster.size() <= 1) {
            d.setCluster(null, 1, 1);
            return;
        }

        // Bounding box in horiz axis and Y
        int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos p : cluster) {
            int h = getHorizCoord(p, facing);
            if (h < minH) minH = h;
            if (h > maxH) maxH = h;
            if (p.getY() < minY) minY = p.getY();
            if (p.getY() > maxY) maxY = p.getY();
        }

        int cols = maxH - minH + 1;
        int rows = maxY  - minY  + 1;

        // Only form a cluster for a fully-filled rectangle
        if (cluster.size() != cols * rows) {
            for (BlockPos p : cluster) {
                BlockEntity b = level.getBlockEntity(p);
                if (b instanceof DisplayBlockEntity disp) disp.setCluster(null, 1, 1);
            }
            return;
        }

        // Master = top-left corner from the viewer's perspective
        BlockPos masterPos = findMaster(cluster, facing, minH, maxH, maxY);

        for (BlockPos p : cluster) {
            BlockEntity b = level.getBlockEntity(p);
            if (b instanceof DisplayBlockEntity disp) {
                if (p.equals(masterPos)) {
                    disp.setCluster(null, cols, rows);
                } else {
                    disp.setCluster(masterPos, cols, rows);
                }
            }
        }

        // Emit formation particles (only on explicit placement, not on load/neighbour cascades)
        if (showFormationParticles && level instanceof ServerLevel sl) {
            for (BlockPos p : cluster) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                        14, 0.48, 0.48, 0.48, 0.06);
            }
        }
    }

    private static void floodFill(Level level, BlockPos pos, Direction facing,
            int portId, int size, int orthogonal, Set<BlockPos> visited) {
        if (visited.contains(pos)) return;
        if (getOrthogonalCoord(pos, facing) != orthogonal) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DisplayBlockEntity d)) return;
        if (d.getBlockState().getValue(DisplayBlock.FACING) != facing) return;
        if (d.portId != portId) return;
        if (d.size != size) return;

        visited.add(pos);
        for (BlockPos nb : getFacingPlaneNeighbors(pos, facing)) {
            floodFill(level, nb, facing, portId, size, orthogonal, visited);
        }
    }

    /**
     * The top-left corner from the front view:
     *   NORTH  → max X (east), max Y
     *   SOUTH  → min X (west), max Y
     *   EAST   → max Z (south), max Y
     *   WEST   → min Z (north), max Y
     */
    private static BlockPos findMaster(Set<BlockPos> cluster, Direction facing,
            int minH, int maxH, int maxY) {
        int masterH = switch (facing) {
            case SOUTH, WEST -> minH;
            default          -> maxH; // NORTH, EAST
        };
        for (BlockPos p : cluster) {
            if (getHorizCoord(p, facing) == masterH && p.getY() == maxY) return p;
        }
        return cluster.iterator().next();
    }

    /** The axis perpendicular to the display face (all cluster members share the same value). */
    private static int getOrthogonalCoord(BlockPos pos, Direction facing) {
        return switch (facing) {
            case NORTH, SOUTH -> pos.getZ();
            case EAST,  WEST  -> pos.getX();
            default           -> 0;
        };
    }

    /** The horizontal axis that runs left-right across the display face. */
    private static int getHorizCoord(BlockPos pos, Direction facing) {
        return switch (facing) {
            case NORTH, SOUTH -> pos.getX();
            case EAST,  WEST  -> pos.getZ();
            default           -> 0;
        };
    }

    private static List<BlockPos> getFacingPlaneNeighbors(BlockPos pos, Direction facing) {
        return switch (facing) {
            case NORTH, SOUTH -> List.of(pos.east(), pos.west(), pos.above(), pos.below());
            case EAST,  WEST  -> List.of(pos.north(), pos.south(), pos.above(), pos.below());
            default           -> List.of(pos.above(), pos.below());
        };
    }

    // ── onLoad re-detection ───────────────────────────────────────────────────

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            detectAndUpdateCluster(level, worldPosition);
        }
    }

    // ── MenuProvider ─────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable(getContainerName());
    }

    protected String getContainerName() { return "container.omnitech.display"; }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId,
            Inventory inv, Player player) {
        return new DisplayMenu(containerId, inv, worldPosition, portId, clusterCols, clusterRows, size);
    }

    // ── Client sync ───────────────────────────────────────────────────────────

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ── Serialization ────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("PortId", portId);
        out.putIntArray("Pixels", pixels);
        out.putInt("ClusterCols", clusterCols);
        out.putInt("ClusterRows", clusterRows);
        if (masterPos != null) {
            out.putLong("MasterPos", masterPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        portId = in.getIntOr("PortId", 0);
        in.getIntArray("Pixels").ifPresent(arr ->
                System.arraycopy(arr, 0, pixels, 0, Math.min(arr.length, pixels.length)));
        clusterCols = Math.max(1, in.getIntOr("ClusterCols", 1));
        clusterRows = Math.max(1, in.getIntOr("ClusterRows", 1));
        long mp = in.getLongOr("MasterPos", Long.MIN_VALUE);
        masterPos = (mp != Long.MIN_VALUE) ? BlockPos.of(mp) : null;
    }
}
