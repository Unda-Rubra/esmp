package xdu.undarubra.esmp.civilization.building;

import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;
import xdu.undarubra.esmp.civilization.settlement.ParcelAction;
import xdu.undarubra.esmp.civilization.settlement.SettlementChange;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class BuildingService {
    public record Policy(int maxVolume) {
        public Policy {
            if (maxVolume < 1 || maxVolume > BuildingBounds.HARD_MAX_VOLUME)
                throw new IllegalArgumentException("Building max-volume must be 1..32768");
        }

        public static Policy defaults() {
            return new Policy(4096);
        }
    }

    public record Stamp(UUID world, long ownership, long claims, long security) {
        public Stamp {
            Objects.requireNonNull(world);
        }
    }

    public enum Ownership {
        WILD,
        CLAIMED
    }

    public record Capture(UUID id, long revision, BuildingBaseline baseline) {
        public Capture {
            Objects.requireNonNull(id);
            Objects.requireNonNull(baseline);
            if (revision < 1) throw new IllegalArgumentException("Building revision required");
        }
    }

    public record View(
            UUID id,
            UUID settlement,
            UUID historySettlement,
            UUID world,
            String name,
            BuildingBounds bounds,
            long ownershipRevision,
            long revision,
            boolean retired,
            Position station) {
        public Ownership ownership() {
            return settlement == null ? Ownership.WILD : Ownership.CLAIMED;
        }

        /** Publication/history routing only; the historical Settlement grants no wild rights. */
        public UUID publicationSettlement() {
            return settlement == null ? historySettlement : settlement;
        }

        public boolean permitsAutomaticMutation(Position position, BuildingCell cell) {
            return !retired
                    && bounds.contains(position)
                    && !position.equals(station)
                    && cell.automaticallyMutable();
        }
    }

    public record Detail(
            View building, BuildingBaseline baseline, BuildingBaseline.Geometry geometry) {}

    public record Revision(long revision, UUID actor, long tick, String kind) {}

    public interface Repository {
        CompletableFuture<Boolean> recorded(UUID operation, UUID actor, UUID building);

        CompletableFuture<List<View>> active(UUID settlement);

        CompletableFuture<List<View>> publication(UUID settlement);

        CompletableFuture<Detail> inspect(UUID id, long revision);

        CompletableFuture<List<Revision>> history(UUID id);

        CompletableFuture<SettlementChange> register(
                UUID actor,
                UUID id,
                UUID settlement,
                UUID world,
                String name,
                BuildingBaseline scan,
                Stamp stamp);

        CompletableFuture<SettlementChange> resize(
                UUID actor, UUID id, long revision, BuildingBaseline scan, Stamp stamp);

        CompletableFuture<SettlementChange> retire(UUID actor, UUID id, long revision);

        CompletableFuture<SettlementChange> station(
                UUID actor, UUID id, long revision, Position point);

        CompletableFuture<SettlementChange> edit(
                UUID operation,
                UUID actor,
                UUID id,
                Stamp stamp,
                ParcelAction action,
                Map<Position, BuildingCell> changes);
    }

    private final Repository repository;

    public CompletableFuture<Boolean> recorded(UUID operation, UUID actor, UUID building) {
        return repository.recorded(operation, actor, building);
    }

    public BuildingService(Repository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public CompletableFuture<List<View>> active(UUID settlement) {
        return repository.active(settlement);
    }

    /** Active current/history associations needed to refresh a committed spatial snapshot. */
    public CompletableFuture<List<View>> publication(UUID settlement) {
        return repository.publication(Objects.requireNonNull(settlement));
    }

    public CompletableFuture<Detail> inspect(UUID id, long revision) {
        return repository.inspect(id, revision);
    }

    public CompletableFuture<List<Revision>> history(UUID id) {
        return repository.history(id);
    }

    public CompletableFuture<SettlementChange> register(
            UUID actor,
            UUID id,
            UUID settlement,
            UUID world,
            String name,
            BuildingBaseline scan,
            Stamp stamp) {
        return repository.register(actor, id, settlement, world, name, scan, stamp);
    }

    public CompletableFuture<SettlementChange> resize(
            UUID actor, UUID id, long revision, BuildingBaseline scan, Stamp stamp) {
        return repository.resize(actor, id, revision, scan, stamp);
    }

    public CompletableFuture<SettlementChange> retire(UUID actor, UUID id, long revision) {
        return repository.retire(actor, id, revision);
    }

    public CompletableFuture<SettlementChange> station(
            UUID actor, UUID id, long revision, Position point) {
        return repository.station(actor, id, revision, point);
    }

    public CompletableFuture<SettlementChange> edit(
            UUID operation,
            UUID actor,
            UUID id,
            Stamp stamp,
            ParcelAction action,
            Map<Position, BuildingCell> changes) {
        if (changes.isEmpty() || changes.size() > BuildingBounds.HARD_MAX_VOLUME)
            throw new IllegalArgumentException("Invalid Building edit size");
        return repository.edit(operation, actor, id, stamp, action, Map.copyOf(changes));
    }
}
