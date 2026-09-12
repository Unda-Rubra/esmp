package xdu.undarubra.esmp.economy;

import java.util.Objects;
import java.util.UUID;

public record LedgerAccount(Kind kind, UUID owner) {
    public enum Kind {
        WALLET,
        TREASURY
    }

    public LedgerAccount {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(owner);
    }

    public static LedgerAccount wallet(UUID player) {
        return new LedgerAccount(Kind.WALLET, player);
    }

    public static LedgerAccount treasury(UUID settlement) {
        return new LedgerAccount(Kind.TREASURY, settlement);
    }

    public String key() {
        return kind.name() + ":" + owner;
    }

    public static LedgerAccount fromKey(String key) {
        if (key == null) return null;
        int separator = key.indexOf(':');
        return new LedgerAccount(
                Kind.valueOf(key.substring(0, separator)),
                UUID.fromString(key.substring(separator + 1)));
    }
}
