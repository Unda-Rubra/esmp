package xdu.undarubra.esmp.civilization.building;

import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Bounded palette snapshot; geometry is reproducibly derived from the active design, not damage.
 */
public record BuildingBaseline(BuildingBounds bounds, List<BuildingCell> cells) {
    public BuildingBaseline {
        cells = List.copyOf(cells);
        if (cells.size() != bounds.volume())
            throw new IllegalArgumentException("Incomplete Building scan");
    }

    public BuildingCell at(Position p) {
        return cells.get(bounds.index(p));
    }

    public BuildingBaseline patch(Map<Position, BuildingCell> changes) {
        var copy = new ArrayList<>(cells);
        changes.forEach((p, c) -> copy.set(bounds.index(p), c));
        return new BuildingBaseline(bounds, copy);
    }

    public record Geometry(
            Set<Integer> occupied,
            Set<Integer> exterior,
            Set<Integer> interiorAir,
            Set<Integer> openings,
            Map<Integer, Integer> supportedBy) {}

    private int neighbor(int i, int direction) {
        var p = bounds.position(i);
        long x = p.x(), y = p.y(), z = p.z();
        switch (direction) {
            case 0 -> x--;
            case 1 -> x++;
            case 2 -> y--;
            case 3 -> y++;
            case 4 -> z--;
            case 5 -> z++;
            default -> throw new IllegalArgumentException();
        }
        if (x < bounds.minX()
                || x > bounds.maxX()
                || y < bounds.minY()
                || y > bounds.maxY()
                || z < bounds.minZ()
                || z > bounds.maxZ()) return -1;
        return bounds.index(new Position((int) x, (int) y, (int) z));
    }

    private boolean air(int index) {
        return index < 0 || cells.get(index).air();
    }

    private boolean solid(int index) {
        return index >= 0 && cells.get(index).solid();
    }

    public Geometry geometry() {
        var occupied = new HashSet<Integer>();
        var exterior = new HashSet<Integer>();
        var interior = new HashSet<Integer>();
        var openings = new HashSet<Integer>();
        var support = new HashMap<Integer, Integer>();
        int count = cells.size();
        var outside = new BitSet(count);
        var queue = new ArrayDeque<Integer>();
        for (int i = 0; i < count; i++) {
            var cell = cells.get(i);
            if (!cell.air()) occupied.add(i);
            // Explicit portals and one-cell apertures between opposing wall faces are openings.
            if (cell.opening()
                    || (cell.air()
                            && ((solid(neighbor(i, 0))
                                            && solid(neighbor(i, 1))
                                            && air(neighbor(i, 4))
                                            && air(neighbor(i, 5)))
                                    || (solid(neighbor(i, 4))
                                            && solid(neighbor(i, 5))
                                            && air(neighbor(i, 0))
                                            && air(neighbor(i, 1)))))) openings.add(i);
            int below = neighbor(i, 2);
            if (cell.solid() && solid(below)) support.put(i, below);
        }
        for (int i = 0; i < count; i++)
            if (cells.get(i).air() && !openings.contains(i))
                for (int d = 0; d < 6; d++)
                    if (neighbor(i, d) < 0) {
                        outside.set(i);
                        queue.add(i);
                        break;
                    }
        while (!queue.isEmpty()) {
            int i = queue.removeFirst();
            for (int d = 0; d < 6; d++) {
                int n = neighbor(i, d);
                if (n >= 0 && cells.get(n).air() && !openings.contains(n) && !outside.get(n)) {
                    outside.set(n);
                    queue.add(n);
                }
            }
        }
        for (int i = 0; i < count; i++) {
            if (cells.get(i).air() && !outside.get(i) && !openings.contains(i)) interior.add(i);
            if (!cells.get(i).air())
                for (int d = 0; d < 6; d++) {
                    int n = neighbor(i, d);
                    if (n < 0 || outside.get(n)) {
                        exterior.add(i);
                        break;
                    }
                }
        }
        return new Geometry(
                Set.copyOf(occupied),
                Set.copyOf(exterior),
                Set.copyOf(interior),
                Set.copyOf(openings),
                Map.copyOf(support));
    }

    public byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
                out.writeInt(1);
                writeBounds(out, bounds);
                var palette = new LinkedHashMap<BuildingCell, Integer>();
                for (var c : cells) palette.computeIfAbsent(c, ignored -> palette.size());
                out.writeInt(palette.size());
                for (var c : palette.keySet()) {
                    writeCell(out, c);
                    if ((long) out.size() + (long) cells.size() * Integer.BYTES > 16 * 1024 * 1024)
                        throw new IOException("Building palette exceeds safety limit");
                }
                for (var c : cells) out.writeInt(palette.get(c));
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    public static BuildingBaseline decode(byte[] bytes) {
        if (bytes == null || bytes.length > 16 * 1024 * 1024 + 65536)
            throw new IllegalArgumentException("Invalid Building snapshot size");
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] raw = gzip.readNBytes(16 * 1024 * 1024 + 1);
            if (raw.length > 16 * 1024 * 1024)
                throw new IOException("Expanded Building snapshot exceeds safety limit");
            var in = new DataInputStream(new ByteArrayInputStream(raw));
            if (in.readInt() != 1) throw new IOException("Unknown Building snapshot version");
            var b = readBounds(in);
            int count = in.readInt();
            if (count < 1 || count > b.volume()) throw new IOException("Invalid Building palette");
            var palette = new ArrayList<BuildingCell>(count);
            for (int i = 0; i < count; i++) palette.add(readCell(in));
            var cells = new ArrayList<BuildingCell>(b.volume());
            for (int i = 0; i < b.volume(); i++) {
                int key = in.readInt();
                if (key < 0 || key >= count) throw new IOException("Invalid palette index");
                cells.add(palette.get(key));
            }
            if (in.read() != -1) throw new IOException("Trailing Building snapshot data");
            return new BuildingBaseline(b, cells);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeBounds(DataOutput out, BuildingBounds b) throws IOException {
        out.writeInt(b.minX());
        out.writeInt(b.minY());
        out.writeInt(b.minZ());
        out.writeInt(b.maxX());
        out.writeInt(b.maxY());
        out.writeInt(b.maxZ());
    }

    public static BuildingBounds readBounds(DataInput in) throws IOException {
        return new BuildingBounds(
                in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
    }

    public static void writeCell(DataOutput out, BuildingCell c) throws IOException {
        out.writeUTF(c.data());
        out.writeByte(
                (c.air() ? 1 : 0)
                        | (c.solid() ? 2 : 0)
                        | (c.opening() ? 4 : 0)
                        | (c.blockEntity() ? 8 : 0));
    }

    public static BuildingCell readCell(DataInput in) throws IOException {
        String data = in.readUTF();
        int flags = in.readUnsignedByte();
        if (flags > 15) throw new IOException("Invalid cell flags");
        return new BuildingCell(
                data, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0);
    }
}
