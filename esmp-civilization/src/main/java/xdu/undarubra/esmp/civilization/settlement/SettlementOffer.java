package xdu.undarubra.esmp.civilization.settlement;

import java.time.Instant;
import java.util.UUID;

public record SettlementOffer(UUID id, UUID settlementId, Type type, UUID issuer, UUID recipient,
        Instant expiresAt, long ownershipRevision, long chunkRevision) {
    public enum Type { INVITE, PROMOTE, TRANSFER, ABANDON }
}
