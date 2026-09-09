package xdu.undarubra.esmp.civilization.settlement;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Main-thread-only spatial index, updated only after a transaction has committed. */
public final class SettlementIndex {
    private final Map<UUID, SettlementView> settlements = new HashMap<>();
    private final Map<String, UUID> names = new HashMap<>();
    private final Map<UUID, Map<Long, UUID>> worlds = new HashMap<>();

    public void load(Collection<SettlementView> values) {
        settlements.clear();
        names.clear();
        worlds.clear();
        values.forEach(value -> replace(value.id(), value));
    }
    public void replace(UUID id, SettlementView value) {
        SettlementView old = settlements.remove(id);
        if (old != null) {
            names.remove(old.name().toLowerCase(Locale.ROOT));
            Map<Long, UUID> chunks = worlds.get(old.worldInstanceId());
            old.chunks().forEach(chunk -> chunks.remove(chunk.key()));
            if (chunks.isEmpty()) { worlds.remove(old.worldInstanceId()); }
        }
        if (value != null) {
            settlements.put(id, value);
            names.put(value.name().toLowerCase(Locale.ROOT), id);
            Map<Long, UUID> chunks = worlds.computeIfAbsent(value.worldInstanceId(), ignored -> new HashMap<>());
            value.chunks().forEach(chunk -> chunks.put(chunk.key(), id));
        }
    }
    public SettlementView byId(UUID id) { return settlements.get(id); }
    public SettlementView byName(String name) { return settlements.get(names.get(name.toLowerCase(Locale.ROOT))); }
    public SettlementView at(UUID world, int x, int z) {
        Map<Long, UUID> chunks = worlds.get(world);
        return chunks == null ? null : settlements.get(chunks.get(((long) x << 32) | (z & 0xffffffffL)));
    }
    public Collection<SettlementView> all() { return List.copyOf(settlements.values()); }
    public int size() { return settlements.size(); }
}
