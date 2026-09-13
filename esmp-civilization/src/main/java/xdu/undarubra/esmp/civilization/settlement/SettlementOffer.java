package xdu.undarubra.esmp.civilization.settlement;

import java.util.UUID;

public record SettlementOffer(
        UUID id,
        UUID settlementId,
        Type type,
        UUID issuer,
        UUID recipient,
        long expiresAtTick,
        long ownershipRevision,
        long chunkRevision) {
    public enum Type {
        INVITE,
        PROMOTE,
        ABANDON
    }
}
