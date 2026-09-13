package xdu.undarubra.esmp.civilization.acquisition;

import java.math.BigInteger;
import java.util.UUID;

public record Acquisition(
        UUID id,
        UUID settlementId,
        UUID owner,
        UUID recipient,
        State state,
        long fee,
        BigInteger baseTotal,
        String context,
        long proposedTick,
        long expiresTick,
        Long acceptedTick,
        UUID decidedBy,
        Long decidedTick,
        String decisionNote) {
    public enum State {
        PROPOSED,
        ACCEPTED,
        APPROVED,
        DENIED,
        CANCELLED
    }
}
