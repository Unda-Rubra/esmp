package xdu.undarubra.esmp.civilization.decay;

import xdu.undarubra.esmp.civilization.building.*;
import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;
import xdu.undarubra.esmp.civilization.decay.DecayService.*;

import java.io.*;
import java.util.*;

/**
 * Deliberately small full-cube material profiles; never entity physics or arbitrary substitutions.
 */
public final class DecayPlan {
    private DecayPlan() {}

    public static final Map<String, String> WEATHER =
            Map.of(
                    "minecraft:stone",
                    "minecraft:cobblestone",
                    "minecraft:stone_bricks",
                    "minecraft:cracked_stone_bricks",
                    "minecraft:cobblestone",
                    "minecraft:mossy_cobblestone",
                    "minecraft:deepslate_bricks",
                    "minecraft:cracked_deepslate_bricks");
    public static final Set<String> STRUCTURAL =
            Set.of(
                    "minecraft:stone",
                    "minecraft:cobblestone",
                    "minecraft:mossy_cobblestone",
                    "minecraft:stone_bricks",
                    "minecraft:cracked_stone_bricks",
                    "minecraft:bricks",
                    "minecraft:deepslate_bricks",
                    "minecraft:cracked_deepslate_bricks",
                    "minecraft:oak_planks",
                    "minecraft:spruce_planks",
                    "minecraft:birch_planks",
                    "minecraft:dark_oak_planks",
                    "minecraft:acacia_planks",
                    "minecraft:jungle_planks",
                    "minecraft:mangrove_planks",
                    "minecraft:cherry_planks",
                    "minecraft:bamboo_planks");

    public static boolean safeNeighbor(BuildingCell cell) {
        return cell.air()
                || (!cell.blockEntity()
                        && (STRUCTURAL.contains(cell.data())
                                || Set.of(
                                                "minecraft:gold_block",
                                                "minecraft:iron_block",
                                                "minecraft:diamond_block",
                                                "minecraft:emerald_block",
                                                "minecraft:bedrock")
                                        .contains(cell.data())));
    }

    public static List<Position> candidates(
            BuildingService.View view, BuildingBaseline baseline, Kind kind, long cursor) {
        var geometry = baseline.geometry();
        var candidates = new ArrayList<Position>();
        for (int i : geometry.exterior()) {
            var p = baseline.bounds().position(i);
            var cell = baseline.cells().get(i);
            if (!view.permitsAutomaticMutation(p, cell)) continue;
            if (kind == Kind.WEATHER
                    ? WEATHER.containsKey(cell.data())
                    : STRUCTURAL.contains(cell.data()) && p.y() > baseline.bounds().minY())
                candidates.add(p);
        }
        candidates.sort(
                Comparator.comparingInt(Position::y)
                        .reversed()
                        .thenComparingInt(Position::x)
                        .thenComparingInt(Position::z));
        if (candidates.isEmpty()) return List.of();
        int offset = Math.floorMod(cursor / 3, candidates.size());
        var result = new ArrayList<Position>();
        for (int n = 0; n < Math.min(32, candidates.size()); n++)
            result.add(candidates.get((offset + n) % candidates.size()));
        return List.copyOf(result);
    }

    public static List<Change> plan(Offer offer, Map<Position, BuildingCell> world) {
        var eligible = new LinkedHashSet<Position>();
        var b = offer.baseline();
        for (var p : offer.candidates()) {
            var actual = world.get(p);
            if (actual == null
                    || !actual.data().equals(offer.expected().getOrDefault(p, b.at(p).data()))
                    || !offer.building().permitsAutomaticMutation(p, actual)) continue;
            if (offer.kind() == Kind.WEATHER) {
                if (WEATHER.containsKey(actual.data()))
                    return List.of(new Change(p, actual.data(), WEATHER.get(actual.data())));
            } else {
                var below = new Position(p.x(), p.y() - 1, p.z());
                var target = world.get(below);
                if (STRUCTURAL.contains(actual.data())
                        && b.bounds().contains(below)
                        && offer.building().permitsAutomaticMutation(below, b.at(below))
                        && target != null
                        && target.air()
                        && !target.blockEntity()
                        && target.data()
                                .equals(offer.expected().getOrDefault(below, b.at(below).data())))
                    eligible.add(p);
            }
        }
        if (eligible.isEmpty()) return List.of();
        Position seed = eligible.iterator().next();
        var patch = new ArrayList<Position>();
        var queue = new ArrayDeque<Position>();
        queue.add(seed);
        eligible.remove(seed);
        while (!queue.isEmpty() && patch.size() < (offer.kind() == Kind.DROP ? 1 : 4)) {
            var p = queue.removeFirst();
            patch.add(p);
            for (var n :
                    List.of(
                            new Position(p.x() - 1, p.y(), p.z()),
                            new Position(p.x() + 1, p.y(), p.z()),
                            new Position(p.x(), p.y(), p.z() - 1),
                            new Position(p.x(), p.y(), p.z() + 1)))
                if ((n.x() >> 4) == (seed.x() >> 4)
                        && (n.z() >> 4) == (seed.z() >> 4)
                        && eligible.remove(n)) queue.add(n);
        }
        var changes = new ArrayList<Change>();
        for (var p : patch) {
            var below = new Position(p.x(), p.y() - 1, p.z());
            changes.add(new Change(below, world.get(below).data(), world.get(p).data()));
        }
        for (var p : patch) changes.add(new Change(p, world.get(p).data(), "minecraft:air"));
        return List.copyOf(changes);
    }

    public static byte[] encode(List<Change> changes) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(1);
                out.writeInt(changes.size());
                for (var c : changes) {
                    out.writeInt(c.position().x());
                    out.writeInt(c.position().y());
                    out.writeInt(c.position().z());
                    out.writeUTF(c.expected());
                    out.writeUTF(c.target());
                }
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Change> decode(byte[] bytes) {
        if (bytes == null || bytes.length > 131072)
            throw new IllegalArgumentException("Invalid Decay intent size");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IOException("Unknown Decay intent version");
            int size = in.readInt();
            if (size < 1 || size > 8) throw new IOException("Invalid Decay batch");
            var result = new ArrayList<Change>();
            var unique = new HashSet<Position>();
            for (int i = 0; i < size; i++) {
                var p = new Position(in.readInt(), in.readInt(), in.readInt());
                if (!unique.add(p)) throw new IOException("Overlapping Decay cells");
                result.add(new Change(p, in.readUTF(), in.readUTF()));
            }
            if (in.read() != -1) throw new IOException("Trailing Decay intent");
            return List.copyOf(result);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
