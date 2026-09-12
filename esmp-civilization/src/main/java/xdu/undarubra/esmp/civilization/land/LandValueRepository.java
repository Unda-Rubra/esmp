package xdu.undarubra.esmp.civilization.land;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persistence port for versioned policy identity and immutable natural land profiles. */
public interface LandValueRepository {
    LandValuePolicy policy();

    CompletableFuture<Void> initialize();

    CompletableFuture<LandValueProfile> find(UUID world, int x, int z);

    /** Captures an immutable, bounded batch, rejecting conflicting existing profiles. */
    CompletableFuture<Void> captureImmutableProfiles(List<LandValueProfile> profiles);
}
