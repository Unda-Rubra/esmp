package xdu.undarubra.esmp.civilization.settlement;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable committed state; only owned Settlements appear in the live index. */
public record SettlementView(UUID id, UUID worldInstanceId, String name, UUID owner,
        Instant createdAt, long ownershipRevision, long chunkRevision,
        Map<UUID, Role> members, Set<ChunkPosition> chunks) {
    public SettlementView {
        members = Map.copyOf(members);
        chunks = Set.copyOf(chunks);
    }
    public Role role(UUID player) {
        return owner.equals(player) ? Role.OWNER : members.getOrDefault(player, Role.VISITOR);
    }
}
