package xdu.undarubra.esmp.civilization.cycle;

import xdu.undarubra.esmp.civilization.settlement.SettlementChange;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class CycleService {
    public record Policy(long graceTicks, long checkTicks) {
        public Policy {
            if (graceTicks < 1 || checkTicks < 1)
                throw new IllegalArgumentException("Positive cycle timing required");
        }

        public static Policy defaults() {
            return new Policy(168000, 1200);
        }
    }

    public record Notice(
            UUID settlement, long dueTick, int chunks, String state, String locations) {}

    public interface Repository {
        CompletableFuture<List<UUID>> due();

        CompletableFuture<Notice> status(UUID settlement);

        CompletableFuture<SettlementChange> advance(UUID settlement);

        CompletableFuture<List<Notice>> notices(UUID actor);
    }

    private final Repository repository;

    public CycleService(Repository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public CompletableFuture<List<UUID>> due() {
        return repository.due();
    }

    public CompletableFuture<Notice> status(UUID settlement) {
        return repository.status(settlement);
    }

    public CompletableFuture<SettlementChange> advance(UUID settlement) {
        return repository.advance(settlement);
    }

    public CompletableFuture<List<Notice>> notices(UUID actor) {
        return repository.notices(actor);
    }
}
