package xdu.undarubra.esmp.civilization.certification;

import xdu.undarubra.esmp.civilization.settlement.SettlementChange;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CertificationService {
    public interface Repository {
        CompletableFuture<CertificationView> view(UUID id);

        CompletableFuture<List<CertificationApplication>> applications(UUID id);

        CompletableFuture<SettlementChange> apply(UUID actor, UUID id, String proposedName);

        CompletableFuture<SettlementChange> decide(
                UUID actor, UUID id, UUID applicationId, boolean approve, String note);

        CompletableFuture<List<VerifiedSettlement>> verifiedSettlements();
    }

    private final Repository repository;

    public CertificationService(Repository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public CompletableFuture<CertificationView> view(UUID id) {
        return repository.view(id);
    }

    public CompletableFuture<List<CertificationApplication>> applications(UUID id) {
        return repository.applications(id);
    }

    public CompletableFuture<SettlementChange> apply(UUID actor, UUID id, String proposedName) {
        return repository.apply(actor, id, proposedName);
    }

    public CompletableFuture<SettlementChange> decide(
            UUID actor, UUID id, UUID applicationId, boolean approve, String note) {
        return repository.decide(actor, id, applicationId, approve, note);
    }

    public CompletableFuture<List<VerifiedSettlement>> verifiedSettlements() {
        return repository.verifiedSettlements();
    }
}
