package com.tygabeast.rootgolem.boundary;

import java.util.ArrayList;
import java.util.List;
import com.tygabeast.rootgolem.RootGolemConfig;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

/**
 * One boundary: markers connected in the order they were placed. Once closed, the last marker
 * connects back to the first and the enclosed area (on the X/Z plane) is the golem's working area.
 *
 * The bounding box and vertex arrays are cached and only rebuilt when markers change, so
 * {@link #contains} is a cheap box test plus one pass over the vertices.
 */
public final class Boundary {
    /** Tree spacing presets a stick cycles through; -1 means "use the server's treeSpacing". */
    static final int[] SPACING_PRESETS = {-1, 2, 4, 8, 12};

    public final UUID id;
    final List<BlockPos> points = new ArrayList<>();
    boolean closed;
    /** Player who made (or first edited) this boundary; null = unowned, anyone may edit. */
    @Nullable UUID owner;
    /** Per-boundary tree spacing, or -1 for the server default. */
    int spacing = -1;

    private int minX, maxX, minZ, maxZ;
    private double[] xs = new double[0];
    private double[] zs = new double[0];

    Boundary(UUID id) {
        this.id = id;
    }

    public List<BlockPos> points() {
        return points;
    }

    public boolean isClosed() {
        return closed && points.size() >= 3;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    /** Empty blocks to keep between trees in this boundary. */
    public int spacing() {
        return spacing >= 0 ? spacing : RootGolemConfig.TREE_SPACING.get();
    }

    public boolean usesDefaultSpacing() {
        return spacing < 0;
    }

    public int minX() { return minX; }
    public int maxX() { return maxX; }
    public int minZ() { return minZ; }
    public int maxZ() { return maxZ; }

    public long boxArea() {
        return (long) (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    void recompute() {
        int n = points.size();
        xs = new double[n];
        zs = new double[n];
        minX = minZ = Integer.MAX_VALUE;
        maxX = maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            BlockPos p = points.get(i);
            xs[i] = p.getX() + 0.5D;
            zs[i] = p.getZ() + 0.5D;
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
        }
    }

    /**
     * Whether the block column at (x, z) is strictly inside the closed area (even-odd rule on block
     * centres). Columns the boundary line itself runs through count as outside, so nothing is planted
     * on the line between two markers.
     */
    public boolean contains(int x, int z) {
        if (!isClosed() || x <= minX || x >= maxX || z <= minZ || z >= maxZ) return false;
        double px = x + 0.5D, pz = z + 0.5D;
        boolean inside = false;
        int n = xs.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if (onSegment(px, pz, xs[j], zs[j], xs[i], zs[i])) return false;
            if ((zs[i] > pz) != (zs[j] > pz)
                    && px < (xs[j] - xs[i]) * (pz - zs[i]) / (zs[j] - zs[i]) + xs[i]) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** True if the point is within half a block of the segment a-b. */
    private static boolean onSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double lenSqr = dx * dx + dz * dz;
        double t = lenSqr == 0 ? 0 : ((px - ax) * dx + (pz - az) * dz) / lenSqr;
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        double ex = ax + dx * t - px, ez = az + dz * t - pz;
        return ex * ex + ez * ez < 0.25D;
    }

    /** Bounding-box size if {@code extra} were added. */
    int sizeWith(BlockPos extra) {
        int w = Math.max(maxX, extra.getX()) - Math.min(minX, extra.getX()) + 1;
        int d = Math.max(maxZ, extra.getZ()) - Math.min(minZ, extra.getZ()) + 1;
        return Math.max(w, d);
    }

    static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
