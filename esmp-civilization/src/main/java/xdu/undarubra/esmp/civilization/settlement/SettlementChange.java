package xdu.undarubra.esmp.civilization.settlement;

import java.util.UUID;

/** The post-commit view is null only when a Settlement has been released. */
public record SettlementChange(UUID settlementId, SettlementView current, SettlementOffer offer, String message) {
}
