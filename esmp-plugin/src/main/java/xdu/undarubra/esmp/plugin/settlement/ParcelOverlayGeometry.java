package xdu.undarubra.esmp.plugin.settlement;

import xdu.undarubra.esmp.civilization.settlement.ParcelCell;

import java.util.List;
import java.util.Set;

/** Exact particle positions, independent of Paper so geometry can be tested directly. */
public final class ParcelOverlayGeometry {
    @FunctionalInterface
    public interface Point {
        void emit(double x, double y, double z);
    }

    public record Edge(long x, long y, long z, int axis) {}

    private static final int[][] SURFACE = surfaceOffsets();

    private ParcelOverlayGeometry() {}

    private static int[][] surfaceOffsets() {
        var points = new java.util.ArrayList<int[]>();
        for (int x = 0; x <= 8; x++)
            for (int y = 0; y <= 8; y++)
                for (int z = 0; z <= 8; z++) {
                    if (x == 0 || x == 8 || y == 0 || y == 8 || z == 0 || z == 8)
                        points.add(new int[] {x, y, z});
                }
        return points.toArray(int[][]::new);
    }

    public static int surface(ParcelCell cell, int budget, Point point) {
        int count = Math.min(Math.max(0, budget), SURFACE.length);
        for (int i = 0; i < count; i++) {
            int[] offset = SURFACE[i * SURFACE.length / count];
            point.emit(
                    cell.x() * 8.0 + offset[0],
                    cell.y() * 8.0 + offset[1],
                    cell.z() * 8.0 + offset[2]);
        }
        return count;
    }

    public static int border(ParcelCell first, ParcelCell last, int budget, Point point) {
        if (budget < 24) return 0;
        double x = Math.min(first.x(), last.x()) * 8.0,
                y = Math.min(first.y(), last.y()) * 8.0,
                z = Math.min(first.z(), last.z()) * 8.0;
        double sx = (Math.abs((long) first.x() - last.x()) + 1) * 8.0;
        double sy = (Math.abs((long) first.y() - last.y()) + 1) * 8.0;
        double sz = (Math.abs((long) first.z() - last.z()) + 1) * 8.0;
        int used = 0, steps = budget / 12 - 1;
        for (int a = 0; a <= 1; a++)
            for (int b = 0; b <= 1; b++) {
                used += line(x, y + a * sy, z + b * sz, 0, sx, steps, point);
                used += line(x + a * sx, y, z + b * sz, 1, sy, steps, point);
                used += line(x + a * sx, y + b * sy, z, 2, sz, steps, point);
            }
        return used;
    }

    private static int line(
            double x, double y, double z, int axis, double length, int limit, Point point) {
        int steps = (int) Math.min(length, limit);
        for (int i = 0; i <= steps; i++) {
            double d = length * i / steps;
            point.emit(x + (axis == 0 ? d : 0), y + (axis == 1 ? d : 0), z + (axis == 2 ? d : 0));
        }
        return steps + 1;
    }

    /** Add only edges belonging to exposed faces of the actual Parcel, not its bounding box. */
    public static void exposedEdges(ParcelCell cell, Set<ParcelCell> parcel, Set<Edge> edges) {
        long x = (long) cell.x() * 8, y = (long) cell.y() * 8, z = (long) cell.z() * 8;
        for (int side = 0; side <= 1; side++) {
            int step = side == 0 ? -1 : 1;
            if (!parcel.contains(new ParcelCell(cell.x() + step, cell.y(), cell.z()))) {
                for (int b = 0; b <= 1; b++) {
                    edges.add(new Edge(x + side * 8, y, z + b * 8, 1));
                    edges.add(new Edge(x + side * 8, y + b * 8, z, 2));
                }
            }
            if (!parcel.contains(new ParcelCell(cell.x(), cell.y() + step, cell.z()))) {
                for (int b = 0; b <= 1; b++) {
                    edges.add(new Edge(x, y + side * 8, z + b * 8, 0));
                    edges.add(new Edge(x + b * 8, y + side * 8, z, 2));
                }
            }
            if (!parcel.contains(new ParcelCell(cell.x(), cell.y(), cell.z() + step))) {
                for (int b = 0; b <= 1; b++) {
                    edges.add(new Edge(x, y + b * 8, z + side * 8, 0));
                    edges.add(new Edge(x + b * 8, y, z + side * 8, 1));
                }
            }
        }
    }

    public static int edges(List<Edge> edges, int start, int budget, Point point) {
        if (edges.isEmpty()) return 0;
        int count = Math.min(edges.size(), Math.max(0, budget) / 9);
        for (int i = 0; i < count; i++) {
            var edge = edges.get(Math.floorMod(start + i, edges.size()));
            line(edge.x(), edge.y(), edge.z(), edge.axis(), 8, 8, point);
        }
        return count * 9;
    }
}
