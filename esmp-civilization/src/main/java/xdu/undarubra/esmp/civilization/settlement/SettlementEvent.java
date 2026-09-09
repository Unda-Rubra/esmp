package xdu.undarubra.esmp.civilization.settlement;

import java.time.Instant;
import java.util.UUID;

/** Durable historical fact. Delivery may repeat; consumers deduplicate by id. */
public record SettlementEvent(UUID id, UUID settlementId, UUID actor, String type, String detail, Instant occurredAt, Long gameTick) {
}
