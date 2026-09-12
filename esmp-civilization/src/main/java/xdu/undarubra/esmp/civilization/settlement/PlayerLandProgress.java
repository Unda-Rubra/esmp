package xdu.undarubra.esmp.civilization.settlement;

/**
 * Personal, never-transferred purchase progression. Every supported mutation consumes a purchase,
 * so the persisted claimsUsed counter is also its monotonic revision; no independent column can
 * drift from it. Acquisition of someone else's Settlement does not mutate this value.
 */
public record PlayerLandProgress(boolean firstFoundingUsed, long claimsUsed) {
    public PlayerLandProgress {
        if (claimsUsed < 0 || (firstFoundingUsed && claimsUsed == 0)) {
            throw new IllegalArgumentException("Invalid personal land progression");
        }
    }

    public long revision() {
        return claimsUsed;
    }
}
