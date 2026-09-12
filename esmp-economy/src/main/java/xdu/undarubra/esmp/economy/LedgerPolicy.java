package xdu.undarubra.esmp.economy;

public record LedgerPolicy(long periodTicks, long issuanceLimit) {
    public LedgerPolicy {
        if (periodTicks <= 0 || issuanceLimit < 0)
            throw new IllegalArgumentException("Invalid monetary period or budget");
    }
}
