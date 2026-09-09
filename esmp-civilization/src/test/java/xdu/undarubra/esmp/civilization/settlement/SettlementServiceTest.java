package xdu.undarubra.esmp.civilization.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.world.WorldBinding;
import xdu.undarubra.esmp.core.world.WorldDefinition;
import xdu.undarubra.esmp.core.world.WorldEnvironment;

@Timeout(20)
class SettlementServiceTest {
    @TempDir Path directory;
    private WorldStore database;
    private SettlementService service;
    private final MutableClock clock = new MutableClock();
    private UUID world;
    private UUID resourceWorld;
    private final UUID owner = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void open() {
        database = new WorldStore(directory.resolve("esmp.db"));
        var snapshot = database.initialize(List.of(
                new WorldDefinition("MAIN", "civilization", WorldEnvironment.NORMAL),
                new WorldDefinition("RESOURCE_OVERWORLD", "resource", WorldEnvironment.NORMAL)), List.of(
                new WorldBinding("MAIN", UUID.randomUUID(), "world"),
                new WorldBinding("RESOURCE_OVERWORLD", UUID.randomUUID(), "resource"))).join();
        world = snapshot.instances().stream().filter(i -> i.definitionId().equals("MAIN")).findFirst().orElseThrow().instanceId();
        resourceWorld = snapshot.instances().stream().filter(i -> i.definitionId().equals("RESOURCE_OVERWORLD")).findFirst().orElseThrow().instanceId();
        service = new SettlementService(database, new SettlementPolicy(true, 3, Duration.ofHours(24),
                Duration.ofMinutes(30), Duration.ofHours(24), Duration.ofSeconds(60)), clock);
        service.initialize().join();
    }

    @AfterEach
    void close() { database.close(); }

    private UUID create(String name, int x, int z) {
        return service.create(owner, world, new ChunkPosition(x, z), name).join().settlementId();
    }

    private void join(UUID id, UUID player) {
        var offer = service.offer(owner, id, player, SettlementOffer.Type.INVITE).join().offer();
        service.accept(player, offer.id(), false).join();
    }

    private SettlementView current(UUID id) {
        return service.initialize().join().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    private static void rejected(Runnable action) {
        CompletionException failure = assertThrows(CompletionException.class, action::run);
        assertTrue(failure.getCause() instanceof SettlementException, failure.toString());
    }

    @Test
    void claimingPreservesEdgesCapAndLastChunkWithoutFailedEvents() {
        UUID id = create("Oakfall", 0, 0);
        rejected(() -> service.claim(owner, id, world, new ChunkPosition(1, 1)).join());
        service.claim(owner, id, world, new ChunkPosition(1, 0)).join();
        service.claim(owner, id, world, new ChunkPosition(2, 0)).join();
        rejected(() -> service.claim(owner, id, world, new ChunkPosition(3, 0)).join());
        rejected(() -> service.unclaim(owner, id, new ChunkPosition(1, 0)).join());
        assertEquals(3, current(id).chunks().size());
        service.unclaim(owner, id, new ChunkPosition(2, 0)).join();
        service.unclaim(owner, id, new ChunkPosition(1, 0)).join();
        rejected(() -> service.unclaim(owner, id, new ChunkPosition(0, 0)).join());
        assertEquals(5, service.history(id).join().size());
        assertEquals(1, current(id).chunks().size());
    }

    @Test
    void adjacentSettlementsRemainSeparateAndResourceWorldCannotBeClaimed() {
        UUID first = create("North", 0, 0);
        UUID second = create("South", 0, 1);
        assertNotEquals(first, second);
        rejected(() -> service.create(other, world, new ChunkPosition(0, 0), "Collision").join());
        rejected(() -> service.create(owner, resourceWorld, new ChunkPosition(0, 0), "Resource").join());
        join(first, member);
        assertEquals(Role.MEMBER, current(first).role(member));
        assertEquals(Role.VISITOR, current(second).role(member));
        assertEquals(2, service.initialize().join().size());
    }

    @Test
    void consentAndOwnershipTransferPreserveRoleBoundaries() {
        UUID id = create("Town", 0, 0);
        var invitation = service.offer(owner, id, member, SettlementOffer.Type.INVITE).join().offer();
        assertEquals(Role.VISITOR, current(id).role(member));
        rejected(() -> service.accept(other, invitation.id(), false).join());
        service.accept(member, invitation.id(), false).join();
        rejected(() -> service.claim(member, id, world, new ChunkPosition(1, 0)).join());
        var promotion = service.offer(owner, id, member, SettlementOffer.Type.PROMOTE).join().offer();
        assertEquals(Role.MEMBER, current(id).role(member));
        service.accept(member, promotion.id(), false).join();
        service.claim(member, id, world, new ChunkPosition(1, 0)).join();
        rejected(() -> service.offer(member, id, member, SettlementOffer.Type.ABANDON).join());
        rejected(() -> service.offer(member, id, other, SettlementOffer.Type.PROMOTE).join());
        join(id, other);
        var transfer = service.offer(owner, id, other, SettlementOffer.Type.TRANSFER).join().offer();
        service.accept(other, transfer.id(), false).join();
        SettlementView changed = current(id);
        assertEquals(other, changed.owner());
        assertEquals(Role.MEMBER, changed.role(owner));
        assertEquals(Role.MANAGER, changed.role(member));
        assertFalse(changed.members().containsKey(other));
        rejected(() -> service.demote(member, id, member).join());
        service.demote(other, id, member).join();
        assertEquals(Role.MEMBER, current(id).role(member));
    }

    @Test
    void revokedManagerCannotGrantMembershipThroughAnOldOffer() {
        UUID id = create("Town", 0, 0);
        join(id, member);
        var promotion = service.offer(owner, id, member, SettlementOffer.Type.PROMOTE).join().offer();
        service.accept(member, promotion.id(), false).join();
        var invitation = service.offer(member, id, other, SettlementOffer.Type.INVITE).join().offer();
        service.demote(owner, id, member).join();
        rejected(() -> service.accept(other, invitation.id(), false).join());
        service.remove(member, id, member, true).join();
        assertEquals(Role.VISITOR, current(id).role(member));
        rejected(() -> service.remove(owner, id, owner, true).join());
    }

    @Test
    void expiryCancellationAndDeclineCannotBeReplayed() {
        UUID id = create("Town", 0, 0);
        var expired = service.offer(owner, id, member, SettlementOffer.Type.INVITE).join().offer();
        clock.now = expired.expiresAt();
        rejected(() -> service.accept(member, expired.id(), false).join());
        assertTrue(service.offers(member).join().isEmpty());
        var invitation = service.offer(owner, id, member, SettlementOffer.Type.INVITE).join().offer();
        service.resolveOffer(member, invitation.id(), true).join();
        rejected(() -> service.accept(member, invitation.id(), false).join());
        join(id, member);
        var promotion = service.offer(owner, id, member, SettlementOffer.Type.PROMOTE).join().offer();
        service.accept(member, promotion.id(), false).join();
        var outgoing = service.offer(owner, id, other, SettlementOffer.Type.INVITE).join().offer();
        service.resolveOffer(member, outgoing.id(), false).join();
        rejected(() -> service.accept(other, outgoing.id(), false).join());
        var transfer = service.offer(owner, id, member, SettlementOffer.Type.TRANSFER).join().offer();
        rejected(() -> service.resolveOffer(member, transfer.id(), false).join());
        service.resolveOffer(member, transfer.id(), true).join();
        assertEquals(owner, current(id).owner());
    }

    @Test
    void abandonmentRequiresFreshRevisionAndPreservesHistoryAfterReclaim() {
        UUID id = create("Town", 0, 0);
        var warning = service.offer(owner, id, owner, SettlementOffer.Type.ABANDON).join().offer();
        service.claim(owner, id, world, new ChunkPosition(1, 0)).join();
        service.unclaim(owner, id, new ChunkPosition(1, 0)).join();
        rejected(() -> service.accept(owner, warning.id(), true).join());
        var expired = service.offer(owner, id, owner, SettlementOffer.Type.ABANDON).join().offer();
        clock.now = expired.expiresAt();
        rejected(() -> service.accept(owner, expired.id(), true).join());
        var valid = service.offer(owner, id, owner, SettlementOffer.Type.ABANDON).join().offer();
        rejected(() -> service.accept(owner, valid.id(), false).join());
        service.accept(owner, valid.id(), true).join();
        assertTrue(service.initialize().join().isEmpty());
        UUID reclaimed = service.create(other, world, new ChunkPosition(0, 0), "Town").join().settlementId();
        assertNotEquals(id, reclaimed);
        assertEquals(Role.VISITOR, current(reclaimed).role(owner));
        assertTrue(service.history(id).join().stream().anyMatch(e -> e.type().equals("RELEASED")));
    }

    @Test
    void outboxAndClaimsSurviveRestartWithoutDuplicateAcknowledgements() {
        UUID id = create("Town", -2, -3);
        join(id, member);
        var pending = service.pendingEvents().join();
        assertEquals(3, pending.size());
        service.acknowledgeEvents(pending.stream().map(SettlementEvent::id).toList()).join();
        service.acknowledgeEvents(pending.stream().map(SettlementEvent::id).toList()).join();
        assertTrue(service.pendingEvents().join().isEmpty());
        database.close();
        database = new WorldStore(directory.resolve("esmp.db"));
        database.initialize(List.of(new WorldDefinition("MAIN", "civilization", WorldEnvironment.NORMAL)), List.of()).join();
        service = new SettlementService(database, SettlementPolicy.defaults(), clock);
        SettlementView persisted = service.initialize().join().getFirst();
        assertEquals(id, persisted.id());
        assertTrue(persisted.chunks().contains(new ChunkPosition(-2, -3)));
        assertEquals(Role.MEMBER, persisted.role(member));
        assertEquals(3, service.history(id).join().size());
        assertTrue(service.pendingEvents().join().isEmpty());
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-09-09T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
