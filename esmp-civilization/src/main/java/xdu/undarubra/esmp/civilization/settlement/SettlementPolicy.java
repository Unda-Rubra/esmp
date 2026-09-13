package xdu.undarubra.esmp.civilization.settlement;

public record SettlementPolicy(
        boolean chunkCapEnabled,
        int maxChunks,
        long invitationTicks,
        long promotionTicks,
        long abandonTicks) {
    public SettlementPolicy {
        if (maxChunks < 1 || invitationTicks <= 0 || promotionTicks <= 0 || abandonTicks <= 0) {
            throw new IllegalArgumentException("Invalid Settlement limits or timing");
        }
    }

    public static SettlementPolicy defaults() {
        return new SettlementPolicy(true, 64, 1_728_000, 1_728_000, 1_200);
    }

    public long lifetimeTicks(SettlementOffer.Type type) {
        return switch (type) {
            case INVITE -> invitationTicks;
            case PROMOTE -> promotionTicks;
            case ABANDON -> abandonTicks;
        };
    }
}
