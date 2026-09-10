package xdu.undarubra.esmp.civilization.activity;

import xdu.undarubra.esmp.civilization.settlement.*;
import xdu.undarubra.esmp.civilization.settlement.SettlementException;
import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.time.GameClock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Owning SQL service: only credited actions write scores; reads project elapsed game time. */
public final class ActivityService {
    private final WorldStore database;
    private final GameClock clock;
    private final ActivityPolicy policy;
    private final java.util.function.Predicate<UUID> administrator;

    private record Value(
            double score,
            long anchor,
            Long inactive,
            long day,
            double credited,
            Long last,
            String category) {}

    public record PauseInterval(long fromTick, Long untilTick) {}

    public ActivityService(
            WorldStore database,
            GameClock clock,
            ActivityPolicy policy,
            java.util.function.Predicate<UUID> administrator) {
        this.database = database;
        this.clock = clock;
        this.policy = policy;
        this.administrator = administrator;
    }

    public CompletableFuture<Void> initialize() {
        return database.transact(
                c -> {
                    execute(
                            c,
                            "CREATE TABLE IF NOT EXISTS activity_policy(id INTEGER PRIMARY KEY"
                                + " CHECK(id=1),definition TEXT NOT NULL)");
                    try (var query = c.createStatement();
                            var rows =
                                    query.executeQuery(
                                            "SELECT definition FROM activity_policy WHERE id=1")) {
                        if (rows.next()
                                && !rows.getString(1)
                                        .replace("visitorCredit=", "publicCredit=")
                                        .equals(policy.toString())) {
                            throw new SQLException(
                                    "Activity policy changed: existing scores require an explicit"
                                        + " policy migration; restore prior settings");
                        }
                    }
                    execute(
                            c,
                            "INSERT INTO activity_policy VALUES(1,?) ON CONFLICT(id) DO UPDATE SET"
                                + " definition=excluded.definition",
                            policy.toString());
                    execute(
                            c,
                            "CREATE TABLE IF NOT EXISTS settlement_activity(settlement_id TEXT"
                                + " PRIMARY KEY REFERENCES settlements(settlement_id),score REAL"
                                + " NOT NULL,anchor_tick INTEGER NOT NULL,inactive_tick INTEGER,day"
                                + " INTEGER NOT NULL,credited REAL NOT NULL,last_tick"
                                + " INTEGER,category TEXT NOT NULL)");
                    execute(
                            c,
                            "CREATE TABLE IF NOT EXISTS activity_actors(settlement_id TEXT NOT NULL"
                                + " REFERENCES settlements(settlement_id),actor_uuid TEXT NOT"
                                + " NULL,day INTEGER NOT NULL,credited REAL NOT NULL,build_tick"
                                + " INTEGER,interaction_tick INTEGER,PRIMARY"
                                + " KEY(settlement_id,actor_uuid))");
                    execute(
                            c,
                            "CREATE TABLE IF NOT EXISTS activity_pauses(settlement_id TEXT NOT NULL"
                                + " REFERENCES settlements(settlement_id),from_tick INTEGER NOT"
                                + " NULL,until_tick INTEGER NOT NULL,PRIMARY"
                                + " KEY(settlement_id,from_tick))");
                    return null;
                });
    }

    public CompletableFuture<Void> contribute(List<ActivityContribution> observations) {
        if (observations.size() > 256) {
            throw new IllegalArgumentException("Activity batch exceeds 256 observations");
        }
        List<ActivityContribution> captured = List.copyOf(observations);
        return database.transact(
                c -> {
                    if (!clock.advancing()) {
                        return null;
                    }
                    long now = clock.ticks();
                    for (ActivityContribution action : captured) {
                        credit(c, action, now);
                    }
                    clock.checkpoint(c);
                    return null;
                });
    }

    public CompletableFuture<ActivityStatus> status(UUID id) {
        return database.transact(
                c -> {
                    long now = clock.ticks();
                    Value value = project(load(c, id), now);
                    return new ActivityStatus(
                            value.score(),
                            value.inactive() == null
                                    ? ActivityStatus.State.ACTIVE
                                    : ActivityStatus.State.INACTIVE,
                            value.inactive(),
                            paused(value.inactive(), now),
                            value.inactive() == null
                                    ? 0
                                    : Math.max(0, policy.recoveryThreshold() - value.score()),
                            value.last(),
                            value.day() == now / GameClock.TICKS_PER_DAY ? value.credited() : 0,
                            now,
                            value.category());
                });
    }

    /**
     * Closed and current pause intervals; future production must exclude them, not just inspect
     * today's boolean.
     */
    public CompletableFuture<List<PauseInterval>> pauseIntervals(UUID id) {
        return database.transact(
                c -> {
                    long now = clock.ticks();
                    Value value = project(load(c, id), now);
                    var result = new ArrayList<PauseInterval>();
                    try (var query =
                                    prepare(
                                            c,
                                            "SELECT from_tick,until_tick FROM activity_pauses WHERE"
                                                + " settlement_id=? ORDER BY from_tick",
                                            id);
                            var rows = query.executeQuery()) {
                        while (rows.next()) {
                            result.add(new PauseInterval(rows.getLong(1), rows.getLong(2)));
                        }
                    }
                    if (paused(value.inactive(), now)) {
                        result.add(
                                new PauseInterval(
                                        Math.addExact(value.inactive(), policy.graceTicks()),
                                        null));
                    }
                    return List.copyOf(result);
                });
    }

    private void credit(Connection c, ActivityContribution a, long now) throws SQLException {
        Role role;
        try (var query =
                        prepare(
                                c,
                                """
                                SELECT s.owner_uuid,m.role FROM settlements s
                                JOIN settlement_details d ON d.settlement_id=s.settlement_id AND d.security_revision=?
                                JOIN settlement_chunks ch ON ch.settlement_id=s.settlement_id AND ch.world_instance_id=? AND ch.chunk_x=? AND ch.chunk_z=?
                                LEFT JOIN settlement_members m ON m.settlement_id=s.settlement_id AND m.player_uuid=?
                                WHERE s.settlement_id=? AND s.world_instance_id=? AND s.status='OWNED' AND s.ownership_revision=? AND s.chunk_revision=?
                                """,
                                a.securityRevision(),
                                a.worldInstanceId(),
                                Math.floorDiv(a.blockX(), 16),
                                Math.floorDiv(a.blockZ(), 16),
                                a.actor(),
                                a.settlementId(),
                                a.worldInstanceId(),
                                a.ownershipRevision(),
                                a.chunkRevision());
                var row = query.executeQuery()) {
            if (!row.next()) return;
            role =
                    a.actor().toString().equals(row.getString(1))
                            ? Role.OWNER
                            : row.getString(2) == null
                                    ? Role.PUBLIC
                                    : Role.valueOf(row.getString(2));
        }
        if (ParcelService.banned(c, a.settlementId(), a.actor()) && !administrator.test(a.actor()))
            return;
        var parcel =
                ParcelService.at(
                        c,
                        a.settlementId(),
                        ParcelCell.atBlock(a.blockX(), a.blockY(), a.blockZ()));
        if (parcel != null) {
            if (!parcel.allows(a.actor(), role, a.action())) return;
        } else if (role == Role.PUBLIC && !(a.action() == ParcelAction.INTERACT && a.safePublic()))
            return;
        boolean publicActor = role == Role.PUBLIC;
        long day = now / GameClock.TICKS_PER_DAY;
        double actorCredit = 0;
        Long build = null, interaction = null;
        try (var query =
                        prepare(
                                c,
                                "SELECT * FROM activity_actors WHERE settlement_id=? AND"
                                    + " actor_uuid=?",
                                a.settlementId(),
                                a.actor());
                var row = query.executeQuery()) {
            if (row.next()) {
                actorCredit = row.getLong("day") == day ? row.getDouble("credited") : 0;
                build = nullable(row, "build_tick");
                interaction = nullable(row, "interaction_tick");
            }
        }
        Long last = a.category() == ActivityContribution.Category.BUILD ? build : interaction;
        if (last != null && now - last < policy.actionCooldownTicks()) {
            return;
        }
        Value value = project(load(c, a.settlementId()), now);
        double daily = value.day() == day ? value.credited() : 0;
        double amount =
                publicActor
                        ? policy.publicCredit()
                        : a.category() == ActivityContribution.Category.BUILD
                                ? policy.buildCredit()
                                : policy.interactionCredit();
        double actorCap = publicActor ? policy.playerDailyCap() / 20 : policy.playerDailyCap();
        amount =
                Math.min(
                        amount,
                        Math.min(
                                actorCap - actorCredit,
                                Math.min(
                                        policy.settlementDailyCap() - daily,
                                        policy.maxScore() - value.score())));
        if (amount <= 0) {
            return;
        }
        double nextScore = value.score() + amount;
        Long inactive = value.inactive();
        if (inactive != null && nextScore >= policy.recoveryThreshold()) {
            if (paused(inactive, now)) {
                execute(
                        c,
                        "INSERT OR IGNORE INTO activity_pauses VALUES(?,?,?)",
                        a.settlementId(),
                        Math.addExact(inactive, policy.graceTicks()),
                        now);
            }
            inactive = null;
        }
        execute(
                c,
                "INSERT INTO settlement_activity VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(settlement_id)"
                    + " DO UPDATE SET"
                    + " score=excluded.score,anchor_tick=excluded.anchor_tick,inactive_tick=excluded.inactive_tick,day=excluded.day,credited=excluded.credited,last_tick=excluded.last_tick,category=excluded.category",
                a.settlementId(),
                nextScore,
                now,
                inactive,
                day,
                daily + amount,
                now,
                a.category().name());
        if (a.category() == ActivityContribution.Category.BUILD) {
            build = now;
        } else {
            interaction = now;
        }
        execute(
                c,
                "INSERT INTO activity_actors VALUES(?,?,?,?,?,?) ON"
                    + " CONFLICT(settlement_id,actor_uuid) DO UPDATE SET"
                    + " day=excluded.day,credited=excluded.credited,build_tick=excluded.build_tick,interaction_tick=excluded.interaction_tick",
                a.settlementId(),
                a.actor(),
                day,
                actorCredit + amount,
                build,
                interaction);
    }

    private Value load(Connection c, UUID id) throws SQLException {
        try (var query =
                        prepare(
                                c,
                                "SELECT s.founded_tick,a.* FROM settlements s LEFT JOIN"
                                    + " settlement_activity a ON a.settlement_id=s.settlement_id"
                                    + " WHERE s.settlement_id=? AND s.status='OWNED'",
                                id);
                var row = query.executeQuery()) {
            if (!row.next()) {
                throw new SettlementException("Settlement is no longer owned or does not exist");
            }
            if (row.getString("settlement_id") == null) {
                return new Value(
                        policy.initialScore(),
                        row.getLong("founded_tick"),
                        null,
                        -1,
                        0,
                        null,
                        "none");
            }
            return new Value(
                    row.getDouble("score"),
                    row.getLong("anchor_tick"),
                    nullable(row, "inactive_tick"),
                    row.getLong("day"),
                    row.getDouble("credited"),
                    nullable(row, "last_tick"),
                    row.getString("category"));
        }
    }

    private Value project(Value value, long now) {
        long elapsed = Math.max(0, now - value.anchor());
        double score =
                value.score()
                        * Math.pow(.5, elapsed / (policy.halfLifeDays() * GameClock.TICKS_PER_DAY));
        Long inactive = value.inactive();
        if (inactive == null && score < policy.inactiveThreshold()) {
            double crossing =
                    policy.halfLifeDays()
                            * GameClock.TICKS_PER_DAY
                            * (Math.log(value.score() / policy.inactiveThreshold()) / Math.log(2));
            long offset = Math.max(0, (long) Math.floor(crossing) + 1);
            inactive = Math.min(now, Math.addExact(value.anchor(), offset));
        }
        return new Value(
                score,
                now,
                inactive,
                value.day(),
                value.credited(),
                value.last(),
                value.category());
    }

    private boolean paused(Long inactive, long now) {
        return inactive != null && now - inactive >= policy.graceTicks();
    }

    private static Long nullable(java.sql.ResultSet row, String key) throws SQLException {
        long value = row.getLong(key);
        return row.wasNull() ? null : value;
    }

    private static PreparedStatement prepare(Connection c, String sql, Object... args)
            throws SQLException {
        var statement = c.prepareStatement(sql);
        try {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i] instanceof UUID ? args[i].toString() : args[i]);
            }
            return statement;
        } catch (SQLException failure) {
            statement.close();
            throw failure;
        }
    }

    private static void execute(Connection c, String sql, Object... args) throws SQLException {
        try (var statement = prepare(c, sql, args)) {
            statement.executeUpdate();
        }
    }
}
