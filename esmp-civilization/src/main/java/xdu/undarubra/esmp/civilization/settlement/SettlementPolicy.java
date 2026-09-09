package xdu.undarubra.esmp.civilization.settlement;

import java.time.Duration;

public record SettlementPolicy(boolean chunkCapEnabled, int maxChunks, Duration invitationLifetime,
        Duration transferLifetime, Duration promotionLifetime, Duration abandonLifetime) {
    public SettlementPolicy {
        if (maxChunks < 1 || invitationLifetime.isNegative() || invitationLifetime.isZero()
                || transferLifetime.isNegative() || transferLifetime.isZero()
                || promotionLifetime.isNegative() || promotionLifetime.isZero()
                || abandonLifetime.isNegative() || abandonLifetime.isZero()) {
            throw new IllegalArgumentException("Settlement limits and offer durations must be positive");
        }
    }
    public static SettlementPolicy defaults() {
        return new SettlementPolicy(true, 64, Duration.ofHours(24), Duration.ofMinutes(30),
                Duration.ofHours(24), Duration.ofSeconds(60));
    }
    public Duration lifetime(SettlementOffer.Type type) {
        return switch (type) {
            case INVITE -> invitationLifetime;
            case TRANSFER -> transferLifetime;
            case PROMOTE -> promotionLifetime;
            case ABANDON -> abandonLifetime;
        };
    }
}
