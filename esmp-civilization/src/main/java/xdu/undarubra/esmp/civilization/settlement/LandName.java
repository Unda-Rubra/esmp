package xdu.undarubra.esmp.civilization.settlement;

/** Shared persisted Settlement/Parcel title rules, also used by read-only previews. */
public final class LandName {
    private LandName() {}

    public static String normalize(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (!name.matches("[\\p{L}\\p{N}_ -]{1,32}")) {
            throw new SettlementException(
                    "Use 1–32 letters, digits, spaces, underscores or hyphens for a name.");
        }
        return name;
    }
}
