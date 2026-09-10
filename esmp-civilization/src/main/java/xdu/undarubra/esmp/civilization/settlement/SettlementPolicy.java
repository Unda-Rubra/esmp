package xdu.undarubra.esmp.civilization.settlement;

public record SettlementPolicy(
        boolean chunkCapEnabled,
        int maxChunks,
        long invitationTicks,
        long transferTicks,
        long promotionTicks,
        long abandonTicks,
        long claimBasePrice,
        long claimPriceStep) {
    public SettlementPolicy {
        if (maxChunks < 1
                || invitationTicks <= 0
                || transferTicks <= 0
                || promotionTicks <= 0
                || abandonTicks <= 0
                || claimBasePrice < 0
                || claimPriceStep <= 0) {
            throw new IllegalArgumentException(
                    "Invalid Settlement limits, timing or numeric prices");
        }
    }

    public static SettlementPolicy defaults() {
        return new SettlementPolicy(true, 64, 1_728_000, 36_000, 1_728_000, 1_200, 100, 25);
    }

    public long claimPrice(int currentArea) {
        if (currentArea < 0) {
            throw new IllegalArgumentException("Negative claim area");
        }
        return Math.addExact(claimBasePrice, Math.multiplyExact(claimPriceStep, currentArea));
    }

    public long lifetimeTicks(SettlementOffer.Type type) {
        return switch (type) {
            case INVITE -> invitationTicks;
            case TRANSFER -> transferTicks;
            case PROMOTE -> promotionTicks;
            case ABANDON -> abandonTicks;
        };
    }
}
