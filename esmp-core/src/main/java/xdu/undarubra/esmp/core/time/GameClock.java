package xdu.undarubra.esmp.core.time;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import xdu.undarubra.esmp.core.storage.WorldStore;

/** Neutral monotonic source-delta clock. The adapter supplies source identity and advancement policy. */
public final class GameClock {
    public static final long TICKS_PER_DAY = 24_000;
    private record Position(UUID source, long raw, long ticks, boolean advancing) { }
    private final WorldStore database;
    private volatile Position position;

    public GameClock(WorldStore database) { this.database = database; }

    public CompletableFuture<Void> initialize(UUID source, long rawTime, boolean advancing) {
        return database.transact(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS game_clock (id INTEGER PRIMARY KEY CHECK(id=1), source_uuid TEXT NOT NULL, raw_tick INTEGER NOT NULL, game_tick INTEGER NOT NULL CHECK(game_tick>=0))");
                try (var rows = statement.executeQuery("SELECT * FROM game_clock WHERE id=1")) {
                    long ticks = 0;
                    if (rows.next()) {
                        if (!source.toString().equals(rows.getString("source_uuid"))) {
                            throw new SQLException("Reckoning source changed; restore the matching Main World/database checkpoint before starting");
                        }
                        // Re-anchor on boot. Downtime is never inferred from wall time or unsaved world time.
                        ticks = rows.getLong("game_tick");
                    }
                    position = new Position(source, rawTime, ticks, advancing);
                }
            }
            checkpoint(connection);
            return null;
        });
    }

    public void sample(UUID source, long rawTime, boolean advancing) {
        Position old = requirePosition();
        if (!old.source().equals(source)) { throw new IllegalStateException("Reckoning source identity changed"); }
        long delta = advancing && old.advancing() && rawTime > old.raw()
                ? Math.subtractExact(rawTime, old.raw()) : 0;
        position = new Position(source, rawTime, Math.addExact(old.ticks(), delta), advancing);
    }

    public long ticks() { Position current = position; return current == null ? 0 : current.ticks(); }
    public boolean advancing() { Position current = position; return current != null && current.advancing(); }

    public void checkpoint(Connection connection) throws SQLException {
        Position current = requirePosition();
        try (var statement = connection.prepareStatement("INSERT INTO game_clock VALUES(1,?,?,?) ON CONFLICT(id) DO UPDATE SET source_uuid=excluded.source_uuid,raw_tick=excluded.raw_tick,game_tick=excluded.game_tick")) {
            statement.setString(1, current.source().toString());
            statement.setLong(2, current.raw());
            statement.setLong(3, current.ticks());
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Void> flush() {
        return database.transact(connection -> { checkpoint(connection); return null; });
    }

    private Position requirePosition() {
        Position current = position;
        if (current == null) { throw new IllegalStateException("Reckoning clock is not initialized"); }
        return current;
    }
}
