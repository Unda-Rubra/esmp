package xdu.undarubra.esmp.civilization.activity;

import java.util.UUID;

/** Immutable authorized-action observation; SQL rechecks identity, revisions and current role. */
public record ActivityContribution(UUID settlementId, UUID worldInstanceId, UUID actor, int chunkX, int chunkZ,
        long ownershipRevision, long chunkRevision, Category category) {
    public enum Category { BUILD, INTERACTION }
}
