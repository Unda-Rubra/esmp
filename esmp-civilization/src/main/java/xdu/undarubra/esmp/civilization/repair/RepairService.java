package xdu.undarubra.esmp.civilization.repair;

import xdu.undarubra.esmp.civilization.building.*;
import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;
import xdu.undarubra.esmp.civilization.decay.DecayService.Change;
import xdu.undarubra.esmp.civilization.settlement.SettlementChange;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class RepairService {
    public record Policy(long batchTicks) {
        public Policy {
            if (batchTicks < 1)
                throw new IllegalArgumentException("Repair batch ticks must be positive");
        }

        public static Policy defaults() {
            return new Policy(200);
        }
    }

    public enum Direction {
        SUPPLY,
        REFUND
    }

    public record Item(String material, int count) {
        public Item {
            Objects.requireNonNull(material);
            if (count < 0 || count > 64 || (count == 0) != material.isEmpty())
                throw new IllegalArgumentException("Invalid plain material stack");
        }

        public static Item empty() {
            return new Item("", 0);
        }
    }

    public record Slot(int index, Item before, Item after) {
        public Slot {
            if (index < 0 || index >= 36)
                throw new IllegalArgumentException("Invalid storage slot");
            Objects.requireNonNull(before);
            Objects.requireNonNull(after);
        }
    }

    public record Stock(String material, long needed, long escrow, long consumed, long refunded) {}

    public record Job(
            UUID id,
            UUID building,
            UUID settlement,
            UUID world,
            UUID payer,
            String state,
            long revision,
            Position station,
            UUID cycle,
            long nextTick,
            int remaining,
            long era,
            List<Stock> stock) {
        public Job {
            stock = List.copyOf(stock);
        }
    }

    public record Quote(
            UUID building, Map<String, Long> materials, int cells, String condition, long era) {
        public Quote {
            materials = Map.copyOf(materials);
        }
    }

    public record Transfer(
            long sequence,
            UUID id,
            UUID job,
            UUID payer,
            Direction direction,
            String state,
            List<Slot> slots) {
        public Transfer {
            slots = List.copyOf(slots);
        }
    }

    public record Offer(
            Job job,
            BuildingService.View building,
            BuildingService.Stamp stamp,
            UUID owner,
            List<Change> cells) {
        public Offer {
            cells = List.copyOf(cells);
        }
    }

    public record Operation(
            long sequence,
            UUID id,
            UUID job,
            UUID building,
            UUID settlement,
            UUID world,
            UUID actor,
            long revision,
            UUID owner,
            long ownership,
            long claims,
            long security,
            String state,
            int chunkX,
            int chunkZ,
            List<Change> cells) {
        public Operation {
            cells = List.copyOf(cells);
        }
    }

    public record Work(Job job, Offer offer, Operation pending, SettlementChange current) {}

    public record Permit(Operation operation, boolean valid, String reason, Set<Position> cells) {
        public Permit {
            cells = Set.copyOf(cells);
        }
    }

    public interface Repository {
        CompletableFuture<Void> initialize();

        CompletableFuture<Set<UUID>> reservations();

        CompletableFuture<Quote> inspect(UUID building);

        CompletableFuture<Job> job(UUID job);

        CompletableFuture<List<Job>> jobs(UUID building);

        CompletableFuture<SettlementChange> prepare(UUID job, UUID actor, UUID building);

        CompletableFuture<Transfer> transfer(
                UUID id, UUID actor, UUID job, Direction direction, List<Slot> slots);

        CompletableFuture<SettlementChange> confirm(UUID transfer);

        CompletableFuture<SettlementChange> abort(UUID transfer);

        CompletableFuture<List<Transfer>> transfers(UUID player, long after);

        CompletableFuture<SettlementChange> start(UUID actor, UUID job);

        CompletableFuture<SettlementChange> cancel(UUID actor, UUID job);

        CompletableFuture<List<Job>> due();

        CompletableFuture<Work> work(UUID job);

        CompletableFuture<Operation> intent(Offer offer, List<Change> cells);

        CompletableFuture<Permit> authorize(UUID operation);

        CompletableFuture<List<Operation>> chunk(UUID world, int x, int z, long after);

        CompletableFuture<SettlementChange> finish(
                UUID operation,
                Map<Position, BuildingCell> actual,
                boolean attempted,
                Set<Position> conflicts);

        CompletableFuture<SettlementChange> defer(UUID job);
    }

    private final Repository repository;

    public RepairService(Repository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public CompletableFuture<Void> initialize() {
        return repository.initialize();
    }

    public CompletableFuture<Set<UUID>> reservations() {
        return repository.reservations();
    }

    public CompletableFuture<Quote> inspect(UUID id) {
        return repository.inspect(id);
    }

    public CompletableFuture<Job> job(UUID id) {
        return repository.job(id);
    }

    public CompletableFuture<List<Job>> jobs(UUID building) {
        return repository.jobs(building);
    }

    public CompletableFuture<SettlementChange> prepare(UUID id, UUID actor, UUID building) {
        return repository.prepare(id, actor, building);
    }

    public CompletableFuture<Transfer> transfer(
            UUID id, UUID actor, UUID job, Direction direction, List<Slot> slots) {
        if (slots.isEmpty() || slots.size() > 36)
            throw new IllegalArgumentException("Transfer requires 1..36 storage slots");
        return repository.transfer(id, actor, job, direction, List.copyOf(slots));
    }

    public CompletableFuture<SettlementChange> confirm(UUID id) {
        return repository.confirm(id);
    }

    public CompletableFuture<SettlementChange> abort(UUID id) {
        return repository.abort(id);
    }

    public CompletableFuture<List<Transfer>> transfers(UUID player, long after) {
        return repository.transfers(player, after);
    }

    public CompletableFuture<SettlementChange> start(UUID player, UUID job) {
        return repository.start(player, job);
    }

    public CompletableFuture<SettlementChange> cancel(UUID player, UUID job) {
        return repository.cancel(player, job);
    }

    public CompletableFuture<List<Job>> due() {
        return repository.due();
    }

    public CompletableFuture<Work> work(UUID job) {
        return repository.work(job);
    }

    public CompletableFuture<Operation> intent(Offer offer, List<Change> cells) {
        if (cells.isEmpty() || cells.size() > 8)
            throw new IllegalArgumentException("Repair batch requires 1..8 cells");
        return repository.intent(offer, List.copyOf(cells));
    }

    public CompletableFuture<Permit> authorize(UUID op) {
        return repository.authorize(op);
    }

    public CompletableFuture<List<Operation>> chunk(UUID world, int x, int z, long after) {
        return repository.chunk(world, x, z, after);
    }

    public CompletableFuture<SettlementChange> finish(
            UUID id,
            Map<Position, BuildingCell> actual,
            boolean attempted,
            Set<Position> conflicts) {
        return repository.finish(id, Map.copyOf(actual), attempted, Set.copyOf(conflicts));
    }

    public CompletableFuture<SettlementChange> defer(UUID job) {
        return repository.defer(job);
    }
}
