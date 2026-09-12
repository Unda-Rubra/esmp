package xdu.undarubra.esmp.civilization.settlement;

import java.util.UUID;

public record ClaimQuote(
        UUID id,
        UUID actor,
        UUID owner,
        UUID settlementId,
        UUID worldInstance,
        ChunkPosition chunk,
        boolean founding,
        Payer payer,
        UUID payerId,
        long amount,
        String context,
        String summary,
        long expiresTick,
        boolean purchased) {
    public enum Payer {
        WALLET,
        TREASURY
    }
}
