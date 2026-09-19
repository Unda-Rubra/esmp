package xdu.undarubra.esmp.civilization.persistence;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;
import static xdu.undarubra.esmp.civilization.settlement.SettlementRules.require;

import org.jooq.DSLContext;
import org.jooq.Record;

import xdu.undarubra.esmp.civilization.activity.*;
import xdu.undarubra.esmp.civilization.api.GovernanceQueries;
import xdu.undarubra.esmp.civilization.building.*;
import xdu.undarubra.esmp.civilization.building.BuildingBounds.Position;
import xdu.undarubra.esmp.civilization.decay.*;
import xdu.undarubra.esmp.civilization.decay.DecayService.Change;
import xdu.undarubra.esmp.civilization.repair.*;
import xdu.undarubra.esmp.civilization.repair.RepairService.*;
import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.core.storage.*;
import xdu.undarubra.esmp.core.time.GameClock;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class JooqRepairRepository implements RepairService.Repository {
    private final UnitOfWork db;
    private final GameClock clock;
    private final Policy policy;
    private final Predicate<UUID> admins;
    private final JooqDecayRepository decay;
    private final JooqBuildingRepository buildingWriter;
    // SQL-lane cursor: blocked candidates must not hide later due jobs.
    private UUID dueCursor;

    public JooqRepairRepository(
            UnitOfWork db,
            GameClock clock,
            ActivityPolicy activity,
            LocalActivityPolicy local,
            DecayService.Policy decayPolicy,
            Policy policy,
            Predicate<UUID> admins) {
        this.db = db;
        this.clock = clock;
        this.policy = policy;
        this.admins = admins;
        decay = new JooqDecayRepository(db, clock, activity, local, decayPolicy);
        buildingWriter =
                new JooqBuildingRepository(db, clock, BuildingService.Policy.defaults(), admins);
    }

    @Override
    public CompletableFuture<Set<UUID>> reservations() {
        return db.transact(
                tx ->
                        Set.copyOf(
                                DatabaseAccess.context(tx)
                                        .selectDistinct(REPAIR_OPERATIONS.SETTLEMENT_ID)
                                        .from(REPAIR_OPERATIONS)
                                        .where(REPAIR_OPERATIONS.STATE.eq("PREPARED"))
                                        .fetch(REPAIR_OPERATIONS.SETTLEMENT_ID)));
    }

    static void era(DSLContext c, UUID building) {
        c.insertInto(BUILDING_ERAS)
                .set(BUILDING_ERAS.BUILDING_ID, building)
                .set(BUILDING_ERAS.ERA, 0L)
                .onConflict(BUILDING_ERAS.BUILDING_ID)
                .doNothing()
                .execute();
    }

    static void condition(
            DSLContext c, UUID building, DecayService.Condition condition, long tick) {
        era(c, building);
        if (condition == DecayService.Condition.RUINED) {
            var row =
                    c.selectFrom(BUILDING_ERAS)
                            .where(BUILDING_ERAS.BUILDING_ID.eq(building))
                            .fetchSingle();
            if (row.get(BUILDING_ERAS.CYCLE_ID) == null) {
                UUID cycle = UUID.randomUUID();
                c.update(BUILDING_ERAS)
                        .set(BUILDING_ERAS.CYCLE_ID, cycle)
                        .where(BUILDING_ERAS.BUILDING_ID.eq(building))
                        .execute();
                event(
                        c,
                        cycle,
                        JooqBuildingRepository.current(c, building).publicationSettlement(),
                        DecayService.SYSTEM_ACTOR,
                        "BUILDING_RUINED",
                        "building=" + building + ";cycle=" + cycle,
                        tick);
            }
        } else if (condition == DecayService.Condition.INTACT)
            c.update(BUILDING_ERAS)
                    .setNull(BUILDING_ERAS.CYCLE_ID)
                    .where(BUILDING_ERAS.BUILDING_ID.eq(building))
                    .execute();
    }

    static boolean protects(DSLContext c, UUID building) {
        return c.fetchExists(
                c.selectOne()
                        .from(REPAIR_JOBS)
                        .where(
                                REPAIR_JOBS
                                        .BUILDING_ID
                                        .eq(building)
                                        .and(REPAIR_JOBS.STATE.in("FUNDED", "CANCELLING"))));
    }

    static void release(DSLContext c, UUID building, long tick) {
        require(
                !c.fetchExists(
                        c.selectOne()
                                .from(REPAIR_OPERATIONS)
                                .where(REPAIR_OPERATIONS.BUILDING_ID.eq(building))
                                .and(REPAIR_OPERATIONS.STATE.eq("PREPARED"))),
                "Reconcile pending Repair before releasing this Building");
        var jobs = c.selectFrom(REPAIR_JOBS).where(REPAIR_JOBS.BUILDING_ID.eq(building)).fetch();
        for (var job : jobs) {
            UUID id = job.get(REPAIR_JOBS.JOB_ID);
            require(
                    !c.fetchExists(
                            c.selectOne()
                                    .from(REPAIR_TRANSFERS)
                                    .where(REPAIR_TRANSFERS.JOB_ID.eq(id))
                                    .and(REPAIR_TRANSFERS.STATE.eq("PREPARED"))),
                    "Reconcile pending material transfer before releasing this Building");
            if (!Set.of("COMPLETED", "CANCELLED").contains(job.get(REPAIR_JOBS.STATE))) {
                c.update(REPAIR_JOBS)
                        .set(REPAIR_JOBS.STATE, "CANCELLING")
                        .set(
                                REPAIR_JOBS.DETAIL,
                                "Building released; unused materials remain refundable")
                        .where(REPAIR_JOBS.JOB_ID.eq(id))
                        .execute();
                for (var cell :
                        c.selectFrom(REPAIR_CELLS)
                                .where(REPAIR_CELLS.JOB_ID.eq(id))
                                .and(REPAIR_CELLS.STATE.eq("PENDING"))
                                .fetch()) retireCell(c, id, position(cell), "SUPERSEDED");
                terminal(c, id, tick, false, null);
            }
        }
    }

    static void rebase(DSLContext c, UUID building, long tick) {
        era(c, building);
        var v = JooqBuildingRepository.current(c, building);
        var base = JooqBuildingRepository.baseline(c, building, v.revision());
        for (var job :
                c.selectFrom(REPAIR_JOBS)
                        .where(
                                REPAIR_JOBS
                                        .BUILDING_ID
                                        .eq(building)
                                        .and(
                                                REPAIR_JOBS.STATE.in(
                                                        "AWAITING",
                                                        "FUNDED",
                                                        "PAUSED",
                                                        "CANCELLING")))
                        .fetch()) {
            UUID id = job.get(REPAIR_JOBS.JOB_ID);
            boolean cancel =
                    v.retired() || v.station() == null || !v.station().equals(station(job));
            if (cancel)
                c.update(REPAIR_JOBS)
                        .set(REPAIR_JOBS.STATE, "CANCELLING")
                        .set(REPAIR_JOBS.DETAIL, "Building or station changed")
                        .where(REPAIR_JOBS.JOB_ID.eq(id))
                        .execute();
            for (var cell :
                    c.selectFrom(REPAIR_CELLS)
                            .where(
                                    REPAIR_CELLS
                                            .JOB_ID
                                            .eq(id)
                                            .and(REPAIR_CELLS.STATE.in("PENDING", "PREPARED")))
                            .fetch()) {
                var p = position(cell);
                boolean valid =
                        !cancel
                                && base.bounds().contains(p)
                                && base.at(p).data().equals(cell.get(REPAIR_CELLS.TARGET))
                                && v.permitsAutomaticMutation(p, base.at(p))
                                && damage(c, building, p) != null;
                if (!valid) retireCell(c, id, p, "SUPERSEDED");
            }
            c.update(REPAIR_JOBS)
                    .set(REPAIR_JOBS.BASELINE_REVISION, v.revision())
                    .set(REPAIR_JOBS.NEXT_TICK, tick)
                    .where(REPAIR_JOBS.JOB_ID.eq(id))
                    .execute();
        }
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    for (var b :
                            c.select(BUILDINGS.BUILDING_ID)
                                    .from(BUILDINGS)
                                    .fetch(BUILDINGS.BUILDING_ID)) {
                        era(c, b);
                        var state =
                                c.select(DECAY_STATE.CONDITION)
                                        .from(DECAY_STATE)
                                        .where(DECAY_STATE.BUILDING_ID.eq(b))
                                        .fetchOne(DECAY_STATE.CONDITION);
                        if ("RUINED".equals(state)
                                && !JooqBuildingRepository.current(c, b).retired())
                            c.update(BUILDING_ERAS)
                                    .set(BUILDING_ERAS.CYCLE_ID, UUID.randomUUID())
                                    .where(
                                            BUILDING_ERAS
                                                    .BUILDING_ID
                                                    .eq(b)
                                                    .and(BUILDING_ERAS.CYCLE_ID.isNull()))
                                    .execute();
                        else if ("INTACT".equals(state))
                            c.update(BUILDING_ERAS)
                                    .setNull(BUILDING_ERAS.CYCLE_ID)
                                    .where(BUILDING_ERAS.BUILDING_ID.eq(b))
                                    .execute();
                    }
                    return null;
                });
    }

    @Override
    public CompletableFuture<Quote> inspect(UUID building) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var v = JooqBuildingRepository.current(c, building);
                    var base = JooqBuildingRepository.baseline(c, building, v.revision());
                    var materials = new TreeMap<String, Long>();
                    int cells = 0;
                    for (var row :
                            c.selectFrom(DECAY_DAMAGE)
                                    .where(DECAY_DAMAGE.BUILDING_ID.eq(building))
                                    .fetch()) {
                        var p = damagePosition(row);
                        if (!base.bounds().contains(p)
                                || !v.permitsAutomaticMutation(p, base.at(p))) continue;
                        String material = RepairMaterials.material(base.at(p).data());
                        if (material != null) materials.merge(material, 1L, Long::sum);
                        cells++;
                    }
                    return new Quote(
                            building,
                            materials,
                            cells,
                            decay.projection(tx, building).condition().name(),
                            c.select(BUILDING_ERAS.ERA)
                                    .from(BUILDING_ERAS)
                                    .where(BUILDING_ERAS.BUILDING_ID.eq(building))
                                    .fetchSingle(BUILDING_ERAS.ERA));
                });
    }

    @Override
    public CompletableFuture<Job> job(UUID id) {
        return db.transact(tx -> job(DatabaseAccess.context(tx), id));
    }

    @Override
    public CompletableFuture<List<Job>> jobs(UUID building) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var result = new ArrayList<Job>();
                    for (var id :
                            c.select(REPAIR_JOBS.JOB_ID)
                                    .from(REPAIR_JOBS)
                                    .where(REPAIR_JOBS.BUILDING_ID.eq(building))
                                    .orderBy(REPAIR_JOBS.CREATED_TICK.desc(), REPAIR_JOBS.JOB_ID)
                                    .limit(50)
                                    .fetch(REPAIR_JOBS.JOB_ID)) result.add(job(c, id));
                    return List.copyOf(result);
                });
    }

    @Override
    public CompletableFuture<SettlementChange> prepare(UUID id, UUID actor, UUID building) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var v = JooqBuildingRepository.current(c, building);
                    require(!v.retired() && v.station() != null, "Building needs a Repair Station");
                    require(
                            !c.fetchExists(
                                    c.selectOne()
                                            .from(REPAIR_JOBS)
                                            .where(
                                                    REPAIR_JOBS
                                                            .BUILDING_ID
                                                            .eq(building)
                                                            .and(
                                                                    REPAIR_JOBS.STATE.in(
                                                                            "AWAITING",
                                                                            "FUNDED",
                                                                            "PAUSED",
                                                                            "CANCELLING")))),
                            "A Repair job already exists for this Building");
                    require(
                            !c.fetchExists(
                                    c.selectOne()
                                            .from(DECAY_OPERATIONS)
                                            .where(
                                                    DECAY_OPERATIONS
                                                            .BUILDING_ID
                                                            .eq(building)
                                                            .and(
                                                                    DECAY_OPERATIONS.STATE.eq(
                                                                            "PREPARED")))),
                            "Reconcile pending Decay before preparing Repair");
                    var s = authority(tx, actor, v);
                    require(
                            admins.test(actor)
                                    || s.role(actor) == Role.OWNER
                                    || s.role(actor) == Role.GOVERNOR,
                            "Owner or Governor required");
                    allow(tx, actor, s, v.station(), ParcelAction.INTERACT);
                    var t = REPAIR_JOBS;
                    c.insertInto(t)
                            .set(t.JOB_ID, id)
                            .set(t.BUILDING_ID, building)
                            .set(t.SETTLEMENT_ID, v.settlement())
                            .set(t.WORLD_INSTANCE_ID, v.world())
                            .set(t.PAYER_UUID, actor)
                            .set(t.STATE, "AWAITING")
                            .set(t.BASELINE_REVISION, v.revision())
                            .set(t.STATION_X, (long) v.station().x())
                            .set(t.STATION_Y, (long) v.station().y())
                            .set(t.STATION_Z, (long) v.station().z())
                            .set(t.CREATED_TICK, clock.ticks())
                            .set(t.NEXT_TICK, clock.ticks())
                            .set(t.BATCH_TICKS, policy.batchTicks())
                            .set(t.DETAIL, "")
                            .execute();
                    refresh(tx, id, true);
                    require(remaining(c, id) > 0, "Building has no repairable damage");
                    clock.checkpoint(tx);
                    return change(
                            c,
                            v.settlement(),
                            "Repair prepared: " + id + ". Supply materials at its station.");
                });
    }

    private void refresh(UnitOfWork.Transaction tx, UUID id, boolean append) throws SQLException {
        var c = DatabaseAccess.context(tx);
        var j = job(c, id);
        var v = JooqBuildingRepository.current(c, j.building());
        var s = authority(tx, j.payer(), v);
        require(v.station() != null && v.station().equals(j.station()), "Repair Station changed");
        allow(tx, j.payer(), s, j.station(), ParcelAction.INTERACT);
        rebase(c, v.id(), clock.ticks());
        for (var cell :
                c.selectFrom(REPAIR_CELLS)
                        .where(
                                REPAIR_CELLS
                                        .JOB_ID
                                        .eq(id)
                                        .and(REPAIR_CELLS.STATE.in("PENDING", "PREPARED")))
                        .fetch()) allow(tx, j.payer(), s, position(cell), ParcelAction.BUILD);
        if (append)
            require(
                    !c.fetchExists(
                            c.selectOne()
                                    .from(DECAY_OPERATIONS)
                                    .where(
                                            DECAY_OPERATIONS
                                                    .BUILDING_ID
                                                    .eq(v.id())
                                                    .and(DECAY_OPERATIONS.STATE.eq("PREPARED")))),
                    "Reconcile pending Decay before funding Repair");
        if (append) {
            var base = JooqBuildingRepository.baseline(c, v.id(), v.revision());
            for (var row :
                    c.selectFrom(DECAY_DAMAGE).where(DECAY_DAMAGE.BUILDING_ID.eq(v.id())).fetch()) {
                var p = damagePosition(row);
                if (!base.bounds().contains(p) || !v.permitsAutomaticMutation(p, base.at(p)))
                    continue;
                allow(tx, j.payer(), s, p, ParcelAction.BUILD);
                String target = base.at(p).data(), material = RepairMaterials.material(target);
                var key = cellKey(id, p);
                var old = c.selectFrom(REPAIR_CELLS).where(key).fetchOne();
                boolean fresh =
                        old == null
                                || !Set.of("PENDING", "PREPARED")
                                        .contains(old.get(REPAIR_CELLS.STATE));
                if (old == null)
                    c.insertInto(REPAIR_CELLS)
                            .set(REPAIR_CELLS.JOB_ID, id)
                            .set(REPAIR_CELLS.X, (long) p.x())
                            .set(REPAIR_CELLS.Y, (long) p.y())
                            .set(REPAIR_CELLS.Z, (long) p.z())
                            .set(REPAIR_CELLS.EXPECTED, row.get(DECAY_DAMAGE.CURRENT_STATE))
                            .set(REPAIR_CELLS.TARGET, target)
                            .set(REPAIR_CELLS.MATERIAL, material)
                            .set(REPAIR_CELLS.STATE, "PENDING")
                            .set(
                                    REPAIR_CELLS.DECAY_OPERATION_ID,
                                    row.get(DECAY_DAMAGE.OPERATION_ID))
                            .execute();
                else if (!old.get(REPAIR_CELLS.STATE).equals("PREPARED"))
                    c.update(REPAIR_CELLS)
                            .set(REPAIR_CELLS.EXPECTED, row.get(DECAY_DAMAGE.CURRENT_STATE))
                            .set(REPAIR_CELLS.TARGET, target)
                            .set(REPAIR_CELLS.MATERIAL, material)
                            .set(REPAIR_CELLS.STATE, "PENDING")
                            .setNull(REPAIR_CELLS.OPERATION_ID)
                            .set(
                                    REPAIR_CELLS.DECAY_OPERATION_ID,
                                    row.get(DECAY_DAMAGE.OPERATION_ID))
                            .where(key)
                            .execute();
                if (fresh && material != null) {
                    stock(c, id, material);
                    c.update(REPAIR_MATERIALS)
                            .set(REPAIR_MATERIALS.NEEDED, REPAIR_MATERIALS.NEEDED.add(1L))
                            .where(stockKey(id, material))
                            .execute();
                }
            }
        }
    }

    private static void stock(DSLContext c, UUID job, String material) {
        var t = REPAIR_MATERIALS;
        c.insertInto(t)
                .set(t.JOB_ID, job)
                .set(t.MATERIAL, material)
                .set(t.NEEDED, 0L)
                .set(t.ESCROW, 0L)
                .set(t.CONSUMED, 0L)
                .set(t.REFUNDED, 0L)
                .onConflict(t.JOB_ID, t.MATERIAL)
                .doNothing()
                .execute();
    }

    @Override
    public CompletableFuture<Transfer> transfer(
            UUID id, UUID actor, UUID job, Direction direction, List<Slot> slots) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var j = job(c, job);
                    require(
                            j.payer().equals(actor),
                            "Only this job's payer can transfer materials");
                    var old =
                            c.selectFrom(REPAIR_TRANSFERS)
                                    .where(REPAIR_TRANSFERS.TRANSFER_ID.eq(id))
                                    .fetchOne();
                    byte[] payload = RepairMaterials.encode(slots);
                    if (old != null) {
                        require(
                                old.get(REPAIR_TRANSFERS.JOB_ID).equals(job)
                                        && old.get(REPAIR_TRANSFERS.DIRECTION)
                                                .equals(direction.name())
                                        && Arrays.equals(
                                                old.get(REPAIR_TRANSFERS.PAYLOAD), payload),
                                "Transfer ID has different content");
                        return transfer(old);
                    }
                    require(
                            !c.fetchExists(
                                    c.selectOne()
                                            .from(REPAIR_TRANSFERS)
                                            .where(
                                                    REPAIR_TRANSFERS
                                                            .PAYER_UUID
                                                            .eq(actor)
                                                            .and(
                                                                    REPAIR_TRANSFERS.STATE.eq(
                                                                            "PREPARED")))),
                            "Reconcile the previous inventory transfer first");
                    if (direction == Direction.SUPPLY) {
                        require(
                                j.state().equals("AWAITING") || j.state().equals("PAUSED"),
                                "Job is not awaiting material supply");
                        refresh(tx, job, true);
                    } else
                        require(
                                Set.of("COMPLETED", "CANCELLED").contains(j.state()),
                                "Cancel or complete Repair before refunding unused materials");
                    var amounts = RepairMaterials.amounts(direction, slots);
                    for (var e : amounts.entrySet()) {
                        var r =
                                c.selectFrom(REPAIR_MATERIALS)
                                        .where(stockKey(job, e.getKey()))
                                        .fetchOne();
                        require(r != null, "Material is not part of this Repair");
                        long available =
                                direction == Direction.SUPPLY
                                        ? Math.max(
                                                0,
                                                r.get(REPAIR_MATERIALS.NEEDED)
                                                        - r.get(REPAIR_MATERIALS.ESCROW))
                                        : r.get(REPAIR_MATERIALS.ESCROW);
                        require(
                                e.getValue() <= available,
                                "Material requirement or refund balance changed");
                        if (direction == Direction.REFUND)
                            c.update(REPAIR_MATERIALS)
                                    .set(
                                            REPAIR_MATERIALS.ESCROW,
                                            REPAIR_MATERIALS.ESCROW.sub(e.getValue()))
                                    .where(stockKey(job, e.getKey()))
                                    .execute();
                    }
                    c.insertInto(REPAIR_TRANSFERS)
                            .set(REPAIR_TRANSFERS.TRANSFER_ID, id)
                            .set(REPAIR_TRANSFERS.JOB_ID, job)
                            .set(REPAIR_TRANSFERS.PAYER_UUID, actor)
                            .set(REPAIR_TRANSFERS.DIRECTION, direction.name())
                            .set(REPAIR_TRANSFERS.STATE, "PREPARED")
                            .set(REPAIR_TRANSFERS.PAYLOAD, payload)
                            .set(REPAIR_TRANSFERS.GAME_TICK, clock.ticks())
                            .execute();
                    clock.checkpoint(tx);
                    return transfer(
                            c.selectFrom(REPAIR_TRANSFERS)
                                    .where(REPAIR_TRANSFERS.TRANSFER_ID.eq(id))
                                    .fetchSingle());
                });
    }

    @Override
    public CompletableFuture<SettlementChange> confirm(UUID id) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var transfer = requiredTransfer(c, id);
                    var job = job(c, transfer.job());
                    if (transfer.state().equals("APPLIED"))
                        return change(c, job.settlement(), "Material transfer already recorded.");
                    require(transfer.state().equals("PREPARED"), "Transfer was cancelled");
                    for (var e :
                            RepairMaterials.amounts(transfer.direction(), transfer.slots())
                                    .entrySet()) {
                        if (transfer.direction() == Direction.SUPPLY)
                            c.update(REPAIR_MATERIALS)
                                    .set(
                                            REPAIR_MATERIALS.ESCROW,
                                            REPAIR_MATERIALS.ESCROW.add(e.getValue()))
                                    .where(stockKey(job.id(), e.getKey()))
                                    .execute();
                        else
                            c.update(REPAIR_MATERIALS)
                                    .set(
                                            REPAIR_MATERIALS.REFUNDED,
                                            REPAIR_MATERIALS.REFUNDED.add(e.getValue()))
                                    .where(stockKey(job.id(), e.getKey()))
                                    .execute();
                    }
                    c.update(REPAIR_TRANSFERS)
                            .set(REPAIR_TRANSFERS.STATE, "APPLIED")
                            .where(REPAIR_TRANSFERS.TRANSFER_ID.eq(id))
                            .execute();
                    try {
                        activate(tx, job.id(), false);
                    } catch (SettlementException changed) {
                        c.update(REPAIR_JOBS)
                                .set(REPAIR_JOBS.STATE, "PAUSED")
                                .set(REPAIR_JOBS.DETAIL, changed.getMessage())
                                .where(
                                        REPAIR_JOBS
                                                .JOB_ID
                                                .eq(job.id())
                                                .and(REPAIR_JOBS.STATE.in("AWAITING", "PAUSED")))
                                .execute();
                    }
                    terminal(c, job.id(), clock.ticks(), false, null);
                    event(
                            c,
                            id,
                            job.settlement(),
                            transfer.payer(),
                            "REPAIR_MATERIAL_TRANSFER",
                            "job="
                                    + job.id()
                                    + ";direction="
                                    + transfer.direction()
                                    + ";materials="
                                    + RepairMaterials.amounts(
                                            transfer.direction(), transfer.slots()),
                            clock.ticks());
                    clock.checkpoint(tx);
                    return change(c, job.settlement(), "Repair material transfer recorded.");
                });
    }

    @Override
    public CompletableFuture<SettlementChange> abort(UUID id) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var transfer = requiredTransfer(c, id);
                    var job = job(c, transfer.job());
                    require(
                            !transfer.state().equals("APPLIED"),
                            "A saved material transfer cannot be cancelled");
                    if (transfer.state().equals("PREPARED")) {
                        if (transfer.direction() == Direction.REFUND)
                            for (var e :
                                    RepairMaterials.amounts(transfer.direction(), transfer.slots())
                                            .entrySet())
                                c.update(REPAIR_MATERIALS)
                                        .set(
                                                REPAIR_MATERIALS.ESCROW,
                                                REPAIR_MATERIALS.ESCROW.add(e.getValue()))
                                        .where(stockKey(job.id(), e.getKey()))
                                        .execute();
                        c.update(REPAIR_TRANSFERS)
                                .set(REPAIR_TRANSFERS.STATE, "CANCELLED")
                                .where(REPAIR_TRANSFERS.TRANSFER_ID.eq(id))
                                .execute();
                    }
                    terminal(c, job.id(), clock.ticks(), false, null);
                    clock.checkpoint(tx);
                    return change(
                            c,
                            job.settlement(),
                            "Unsaved inventory transfer cancelled; no materials consumed.");
                });
    }

    @Override
    public CompletableFuture<List<Transfer>> transfers(UUID player, long after) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    Long latest =
                            c.select(org.jooq.impl.DSL.max(REPAIR_TRANSFERS.SEQUENCE))
                                    .from(REPAIR_TRANSFERS)
                                    .where(REPAIR_TRANSFERS.PAYER_UUID.eq(player))
                                    .fetchSingle(0, Long.class);
                    require(
                            after >= 0 && after <= (latest == null ? 0 : latest),
                            "Player Repair receipt is ahead of the database; restore matching"
                                    + " player and SQL data");
                    return c.selectFrom(REPAIR_TRANSFERS)
                            .where(
                                    REPAIR_TRANSFERS
                                            .PAYER_UUID
                                            .eq(player)
                                            .and(REPAIR_TRANSFERS.STATE.ne("CANCELLED"))
                                            .and(
                                                    REPAIR_TRANSFERS
                                                            .SEQUENCE
                                                            .gt(after)
                                                            .or(
                                                                    REPAIR_TRANSFERS.STATE.eq(
                                                                            "PREPARED"))))
                            .orderBy(REPAIR_TRANSFERS.SEQUENCE)
                            .limit(32)
                            .fetch(JooqRepairRepository::transfer);
                });
    }

    @Override
    public CompletableFuture<SettlementChange> start(UUID actor, UUID id) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var j = job(c, id);
                    require(j.payer().equals(actor), "Only the payer can start or resume Repair");
                    if (j.state().equals("FUNDED"))
                        return change(c, j.settlement(), "Repair is already funded.");
                    require(
                            Set.of("AWAITING", "PAUSED").contains(j.state()),
                            "Repair is completed or cancelled; prepare a new job for new damage");
                    refresh(tx, id, true);
                    activate(tx, id, true);
                    clock.checkpoint(tx);
                    return change(c, j.settlement(), "Repair is funded and scheduled.");
                });
    }

    private void activate(UnitOfWork.Transaction tx, UUID id, boolean required)
            throws SQLException {
        var c = DatabaseAccess.context(tx);
        var j = job(c, id);
        if (!Set.of("AWAITING", "PAUSED").contains(j.state())) return;
        boolean funded = j.stock().stream().allMatch(s -> s.escrow() >= s.needed());
        if (required) require(funded, "Repair still needs materials");
        if (!funded) return;
        refresh(tx, id, false);
        var v = JooqBuildingRepository.current(c, j.building());
        authority(tx, j.payer(), v);
        var state = decay.refresh(tx, v.id());
        UUID currentCycle =
                c.select(BUILDING_ERAS.CYCLE_ID)
                        .from(BUILDING_ERAS)
                        .where(BUILDING_ERAS.BUILDING_ID.eq(v.id()))
                        .fetchSingle(BUILDING_ERAS.CYCLE_ID);
        UUID cycle =
                j.cycle() != null && j.cycle().equals(currentCycle)
                        ? j.cycle()
                        : state.condition() == DecayService.Condition.RUINED ? currentCycle : null;
        c.update(REPAIR_JOBS)
                .set(REPAIR_JOBS.STATE, "FUNDED")
                .set(REPAIR_JOBS.CYCLE_ID, cycle)
                .set(REPAIR_JOBS.NEXT_TICK, Math.addExact(clock.ticks(), policy.batchTicks()))
                .where(REPAIR_JOBS.JOB_ID.eq(id))
                .execute();
        event(
                c,
                eventId(id, "started"),
                j.settlement(),
                j.payer(),
                "REPAIR_STARTED",
                "job=" + id + ";building=" + j.building() + ";cycle=" + cycle,
                clock.ticks());
    }

    @Override
    public CompletableFuture<SettlementChange> cancel(UUID actor, UUID id) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var j = job(c, id);
                    require(
                            j.payer().equals(actor) || admins.test(actor),
                            "Only the payer or administrator may cancel");
                    if (!Set.of("COMPLETED", "CANCELLED").contains(j.state())) {
                        c.update(REPAIR_JOBS)
                                .set(REPAIR_JOBS.STATE, "CANCELLING")
                                .set(REPAIR_JOBS.NEXT_TICK, clock.ticks())
                                .set(REPAIR_JOBS.DETAIL, "Cancelled by " + actor)
                                .where(REPAIR_JOBS.JOB_ID.eq(id))
                                .execute();
                        for (var r :
                                c.selectFrom(REPAIR_CELLS)
                                        .where(
                                                REPAIR_CELLS
                                                        .JOB_ID
                                                        .eq(id)
                                                        .and(REPAIR_CELLS.STATE.eq("PENDING")))
                                        .fetch()) retireCell(c, id, position(r), "SUPERSEDED");
                        terminal(c, id, clock.ticks(), false, null);
                    }
                    clock.checkpoint(tx);
                    return change(
                            c,
                            j.settlement(),
                            "Repair cancellation recorded; claim unused material refund after"
                                    + " reconciliation.");
                });
    }

    @Override
    public CompletableFuture<List<Job>> due() {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var ids =
                            c.select(REPAIR_JOBS.JOB_ID)
                                    .from(REPAIR_JOBS)
                                    .where(
                                            REPAIR_JOBS
                                                    .STATE
                                                    .in("FUNDED", "CANCELLING")
                                                    .and(REPAIR_JOBS.NEXT_TICK.le(clock.ticks()))
                                                    .and(
                                                            dueCursor == null
                                                                    ? org.jooq.impl.DSL
                                                                            .noCondition()
                                                                    : REPAIR_JOBS.JOB_ID.gt(
                                                                            dueCursor)))
                                    .orderBy(REPAIR_JOBS.JOB_ID)
                                    // The runtime attempts one candidate, so advance only past one.
                                    .limit(1)
                                    .fetch(REPAIR_JOBS.JOB_ID);
                    dueCursor = ids.isEmpty() ? null : ids.getLast();
                    var jobs = new ArrayList<Job>();
                    for (var id : ids) jobs.add(job(c, id));
                    return List.copyOf(jobs);
                });
    }

    @Override
    public CompletableFuture<Work> work(UUID id) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var j = job(c, id);
                    var pending =
                            c.selectFrom(REPAIR_OPERATIONS)
                                    .where(
                                            REPAIR_OPERATIONS
                                                    .JOB_ID
                                                    .eq(id)
                                                    .and(REPAIR_OPERATIONS.STATE.eq("PREPARED")))
                                    .orderBy(REPAIR_OPERATIONS.SEQUENCE)
                                    .fetchOne();
                    if (pending != null)
                        return new Work(
                                j,
                                null,
                                operation(pending),
                                change(c, j.settlement(), "Repair reconciliation pending."));
                    if (j.state().equals("CANCELLING")) {
                        terminal(c, id, clock.ticks(), false, null);
                        return new Work(
                                job(c, id),
                                null,
                                null,
                                change(c, j.settlement(), "Repair cancelled."));
                    }
                    if (!j.state().equals("FUNDED")
                            || !clock.advancing()
                            || j.nextTick() > clock.ticks())
                        return new Work(
                                j, null, null, change(c, j.settlement(), "Repair is waiting."));
                    try {
                        refresh(tx, id, false);
                    } catch (SettlementException blocked) {
                        c.update(REPAIR_JOBS)
                                .set(REPAIR_JOBS.STATE, "PAUSED")
                                .set(REPAIR_JOBS.DETAIL, blocked.getMessage())
                                .where(REPAIR_JOBS.JOB_ID.eq(id))
                                .execute();
                        return new Work(
                                job(c, id),
                                null,
                                null,
                                change(
                                        c,
                                        j.settlement(),
                                        "Repair paused; inspect and resume at the station."));
                    }
                    j = job(c, id);
                    var v = JooqBuildingRepository.current(c, j.building());
                    var s = authority(tx, j.payer(), v);
                    var rows =
                            c.selectFrom(REPAIR_CELLS)
                                    .where(
                                            REPAIR_CELLS
                                                    .JOB_ID
                                                    .eq(id)
                                                    .and(REPAIR_CELLS.STATE.eq("PENDING")))
                                    .orderBy(REPAIR_CELLS.Y.desc(), REPAIR_CELLS.X, REPAIR_CELLS.Z)
                                    .limit(64)
                                    .fetch();
                    var cells = new ArrayList<Change>();
                    Integer chunkX = null, chunkZ = null;
                    for (var row : rows) {
                        var p = position(row);
                        if (chunkX == null) {
                            chunkX = p.x() >> 4;
                            chunkZ = p.z() >> 4;
                        }
                        if ((p.x() >> 4) != chunkX || (p.z() >> 4) != chunkZ) continue;
                        allow(tx, j.payer(), s, p, ParcelAction.BUILD);
                        cells.add(
                                new Change(
                                        p,
                                        row.get(REPAIR_CELLS.EXPECTED),
                                        row.get(REPAIR_CELLS.TARGET)));
                        if (cells.size() == 8) break;
                    }
                    if (cells.isEmpty()) {
                        terminal(c, id, clock.ticks(), false, null);
                        return new Work(
                                job(c, id),
                                null,
                                null,
                                change(c, j.settlement(), "Repair plan complete."));
                    }
                    return new Work(
                            j,
                            new Offer(
                                    j,
                                    v,
                                    new BuildingService.Stamp(
                                            v.world(),
                                            s.ownershipRevision(),
                                            s.chunkRevision(),
                                            security(c, s.id())),
                                    s.owner(),
                                    cells),
                            null,
                            change(c, j.settlement(), "Repair scheduled."));
                });
    }

    @Override
    public CompletableFuture<Operation> intent(Offer offer, List<Change> cells) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var j = job(c, offer.job().id());
                    var v = JooqBuildingRepository.current(c, j.building());
                    require(
                            j.state().equals("FUNDED")
                                    && clock.advancing()
                                    && j.nextTick() <= clock.ticks(),
                            "Repair is not ready");
                    require(
                            v.revision() == offer.building().revision()
                                    && context(tx, j, v, offer.stamp(), offer.owner()),
                            "Repair authorization changed");
                    require(
                            !c.fetchExists(
                                    c.selectOne()
                                            .from(REPAIR_OPERATIONS)
                                            .where(
                                                    REPAIR_OPERATIONS
                                                            .JOB_ID
                                                            .eq(j.id())
                                                            .and(
                                                                    REPAIR_OPERATIONS.STATE.eq(
                                                                            "PREPARED")))),
                            "Repair operation already pending");
                    var baseline = JooqBuildingRepository.baseline(c, v.id(), v.revision());
                    var first = cells.getFirst().position();
                    var unique = new HashSet<Position>();
                    for (var cell : cells) {
                        var p = cell.position();
                        var r = c.selectFrom(REPAIR_CELLS).where(cellKey(j.id(), p)).fetchOne();
                        require(
                                r != null
                                        && r.get(REPAIR_CELLS.STATE).equals("PENDING")
                                        && unique.add(p)
                                        && (p.x() >> 4) == (first.x() >> 4)
                                        && (p.z() >> 4) == (first.z() >> 4),
                                "Invalid Repair batch cells");
                        require(
                                v.permitsAutomaticMutation(p, baseline.at(p))
                                        && cell.target().equals(baseline.at(p).data())
                                        && cell.target().equals(r.get(REPAIR_CELLS.TARGET))
                                        && (cell.expected().equals(r.get(REPAIR_CELLS.EXPECTED))
                                                || cell.expected().equals(cell.target())),
                                "Repair cell changed");
                        RepairMaterials.material(cell.target());
                    }
                    UUID op = UUID.randomUUID();
                    var t = REPAIR_OPERATIONS;
                    c.insertInto(t)
                            .set(t.OPERATION_ID, op)
                            .set(t.JOB_ID, j.id())
                            .set(t.BUILDING_ID, v.id())
                            .set(t.SETTLEMENT_ID, j.settlement())
                            .set(t.WORLD_INSTANCE_ID, j.world())
                            .set(t.ACTOR_UUID, j.payer())
                            .set(t.BASELINE_REVISION, v.revision())
                            .set(t.OWNER_UUID, offer.owner())
                            .set(t.OWNERSHIP_REVISION, offer.stamp().ownership())
                            .set(t.CHUNK_REVISION, offer.stamp().claims())
                            .set(t.SECURITY_REVISION, offer.stamp().security())
                            .set(t.STATE, "PREPARED")
                            .set(t.CHUNK_X, (long) (first.x() >> 4))
                            .set(t.CHUNK_Z, (long) (first.z() >> 4))
                            .set(t.PAYLOAD, DecayPlan.encode(cells))
                            .set(t.GAME_TICK, clock.ticks())
                            .execute();
                    for (var cell : cells)
                        c.update(REPAIR_CELLS)
                                .set(REPAIR_CELLS.STATE, "PREPARED")
                                .set(REPAIR_CELLS.OPERATION_ID, op)
                                .where(cellKey(j.id(), cell.position()))
                                .execute();
                    clock.checkpoint(tx);
                    return operation(c.selectFrom(t).where(t.OPERATION_ID.eq(op)).fetchSingle());
                });
    }

    @Override
    public CompletableFuture<Permit> authorize(UUID id) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var op = requiredOperation(c, id);
                    var j = job(c, op.job());
                    var v = JooqBuildingRepository.current(c, j.building());
                    boolean valid =
                            !v.retired()
                                    && v.world().equals(op.world())
                                    && clock.advancing()
                                    && (op.state().equals("RESOLVED")
                                            || v.revision() == op.revision()
                                                    && j.state().equals("FUNDED"));
                    try {
                        valid =
                                valid
                                        && context(
                                                tx,
                                                j,
                                                v,
                                                new BuildingService.Stamp(
                                                        op.world(),
                                                        op.ownership(),
                                                        op.claims(),
                                                        op.security()),
                                                op.owner());
                    } catch (SettlementException ignored) {
                        valid = false;
                    }
                    var baseline = JooqBuildingRepository.baseline(c, v.id(), v.revision());
                    var allowed = new HashSet<Position>();
                    for (var cell : op.cells()) {
                        var row =
                                c.selectFrom(REPAIR_CELLS)
                                        .where(cellKey(j.id(), cell.position()))
                                        .fetchOne();
                        if (row == null || !op.id().equals(row.get(REPAIR_CELLS.OPERATION_ID)))
                            continue;
                        var currentDamage = damage(c, v.id(), cell.position());
                        if (currentDamage != null
                                && !currentDamage
                                        .get(DECAY_DAMAGE.OPERATION_ID)
                                        .equals(row.get(REPAIR_CELLS.DECAY_OPERATION_ID))) continue;
                        boolean recorded =
                                Set.of("APPLIED", "SKIPPED").contains(row.get(REPAIR_CELLS.STATE));
                        if ((recorded
                                        || row.get(REPAIR_CELLS.STATE).equals("PREPARED")
                                                && j.state().equals("FUNDED"))
                                && baseline.bounds().contains(cell.position())
                                && baseline.at(cell.position()).data().equals(cell.target())
                                && v.permitsAutomaticMutation(
                                        cell.position(), baseline.at(cell.position()))) {
                            RepairMaterials.material(cell.target());
                            allowed.add(cell.position());
                        }
                    }
                    return new Permit(op, valid, valid ? "" : "Repair context changed", allowed);
                });
    }

    @Override
    public CompletableFuture<List<Operation>> chunk(UUID world, int x, int z, long after) {
        return db.transact(
                tx ->
                        DatabaseAccess.context(tx)
                                .selectFrom(REPAIR_OPERATIONS)
                                .where(
                                        REPAIR_OPERATIONS
                                                .WORLD_INSTANCE_ID
                                                .eq(world)
                                                .and(REPAIR_OPERATIONS.CHUNK_X.eq((long) x))
                                                .and(REPAIR_OPERATIONS.CHUNK_Z.eq((long) z))
                                                .and(REPAIR_OPERATIONS.SEQUENCE.gt(after)))
                                .orderBy(REPAIR_OPERATIONS.SEQUENCE)
                                .limit(32)
                                .fetch(JooqRepairRepository::operation));
    }

    @Override
    public CompletableFuture<SettlementChange> finish(
            UUID id,
            Map<Position, BuildingCell> actual,
            boolean attempted,
            Set<Position> conflicts) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var op = requiredOperation(c, id);
                    var j = job(c, op.job());
                    var v = JooqBuildingRepository.current(c, j.building());
                    var base = JooqBuildingRepository.baseline(c, v.id(), v.revision());
                    UUID cycle =
                            c.select(BUILDING_ERAS.CYCLE_ID)
                                    .from(BUILDING_ERAS)
                                    .where(BUILDING_ERAS.BUILDING_ID.eq(v.id()))
                                    .fetchSingle(BUILDING_ERAS.CYCLE_ID);
                    boolean progressed = false;
                    for (var change : op.cells()) {
                        var p = change.position();
                        var row = c.selectFrom(REPAIR_CELLS).where(cellKey(j.id(), p)).fetchOne();
                        if (row == null
                                || !id.equals(row.get(REPAIR_CELLS.OPERATION_ID))
                                || !row.get(REPAIR_CELLS.STATE).equals("PREPARED")) continue;
                        boolean current =
                                !v.retired()
                                        && base.bounds().contains(p)
                                        && base.at(p).data().equals(change.target())
                                        && v.permitsAutomaticMutation(p, base.at(p));
                        if (!current) {
                            retireCell(c, j.id(), p, "SUPERSEDED");
                            continue;
                        }
                        var cell = actual.get(p);
                        if (cell == null) {
                            releaseCell(c, j.id(), p);
                            continue;
                        }
                        if (conflicts.contains(p)) {
                            retireCell(c, j.id(), p, "CONFLICT");
                            continue;
                        }
                        if (cell.data().equals(change.target())) {
                            progressed = true;
                            String material = row.get(REPAIR_CELLS.MATERIAL);
                            boolean charge =
                                    material != null && !change.expected().equals(change.target());
                            if (charge) {
                                var stock =
                                        c.selectFrom(REPAIR_MATERIALS)
                                                .where(stockKey(j.id(), material))
                                                .fetchSingle();
                                require(
                                        stock.get(REPAIR_MATERIALS.ESCROW) > 0,
                                        "Reserved Repair material is missing");
                                c.update(REPAIR_MATERIALS)
                                        .set(
                                                REPAIR_MATERIALS.ESCROW,
                                                REPAIR_MATERIALS.ESCROW.sub(1L))
                                        .set(
                                                REPAIR_MATERIALS.CONSUMED,
                                                REPAIR_MATERIALS.CONSUMED.add(1L))
                                        .where(stockKey(j.id(), material))
                                        .execute();
                            }
                            retireCell(
                                    c,
                                    j.id(),
                                    p,
                                    charge || material == null ? "APPLIED" : "SKIPPED");
                            c.deleteFrom(DECAY_DAMAGE)
                                    .where(
                                            DECAY_DAMAGE
                                                    .BUILDING_ID
                                                    .eq(v.id())
                                                    .and(DECAY_DAMAGE.X.eq((long) p.x()))
                                                    .and(DECAY_DAMAGE.Y.eq((long) p.y()))
                                                    .and(DECAY_DAMAGE.Z.eq((long) p.z())))
                                    .execute();
                        } else if (!attempted || cell.data().equals(change.expected()))
                            releaseCell(c, j.id(), p);
                        else retireCell(c, j.id(), p, "CONFLICT");
                    }
                    c.update(REPAIR_OPERATIONS)
                            .set(REPAIR_OPERATIONS.STATE, "RESOLVED")
                            .where(REPAIR_OPERATIONS.OPERATION_ID.eq(id))
                            .execute();
                    if (progressed) {
                        buildingWriter.repaired(tx, c, j.payer(), v.id());
                        c.update(REPAIR_JOBS)
                                .set(
                                        REPAIR_JOBS.BASELINE_REVISION,
                                        JooqBuildingRepository.current(c, v.id()).revision())
                                .where(REPAIR_JOBS.JOB_ID.eq(j.id()))
                                .execute();
                    }
                    long delay =
                            c.select(REPAIR_JOBS.BATCH_TICKS)
                                    .from(REPAIR_JOBS)
                                    .where(REPAIR_JOBS.JOB_ID.eq(j.id()))
                                    .fetchSingle(REPAIR_JOBS.BATCH_TICKS);
                    c.update(REPAIR_JOBS)
                            .set(REPAIR_JOBS.NEXT_TICK, Math.addExact(clock.ticks(), delay))
                            .where(REPAIR_JOBS.JOB_ID.eq(j.id()))
                            .execute();
                    decay.refresh(tx, v.id());
                    terminal(c, j.id(), clock.ticks(), attempted, cycle);
                    clock.checkpoint(tx);
                    return change(c, j.settlement(), "Repair batch recorded.");
                });
    }

    @Override
    public CompletableFuture<SettlementChange> defer(UUID id) {
        return db.transact(
                tx -> {
                    var c = DatabaseAccess.context(tx);
                    var j = job(c, id);
                    c.update(REPAIR_JOBS)
                            .set(
                                    REPAIR_JOBS.NEXT_TICK,
                                    Math.addExact(clock.ticks(), policy.batchTicks()))
                            .where(REPAIR_JOBS.JOB_ID.eq(id))
                            .execute();
                    return change(
                            c,
                            j.settlement(),
                            "Repair waits for its station and naturally loaded work.");
                });
    }

    private static void terminal(
            DSLContext c, UUID id, long tick, boolean repairCompletion, UUID priorCycle) {
        var j = c.selectFrom(REPAIR_JOBS).where(REPAIR_JOBS.JOB_ID.eq(id)).fetchSingle();
        if (Set.of("COMPLETED", "CANCELLED").contains(j.get(REPAIR_JOBS.STATE))) return;
        boolean ops =
                c.fetchExists(
                        c.selectOne()
                                .from(REPAIR_OPERATIONS)
                                .where(
                                        REPAIR_OPERATIONS
                                                .JOB_ID
                                                .eq(id)
                                                .and(REPAIR_OPERATIONS.STATE.eq("PREPARED"))));
        boolean transfers =
                c.fetchExists(
                        c.selectOne()
                                .from(REPAIR_TRANSFERS)
                                .where(
                                        REPAIR_TRANSFERS
                                                .JOB_ID
                                                .eq(id)
                                                .and(REPAIR_TRANSFERS.STATE.eq("PREPARED"))));
        if (ops || transfers) return;
        if (c.fetchCount(REPAIR_CELLS, REPAIR_CELLS.JOB_ID.eq(id)) == 0
                && !j.get(REPAIR_JOBS.STATE).equals("CANCELLING")) return;
        boolean cancel = j.get(REPAIR_JOBS.STATE).equals("CANCELLING");
        if (cancel)
            for (var r :
                    c.selectFrom(REPAIR_CELLS)
                            .where(REPAIR_CELLS.JOB_ID.eq(id).and(REPAIR_CELLS.STATE.eq("PENDING")))
                            .fetch()) retireCell(c, id, position(r), "SUPERSEDED");
        if (remaining(c, id) > 0) return;
        c.update(REPAIR_JOBS)
                .set(REPAIR_JOBS.STATE, cancel ? "CANCELLED" : "COMPLETED")
                .set(REPAIR_JOBS.COMPLETED_TICK, tick)
                .where(REPAIR_JOBS.JOB_ID.eq(id))
                .execute();
        UUID building = j.get(REPAIR_JOBS.BUILDING_ID),
                settlement = j.get(REPAIR_JOBS.SETTLEMENT_ID);
        event(
                c,
                eventId(id, cancel ? "cancelled" : "completed"),
                settlement,
                j.get(REPAIR_JOBS.PAYER_UUID),
                cancel ? "REPAIR_CANCELLED" : "REPAIR_COMPLETED",
                "job="
                        + id
                        + ";building="
                        + building
                        + ";remainingDamage="
                        + c.fetchCount(DECAY_DAMAGE, DECAY_DAMAGE.BUILDING_ID.eq(building)),
                tick);
        UUID cycle = j.get(REPAIR_JOBS.CYCLE_ID);
        long consumed =
                c.select(
                                org.jooq.impl.DSL.coalesce(
                                        org.jooq.impl.DSL.sum(REPAIR_MATERIALS.CONSUMED),
                                        java.math.BigDecimal.ZERO))
                        .from(REPAIR_MATERIALS)
                        .where(REPAIR_MATERIALS.JOB_ID.eq(id))
                        .fetchSingle(0, Long.class);
        if (!cancel
                && repairCompletion
                && cycle != null
                && cycle.equals(priorCycle)
                && consumed > 0
                && !c.fetchExists(
                        c.selectOne()
                                .from(DECAY_DAMAGE)
                                .where(DECAY_DAMAGE.BUILDING_ID.eq(building)))
                && !c.fetchExists(
                        c.selectOne()
                                .from(REPAIR_ERA_AWARDS)
                                .where(REPAIR_ERA_AWARDS.CYCLE_ID.eq(cycle)))) {
            era(c, building);
            long next =
                    Math.addExact(
                            c.select(BUILDING_ERAS.ERA)
                                    .from(BUILDING_ERAS)
                                    .where(BUILDING_ERAS.BUILDING_ID.eq(building))
                                    .fetchSingle(BUILDING_ERAS.ERA),
                            1);
            c.insertInto(REPAIR_ERA_AWARDS)
                    .set(REPAIR_ERA_AWARDS.CYCLE_ID, cycle)
                    .set(REPAIR_ERA_AWARDS.BUILDING_ID, building)
                    .set(REPAIR_ERA_AWARDS.JOB_ID, id)
                    .set(REPAIR_ERA_AWARDS.ERA, next)
                    .set(REPAIR_ERA_AWARDS.GAME_TICK, tick)
                    .execute();
            c.update(BUILDING_ERAS)
                    .set(BUILDING_ERAS.ERA, next)
                    .setNull(BUILDING_ERAS.CYCLE_ID)
                    .where(BUILDING_ERAS.BUILDING_ID.eq(building))
                    .execute();
            event(
                    c,
                    eventId(id, "era"),
                    settlement,
                    j.get(REPAIR_JOBS.PAYER_UUID),
                    "ERA_ADVANCED",
                    "job=" + id + ";building=" + building + ";cycle=" + cycle + ";era=" + next,
                    tick);
        }
    }

    private SettlementView authority(UnitOfWork.Transaction tx, UUID actor, BuildingService.View v)
            throws SQLException {
        var c = DatabaseAccess.context(tx);
        require(
                v.ownership() == BuildingService.Ownership.CLAIMED,
                "Claim the whole wild Building before preparing Repair");
        var s = JooqSettlementRepository.load(c, v.settlement());
        require(
                s != null && !v.retired() && v.world().equals(s.worldInstanceId()),
                "Building is not in an owned active Settlement");
        require(
                admins.test(actor) || !GovernanceQueries.banned(tx, s.id(), actor),
                "Player is banned");
        return s;
    }

    private void allow(
            UnitOfWork.Transaction tx,
            UUID actor,
            SettlementView s,
            Position p,
            ParcelAction action)
            throws SQLException {
        require(
                s.chunks().contains(new ChunkPosition(p.x() >> 4, p.z() >> 4)),
                "Repair Claim changed");
        if (admins.test(actor)) return;
        var parcel = GovernanceQueries.at(tx, s.id(), ParcelCell.atBlock(p.x(), p.y(), p.z()));
        require(
                parcel == null
                        ? s.role(actor).canBuild()
                        : parcel.allows(actor, s.role(actor), action),
                "Repair permission denied");
    }

    private boolean context(
            UnitOfWork.Transaction tx,
            Job j,
            BuildingService.View v,
            BuildingService.Stamp stamp,
            UUID owner)
            throws SQLException {
        var s = authority(tx, j.payer(), v);
        if (!Objects.equals(v.station(), j.station())
                || !v.world().equals(stamp.world())
                || !s.owner().equals(owner)
                || s.ownershipRevision() != stamp.ownership()
                || s.chunkRevision() != stamp.claims()
                || security(DatabaseAccess.context(tx), s.id()) != stamp.security()) return false;
        allow(tx, j.payer(), s, j.station(), ParcelAction.INTERACT);
        for (var r :
                DatabaseAccess.context(tx)
                        .selectFrom(REPAIR_CELLS)
                        .where(
                                REPAIR_CELLS
                                        .JOB_ID
                                        .eq(j.id())
                                        .and(REPAIR_CELLS.STATE.in("PENDING", "PREPARED")))
                        .fetch()) allow(tx, j.payer(), s, position(r), ParcelAction.BUILD);
        return true;
    }

    private static long security(DSLContext c, UUID id) {
        return c.select(SETTLEMENT_DETAILS.SECURITY_REVISION)
                .from(SETTLEMENT_DETAILS)
                .where(SETTLEMENT_DETAILS.SETTLEMENT_ID.eq(id))
                .fetchSingle(SETTLEMENT_DETAILS.SECURITY_REVISION);
    }

    private static org.jooq.Condition cellKey(UUID job, Position p) {
        return REPAIR_CELLS
                .JOB_ID
                .eq(job)
                .and(REPAIR_CELLS.X.eq((long) p.x()))
                .and(REPAIR_CELLS.Y.eq((long) p.y()))
                .and(REPAIR_CELLS.Z.eq((long) p.z()));
    }

    private static org.jooq.Condition stockKey(UUID job, String material) {
        return REPAIR_MATERIALS.JOB_ID.eq(job).and(REPAIR_MATERIALS.MATERIAL.eq(material));
    }

    private static Record damage(DSLContext c, UUID building, Position p) {
        return c.selectFrom(DECAY_DAMAGE)
                .where(
                        DECAY_DAMAGE
                                .BUILDING_ID
                                .eq(building)
                                .and(DECAY_DAMAGE.X.eq((long) p.x()))
                                .and(DECAY_DAMAGE.Y.eq((long) p.y()))
                                .and(DECAY_DAMAGE.Z.eq((long) p.z())))
                .fetchOne();
    }

    private static Position position(Record r) {
        return new Position(
                Math.toIntExact(r.get(REPAIR_CELLS.X)),
                Math.toIntExact(r.get(REPAIR_CELLS.Y)),
                Math.toIntExact(r.get(REPAIR_CELLS.Z)));
    }

    private static Position damagePosition(Record r) {
        return new Position(
                Math.toIntExact(r.get(DECAY_DAMAGE.X)),
                Math.toIntExact(r.get(DECAY_DAMAGE.Y)),
                Math.toIntExact(r.get(DECAY_DAMAGE.Z)));
    }

    private static Position station(Record r) {
        return new Position(
                Math.toIntExact(r.get(REPAIR_JOBS.STATION_X)),
                Math.toIntExact(r.get(REPAIR_JOBS.STATION_Y)),
                Math.toIntExact(r.get(REPAIR_JOBS.STATION_Z)));
    }

    private static int remaining(DSLContext c, UUID job) {
        return c.fetchCount(
                REPAIR_CELLS,
                REPAIR_CELLS.JOB_ID.eq(job).and(REPAIR_CELLS.STATE.in("PENDING", "PREPARED")));
    }

    private static void retireCell(DSLContext c, UUID job, Position p, String state) {
        var row = c.selectFrom(REPAIR_CELLS).where(cellKey(job, p)).fetchOne();
        if (row == null || !Set.of("PENDING", "PREPARED").contains(row.get(REPAIR_CELLS.STATE)))
            return;
        String material = row.get(REPAIR_CELLS.MATERIAL);
        if (material != null)
            c.update(REPAIR_MATERIALS)
                    .set(REPAIR_MATERIALS.NEEDED, REPAIR_MATERIALS.NEEDED.sub(1L))
                    .where(stockKey(job, material))
                    .execute();
        c.update(REPAIR_CELLS).set(REPAIR_CELLS.STATE, state).where(cellKey(job, p)).execute();
    }

    private static void releaseCell(DSLContext c, UUID job, Position p) {
        c.update(REPAIR_CELLS)
                .set(REPAIR_CELLS.STATE, "PENDING")
                .setNull(REPAIR_CELLS.OPERATION_ID)
                .where(cellKey(job, p))
                .execute();
    }

    private static Job job(DSLContext c, UUID id) {
        var t = REPAIR_JOBS;
        var row = c.selectFrom(t).where(t.JOB_ID.eq(id)).fetchOne();
        require(row != null, "Repair job not found");
        var stock =
                c.selectFrom(REPAIR_MATERIALS)
                        .where(REPAIR_MATERIALS.JOB_ID.eq(id))
                        .orderBy(REPAIR_MATERIALS.MATERIAL)
                        .fetch(
                                r ->
                                        new Stock(
                                                r.get(REPAIR_MATERIALS.MATERIAL),
                                                r.get(REPAIR_MATERIALS.NEEDED),
                                                r.get(REPAIR_MATERIALS.ESCROW),
                                                r.get(REPAIR_MATERIALS.CONSUMED),
                                                r.get(REPAIR_MATERIALS.REFUNDED)));
        long era =
                c.select(BUILDING_ERAS.ERA)
                        .from(BUILDING_ERAS)
                        .where(BUILDING_ERAS.BUILDING_ID.eq(row.get(t.BUILDING_ID)))
                        .fetchSingle(BUILDING_ERAS.ERA);
        return new Job(
                id,
                row.get(t.BUILDING_ID),
                row.get(t.SETTLEMENT_ID),
                row.get(t.WORLD_INSTANCE_ID),
                row.get(t.PAYER_UUID),
                row.get(t.STATE),
                row.get(t.BASELINE_REVISION),
                station(row),
                row.get(t.CYCLE_ID),
                row.get(t.NEXT_TICK),
                remaining(c, id),
                era,
                stock);
    }

    private static Transfer requiredTransfer(DSLContext c, UUID id) {
        var row =
                c.selectFrom(REPAIR_TRANSFERS)
                        .where(REPAIR_TRANSFERS.TRANSFER_ID.eq(id))
                        .fetchOne();
        require(row != null, "Repair transfer not found");
        return transfer(row);
    }

    private static Transfer transfer(Record r) {
        var t = REPAIR_TRANSFERS;
        return new Transfer(
                r.get(t.SEQUENCE),
                r.get(t.TRANSFER_ID),
                r.get(t.JOB_ID),
                r.get(t.PAYER_UUID),
                Direction.valueOf(r.get(t.DIRECTION)),
                r.get(t.STATE),
                RepairMaterials.decode(r.get(t.PAYLOAD)));
    }

    private static Operation requiredOperation(DSLContext c, UUID id) {
        var row =
                c.selectFrom(REPAIR_OPERATIONS)
                        .where(REPAIR_OPERATIONS.OPERATION_ID.eq(id))
                        .fetchOne();
        require(row != null, "Repair operation not found");
        return operation(row);
    }

    private static Operation operation(Record r) {
        var t = REPAIR_OPERATIONS;
        return new Operation(
                r.get(t.SEQUENCE),
                r.get(t.OPERATION_ID),
                r.get(t.JOB_ID),
                r.get(t.BUILDING_ID),
                r.get(t.SETTLEMENT_ID),
                r.get(t.WORLD_INSTANCE_ID),
                r.get(t.ACTOR_UUID),
                r.get(t.BASELINE_REVISION),
                r.get(t.OWNER_UUID),
                r.get(t.OWNERSHIP_REVISION),
                r.get(t.CHUNK_REVISION),
                r.get(t.SECURITY_REVISION),
                r.get(t.STATE),
                Math.toIntExact(r.get(t.CHUNK_X)),
                Math.toIntExact(r.get(t.CHUNK_Z)),
                DecayPlan.decode(r.get(t.PAYLOAD)));
    }

    private static UUID eventId(UUID id, String phase) {
        return UUID.nameUUIDFromBytes(
                ("repair:" + id + ":" + phase).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void event(
            DSLContext c,
            UUID id,
            UUID settlement,
            UUID actor,
            String type,
            String detail,
            long tick) {
        c.insertInto(SETTLEMENT_EVENTS)
                .set(SETTLEMENT_EVENTS.EVENT_ID, id)
                .set(SETTLEMENT_EVENTS.SETTLEMENT_ID, settlement)
                .set(SETTLEMENT_EVENTS.ACTOR_UUID, actor)
                .set(SETTLEMENT_EVENTS.TYPE, type)
                .set(SETTLEMENT_EVENTS.DETAIL, detail)
                .set(SETTLEMENT_EVENTS.GAME_TICK, tick)
                .set(SETTLEMENT_EVENTS.OCCURRED_AT, Instant.now().toEpochMilli())
                .set(SETTLEMENT_EVENTS.DELIVERED, 0L)
                .onConflict(SETTLEMENT_EVENTS.EVENT_ID)
                .doNothing()
                .execute();
    }

    private static SettlementChange change(DSLContext c, UUID id, String message)
            throws SQLException {
        return new SettlementChange(id, JooqSettlementRepository.load(c, id), null, message);
    }
}
