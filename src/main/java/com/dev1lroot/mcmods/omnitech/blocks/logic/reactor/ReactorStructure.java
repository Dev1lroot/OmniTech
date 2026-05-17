package com.dev1lroot.mcmods.omnitech.blocks.logic.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReactorStructure {

    public static final int HEIGHT   = 7;
    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 11;

    public final BlockPos    origin;
    public final int         width;
    public final int         depth;
    public final List<BlockPos> cells;  // top-face ReactorCell positions, sorted Z then X

    ReactorStructure(BlockPos origin, int width, int depth, List<BlockPos> cells) {
        this.origin = origin;
        this.width  = width;
        this.depth  = depth;
        this.cells  = cells;
    }

    /**
     * Tries every valid structure size whose bounding box could contain {@code fromPos}.
     * Returns the first matching structure, or empty if none is found.
     */
    public static Optional<ReactorStructure> detect(Level level, BlockPos fromPos) {
        for (int w = MIN_SIZE; w <= MAX_SIZE; w++) {
            for (int d = MIN_SIZE; d <= MAX_SIZE; d++) {
                for (int ox = fromPos.getX() - (w - 1); ox <= fromPos.getX(); ox++) {
                    for (int oz = fromPos.getZ() - (d - 1); oz <= fromPos.getZ(); oz++) {
                        for (int oy = fromPos.getY() - (HEIGHT - 1); oy <= fromPos.getY(); oy++) {
                            Optional<ReactorStructure> s =
                                    tryValidate(level, new BlockPos(ox, oy, oz), w, d);
                            if (s.isPresent()) return s;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<ReactorStructure> tryValidate(Level level, BlockPos origin,
                                                           int w, int d) {
        int minX = origin.getX(), maxX = minX + w - 1;
        int minY = origin.getY(), maxY = minY + HEIGHT - 1;
        int minZ = origin.getZ(), maxZ = minZ + d - 1;

        List<BlockPos> cells = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean onShell = x == minX || x == maxX
                                   || y == minY || y == maxY
                                   || z == minZ || z == maxZ;

                    if (!onShell) {
                        if (!level.isEmptyBlock(new BlockPos(x, y, z))) return Optional.empty();
                        continue;
                    }

                    BlockPos pos   = new BlockPos(x, y, z);
                    Block    block = level.getBlockState(pos).getBlock();
                    boolean  corner = isCorner(x, y, z, minX, minY, minZ, maxX, maxY, maxZ);

                    if (corner) {
                        if (!(block instanceof ReactorBlock)) return Optional.empty();
                    } else if (y == maxY) {
                        // Top face (non-corner): ReactorCell or ReactorBlock
                        if (block instanceof ReactorCell) {
                            cells.add(pos);
                        } else if (!(block instanceof ReactorBlock)) {
                            return Optional.empty();
                        }
                    } else {
                        // Bottom face and all side walls (non-corner): ReactorBlock or ReactorPort
                        if (!(block instanceof ReactorBlock) && !(block instanceof ReactorPort)) {
                            return Optional.empty();
                        }
                    }
                }
            }
        }

        cells.sort((a, b) -> {
            int cmp = Integer.compare(a.getZ(), b.getZ());
            return cmp != 0 ? cmp : Integer.compare(a.getX(), b.getX());
        });
        return Optional.of(new ReactorStructure(origin, w, d, cells));
    }

    private static boolean isCorner(int x, int y, int z,
                                    int minX, int minY, int minZ,
                                    int maxX, int maxY, int maxZ) {
        return (x == minX || x == maxX) && (y == minY || y == maxY) && (z == minZ || z == maxZ);
    }

    public boolean isStillValid(Level level) {
        return tryValidate(level, origin, width, depth).isPresent();
    }

    /**
     * When a ReactorPort or ReactorCell is removed, scan for any nearby formed master
     * and invalidate it. The origin (master) is always at lower-or-equal coordinates
     * than the removed block, so we only need to scan in the negative dx/dy/dz direction.
     */
    public static void invalidateNearbyMaster(Level level, BlockPos removedPos) {
        if (level.isClientSide()) return;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int dx = -(MAX_SIZE - 1); dx <= 0; dx++) {
            for (int dy = -(HEIGHT - 1); dy <= 0; dy++) {
                for (int dz = -(MAX_SIZE - 1); dz <= 0; dz++) {
                    mpos.set(removedPos.getX() + dx, removedPos.getY() + dy, removedPos.getZ() + dz);
                    if (level.getBlockEntity(mpos) instanceof ReactorBlockEntity rbe
                            && rbe.isFormed()) {
                        rbe.invalidate();
                    }
                }
            }
        }
    }
}
