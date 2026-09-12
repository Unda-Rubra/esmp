package xdu.undarubra.esmp.civilization.settlement;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Public Parcel operations, independent of the database implementation. */
public final class ParcelService {
    public record Request(
            UUID id,
            UUID settlementId,
            UUID parcelId,
            String operation,
            UUID issuer,
            long expiresTick,
            Set<UUID> requiredHolders,
            Set<UUID> approvals) {}

    public interface Repository {
        CompletableFuture<LandSnapshot> snapshot();

        CompletableFuture<LandSnapshot.Land> land(UUID id);

        CompletableFuture<SettlementChange> add(
                UUID actor,
                UUID settlement,
                String name,
                Set<ParcelCell> selection,
                int minHeight,
                int maxHeight);

        CompletableFuture<SettlementChange> removeCell(
                UUID actor, UUID settlement, UUID parcel, ParcelCell cell);

        CompletableFuture<SettlementChange> delete(UUID actor, UUID settlement, UUID parcel);

        CompletableFuture<SettlementChange> holders(
                UUID actor, UUID settlement, UUID parcel, Set<UUID> holders);

        CompletableFuture<SettlementChange> policy(
                UUID actor,
                UUID settlement,
                UUID parcel,
                ParcelAction action,
                Set<ParcelRelation> relations);

        CompletableFuture<SettlementChange> rename(
                UUID actor, UUID settlement, UUID parcel, String name);

        CompletableFuture<List<Request>> requests(UUID settlement);

        CompletableFuture<SettlementChange> resolve(
                UUID actor, UUID settlement, UUID id, boolean approve);
    }

    private final Repository repository;

    public ParcelService(Repository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public CompletableFuture<LandSnapshot> snapshot() {
        return repository.snapshot();
    }

    public CompletableFuture<LandSnapshot.Land> land(UUID id) {
        return repository.land(id);
    }

    public CompletableFuture<SettlementChange> add(
            UUID actor,
            UUID settlement,
            String name,
            Set<ParcelCell> selection,
            int minHeight,
            int maxHeight) {
        return repository.add(actor, settlement, name, selection, minHeight, maxHeight);
    }

    public CompletableFuture<SettlementChange> removeCell(
            UUID actor, UUID settlement, UUID parcel, ParcelCell cell) {
        return repository.removeCell(actor, settlement, parcel, cell);
    }

    public CompletableFuture<SettlementChange> delete(UUID actor, UUID settlement, UUID parcel) {
        return repository.delete(actor, settlement, parcel);
    }

    public CompletableFuture<SettlementChange> holders(
            UUID actor, UUID settlement, UUID parcel, Set<UUID> holders) {
        return repository.holders(actor, settlement, parcel, holders);
    }

    public CompletableFuture<SettlementChange> policy(
            UUID actor,
            UUID settlement,
            UUID parcel,
            ParcelAction action,
            Set<ParcelRelation> relations) {
        return repository.policy(actor, settlement, parcel, action, relations);
    }

    public CompletableFuture<SettlementChange> rename(
            UUID actor, UUID settlement, UUID parcel, String name) {
        return repository.rename(actor, settlement, parcel, name);
    }

    public CompletableFuture<List<Request>> requests(UUID settlement) {
        return repository.requests(settlement);
    }

    public CompletableFuture<SettlementChange> resolve(
            UUID actor, UUID settlement, UUID id, boolean approve) {
        return repository.resolve(actor, settlement, id, approve);
    }
}
