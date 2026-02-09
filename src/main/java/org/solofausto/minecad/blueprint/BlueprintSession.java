package org.solofausto.minecad.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class BlueprintSession {
    private BlueprintOrigin origin;
    private final List<BlueprintPoint> points = new ArrayList<>();
    private final List<BlueprintLine> lines = new ArrayList<>();
    private BlueprintPoint pendingLineStart;
    private java.util.UUID currentBlueprintId;
    private final Object geometryLock = new Object();
    private long geometryVersion;
    private transient long cachedVersion = -1;
    private transient BlueprintOrigin cachedOrigin;
    private transient List<BlockPos> cachedPointBlocks = List.of();
    private transient List<LineBlockCache> cachedLineBlocks = List.of();

    public void start(BlueprintOrigin origin) {
        synchronized (geometryLock) {
            this.origin = origin;
            this.points.clear();
            this.lines.clear();
            this.pendingLineStart = null;
            markGeometryDirtyLocked();
        }
    }

    public BlueprintOrigin getOrigin() {
        return origin;
    }

    public java.util.UUID getCurrentBlueprintId() {
        return currentBlueprintId;
    }

    public void setCurrentBlueprintId(java.util.UUID currentBlueprintId) {
        this.currentBlueprintId = currentBlueprintId;
    }

    public void addPoint(BlueprintPoint point) {
        synchronized (geometryLock) {
            points.add(point);
            markGeometryDirtyLocked();
        }
    }

    public void addLine(BlueprintLine line) {
        synchronized (geometryLock) {
            lines.add(line);
            markGeometryDirtyLocked();
        }
    }

    public List<BlueprintPoint> getPoints() {
        synchronized (geometryLock) {
            return Collections.unmodifiableList(new ArrayList<>(points));
        }
    }

    public List<BlueprintLine> getLines() {
        synchronized (geometryLock) {
            return Collections.unmodifiableList(new ArrayList<>(lines));
        }
    }

    public BlueprintPoint getPendingLineStart() {
        return pendingLineStart;
    }

    public void setPendingLineStart(BlueprintPoint pendingLineStart) {
        this.pendingLineStart = pendingLineStart;
    }

    public void clear() {
        synchronized (geometryLock) {
            origin = null;
            points.clear();
            lines.clear();
            pendingLineStart = null;
            currentBlueprintId = null;
            markGeometryDirtyLocked();
        }
    }

    public List<BlockPos> getCachedPointBlocks() {
        ensureCache();
        return cachedPointBlocks;
    }

    public List<LineBlockCache> getCachedLineBlocks() {
        ensureCache();
        return cachedLineBlocks;
    }

    private void markGeometryDirty() {
        synchronized (geometryLock) {
            markGeometryDirtyLocked();
        }
    }

    private void markGeometryDirtyLocked() {
        geometryVersion++;
    }

    private void ensureCache() {
        synchronized (geometryLock) {
            if (origin == null) {
                cachedPointBlocks = List.of();
                cachedLineBlocks = List.of();
                cachedVersion = geometryVersion;
                cachedOrigin = null;
                return;
            }

            if (cachedVersion == geometryVersion && origin.equals(cachedOrigin)) {
                return;
            }

            List<BlockPos> pointBlocks = new ArrayList<>(points.size());
            for (BlueprintPoint point : points) {
                Vec3d world = BlueprintGeometry.toWorldCoords(point, origin);
                pointBlocks.add(BlockPos.ofFloored(world));
            }

            List<LineBlockCache> lineBlocks = new ArrayList<>(lines.size());
            for (BlueprintLine line : lines) {
                Vec3d startWorld = BlueprintGeometry.toWorldCoords(line.start(), origin);
                Vec3d endWorld = BlueprintGeometry.toWorldCoords(line.end(), origin);
                BlockPos start = BlockPos.ofFloored(startWorld);
                BlockPos end = BlockPos.ofFloored(endWorld);
                List<BlockPos> blocks = getLineBlocks(start, end);
                Box bounds = new Box(
                        Math.min(start.getX(), end.getX()),
                        Math.min(start.getY(), end.getY()),
                        Math.min(start.getZ(), end.getZ()),
                        Math.max(start.getX(), end.getX()) + 1,
                        Math.max(start.getY(), end.getY()) + 1,
                        Math.max(start.getZ(), end.getZ()) + 1);
                lineBlocks.add(new LineBlockCache(blocks, bounds));
            }

            cachedPointBlocks = Collections.unmodifiableList(pointBlocks);
            cachedLineBlocks = Collections.unmodifiableList(lineBlocks);
            cachedVersion = geometryVersion;
            cachedOrigin = origin;
        }
    }

    private static List<BlockPos> getLineBlocks(BlockPos start, BlockPos end) {
        List<BlockPos> blocks = new ArrayList<>();
        int x1 = start.getX();
        int y1 = start.getY();
        int z1 = start.getZ();
        int x2 = end.getX();
        int y2 = end.getY();
        int z2 = end.getZ();

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);

        int xs = x2 > x1 ? 1 : -1;
        int ys = y2 > y1 ? 1 : -1;
        int zs = z2 > z1 ? 1 : -1;

        blocks.add(new BlockPos(x1, y1, z1));

        if (dx >= dy && dx >= dz) {
            int p1 = 2 * dy - dx;
            int p2 = 2 * dz - dx;
            while (x1 != x2) {
                x1 += xs;
                if (p1 >= 0) {
                    y1 += ys;
                    p1 -= 2 * dx;
                }
                if (p2 >= 0) {
                    z1 += zs;
                    p2 -= 2 * dx;
                }
                p1 += 2 * dy;
                p2 += 2 * dz;
                blocks.add(new BlockPos(x1, y1, z1));
            }
        } else if (dy >= dx && dy >= dz) {
            int p1 = 2 * dx - dy;
            int p2 = 2 * dz - dy;
            while (y1 != y2) {
                y1 += ys;
                if (p1 >= 0) {
                    x1 += xs;
                    p1 -= 2 * dy;
                }
                if (p2 >= 0) {
                    z1 += zs;
                    p2 -= 2 * dy;
                }
                p1 += 2 * dx;
                p2 += 2 * dz;
                blocks.add(new BlockPos(x1, y1, z1));
            }
        } else {
            int p1 = 2 * dy - dz;
            int p2 = 2 * dx - dz;
            while (z1 != z2) {
                z1 += zs;
                if (p1 >= 0) {
                    y1 += ys;
                    p1 -= 2 * dz;
                }
                if (p2 >= 0) {
                    x1 += xs;
                    p2 -= 2 * dz;
                }
                p1 += 2 * dy;
                p2 += 2 * dx;
                blocks.add(new BlockPos(x1, y1, z1));
            }
        }

        return blocks;
    }

    public record LineBlockCache(List<BlockPos> blocks, Box bounds) {
    }
}
