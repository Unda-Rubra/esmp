package xdu.undarubra.esmp.civilization.settlement;

/** A rejected gameplay operation, not a storage failure. */
public final class SettlementException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public SettlementException(String message) { super(message); }
}
