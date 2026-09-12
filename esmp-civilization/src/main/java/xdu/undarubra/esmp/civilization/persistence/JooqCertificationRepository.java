package xdu.undarubra.esmp.civilization.persistence;

import static xdu.undarubra.esmp.civilization.generated.Tables.SETTLEMENTS;
import static xdu.undarubra.esmp.civilization.generated.Tables.SETTLEMENT_ACTIVITY_LIFETIME;
import static xdu.undarubra.esmp.civilization.generated.Tables.SETTLEMENT_CERTIFICATION_APPLICATIONS;
import static xdu.undarubra.esmp.civilization.generated.Tables.SETTLEMENT_DETAILS;
import static xdu.undarubra.esmp.civilization.generated.Tables.SETTLEMENT_EVENTS;
import static xdu.undarubra.esmp.civilization.settlement.SettlementRules.require;

import org.jooq.DSLContext;
import org.jooq.Record;

import xdu.undarubra.esmp.civilization.certification.CertificationApplication;
import xdu.undarubra.esmp.civilization.certification.CertificationApplication.State;
import xdu.undarubra.esmp.civilization.certification.CertificationPolicy;
import xdu.undarubra.esmp.civilization.certification.CertificationService;
import xdu.undarubra.esmp.civilization.certification.CertificationStatus;
import xdu.undarubra.esmp.civilization.certification.CertificationView;
import xdu.undarubra.esmp.civilization.certification.VerifiedSettlement;
import xdu.undarubra.esmp.civilization.settlement.LandName;
import xdu.undarubra.esmp.civilization.settlement.Role;
import xdu.undarubra.esmp.civilization.settlement.SettlementChange;
import xdu.undarubra.esmp.civilization.settlement.SettlementService.ClaimActivity;
import xdu.undarubra.esmp.civilization.settlement.SettlementView;
import xdu.undarubra.esmp.core.storage.DatabaseAccess;
import xdu.undarubra.esmp.core.storage.UnitOfWork;
import xdu.undarubra.esmp.core.storage.UnitOfWork.Transaction;
import xdu.undarubra.esmp.core.time.GameClock;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Civilization-owned certification adapter. The serial UnitOfWork is the pending-application guard;
 * consent checks, decisions, naming, audit and clock checkpoint share its transaction. Lifetime
 * credit is read here, never synthesized from current activity or modified by certification.
 */
public final class JooqCertificationRepository implements CertificationService.Repository {
    private final UnitOfWork database;
    private final GameClock clock;
    private final CertificationPolicy policy;
    private final Predicate<UUID> administrators;
    private final ClaimActivity activity;

    public JooqCertificationRepository(
            UnitOfWork database,
            GameClock clock,
            CertificationPolicy policy,
            Predicate<UUID> administrators,
            ClaimActivity activity) {
        this.database = Objects.requireNonNull(database);
        this.clock = Objects.requireNonNull(clock);
        this.policy = Objects.requireNonNull(policy);
        this.administrators = Objects.requireNonNull(administrators);
        this.activity = Objects.requireNonNull(activity);
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Transaction transaction, DSLContext context) throws Exception;
    }

    private <T> CompletableFuture<T> transact(Work<T> work) {
        return database.transact(tx -> work.run(tx, DatabaseAccess.context(tx)));
    }

    @Override
    public CompletableFuture<CertificationView> view(UUID id) {
        return transact((tx, c) -> readView(tx, c, id, clock.ticks()));
    }

    @Override
    public CompletableFuture<List<CertificationApplication>> applications(UUID id) {
        return transact(
                (tx, c) -> {
                    var a = SETTLEMENT_CERTIFICATION_APPLICATIONS;
                    var result = new ArrayList<CertificationApplication>();
                    for (Record row :
                            c.selectFrom(a)
                                    .where(a.SETTLEMENT_ID.eq(id))
                                    .orderBy(a.APPLIED_TICK.desc(), a.APPLICATION_ID.asc())
                                    .fetch()) {
                        result.add(application(row));
                    }
                    return List.copyOf(result);
                });
    }

    @Override
    public CompletableFuture<List<VerifiedSettlement>> verifiedSettlements() {
        return transact(
                (tx, c) -> {
                    var s = SETTLEMENTS;
                    var d = SETTLEMENT_DETAILS;
                    var result = new ArrayList<VerifiedSettlement>();
                    for (Record row :
                            c.select(s.SETTLEMENT_ID, d.NUMBER, d.OFFICIAL_NAME)
                                    .from(s)
                                    .join(d)
                                    .on(d.SETTLEMENT_ID.eq(s.SETTLEMENT_ID))
                                    .where(
                                            s.STATUS
                                                    .eq("OWNED")
                                                    .and(
                                                            d.CERTIFICATION_STATUS.eq(
                                                                    CertificationStatus.VERIFIED
                                                                            .name()))
                                                    .and(d.OFFICIAL_NAME.isNotNull()))
                                    .orderBy(d.NUMBER.asc(), s.SETTLEMENT_ID.asc())
                                    .fetch()) {
                        String name = row.get(d.OFFICIAL_NAME);
                        if (!name.isBlank()) {
                            result.add(
                                    new VerifiedSettlement(
                                            row.get(s.SETTLEMENT_ID), row.get(d.NUMBER), name));
                        }
                    }
                    return List.copyOf(result);
                });
    }

    @Override
    public CompletableFuture<SettlementChange> apply(
            UUID actor, UUID id, String proposedNameNullable) {
        return transact(
                (tx, c) -> {
                    String name = optionalName(proposedNameNullable);
                    SettlementView settlement = owned(c, id);
                    requireGovernance(tx, settlement, actor);
                    Record details = details(c, id);
                    require(
                            CertificationStatus.UNVERIFIED
                                    .name()
                                    .equals(details.get(SETTLEMENT_DETAILS.CERTIFICATION_STATUS)),
                            "Settlement is already verified.");

                    Record pending = pending(c, id);
                    if (pending != null) {
                        CertificationApplication existing = application(pending);
                        require(
                                existing.applicant().equals(actor)
                                        && Objects.equals(existing.proposedName(), name),
                                "A certification application is already pending.");
                        // Receipt replay does not renew consent, recheck fluctuating activity,
                        // checkpoint the clock or append another event.
                        return change(c, id, appliedReceipt(existing.id()));
                    }

                    long tick = clock.ticks();
                    CertificationView current = readView(tx, c, id, tick);
                    requireEligible(current);
                    if (name != null) requireNameAvailable(c, name, id);

                    UUID applicationId = UUID.randomUUID();
                    var a = SETTLEMENT_CERTIFICATION_APPLICATIONS;
                    c.insertInto(a)
                            .set(a.APPLICATION_ID, applicationId)
                            .set(a.SETTLEMENT_ID, id)
                            .set(a.APPLICANT_UUID, actor)
                            .set(a.PROPOSED_NAME, name)
                            .set(a.APPLIED_TICK, tick)
                            .set(a.STATE, State.PENDING.name())
                            .setNull(a.DECIDED_BY_UUID)
                            .setNull(a.DECIDED_TICK)
                            .setNull(a.DECISION_NOTE)
                            .set(a.OWNERSHIP_REVISION, settlement.ownershipRevision())
                            .set(
                                    a.NAME_REVISION,
                                    details.get(SETTLEMENT_DETAILS.CERTIFICATION_REVISION))
                            .execute();
                    event(
                            c,
                            id,
                            actor,
                            "CERTIFICATION_APPLIED",
                            "application="
                                    + applicationId
                                    + ";applicant="
                                    + actor
                                    + ";proposedName="
                                    + name,
                            tick);
                    clock.checkpoint(tx);
                    return change(c, id, appliedReceipt(applicationId));
                });
    }

    @Override
    public CompletableFuture<SettlementChange> decide(
            UUID actor, UUID id, UUID applicationId, boolean approve, String note) {
        return transact(
                (tx, c) -> {
                    require(actor != null && administrators.test(actor), "Administrator required.");
                    String decisionNote = normalizedNote(note);
                    var a = SETTLEMENT_CERTIFICATION_APPLICATIONS;
                    Record row =
                            c.selectFrom(a)
                                    .where(
                                            a.APPLICATION_ID
                                                    .eq(applicationId)
                                                    .and(a.SETTLEMENT_ID.eq(id)))
                                    .fetchOne();
                    require(row != null, "Certification application not found.");
                    CertificationApplication application = application(row);
                    State outcome = approve ? State.APPROVED : State.DENIED;
                    if (application.state() != State.PENDING) {
                        require(
                                application.state() == outcome
                                        && actor.equals(application.decidedBy())
                                        && Objects.equals(decisionNote, application.decisionNote()),
                                "Certification application already has a different decision.");
                        // Return the current snapshot, not a historical name. A later rename
                        // must survive an exact decision replay.
                        return change(c, id, decisionReceipt(applicationId, outcome));
                    }

                    long tick = clock.ticks();
                    if (approve) {
                        SettlementView settlement = owned(c, id);
                        Record details = details(c, id);
                        require(
                                settlement.ownershipRevision() == row.get(a.OWNERSHIP_REVISION)
                                        && Objects.equals(
                                                details.get(
                                                        SETTLEMENT_DETAILS.CERTIFICATION_REVISION),
                                                row.get(a.NAME_REVISION)),
                                "Application is stale: ownership or naming consent changed.");
                        requireGovernance(tx, settlement, application.applicant());
                        CertificationView current = readView(tx, c, id, tick);
                        requireEligible(current);
                        String name = application.proposedName();
                        if (name != null) requireNameAvailable(c, name, id);

                        var d = SETTLEMENT_DETAILS;
                        int updated =
                                c.update(d)
                                        .set(
                                                d.CERTIFICATION_STATUS,
                                                CertificationStatus.VERIFIED.name())
                                        .set(
                                                d.CERTIFICATION_REVISION,
                                                d.CERTIFICATION_REVISION.add(1L))
                                        .where(
                                                d.SETTLEMENT_ID
                                                        .eq(id)
                                                        .and(
                                                                d.CERTIFICATION_STATUS.eq(
                                                                        CertificationStatus
                                                                                .UNVERIFIED
                                                                                .name()))
                                                        .and(
                                                                d.CERTIFICATION_REVISION.eq(
                                                                        row.get(a.NAME_REVISION))))
                                        .execute();
                        require(updated == 1, "Certification consent changed.");

                        if (name != null) {
                            c.update(SETTLEMENTS)
                                    .set(SETTLEMENTS.NAME, name)
                                    .set(SETTLEMENTS.NAME_KEY, nameKey(name))
                                    .where(SETTLEMENTS.SETTLEMENT_ID.eq(id))
                                    .execute();
                            c.update(d)
                                    .set(d.OFFICIAL_NAME, name)
                                    .where(d.SETTLEMENT_ID.eq(id))
                                    .execute();
                        }
                        resolve(c, id, applicationId, actor, outcome, tick, decisionNote);
                        event(
                                c,
                                id,
                                actor,
                                "CERTIFICATION_APPROVED",
                                decisionDetail(applicationId, decisionNote),
                                tick);
                        if (name != null && !Objects.equals(current.officialName(), name)) {
                            event(
                                    c,
                                    id,
                                    actor,
                                    "RENAMED",
                                    settlement.name()
                                            + " -> "
                                            + name
                                            + ";application="
                                            + applicationId,
                                    tick);
                        }
                    } else {
                        // Denial needs no current consent or eligibility. Even released or
                        // otherwise stale applications can be resolved. Do not touch Settlement
                        // status, names, claims, ownership or protection (and never revoke).
                        resolve(c, id, applicationId, actor, outcome, tick, decisionNote);
                        event(
                                c,
                                id,
                                actor,
                                "CERTIFICATION_DENIED",
                                decisionDetail(applicationId, decisionNote),
                                tick);
                    }
                    clock.checkpoint(tx);
                    return change(c, id, decisionReceipt(applicationId, outcome));
                });
    }

    private CertificationView readView(Transaction tx, DSLContext c, UUID id, long tick)
            throws Exception {
        var s = SETTLEMENTS;
        var d = SETTLEMENT_DETAILS;
        var l = SETTLEMENT_ACTIVITY_LIFETIME;
        Record row =
                c.select(
                                s.SETTLEMENT_ID,
                                s.FOUNDED_TICK,
                                d.NUMBER,
                                d.CERTIFICATION_STATUS,
                                d.OFFICIAL_NAME,
                                l.CREDITED)
                        .from(s)
                        .join(d)
                        .on(d.SETTLEMENT_ID.eq(s.SETTLEMENT_ID))
                        .leftJoin(l)
                        .on(l.SETTLEMENT_ID.eq(s.SETTLEMENT_ID))
                        .where(s.SETTLEMENT_ID.eq(id).and(s.STATUS.eq("OWNED")))
                        .fetchOne();
        if (row == null) return null;

        Long foundedTick = row.get(s.FOUNDED_TICK);
        // Never infer age from wall time. Missing founding data earns no age.
        long age = foundedTick == null ? 0L : Math.max(0L, tick - Math.max(0L, foundedTick));
        Double credited = row.get(l.CREDITED);
        double lifetime = credited == null ? 0.0 : credited;
        boolean active = activity.active(tx, id);
        CertificationStatus status = CertificationStatus.valueOf(row.get(d.CERTIFICATION_STATUS));
        boolean eligible =
                status == CertificationStatus.UNVERIFIED
                        && age >= policy.minimumAgeTicks()
                        && active
                        && Double.isFinite(lifetime)
                        && lifetime >= policy.minimumLifetimeCredit();
        Record pending = pending(c, id);
        return new CertificationView(
                row.get(s.SETTLEMENT_ID),
                row.get(d.NUMBER),
                status,
                row.get(d.OFFICIAL_NAME),
                age,
                lifetime,
                active,
                eligible,
                policy.minimumAgeTicks(),
                policy.minimumLifetimeCredit(),
                pending == null ? null : application(pending));
    }

    private void requireGovernance(Transaction tx, SettlementView settlement, UUID actor)
            throws SQLException {
        require(actor != null, "Applicant required.");
        Role role = settlement.role(actor);
        require(
                role == Role.OWNER || role == Role.GOVERNOR,
                "Only the Owner or a Governor may apply for certification.");
        require(
                administrators.test(actor)
                        || !JooqParcelRepository.banned(tx, settlement.id(), actor),
                "Applicant is banned from this Settlement.");
    }

    private static void requireEligible(CertificationView current) {
        require(current != null, "Settlement is no longer owned or does not exist.");
        require(
                current.status() == CertificationStatus.UNVERIFIED,
                "Settlement is already verified.");
        require(
                current.ageTicks() >= current.minimumAgeTicks(),
                "Settlement has not reached the minimum certification age.");
        require(current.active(), "Settlement must currently be active for certification.");
        require(
                Double.isFinite(current.lifetimeCredit())
                        && current.lifetimeCredit() >= current.minimumLifetimeCredit(),
                "Settlement has insufficient lifetime activity credit.");
    }

    private static SettlementView owned(DSLContext c, UUID id) throws SQLException {
        SettlementView settlement = JooqSettlementRepository.load(c, id);
        require(settlement != null, "Settlement is no longer owned or does not exist.");
        return settlement;
    }

    private static Record details(DSLContext c, UUID id) throws SQLException {
        Record row =
                c.selectFrom(SETTLEMENT_DETAILS)
                        .where(SETTLEMENT_DETAILS.SETTLEMENT_ID.eq(id))
                        .fetchOne();
        if (row == null) throw new SQLException("Missing Settlement metadata: " + id);
        return row;
    }

    private static Record pending(DSLContext c, UUID id) {
        var a = SETTLEMENT_CERTIFICATION_APPLICATIONS;
        return c.selectFrom(a)
                .where(a.SETTLEMENT_ID.eq(id).and(a.STATE.eq(State.PENDING.name())))
                .fetchOne();
    }

    private static CertificationApplication application(Record row) {
        var a = SETTLEMENT_CERTIFICATION_APPLICATIONS;
        return new CertificationApplication(
                row.get(a.APPLICATION_ID),
                row.get(a.SETTLEMENT_ID),
                row.get(a.APPLICANT_UUID),
                row.get(a.PROPOSED_NAME),
                row.get(a.APPLIED_TICK),
                State.valueOf(row.get(a.STATE)),
                row.get(a.DECIDED_BY_UUID),
                row.get(a.DECIDED_TICK),
                row.get(a.DECISION_NOTE));
    }

    private static void resolve(
            DSLContext c,
            UUID id,
            UUID applicationId,
            UUID actor,
            State outcome,
            long tick,
            String note) {
        var a = SETTLEMENT_CERTIFICATION_APPLICATIONS;
        int updated =
                c.update(a)
                        .set(a.STATE, outcome.name())
                        .set(a.DECIDED_BY_UUID, actor)
                        .set(a.DECIDED_TICK, tick)
                        .set(a.DECISION_NOTE, note)
                        .where(
                                a.APPLICATION_ID
                                        .eq(applicationId)
                                        .and(a.SETTLEMENT_ID.eq(id))
                                        .and(a.STATE.eq(State.PENDING.name())))
                        .execute();
        require(updated == 1, "Certification application is no longer pending.");
    }

    private static String optionalName(String raw) {
        return raw == null || raw.isBlank() ? null : LandName.normalize(raw);
    }

    private static String normalizedNote(String raw) {
        if (raw == null) return null;
        String note = raw.trim();
        require(note.length() <= 256, "Decision note must be at most 256 characters.");
        return note;
    }

    private static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static void requireNameAvailable(DSLContext c, String name, UUID self) {
        var s = SETTLEMENTS;
        require(
                !c.fetchExists(
                        c.selectOne()
                                .from(s)
                                .where(
                                        s.NAME_KEY
                                                .eq(nameKey(name))
                                                .and(s.STATUS.eq("OWNED"))
                                                .and(s.SETTLEMENT_ID.ne(self)))),
                "An owned Settlement already uses that name.");
    }

    private static String appliedReceipt(UUID applicationId) {
        return "Certification application " + applicationId + " pending.";
    }

    private static String decisionReceipt(UUID applicationId, State outcome) {
        return "Certification application "
                + applicationId
                + " "
                + outcome.name().toLowerCase(Locale.ROOT)
                + ".";
    }

    private static String decisionDetail(UUID applicationId, String note) {
        return "application=" + applicationId + ";note=" + note;
    }

    private static SettlementChange change(DSLContext c, UUID id, String message)
            throws SQLException {
        return new SettlementChange(id, JooqSettlementRepository.load(c, id), null, message);
    }

    private static void event(
            DSLContext c, UUID id, UUID actor, String type, String detail, long tick) {
        var e = SETTLEMENT_EVENTS;
        c.insertInto(e)
                .set(e.EVENT_ID, UUID.randomUUID())
                .set(e.SETTLEMENT_ID, id)
                .set(e.ACTOR_UUID, actor)
                .set(e.TYPE, type)
                .set(e.DETAIL, detail)
                .set(e.OCCURRED_AT, Instant.now().toEpochMilli())
                .set(e.DELIVERED, 0L)
                .set(e.GAME_TICK, tick)
                .execute();
    }
}
