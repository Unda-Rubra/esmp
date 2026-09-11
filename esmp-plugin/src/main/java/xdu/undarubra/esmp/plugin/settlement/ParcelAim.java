package xdu.undarubra.esmp.plugin.settlement;

import xdu.undarubra.esmp.civilization.settlement.ParcelCell;

/** Camera-ray math shared by the live overlay and click selection. */
public final class ParcelAim {
    public static final double MAX_REACH = 64;

    @FunctionalInterface
    public interface LoadedChunk {
        boolean test(int x, int z);
    }

    private ParcelAim() {}

    public static ParcelCell adjacent(
            double x, double y, double z, double dx, double dy, double dz) {
        requireRay(x, y, z, dx, dy, dz);
        var cell =
                ParcelCell.atBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return after(cell, x, y, z, dx, dy, dz);
    }

    public static ParcelCell outsidePlayer(
            double x, double y, double z, double dx, double dy, double dz, ParcelCell body) {
        var next = adjacent(x, y, z, dx, dy, dz);
        return next.equals(body) ? after(body, x, y, z, dx, dy, dz) : next;
    }

    private static ParcelCell after(
            ParcelCell cell, double x, double y, double z, double dx, double dy, double dz) {
        double tx = exit(x, dx, cell.x(), 8);
        double ty = exit(y, dy, cell.y(), 8);
        double tz = exit(z, dz, cell.z(), 8);
        double distance = Math.min(tx, Math.min(ty, tz));
        double tolerance = Math.max(1e-10, Math.ulp(distance) * 4);
        return new ParcelCell(
                cell.x() + (Math.abs(tx - distance) <= tolerance ? sign(dx) : 0),
                cell.y() + (Math.abs(ty - distance) <= tolerance ? sign(dy) : 0),
                cell.z() + (Math.abs(tz - distance) <= tolerance ? sign(dz) : 0));
    }

    /** Clip before the first unavailable chunk, including corner-touching side chunks. */
    public static double loadedReach(
            double x, double z, double dx, double dz, double reach, LoadedChunk loaded) {
        if (!Double.isFinite(reach)
                || reach <= 0
                || !Double.isFinite(x)
                || !Double.isFinite(z)
                || !Double.isFinite(dx)
                || !Double.isFinite(dz)) return 0;
        double limit = Math.min(MAX_REACH, reach);
        int cx = Math.floorDiv((int) Math.floor(x), 16);
        int cz = Math.floorDiv((int) Math.floor(z), 16);
        int sx = sign(dx), sz = sign(dz);
        double tx = exit(x, dx, cx, 16), tz = exit(z, dz, cz, 16);
        double traveled = 0;
        while (traveled <= limit) {
            if (!loaded.test(cx, cz)) return Math.max(0, traveled - 1e-6);
            double next = Math.min(tx, tz);
            if (next > limit) break;
            boolean crossX = tx <= tz, crossZ = tz <= tx;
            if (crossX && crossZ && (!loaded.test(cx + sx, cz) || !loaded.test(cx, cz + sz))) {
                return Math.max(0, next - 1e-6);
            }
            if (crossX) {
                cx += sx;
                tx += 16 / Math.abs(dx);
            }
            if (crossZ) {
                cz += sz;
                tz += 16 / Math.abs(dz);
            }
            traveled = next;
        }
        return limit;
    }

    private static double exit(double position, double direction, int cell, int size) {
        if (direction == 0) return Double.POSITIVE_INFINITY;
        double boundary = ((double) cell + (direction > 0 ? 1 : 0)) * size;
        return Math.max(0, (boundary - position) / direction);
    }

    private static int sign(double value) {
        return value > 0 ? 1 : value < 0 ? -1 : 0;
    }

    private static void requireRay(double x, double y, double z, double dx, double dy, double dz) {
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || !Double.isFinite(dx)
                || !Double.isFinite(dy)
                || !Double.isFinite(dz)
                || (dx == 0 && dy == 0 && dz == 0)) {
            throw new IllegalArgumentException("Invalid targeting ray");
        }
    }
}
