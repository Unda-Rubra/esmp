package xdu.undarubra.esmp.civilization.certification;

import java.util.UUID;

public record CertificationApplication(
        UUID id,
        UUID settlementId,
        UUID applicant,
        String proposedName,
        long appliedTick,
        State state,
        UUID decidedBy,
        Long decidedTick,
        String decisionNote) {
    public enum State {
        PENDING,
        APPROVED,
        DENIED
    }
}
