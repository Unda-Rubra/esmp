package xdu.undarubra.esmp.civilization.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.civilization.land.LandValuePolicy;
import xdu.undarubra.esmp.civilization.land.LandValueProfile;
import xdu.undarubra.esmp.civilization.land.LandValueService;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;
import xdu.undarubra.esmp.core.world.WorldBinding;
import xdu.undarubra.esmp.core.world.WorldDefinition;
import xdu.undarubra.esmp.core.world.WorldEnvironment;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Timeout(20)
class SettlementServiceTest {
    @TempDir Path directory;
    private static final LandValuePolicy LAND_POLICY = LandValuePolicy.defaults();
    private static final LandValueProfile.Terrain PROFILE_TERRAIN =
            LandValueProfile.Terrain.values()[0];
    private WorldStore database;
    private SettlementService service;
    private LandValueService landValues;
    private GameClock clock;
    private final UUID clockSource = UUID.randomUUID();
    private UUID world;
    private UUID resourceWorld;
    private final UUID owner = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID reviewer = UUID.randomUUID();

    private xdu.undarubra.esmp.civilization.acquisition.AcquisitionService acquisitions() {
        // This fixture isolates governance; dedicated Economy tests verify actual fee debits.
        return xdu.undarubra.esmp.civilization.api.SettlementServices.acquisition(
                database,
                clock,
                xdu.undarubra.esmp.civilization.acquisition.AcquisitionPolicy.defaults(),
                reviewer::equals,
                (tx, acquisition) -> {});
    }

    @BeforeEach
    void open() {
        database = new WorldStore(directory.resolve("esmp.db"));
        var snapshot =
                database.initialize(
                                List.of(
                                        new WorldDefinition(
                                                "MAIN", "civilization", WorldEnvironment.NORMAL),
                                        new WorldDefinition(
                                                "RESOURCE_OVERWORLD",
                                                "resource",
                                                WorldEnvironment.NORMAL)),
                                List.of(
                                        new WorldBinding("MAIN", UUID.randomUUID(), "world"),
                                        new WorldBinding(
                                                "RESOURCE_OVERWORLD",
                                                UUID.randomUUID(),
                                                "resource")))
                        .join();
        world =
                snapshot.instances().stream()
                        .filter(i -> i.definitionId().equals("MAIN"))
                        .findFirst()
                        .orElseThrow()
                        .instanceId();
        resourceWorld =
                snapshot.instances().stream()
                        .filter(i -> i.definitionId().equals("RESOURCE_OVERWORLD"))
                        .findFirst()
                        .orElseThrow()
                        .instanceId();
        clock = new GameClock(database);
        clock.initialize(clockSource, 0, true).join();
        landValues =
                xdu.undarubra.esmp.civilization.api.ObservationServices.land(database, LAND_POLICY);
        landValues.initialize().join();
        service =
                xdu.undarubra.esmp.civilization.api.SettlementServices.settlement(
                        database,
                        new SettlementPolicy(true, 3, 1_728_000, 1_728_000, 1_200),
                        clock,
                        ignored -> false,
                        (c, id) -> {},
                        ClaimPricingPolicy.defaults(),
                        // These fixtures test governance, not wallet or treasury accounting.
                        (c, quote) -> {},
                        (c, id) -> true);
        service.initialize().join();
    }

    @AfterEach
    void close() {
        database.close();
    }

    private void restartForLegacyMigration() {
        // Reopen so DatabaseSchema detects the metadata-free legacy fixture. Do not
        // recreate world bindings, settlements, purchases, or pending offers.
        database.close();
        database = new WorldStore(directory.resolve("esmp.db"));
        database.initialize(
                        List.of(
                                new WorldDefinition(
                                        "MAIN", "civilization", WorldEnvironment.NORMAL),
                                new WorldDefinition(
                                        "RESOURCE_OVERWORLD", "resource", WorldEnvironment.NORMAL)),
                        List.of())
                .join();
        clock = new GameClock(database);
        clock.initialize(clockSource, 0, true).join();
        landValues =
                xdu.undarubra.esmp.civilization.api.ObservationServices.land(database, LAND_POLICY);
        landValues.initialize().join();
        service =
                xdu.undarubra.esmp.civilization.api.SettlementServices.settlement(
                        database,
                        new SettlementPolicy(true, 3, 1_728_000, 1_728_000, 1_200),
                        clock,
                        ignored -> false,
                        (c, id) -> {},
                        ClaimPricingPolicy.defaults(),
                        // Same governance-only payment fixture as in open().
                        (c, quote) -> {},
                        (c, id) -> true);
        // Leave settlement initialization to the test, then finishMigration to baseline
        // only Phase0 and apply Phase2 before querying the new land snapshot schema.
    }

    private void captureProfile(int x, int z) {
        // Each location has its own real, immutable profile. Reclaiming or retrying a
        // location captures the identical value through LandValueService idempotently.
        landValues
                .capture(
                        List.of(
                                new LandValueProfile(
                                        world,
                                        x,
                                        z,
                                        LAND_POLICY.version(),
                                        LAND_POLICY.baseValue(),
                                        PROFILE_TERRAIN)))
                .join();
    }

    private UUID create(int x, int z) {
        return create(owner, x, z).settlementId();
    }

    private SettlementChange create(UUID actor, int x, int z) {
        captureProfile(x, z);
        ClaimQuote quote = service.quoteCreate(actor, world, new ChunkPosition(x, z)).join();
        return service.purchase(actor, quote.id(), java.util.List.of()).join();
    }

    private SettlementChange claim(UUID actor, UUID settlement, int x, int z) {
        captureProfile(x, z);
        ClaimQuote quote =
                service.quoteClaim(actor, settlement, world, new ChunkPosition(x, z)).join();
        return service.purchase(actor, quote.id(), java.util.List.of()).join();
    }

    private void join(UUID id, UUID player) {
        var offer = service.offer(owner, id, player, SettlementOffer.Type.INVITE).join().offer();
        service.accept(player, offer.id(), false).join();
    }

    private SettlementView current(UUID id) {
        return service.initialize().join().stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static void rejected(Runnable action) {
        CompletionException failure = assertThrows(CompletionException.class, action::run);
        assertTrue(failure.getCause() instanceof SettlementException, failure.toString());
    }

    @Test
    void claimingPreservesEdgesCapAndLastChunkWithoutFailedEvents() {
        UUID id = create(0, 0);
        rejected(() -> claim(owner, id, 1, 1));
        claim(owner, id, 1, 0);
        claim(owner, id, 2, 0);
        rejected(() -> claim(owner, id, 3, 0));
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
        UUID first = create(0, 0);
        assertEquals(Role.PUBLIC, current(first).role(null));
        UUID second = create(0, 1);
        assertNotEquals(first, second);
        rejected(() -> create(other, 0, 0));
        // Resource worlds cannot have natural claim profiles captured by LandValueService.
        // Exercise the quote API directly rather than bypassing that restriction in a fixture.
        rejected(() -> service.quoteCreate(owner, resourceWorld, new ChunkPosition(0, 0)).join());
        join(first, member);
        assertEquals(Role.CITIZEN, current(first).role(member));
        assertEquals(Role.PUBLIC, current(second).role(member));
        assertEquals(2, service.initialize().join().size());
    }

    @Test
    void consentAndOwnershipTransferPreserveRoleBoundaries() {
        UUID id = create(0, 0);
        var invitation =
                service.offer(owner, id, member, SettlementOffer.Type.INVITE).join().offer();
        assertEquals(Role.PUBLIC, current(id).role(member));
        rejected(() -> service.accept(other, invitation.id(), false).join());
        service.accept(member, invitation.id(), false).join();
        rejected(() -> claim(member, id, 1, 0));
        var promotion =
                service.offer(owner, id, member, SettlementOffer.Type.PROMOTE).join().offer();
        assertEquals(Role.CITIZEN, current(id).role(member));
        service.accept(member, promotion.id(), false).join();
        claim(member, id, 1, 0);
        rejected(() -> service.offer(member, id, member, SettlementOffer.Type.ABANDON).join());
        rejected(() -> service.offer(member, id, other, SettlementOffer.Type.PROMOTE).join());
        join(id, other);
        var acquisitions = acquisitions();
        acquisitions.propose(owner, id, other).join();
        var transfer = acquisitions.history(id).join().getFirst();
        acquisitions.accept(other, transfer.id(), transfer.fee()).join();
        assertEquals(owner, current(id).owner());
        acquisitions.review(reviewer, transfer.id(), true, "approved").join();
        SettlementView changed = current(id);
        assertEquals(other, changed.owner());
        assertEquals(Role.CITIZEN, changed.role(owner));
        assertEquals(Role.GOVERNOR, changed.role(member));
        assertFalse(changed.members().containsKey(other));
        rejected(() -> service.demote(member, id, member).join());
        service.demote(other, id, member).join();
        assertEquals(Role.CITIZEN, current(id).role(member));
    }

    @Test
    void revokedGovernorCannotGrantCitizenshipThroughAnOldOffer() {
        UUID id = create(0, 0);
        join(id, member);
        var promotion =
                service.offer(owner, id, member, SettlementOffer.Type.PROMOTE).join().offer();
        service.accept(member, promotion.id(), false).join();
        var invitation =
                service.offer(member, id, other, SettlementOffer.Type.INVITE).join().offer();
        service.demote(owner, id, member).join();
        rejected(() -> service.accept(other, invitation.id(), false).join());
        service.remove(member, id, member, true).join();
        assertEquals(Role.PUBLIC, current(id).role(member));
        rejected(() -> service.remove(owner, id, owner, true).join());
    }

    @Test
    void expiryCancellationAndDeclineCannotBeReplayed() {
        UUID id = create(0, 0);
        var expired = service.offer(owner, id, member, SettlementOffer.Type.INVITE).join().offer();
        clock.sample(clockSource, expired.expiresAtTick(), true);
        rejected(() -> service.accept(member, expired.id(), false).join());
        assertTrue(service.offers(member).join().isEmpty());
        var invitation =
                service.offer(owner, id, member, SettlementOffer.Type.INVITE).join().offer();
        service.resolveOffer(member, invitation.id(), true).join();
        rejected(() -> service.accept(member, invitation.id(), false).join());
        join(id, member);
        var promotion =
                service.offer(owner, id, member, SettlementOffer.Type.PROMOTE).join().offer();
        service.accept(member, promotion.id(), false).join();
        var outgoing = service.offer(owner, id, other, SettlementOffer.Type.INVITE).join().offer();
        service.resolveOffer(member, outgoing.id(), false).join();
        rejected(() -> service.accept(other, outgoing.id(), false).join());
        var acquisitions = acquisitions();
        acquisitions.propose(owner, id, member).join();
        var transfer = acquisitions.history(id).join().getFirst();
        rejected(() -> acquisitions.cancel(other, transfer.id()).join());
        acquisitions.cancel(member, transfer.id()).join();
        rejected(() -> acquisitions.accept(member, transfer.id(), transfer.fee()).join());
        assertEquals(owner, current(id).owner());
    }

    @Test
    void abandonmentRequiresFreshRevisionAndPreservesHistoryAfterReclaim() {
        UUID id = create(0, 0);
        var warning = service.offer(owner, id, owner, SettlementOffer.Type.ABANDON).join().offer();
        claim(owner, id, 1, 0);
        service.unclaim(owner, id, new ChunkPosition(1, 0)).join();
        rejected(() -> service.accept(owner, warning.id(), true).join());
        var expired = service.offer(owner, id, owner, SettlementOffer.Type.ABANDON).join().offer();
        clock.sample(clockSource, expired.expiresAtTick(), true);
        rejected(() -> service.accept(owner, expired.id(), true).join());
        var valid = service.offer(owner, id, owner, SettlementOffer.Type.ABANDON).join().offer();
        rejected(() -> service.accept(owner, valid.id(), false).join());
        service.accept(owner, valid.id(), true).join();
        assertTrue(service.initialize().join().isEmpty());
        UUID reclaimed = create(other, 0, 0).settlementId();
        assertNotEquals(id, reclaimed);
        assertEquals(Role.PUBLIC, current(reclaimed).role(owner));
        assertTrue(service.history(id).join().stream().anyMatch(e -> e.type().equals("RELEASED")));
    }

    @Test
    void outboxAndClaimsSurviveRestartWithoutDuplicateAcknowledgements() {
        UUID id = create(-2, -3);
        join(id, member);
        var pending = service.pendingEvents().join();
        assertEquals(3, pending.size());
        service.acknowledgeEvents(pending.stream().map(SettlementEvent::id).toList()).join();
        service.acknowledgeEvents(pending.stream().map(SettlementEvent::id).toList()).join();
        assertTrue(service.pendingEvents().join().isEmpty());
        database.close();
        database = new WorldStore(directory.resolve("esmp.db"));
        database.initialize(
                        List.of(
                                new WorldDefinition(
                                        "MAIN", "civilization", WorldEnvironment.NORMAL)),
                        List.of())
                .join();
        clock = new GameClock(database);
        clock.initialize(clockSource, 0, true).join();
        landValues =
                xdu.undarubra.esmp.civilization.api.ObservationServices.land(database, LAND_POLICY);
        landValues.initialize().join();
        service =
                xdu.undarubra.esmp.civilization.api.SettlementServices.settlement(
                        database,
                        SettlementPolicy.defaults(),
                        clock,
                        ignored -> false,
                        (c, settlementId) -> {},
                        ClaimPricingPolicy.defaults(),
                        // Governance-only payment fixture, as in open().
                        (c, quote) -> {},
                        (c, settlementId) -> true);
        SettlementView persisted = service.initialize().join().getFirst();
        assertEquals(id, persisted.id());
        assertTrue(persisted.chunks().contains(new ChunkPosition(-2, -3)));
        assertEquals(Role.CITIZEN, persisted.role(member));
        assertEquals(3, service.history(id).join().size());
        assertTrue(service.pendingEvents().join().isEmpty());
    }

    @Test
    void legacyOfferDeadlinesConvertOnceWithoutResettingIdentityOrLifetime() {
        UUID id = create(0, 0);
        var offer = service.offer(owner, id, member, SettlementOffer.Type.INVITE).join().offer();
        dbLegacyDeadline(offer.id());
        restartForLegacyMigration();
        service.initialize().join();
        database.finishMigration().join();
        var migrated = service.inspectOffer(offer.id()).join();
        assertEquals(offer.id(), migrated.id());
        assertTrue(migrated.expiresAtTick() > clock.ticks());
        assertTrue(migrated.expiresAtTick() <= clock.ticks() + 1200);
        assertEquals(id, current(id).id());
        assertEquals(2, service.history(id).join().size());
        assertTrue(service.history(id).join().stream().allMatch(e -> e.gameTick() == null));
        service.initialize().join();
        assertEquals(
                migrated.expiresAtTick(), service.inspectOffer(offer.id()).join().expiresAtTick());
        clock.sample(clockSource, migrated.expiresAtTick(), true);
        rejected(() -> service.accept(member, offer.id(), false).join());
    }

    @Test
    void versionTwoMigratesRolesAndNumbersWithoutReConvertingTickDeadlines() {
        UUID first = create(0, 0);
        UUID second = create(2, 0);
        join(first, member);
        join(first, other);
        var promotion =
                service.offer(owner, first, member, SettlementOffer.Type.PROMOTE).join().offer();
        service.accept(member, promotion.id(), false).join();
        var invitation =
                service.offer(owner, second, member, SettlementOffer.Type.INVITE).join().offer();
        var before = service.pendingEvents().join().stream().map(SettlementEvent::id).toList();
        // Use the version installed by the canonical migrations, including claim_pricing,
        // rather than pinning the old governance-only schema version.
        long initializedVersion =
                database.transact(
                                c ->
                                        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                                .select(CIVILIZATION_SCHEMA.VERSION)
                                                .from(CIVILIZATION_SCHEMA)
                                                .where(CIVILIZATION_SCHEMA.ID.eq(1L))
                                                .fetchOne(CIVILIZATION_SCHEMA.VERSION))
                        .join();
        database.transact(
                        c -> {
                            governanceLegacyFixture(c);
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .update(CIVILIZATION_SCHEMA)
                                    .set(CIVILIZATION_SCHEMA.VERSION, 2L)
                                    .where(CIVILIZATION_SCHEMA.ID.eq(1L))
                                    .execute();
                            return null;
                        })
                .join();
        restartForLegacyMigration();
        service.initialize().join();
        database.finishMigration().join();
        assertEquals(Role.GOVERNOR, current(first).role(member));
        assertEquals(Role.CITIZEN, current(first).role(other));
        assertEquals(
                invitation.expiresAtTick(),
                service.inspectOffer(invitation.id()).join().expiresAtTick());
        assertEquals(
                before, service.pendingEvents().join().stream().map(SettlementEvent::id).toList());
        database.transact(
                        c -> {
                            assertEquals(
                                    initializedVersion,
                                    xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                            .select(CIVILIZATION_SCHEMA.VERSION)
                                            .from(CIVILIZATION_SCHEMA)
                                            .where(CIVILIZATION_SCHEMA.ID.eq(1L))
                                            .fetchOne(CIVILIZATION_SCHEMA.VERSION)
                                            .longValue());
                            assertEquals(
                                    List.of(first, second),
                                    xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                            .select(SETTLEMENT_DETAILS.SETTLEMENT_ID)
                                            .from(SETTLEMENT_DETAILS)
                                            .orderBy(SETTLEMENT_DETAILS.NUMBER)
                                            .fetch(SETTLEMENT_DETAILS.SETTLEMENT_ID));
                            assertEquals(
                                    List.of(1L, 2L),
                                    xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                            .select(SETTLEMENT_DETAILS.NUMBER)
                                            .from(SETTLEMENT_DETAILS)
                                            .orderBy(SETTLEMENT_DETAILS.NUMBER)
                                            .fetch(SETTLEMENT_DETAILS.NUMBER));
                            assertEquals(
                                    2,
                                    xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                            .fetchCount(
                                                    SETTLEMENT_DETAILS,
                                                    SETTLEMENT_DETAILS.CERTIFICATION_STATUS.eq(
                                                            "VERIFIED")));
                            return null;
                        })
                .join();
        UUID third = create(4, 0);
        assertEquals("S#3", current(third).name());
    }

    @Test
    void historyUsesDescendingRowidForEqualTimesAndOutboxKeepsInsertionOrder() {
        UUID id = create(0, 0);
        join(id, member);
        var inserted = service.pendingEvents().join().stream().map(SettlementEvent::id).toList();
        database.transact(
                        c -> {
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .update(SETTLEMENT_EVENTS)
                                    .set(SETTLEMENT_EVENTS.OCCURRED_AT, 1234L)
                                    .where(SETTLEMENT_EVENTS.SETTLEMENT_ID.eq(id))
                                    .execute();
                            return null;
                        })
                .join();
        assertEquals(
                inserted,
                service.pendingEvents().join().stream().map(SettlementEvent::id).toList());
        assertEquals(
                inserted.reversed(),
                service.history(id).join().stream().map(SettlementEvent::id).toList());
    }

    @Test
    void treasuryPermissionUsesLatestAcceptedPromotionAndNullActorIsPublic() {
        UUID id = create(0, 0);
        join(id, member);
        var first = service.offer(owner, id, member, SettlementOffer.Type.PROMOTE).join().offer();
        service.accept(member, first.id(), false).join();
        service.demote(owner, id, member).join();
        var second = service.offer(owner, id, member, SettlementOffer.Type.PROMOTE).join().offer();
        service.accept(member, second.id(), false).join();
        database.transact(
                        c -> {
                            var permission = service.treasuryPermission(c, member, id);
                            assertEquals(Role.GOVERNOR, permission.role());
                            assertEquals(second.id(), permission.promotionId());
                            assertEquals(1L, permission.ownershipRevision());
                            assertEquals(
                                    Role.PUBLIC, service.treasuryPermission(c, null, id).role());
                            assertEquals(
                                    Role.OWNER, service.treasuryPermission(c, owner, id).role());
                            return null;
                        })
                .join();
    }

    private void dbLegacyDeadline(UUID offer) {
        database.transact(
                        c -> {
                            // Deliberate migration fixture: remove post-v1 columns and restore the
                            // legacy role constraint. Keep real land profiles and pricing receipts
                            // and progress; this tests offer migration, not pre-pricing state.
                            governanceLegacyFixture(c);
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute("ALTER TABLE settlements DROP COLUMN founded_tick");
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .execute("ALTER TABLE settlement_events DROP COLUMN game_tick");
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .update(CIVILIZATION_SCHEMA)
                                    .set(CIVILIZATION_SCHEMA.VERSION, 1L)
                                    .where(CIVILIZATION_SCHEMA.ID.eq(1L))
                                    .execute();
                            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                    .update(SETTLEMENT_OFFERS)
                                    .set(
                                            SETTLEMENT_OFFERS.EXPIRES_AT,
                                            System.currentTimeMillis() + 60000L)
                                    .where(SETTLEMENT_OFFERS.OFFER_ID.eq(offer))
                                    .execute();
                            return null;
                        })
                .join();
    }

    /** Deliberate pre-governance fixture, not a second runtime/codegen schema. */
    private static void governanceLegacyFixture(
            xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction c) {
        // Forward-phase tables did not exist in a genuine legacy database. Drop them
        // before removing Liquibase metadata or downgrading governance.
        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                .execute("DROP TABLE settlement_acquisitions");
        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                .execute("DROP TABLE settlement_local_activity");
        for (String table :
                List.of(
                        "cycle_notices",
                        "cycle_terminal",
                        "cycle_parcel_history",
                        "wild_activity_actors",
                        "wild_activity",
                        "repair_era_awards",
                        "repair_operations",
                        "repair_transfers",
                        "repair_materials",
                        "repair_cells",
                        "repair_jobs",
                        "building_eras",
                        "decay_damage",
                        "decay_operations",
                        "decay_state",
                        "building_operations",
                        "building_revisions",
                        "buildings"))
            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                    .execute("DROP TABLE " + table);
        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                .execute("DROP TABLE settlement_certification_applications");
        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                .execute("DROP TABLE settlement_activity_lifetime");
        // A genuine legacy database has no Liquibase metadata. Remove it in the same
        // fixture transaction as the downgrade, then reopen before migration.
        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                .execute("DROP TABLE IF EXISTS DATABASECHANGELOG");
        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                .execute("DROP TABLE IF EXISTS DATABASECHANGELOGLOCK");
        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c).execute("PRAGMA user_version=1");
        // Only rewind governance. The claim_pricing migration must retain the progress
        // and purchased receipts produced by the normal quote/purchase setup.
        for (String table :
                List.of(
                        "parcel_approvals",
                        "parcel_requests",
                        "parcel_policy",
                        "parcel_holders",
                        "parcel_cells",
                        "parcels",
                        "settlement_bans",
                        "settlement_details")) {
            xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                    .execute("DROP TABLE " + table);
        }
        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                .execute(
                        """
                        CREATE TABLE settlement_members_legacy (
                            settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id),
                            player_uuid TEXT NOT NULL,
                            role TEXT NOT NULL CHECK(role IN ('MANAGER','MEMBER')),
                            PRIMARY KEY(settlement_id,player_uuid)
                        )
                        """);
        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                .execute(
                        """
                        INSERT INTO settlement_members_legacy
                        SELECT settlement_id,player_uuid,
                            CASE role WHEN 'GOVERNOR' THEN 'MANAGER' WHEN 'CITIZEN' THEN 'MEMBER' ELSE role END
                        FROM settlement_members
                        """);
        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                .execute("DROP TABLE settlement_members");
        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                .execute("ALTER TABLE settlement_members_legacy RENAME TO settlement_members");
    }
}
