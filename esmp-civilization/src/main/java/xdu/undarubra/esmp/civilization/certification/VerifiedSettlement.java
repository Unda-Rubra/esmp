package xdu.undarubra.esmp.civilization.certification;

import java.util.UUID;

/** Domain read/export boundary; no website, jOOQ record or live persistence context. */
public record VerifiedSettlement(UUID id, long number, String officialName) {}
