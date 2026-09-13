package xdu.undarubra.esmp.civilization.decay;

import xdu.undarubra.esmp.civilization.building.*;
import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;
import xdu.undarubra.esmp.civilization.settlement.SettlementChange;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class DecayService {
    public static final UUID SYSTEM_ACTOR = new UUID(0, 0);

    public enum Kind {
        WEATHER,
        DROP,
        CAVE_IN
    }

    public enum Outcome {
        APPLIED,
        CONFLICT,
        CANCELLED
    }

    public enum Condition {
        INTACT,
        DAMAGED,
        RUINED
    }

    public record Policy(
            long graceTicks,
            long intervalTicks,
            long checkTicks,
            double inactiveThreshold,
            double recoveryThreshold,
            double ruinedFraction) {
        public Policy {
            if (graceTicks < 0
                    || intervalTicks < 1
                    || checkTicks < 1
                    || !Double.isFinite(inactiveThreshold)
                    || inactiveThreshold <= 0
                    || !Double.isFinite(recoveryThreshold)
                    || recoveryThreshold <= inactiveThreshold
                    || !Double.isFinite(ruinedFraction)
                    || ruinedFraction <= 0
                    || ruinedFraction > 1)
                throw new IllegalArgumentException("Invalid Decay timing or thresholds");
        }

        public static Policy defaults() {
            return new Policy(72000, 24000, 1200, 20, 30, .5);
        }
    }

    public record State(
            UUID building,
            UUID settlement,
            UUID world,
            double support,
            Long inactiveSince,
            long nextCheck,
            long activityRevision,
            Condition condition,
            int damagedCells,
            int lostCells,
            boolean eligible) {}

    public record Change(Position position, String expected, String target) {
        public Change {
            Objects.requireNonNull(position);
            Objects.requireNonNull(expected);
            Objects.requireNonNull(target);
            if (expected.length() > 4096 || target.length() > 4096)
                throw new IllegalArgumentException("Oversized block data");
        }
    }

    public record Operation(
            long sequence,
            UUID id,
            UUID building,
            UUID settlement,
            UUID world,
            long baselineRevision,
            UUID owner,
            long ownershipRevision,
            long claimRevision,
            long securityRevision,
            long activityRevision,
            String policy,
            Kind kind,
            String state,
            int chunkX,
            int chunkZ,
            List<Change> changes) {
        public Operation {
            changes = List.copyOf(changes);
        }
    }

    public record Offer(
            BuildingService.View building,
            BuildingBaseline baseline,
            BuildingService.Stamp stamp,
            UUID owner,
            long activityRevision,
            long cursor,
            Kind kind,
            List<Position> candidates,
            Map<Position, String> expected) {
        public Offer {
            candidates = List.copyOf(candidates);
            expected = Map.copyOf(expected);
        }
    }

    public record Work(State state, Offer offer, Operation pending) {}

    public record Permit(Operation operation, boolean valid, String reason) {}

    public interface Repository {
        CompletableFuture<Void> initialize();

        CompletableFuture<List<UUID>> due();

        CompletableFuture<State> status(UUID building);

        CompletableFuture<Work> work(UUID building);

        CompletableFuture<Operation> prepare(Offer offer, List<Change> changes);

        CompletableFuture<Permit> authorize(UUID operation);

        CompletableFuture<List<Operation>> chunk(UUID world, int x, int z, long after);

        CompletableFuture<SettlementChange> finish(
                UUID operation, Outcome outcome, Map<Position, BuildingCell> actual);

        CompletableFuture<SettlementChange> defer(UUID building, boolean rotate);
    }

    private final Repository repository;

    public DecayService(Repository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public CompletableFuture<Void> initialize() {
        return repository.initialize();
    }

    public CompletableFuture<List<UUID>> due() {
        return repository.due();
    }

    public CompletableFuture<State> status(UUID id) {
        return repository.status(id);
    }

    public CompletableFuture<Work> work(UUID id) {
        return repository.work(id);
    }

    public CompletableFuture<Operation> prepare(Offer offer, List<Change> changes) {
        if (changes.isEmpty() || changes.size() > 8)
            throw new IllegalArgumentException("Decay batch must contain 1..8 cells");
        return repository.prepare(offer, List.copyOf(changes));
    }

    public CompletableFuture<Permit> authorize(UUID id) {
        return repository.authorize(id);
    }

    public CompletableFuture<List<Operation>> chunk(UUID world, int x, int z, long after) {
        return repository.chunk(world, x, z, after);
    }

    public CompletableFuture<SettlementChange> finish(
            UUID id, Outcome outcome, Map<Position, BuildingCell> actual) {
        return repository.finish(id, outcome, Map.copyOf(actual));
    }

    public CompletableFuture<SettlementChange> defer(UUID id, boolean rotate) {
        return repository.defer(id, rotate);
    }
}
