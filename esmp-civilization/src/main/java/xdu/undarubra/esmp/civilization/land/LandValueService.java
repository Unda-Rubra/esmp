package xdu.undarubra.esmp.civilization.land;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Civilization-owned natural land data; no Paper objects or current-terrain backfill. */
public final class LandValueService {
    private final LandValueRepository repository;

    public LandValueService(LandValueRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public LandValuePolicy policy() {
        return repository.policy();
    }

    public CompletableFuture<Void> initialize() {
        return repository.initialize();
    }

    public CompletableFuture<LandValueProfile> find(UUID world, int x, int z) {
        return repository.find(world, x, z);
    }

    public CompletableFuture<Void> capture(List<LandValueProfile> profiles) {
        if (profiles.size() > 32) {
            throw new IllegalArgumentException("Capture at most 32 land profiles per batch");
        }
        return repository.captureImmutableProfiles(List.copyOf(profiles));
    }
}
