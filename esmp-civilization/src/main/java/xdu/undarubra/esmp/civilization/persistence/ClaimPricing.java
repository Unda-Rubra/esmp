package xdu.undarubra.esmp.civilization.persistence;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;
import static xdu.undarubra.esmp.civilization.settlement.SettlementRules.require;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import xdu.undarubra.esmp.civilization.persistence.legacy.LegacySettlementMigrations;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.DatabaseAccess;
import xdu.undarubra.esmp.core.time.GameClock;

import java.sql.SQLException;
import java.util.UUID;

/** Quote persistence and pricing projections in the caller's Settlement transaction. */
final class ClaimPricing {
    private final GameClock clock;
    private final ClaimPricingPolicy policy;
    private final SettlementService.ClaimActivity activity;

    private record Calculation(
            UUID owner,
            ClaimQuote.Payer payer,
            UUID payerId,
            long amount,
            String context,
            String summary) {}

    ClaimPricing(
            GameClock clock, ClaimPricingPolicy policy, SettlementService.ClaimActivity activity) {
        this.clock = clock;
        this.policy = java.util.Objects.requireNonNull(policy);
        this.activity = java.util.Objects.requireNonNull(activity);
    }

    void initialize(DSLContext c) {
        if (DatabaseAccess.legacy(DatabaseAccess.transaction(c)))
            LegacySettlementMigrations.migrateClaimPricing(c);
        Long version =
                c.select(CLAIM_PRICING_SCHEMA.VERSION)
                        .from(CLAIM_PRICING_SCHEMA)
                        .where(CLAIM_PRICING_SCHEMA.ID.eq(1L))
                        .fetchOne(CLAIM_PRICING_SCHEMA.VERSION);
        if (version == null || version != 1L)
            throw new IllegalStateException("Unsupported Claim pricing schema");
    }

    ClaimQuote quote(
            DSLContext c, UUID actor, SettlementView settlement, UUID world, ChunkPosition chunk)
            throws SQLException {
        var value = calculate(c, actor, settlement, world, chunk);
        var q =
                new ClaimQuote(
                        UUID.randomUUID(),
                        actor,
                        value.owner(),
                        settlement == null ? UUID.randomUUID() : settlement.id(),
                        world,
                        chunk,
                        settlement == null,
                        value.payer(),
                        value.payerId(),
                        value.amount(),
                        value.context(),
                        value.summary(),
                        Math.addExact(clock.ticks(), policy.quoteTicks()),
                        false);
        // One outstanding quote per actor; paid receipts remain available.
        c.deleteFrom(CLAIM_QUOTES)
                .where(CLAIM_QUOTES.ACTOR_UUID.eq(actor).and(CLAIM_QUOTES.PURCHASED.eq(0L)))
                .execute();
        c.insertInto(CLAIM_QUOTES)
                .set(CLAIM_QUOTES.QUOTE_ID, q.id())
                .set(CLAIM_QUOTES.ACTOR_UUID, actor)
                .set(CLAIM_QUOTES.OWNER_UUID, q.owner())
                .set(CLAIM_QUOTES.SETTLEMENT_ID, q.settlementId())
                .set(CLAIM_QUOTES.WORLD_INSTANCE_ID, world)
                .set(CLAIM_QUOTES.CHUNK_X, (long) chunk.x())
                .set(CLAIM_QUOTES.CHUNK_Z, (long) chunk.z())
                .set(CLAIM_QUOTES.FOUNDING, q.founding() ? 1L : 0L)
                .set(CLAIM_QUOTES.PAYER_KIND, q.payer().name())
                .set(CLAIM_QUOTES.PAYER_UUID, q.payerId())
                .set(CLAIM_QUOTES.AMOUNT, q.amount())
                .set(CLAIM_QUOTES.CONTEXT, q.context())
                .set(CLAIM_QUOTES.SUMMARY, q.summary())
                .set(CLAIM_QUOTES.EXPIRES_TICK, q.expiresTick())
                .set(CLAIM_QUOTES.PURCHASED, 0L)
                .execute();
        clock.checkpoint(DatabaseAccess.transaction(c));
        return q;
    }

    ClaimQuote load(DSLContext c, UUID actor, UUID id) {
        var r = c.selectFrom(CLAIM_QUOTES).where(CLAIM_QUOTES.QUOTE_ID.eq(id)).fetchOne();
        require(r != null, "Quote not found; request a new Claim quote.");
        require(actor.equals(r.getActorUuid()), "That quote belongs to another player.");
        return new ClaimQuote(
                id,
                actor,
                r.getOwnerUuid(),
                r.getSettlementId(),
                r.getWorldInstanceId(),
                new ChunkPosition(Math.toIntExact(r.getChunkX()), Math.toIntExact(r.getChunkZ())),
                r.getFounding() != 0,
                ClaimQuote.Payer.valueOf(r.getPayerKind()),
                r.getPayerUuid(),
                r.getAmount(),
                r.getContext(),
                r.getSummary(),
                r.getExpiresTick(),
                r.getPurchased() != 0);
    }

    void validate(DSLContext c, ClaimQuote q, SettlementView settlement) throws SQLException {
        require(clock.ticks() < q.expiresTick(), "Claim quote expired; request another quote.");
        var value = calculate(c, q.actor(), settlement, q.worldInstance(), q.chunk());
        require(
                value.context().equals(q.context()) && value.amount() == q.amount(),
                "Claim price context changed; request another quote.");
    }

    void complete(DSLContext c, ClaimQuote q) {
        var p = PLAYER_LAND_PROGRESS;
        c.insertInto(p)
                .set(p.PLAYER_UUID, q.owner())
                .set(p.CLAIMS_USED, 0L)
                .set(p.FIRST_FOUNDING_USED, 0L)
                .onConflict(p.PLAYER_UUID)
                .doNothing()
                .execute();
        var r = c.selectFrom(p).where(p.PLAYER_UUID.eq(q.owner())).fetchSingle();
        c.update(p)
                .set(p.CLAIMS_USED, Math.addExact(r.getClaimsUsed(), 1L))
                .set(p.FIRST_FOUNDING_USED, q.founding() ? 1L : r.getFirstFoundingUsed())
                .where(p.PLAYER_UUID.eq(q.owner()))
                .execute();
        c.update(CLAIM_QUOTES)
                .set(CLAIM_QUOTES.PURCHASED, 1L)
                .where(CLAIM_QUOTES.QUOTE_ID.eq(q.id()))
                .execute();
        clock.checkpoint(DatabaseAccess.transaction(c));
    }

    private Calculation calculate(
            DSLContext c, UUID actor, SettlementView s, UUID world, ChunkPosition chunk)
            throws SQLException {
        var land =
                c.selectFrom(LAND_VALUE_PROFILES)
                        .where(
                                LAND_VALUE_PROFILES
                                        .WORLD_INSTANCE_ID
                                        .eq(world)
                                        .and(LAND_VALUE_PROFILES.CHUNK_X.eq((long) chunk.x()))
                                        .and(LAND_VALUE_PROFILES.CHUNK_Z.eq((long) chunk.z())))
                        .fetchOne();
        require(
                land != null,
                "No natural land value is captured for this chunk; purchase is unavailable.");
        UUID owner = s == null ? actor : s.owner();
        var payer =
                s == null || owner.equals(actor)
                        ? ClaimQuote.Payer.WALLET
                        : ClaimQuote.Payer.TREASURY;
        UUID payerId = payer == ClaimQuote.Payer.WALLET ? actor : s.id();
        var owned = SETTLEMENTS.as("owned");
        var chunks = SETTLEMENT_CHUNKS.as("chunks");
        long count =
                c.selectCount()
                        .from(owned)
                        .where(owned.OWNER_UUID.eq(owner).and(owned.STATUS.eq("OWNED")))
                        .fetchSingle(0, int.class);
        long area =
                c.selectCount()
                        .from(chunks)
                        .join(owned)
                        .on(chunks.SETTLEMENT_ID.eq(owned.SETTLEMENT_ID))
                        .where(owned.OWNER_UUID.eq(owner).and(owned.STATUS.eq("OWNED")))
                        .fetchSingle(0, int.class);
        long distance = Long.MAX_VALUE;
        if (s == null) {
            Long nearest =
                    c.select(
                                    DSL.min(
                                            DSL.abs(chunks.CHUNK_X.sub((long) chunk.x()))
                                                    .add(
                                                            DSL.abs(
                                                                    chunks.CHUNK_Z.sub(
                                                                            (long) chunk.z())))))
                            .from(chunks)
                            .join(owned)
                            .on(chunks.SETTLEMENT_ID.eq(owned.SETTLEMENT_ID))
                            .where(
                                    owned.OWNER_UUID
                                            .eq(owner)
                                            .and(owned.STATUS.eq("OWNED"))
                                            .and(chunks.WORLD_INSTANCE_ID.eq(world)))
                            .fetchOne(0, Long.class);
            if (nearest != null) distance = nearest;
        }
        var progress =
                c.selectFrom(PLAYER_LAND_PROGRESS)
                        .where(PLAYER_LAND_PROGRESS.PLAYER_UUID.eq(owner))
                        .fetchOne();
        long used = progress == null ? 0 : progress.getClaimsUsed();
        boolean founded = progress != null && progress.getFirstFoundingUsed() != 0;
        boolean active = s == null || activity.active(DatabaseAccess.transaction(c), s.id());
        int size = s == null ? 0 : s.chunks().size();
        long amount =
                policy.price(
                        land.getBaseValue(),
                        count,
                        area,
                        size,
                        active,
                        s == null,
                        distance,
                        used,
                        founded);
        // Full context also invalidates consent for changes that leave the price unchanged.
        String governance = "founding";
        if (s != null) {
            long security =
                    c.select(SETTLEMENT_DETAILS.SECURITY_REVISION)
                            .from(SETTLEMENT_DETAILS)
                            .where(SETTLEMENT_DETAILS.SETTLEMENT_ID.eq(s.id()))
                            .fetchSingle(SETTLEMENT_DETAILS.SECURITY_REVISION);
            governance =
                    s.id()
                            + ":"
                            + s.ownershipRevision()
                            + ":"
                            + s.chunkRevision()
                            + ":"
                            + s.role(actor)
                            + ":"
                            + security;
        }
        String context =
                policy
                        + "|"
                        + owner
                        + "|"
                        + payer
                        + "|"
                        + payerId
                        + "|"
                        + world
                        + "|"
                        + chunk
                        + "|"
                        + land.getValuationVersion()
                        + "|"
                        + land.getBaseValue()
                        + "|"
                        + count
                        + "|"
                        + area
                        + "|"
                        + distance
                        + "|"
                        + used
                        + "|"
                        + founded
                        + "|"
                        + active
                        + "|"
                        + governance;
        String summary =
                (s == null && !founded ? "First founding: fixed 25. " : "")
                        + "Base "
                        + land.getBaseValue()
                        + "; portfolio "
                        + count
                        + " settlements / "
                        + area
                        + " chunks; "
                        + (s == null
                                ? "nearest own land "
                                        + (distance == Long.MAX_VALUE
                                                ? "none"
                                                : distance + " chunks")
                                : "size " + size + ", Activity " + (active ? "active" : "inactive"))
                        + "; early discount "
                        + ((10000 - policy.early(used)) / 100)
                        + "%; payer "
                        + (payer == ClaimQuote.Payer.WALLET ? "your wallet" : "Settlement treasury")
                        + ".";
        return new Calculation(owner, payer, payerId, amount, context, summary);
    }
}
