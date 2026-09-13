package xdu.undarubra.esmp.civilization.repair;

import xdu.undarubra.esmp.civilization.decay.DecayPlan;
import xdu.undarubra.esmp.civilization.repair.RepairService.*;

import java.io.*;
import java.util.*;

/** Current Decay profiles use ordinary full-cube block items; no NBT/custom items enter escrow. */
public final class RepairMaterials {
    private RepairMaterials() {}

    public static String material(String baseline) {
        if (Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air").contains(baseline))
            return null;
        if (!DecayPlan.STRUCTURAL.contains(baseline))
            throw new IllegalArgumentException("Unsupported original repair material: " + baseline);
        return baseline;
    }

    public static List<Slot> supply(Job job, List<Item> inventory) {
        var needed = new HashMap<String, Long>();
        for (var s : job.stock()) needed.put(s.material(), Math.max(0, s.needed() - s.escrow()));
        var result = new ArrayList<Slot>();
        for (int i = 0; i < inventory.size(); i++) {
            var old = inventory.get(i);
            int count = (int) Math.min(old.count(), needed.getOrDefault(old.material(), 0L));
            if (count == 0) continue;
            needed.merge(old.material(), -(long) count, Long::sum);
            int after = old.count() - count;
            result.add(
                    new Slot(i, old, after == 0 ? Item.empty() : new Item(old.material(), after)));
        }
        return List.copyOf(result);
    }

    public static List<Slot> refund(Job job, List<Item> inventory) {
        var available = new TreeMap<String, Long>();
        for (var s : job.stock()) if (s.escrow() > 0) available.put(s.material(), s.escrow());
        var result = new LinkedHashMap<Integer, Slot>();
        var copy = new ArrayList<>(inventory);
        for (var entry : available.entrySet()) {
            long remaining = entry.getValue();
            for (int pass = 0; pass < 2 && remaining > 0; pass++)
                for (int i = 0; i < copy.size() && remaining > 0; i++) {
                    var old = copy.get(i);
                    if (pass == 0 ? !old.material().equals(entry.getKey()) : old.count() != 0)
                        continue;
                    int amount = (int) Math.min(64 - old.count(), remaining);
                    if (amount == 0) continue;
                    var next = new Item(entry.getKey(), old.count() + amount);
                    var original = result.containsKey(i) ? result.get(i).before() : old;
                    result.put(i, new Slot(i, original, next));
                    copy.set(i, next);
                    remaining -= amount;
                }
        }
        return List.copyOf(result.values());
    }

    public static Map<String, Long> amounts(Direction direction, List<Slot> slots) {
        var result = new TreeMap<String, Long>();
        var seen = new HashSet<Integer>();
        for (var s : slots) {
            if (!seen.add(s.index()))
                throw new IllegalArgumentException("Duplicate inventory slot");
            Item source = direction == Direction.SUPPLY ? s.before() : s.after(),
                    target = direction == Direction.SUPPLY ? s.after() : s.before();
            if (source.count() == 0
                    || (!target.material().isEmpty()
                            && !target.material().equals(source.material()))
                    || source.count() <= target.count()
                    || !DecayPlan.STRUCTURAL.contains(source.material()))
                throw new IllegalArgumentException(
                        "Transfer must only move plain repair materials in its declared direction");
            result.merge(source.material(), (long) source.count() - target.count(), Long::sum);
        }
        return Map.copyOf(result);
    }

    public static byte[] encode(List<Slot> slots) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(1);
                out.writeInt(slots.size());
                for (var s : slots) {
                    out.writeInt(s.index());
                    out.writeUTF(s.before().material());
                    out.writeInt(s.before().count());
                    out.writeUTF(s.after().material());
                    out.writeInt(s.after().count());
                }
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Slot> decode(byte[] bytes) {
        if (bytes == null || bytes.length > 16384)
            throw new IllegalArgumentException("Invalid inventory transfer size");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IOException("Unknown transfer version");
            int size = in.readInt();
            if (size < 1 || size > 36) throw new IOException("Invalid transfer slot count");
            var result = new ArrayList<Slot>();
            for (int i = 0; i < size; i++)
                result.add(
                        new Slot(
                                in.readInt(),
                                new Item(in.readUTF(), in.readInt()),
                                new Item(in.readUTF(), in.readInt())));
            if (in.read() != -1) throw new IOException("Trailing transfer data");
            return List.copyOf(result);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
