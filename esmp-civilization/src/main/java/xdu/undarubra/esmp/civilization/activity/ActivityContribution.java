package xdu.undarubra.esmp.civilization.activity;

import xdu.undarubra.esmp.civilization.settlement.ParcelAction;

import java.util.UUID;

/**
 * Immutable observation; persisted authorization and all relevant revisions are rechecked before
 * credit.
 */
public record ActivityContribution(
        UUID settlementId,
        UUID worldInstanceId,
        UUID actor,
        int blockX,
        int blockY,
        int blockZ,
        long ownershipRevision,
        long chunkRevision,
        long securityRevision,
        ParcelAction action,
        boolean safePublic) {
    public enum Category {
        BUILD,
        INTERACTION
    }

    public Category category() {
        return action == ParcelAction.BUILD ? Category.BUILD : Category.INTERACTION;
    }
}
