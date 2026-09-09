package xdu.undarubra.esmp.civilization.settlement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import xdu.undarubra.esmp.core.storage.WorldStore;

/** Owns Settlement invariants and SQL. All work runs in the core database worker. */
public final class SettlementService {
    private final WorldStore database;
    private final SettlementPolicy policy;
    private final Clock clock;

    public SettlementService(WorldStore database, SettlementPolicy policy, Clock clock) {
        this.database = database;
        this.policy = policy;
        this.clock = clock;
    }

    public CompletableFuture<List<SettlementView>> initialize() {
        return database.transact(connection -> {
            migrate(connection);
            return loadAll(connection);
        });
    }

    public CompletableFuture<SettlementChange> create(UUID actor, UUID world, ChunkPosition chunk, String rawName) {
        String name = validName(rawName);
        return database.transact(connection -> {
            requireMainWorld(connection, world);
            requireNameAvailable(connection, name, null);
            requireUnclaimed(connection, world, chunk);
            UUID id = UUID.randomUUID();
            execute(connection, """
                    INSERT INTO settlements(settlement_id,world_instance_id,name,name_key,owner_uuid,created_at,
                        ownership_revision,chunk_revision,status) VALUES(?,?,?,?,?,?,1,1,'OWNED')
                    """, id, world, name, key(name), actor, clock.millis());
            insertChunk(connection, id, world, chunk);
            event(connection, id, actor, "FOUNDED", name + " at " + chunk.x() + "," + chunk.z());
            return changed(connection, id, null, "Created " + name + " and claimed this chunk.");
        });
    }

    public CompletableFuture<SettlementChange> claim(UUID actor, UUID id, UUID world, ChunkPosition chunk) {
        return database.transact(connection -> {
            SettlementView settlement = owned(connection, id);
            require(settlement.role(actor).canManage(), "Only the Owner or a Manager may claim chunks.");
            require(settlement.worldInstanceId().equals(world), "Claims cannot cross world instances.");
            requireMainWorld(connection, world);
            requireUnclaimed(connection, world, chunk);
            require(!policy.chunkCapEnabled() || settlement.chunks().size() < policy.maxChunks(), "Settlement chunk cap reached.");
            require(neighbors(chunk).stream().anyMatch(settlement.chunks()::contains), "The new chunk must share an edge with this Settlement.");
            insertChunk(connection, id, world, chunk);
            execute(connection, "UPDATE settlements SET chunk_revision=chunk_revision+1 WHERE settlement_id=?", id);
            event(connection, id, actor, "CHUNK_CLAIMED", chunk.x() + "," + chunk.z());
            return changed(connection, id, null, "Claimed chunk for " + settlement.name() + ".");
        });
    }

    public CompletableFuture<SettlementChange> unclaim(UUID actor, UUID id, ChunkPosition chunk) {
        return database.transact(connection -> {
            SettlementView settlement = owned(connection, id);
            require(settlement.role(actor).canManage(), "Only the Owner or a Manager may unclaim chunks.");
            require(settlement.chunks().contains(chunk), "This chunk does not belong to that Settlement.");
            require(settlement.chunks().size() > 1, "The last chunk requires the Owner's explicit abandon confirmation.");
            Set<ChunkPosition> remaining = new HashSet<>(settlement.chunks());
            remaining.remove(chunk);
            require(connected(remaining), "Unclaiming this chunk would split the Settlement.");
            execute(connection, "DELETE FROM settlement_chunks WHERE settlement_id=? AND chunk_x=? AND chunk_z=?", id, chunk.x(), chunk.z());
            execute(connection, "UPDATE settlements SET chunk_revision=chunk_revision+1 WHERE settlement_id=?", id);
            event(connection, id, actor, "CHUNK_UNCLAIMED", chunk.x() + "," + chunk.z());
            return changed(connection, id, null, "Unclaimed chunk from " + settlement.name() + ".");
        });
    }

    public CompletableFuture<SettlementChange> rename(UUID actor, UUID id, String rawName) {
        String name = validName(rawName);
        return database.transact(connection -> {
            SettlementView settlement = owned(connection, id);
            require(settlement.role(actor).canManage(), "Only the Owner or a Manager may change settings.");
            requireNameAvailable(connection, name, id);
            execute(connection, "UPDATE settlements SET name=?,name_key=? WHERE settlement_id=?", name, key(name), id);
            event(connection, id, actor, "RENAMED", settlement.name() + " -> " + name);
            return changed(connection, id, null, "Renamed Settlement to " + name + ".");
        });
    }

    public CompletableFuture<SettlementChange> offer(UUID actor, UUID id, UUID recipient, SettlementOffer.Type type) {
        return database.transact(connection -> {
            SettlementView settlement = owned(connection, id);
            Role issuerRole = settlement.role(actor);
            Role targetRole = settlement.role(recipient);
            require(type == SettlementOffer.Type.INVITE ? issuerRole.canManage() : issuerRole == Role.OWNER,
                    "You do not have authority for this offer.");
            switch (type) {
                case INVITE -> require(targetRole == Role.VISITOR, "That player is already in the Settlement.");
                case PROMOTE -> require(targetRole == Role.MEMBER, "Only an existing Member can be promoted.");
                case TRANSFER -> require(targetRole == Role.MEMBER || targetRole == Role.MANAGER,
                        "The new Owner must already be a Member or Manager.");
                case ABANDON -> require(actor.equals(recipient), "Only the Owner may confirm abandonment.");
            }
            execute(connection, "UPDATE settlement_offers SET state='EXPIRED' WHERE settlement_id=? AND state='PENDING' AND expires_at<=?", id, clock.millis());
            if (type == SettlementOffer.Type.ABANDON) {
                execute(connection, "UPDATE settlement_offers SET state='CANCELLED' WHERE settlement_id=? AND type='ABANDON' AND state='PENDING'", id);
            }
            try (PreparedStatement query = prepare(connection,
                    "SELECT offer_id FROM settlement_offers WHERE settlement_id=? AND recipient_uuid=? AND type=? AND state='PENDING'", id, recipient, type);
                    ResultSet result = query.executeQuery()) {
                require(!result.next(), "A pending offer already exists; inspect /settlement offers.");
            }
            SettlementOffer offer = new SettlementOffer(UUID.randomUUID(), id, type, actor, recipient,
                    clock.instant().plus(policy.lifetime(type)), settlement.ownershipRevision(), settlement.chunkRevision());
            execute(connection, """
                    INSERT INTO settlement_offers(offer_id,settlement_id,type,issuer_uuid,recipient_uuid,expires_at,
                        ownership_revision,chunk_revision,state) VALUES(?,?,?,?,?,?,?,?,'PENDING')
                    """, offer.id(), id, type, actor, recipient, offer.expiresAt().toEpochMilli(),
                    offer.ownershipRevision(), offer.chunkRevision());
            event(connection, id, actor, type + "_OFFERED", "recipient=" + recipient + ";offer=" + offer.id());
            return new SettlementChange(id, settlement, offer,
                    type == SettlementOffer.Type.ABANDON ? "WARNING: abandonment removes all claim protection for " + settlement.name() + "."
                            : type + " offer created for " + settlement.name() + ".");
        });
    }

    public CompletableFuture<SettlementChange> accept(UUID actor, UUID offerId, boolean abandonmentConfirmation) {
        return database.transact(connection -> {
            SettlementOffer offer = pendingOffer(connection, offerId);
            require(offer.recipient().equals(actor), "Only the nominated recipient may accept this offer.");
            require((offer.type() == SettlementOffer.Type.ABANDON) == abandonmentConfirmation,
                    "Use /settlement confirm for abandonment, and /settlement accept for other offers.");
            SettlementView settlement = owned(connection, offer.settlementId());
            Role issuerRole = settlement.role(offer.issuer());
            Role targetRole = settlement.role(actor);
            if (offer.type() == SettlementOffer.Type.INVITE) {
                require(issuerRole.canManage(), "The inviter no longer has authority.");
                require(targetRole == Role.VISITOR, "Existing membership cannot be overwritten by an invitation.");
            } else {
                require(issuerRole == Role.OWNER && settlement.ownershipRevision() == offer.ownershipRevision(),
                        "Ownership changed; request a fresh offer.");
            }
            switch (offer.type()) {
                case INVITE -> addMember(connection, settlement.id(), actor, Role.MEMBER);
                case PROMOTE -> {
                    require(targetRole == Role.MEMBER, "The recipient is no longer a Member.");
                    execute(connection, "UPDATE settlement_members SET role='MANAGER' WHERE settlement_id=? AND player_uuid=?", settlement.id(), actor);
                }
                case TRANSFER -> {
                    require(targetRole == Role.MEMBER || targetRole == Role.MANAGER, "The recipient is no longer eligible for ownership.");
                    execute(connection, "DELETE FROM settlement_members WHERE settlement_id=? AND player_uuid=?", settlement.id(), actor);
                    execute(connection, "UPDATE settlements SET owner_uuid=?,ownership_revision=ownership_revision+1 WHERE settlement_id=?", actor, settlement.id());
                    addMember(connection, settlement.id(), settlement.owner(), Role.MEMBER);
                }
                case ABANDON -> {
                    require(targetRole == Role.OWNER && settlement.chunkRevision() == offer.chunkRevision(),
                            "Ownership or claims changed; request a fresh abandonment warning.");
                    execute(connection, "DELETE FROM settlement_chunks WHERE settlement_id=?", settlement.id());
                    execute(connection, "DELETE FROM settlement_members WHERE settlement_id=?", settlement.id());
                    execute(connection, "UPDATE settlements SET owner_uuid=NULL,status='RELEASED',ownership_revision=ownership_revision+1,chunk_revision=chunk_revision+1 WHERE settlement_id=?", settlement.id());
                }
            }
            execute(connection, "UPDATE settlement_offers SET state='ACCEPTED' WHERE offer_id=? AND state='PENDING'", offerId);
            if (offer.type() == SettlementOffer.Type.TRANSFER || offer.type() == SettlementOffer.Type.ABANDON) {
                execute(connection, "UPDATE settlement_offers SET state='CANCELLED' WHERE settlement_id=? AND state='PENDING'", settlement.id());
            }
            String eventType = switch (offer.type()) {
                case INVITE -> "MEMBER_JOINED";
                case PROMOTE -> "MANAGER_PROMOTED";
                case TRANSFER -> "OWNERSHIP_TRANSFERRED";
                case ABANDON -> "RELEASED";
            };
            event(connection, settlement.id(), actor, eventType, "issuer=" + offer.issuer() + ";recipient=" + actor);
            return changed(connection, settlement.id(), null,
                    offer.type() == SettlementOffer.Type.ABANDON ? "Released " + settlement.name() + "; claim protection removed."
                            : "Accepted " + offer.type() + " for " + settlement.name() + ".");
        });
    }

    public CompletableFuture<SettlementChange> resolveOffer(UUID actor, UUID offerId, boolean decline) {
        return database.transact(connection -> {
            SettlementOffer offer = pendingOffer(connection, offerId);
            SettlementView settlement = owned(connection, offer.settlementId());
            if (decline) {
                require(offer.recipient().equals(actor), "Only the recipient may decline.");
            } else {
                Role role = settlement.role(actor);
                require(offer.type() == SettlementOffer.Type.INVITE ? role.canManage() : role == Role.OWNER,
                        "You cannot cancel this offer.");
            }
            String state = decline ? "DECLINED" : "CANCELLED";
            execute(connection, "UPDATE settlement_offers SET state=? WHERE offer_id=?", state, offerId);
            event(connection, settlement.id(), actor, "OFFER_" + state, offer.type() + ":" + offer.id());
            return new SettlementChange(settlement.id(), settlement, null, "Offer " + state.toLowerCase(Locale.ROOT) + ".");
        });
    }

    public CompletableFuture<SettlementChange> remove(UUID actor, UUID id, UUID target, boolean voluntary) {
        return database.transact(connection -> {
            SettlementView settlement = owned(connection, id);
            Role actorRole = settlement.role(actor);
            Role targetRole = settlement.role(target);
            require(targetRole == Role.MEMBER || targetRole == Role.MANAGER,
                    "Only Members or Managers can be removed; an Owner must transfer or abandon.");
            if (voluntary) {
                require(actor.equals(target), "You may only leave for yourself.");
            } else {
                require(actorRole == Role.OWNER || (actorRole == Role.MANAGER && targetRole == Role.MEMBER),
                        "You do not have permission to remove this player.");
            }
            execute(connection, "DELETE FROM settlement_members WHERE settlement_id=? AND player_uuid=?", id, target);
            execute(connection, "UPDATE settlement_offers SET state='CANCELLED' WHERE settlement_id=? AND state='PENDING' AND (issuer_uuid=? OR recipient_uuid=?)", id, target, target);
            event(connection, id, actor, voluntary ? "MEMBER_LEFT" : "MEMBER_REMOVED", target.toString());
            return changed(connection, id, null, "Player is now a Visitor of " + settlement.name() + ".");
        });
    }

    public CompletableFuture<SettlementChange> demote(UUID actor, UUID id, UUID target) {
        return database.transact(connection -> {
            SettlementView settlement = owned(connection, id);
            require(settlement.role(actor) == Role.OWNER, "Only the Owner may demote Managers.");
            require(settlement.role(target) == Role.MANAGER, "That player is not a Manager.");
            execute(connection, "UPDATE settlement_members SET role='MEMBER' WHERE settlement_id=? AND player_uuid=?", id, target);
            execute(connection, "UPDATE settlement_offers SET state='CANCELLED' WHERE settlement_id=? AND issuer_uuid=? AND state='PENDING'", id, target);
            event(connection, id, actor, "MANAGER_DEMOTED", target.toString());
            return changed(connection, id, null, "Manager demoted to Member.");
        });
    }

    public CompletableFuture<List<SettlementOffer>> offers(UUID actor) {
        return database.transact(connection -> {
            var result = new ArrayList<SettlementOffer>();
            try (PreparedStatement query = prepare(connection, """
                    SELECT o.* FROM settlement_offers o JOIN settlements s ON s.settlement_id=o.settlement_id
                    WHERE o.state='PENDING' AND o.expires_at>? AND s.status='OWNED' AND
                    (o.recipient_uuid=? OR o.issuer_uuid=? OR s.owner_uuid=? OR
                     (o.type='INVITE' AND EXISTS(SELECT 1 FROM settlement_members m
                      WHERE m.settlement_id=s.settlement_id AND m.player_uuid=? AND m.role='MANAGER')))
                    ORDER BY o.expires_at LIMIT 50
                    """, clock.millis(), actor, actor, actor, actor); ResultSet rows = query.executeQuery()) {
                while (rows.next()) { result.add(offerFrom(rows)); }
            }
            return List.copyOf(result);
        });
    }

    public CompletableFuture<SettlementOffer> inspectOffer(UUID id) {
        return database.transact(connection -> pendingOffer(connection, id));
    }

    public CompletableFuture<List<SettlementEvent>> history(UUID settlementId) {
        return database.transact(connection -> events(connection,
                "SELECT * FROM settlement_events WHERE settlement_id=? ORDER BY occurred_at DESC,rowid DESC LIMIT 50", settlementId));
    }

    public CompletableFuture<List<SettlementEvent>> pendingEvents() {
        return database.transact(connection -> events(connection,
                "SELECT * FROM settlement_events WHERE delivered=0 ORDER BY rowid LIMIT 128"));
    }

    public CompletableFuture<Void> acknowledgeEvents(List<UUID> ids) {
        List<UUID> captured = List.copyOf(ids);
        return database.transact(connection -> {
            for (UUID id : captured) { execute(connection, "UPDATE settlement_events SET delivered=1 WHERE event_id=?", id); }
            return null;
        });
    }

    private SettlementChange changed(Connection connection, UUID id, SettlementOffer offer, String message) throws SQLException {
        return new SettlementChange(id, load(connection, id), offer, message);
    }

    private void event(Connection connection, UUID id, UUID actor, String type, String detail) throws SQLException {
        execute(connection, "INSERT INTO settlement_events VALUES(?,?,?,?,?,?,0)", UUID.randomUUID(), id, actor, type, detail, clock.millis());
    }

    private static List<SettlementEvent> events(Connection connection, String sql, Object... parameters) throws SQLException {
        var events = new ArrayList<SettlementEvent>();
        try (PreparedStatement query = prepare(connection, sql, parameters); ResultSet result = query.executeQuery()) {
            while (result.next()) {
                events.add(new SettlementEvent(UUID.fromString(result.getString("event_id")),
                        UUID.fromString(result.getString("settlement_id")), UUID.fromString(result.getString("actor_uuid")),
                        result.getString("type"), result.getString("detail"), Instant.ofEpochMilli(result.getLong("occurred_at"))));
            }
        }
        return List.copyOf(events);
    }

    private SettlementOffer pendingOffer(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement query = prepare(connection, "SELECT * FROM settlement_offers WHERE offer_id=? AND state='PENDING'", id);
                ResultSet result = query.executeQuery()) {
            require(result.next(), "Offer not found or already resolved.");
            SettlementOffer offer = offerFrom(result);
            require(clock.instant().isBefore(offer.expiresAt()), "Offer expired; request a new one.");
            return offer;
        }
    }

    private static SettlementOffer offerFrom(ResultSet row) throws SQLException {
        return new SettlementOffer(UUID.fromString(row.getString("offer_id")), UUID.fromString(row.getString("settlement_id")),
                SettlementOffer.Type.valueOf(row.getString("type")), UUID.fromString(row.getString("issuer_uuid")),
                UUID.fromString(row.getString("recipient_uuid")), Instant.ofEpochMilli(row.getLong("expires_at")),
                row.getLong("ownership_revision"), row.getLong("chunk_revision"));
    }

    private static void addMember(Connection connection, UUID id, UUID player, Role role) throws SQLException {
        execute(connection, "INSERT INTO settlement_members VALUES(?,?,?)", id, player, role);
    }

    private static void insertChunk(Connection connection, UUID id, UUID world, ChunkPosition chunk) throws SQLException {
        execute(connection, "INSERT INTO settlement_chunks VALUES(?,?,?,?)", world, chunk.x(), chunk.z(), id);
    }

    private static void requireMainWorld(Connection connection, UUID world) throws SQLException {
        try (PreparedStatement query = prepare(connection,
                "SELECT 1 FROM world_instances WHERE world_instance_id=? AND world_definition_id='MAIN'", world);
                ResultSet rows = query.executeQuery()) {
            require(rows.next(), "Settlements may only claim registered Main World instances.");
        }
    }

    private static void requireUnclaimed(Connection connection, UUID world, ChunkPosition chunk) throws SQLException {
        try (PreparedStatement query = prepare(connection,
                "SELECT 1 FROM settlement_chunks WHERE world_instance_id=? AND chunk_x=? AND chunk_z=?", world, chunk.x(), chunk.z());
                ResultSet rows = query.executeQuery()) {
            require(!rows.next(), "This chunk is already claimed.");
        }
    }

    private static void requireNameAvailable(Connection connection, String name, UUID self) throws SQLException {
        try (PreparedStatement query = prepare(connection, "SELECT settlement_id FROM settlements WHERE name_key=? AND status='OWNED'", key(name));
                ResultSet rows = query.executeQuery()) {
            require(!rows.next() || rows.getString(1).equals(String.valueOf(self)), "An owned Settlement already uses that name.");
        }
    }

    private static String validName(String name) {
        require(name != null && name.matches("[\\p{L}\\p{N}_-]{1,32}"), "Use 1-32 letters, digits, underscores, or hyphens for a name.");
        return name;
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new SettlementException(message); }
    }

    private static List<ChunkPosition> neighbors(ChunkPosition chunk) {
        return List.of(new ChunkPosition(chunk.x() + 1, chunk.z()), new ChunkPosition(chunk.x() - 1, chunk.z()),
                new ChunkPosition(chunk.x(), chunk.z() + 1), new ChunkPosition(chunk.x(), chunk.z() - 1));
    }

    private static boolean connected(Set<ChunkPosition> chunks) {
        var queue = new ArrayDeque<ChunkPosition>();
        var visited = new HashSet<ChunkPosition>();
        queue.add(chunks.iterator().next());
        while (!queue.isEmpty()) {
            ChunkPosition current = queue.removeFirst();
            if (!visited.add(current)) { continue; }
            for (ChunkPosition next : neighbors(current)) {
                if (chunks.contains(next) && !visited.contains(next)) { queue.addLast(next); }
            }
        }
        return visited.size() == chunks.size();
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... parameters) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < parameters.length; i++) {
                Object value = parameters[i];
                statement.setObject(i + 1, value instanceof UUID || value instanceof Enum<?> ? value.toString() : value);
            }
            return statement;
        } catch (SQLException failure) {
            statement.close();
            throw failure;
        }
    }

    private static int execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters)) { return statement.executeUpdate(); }
    }

    private static SettlementView owned(Connection connection, UUID id) throws SQLException {
        SettlementView result = load(connection, id);
        require(result != null, "Settlement is no longer owned or does not exist.");
        return result;
    }

    private static SettlementView load(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement query = prepare(connection, "SELECT * FROM settlements WHERE settlement_id=? AND status='OWNED'", id);
                ResultSet row = query.executeQuery()) {
            if (!row.next()) { return null; }
            var members = new HashMap<UUID, Role>();
            var chunks = new HashSet<ChunkPosition>();
            try (PreparedStatement m = prepare(connection, "SELECT * FROM settlement_members WHERE settlement_id=?", id);
                    ResultSet rows = m.executeQuery()) {
                while (rows.next()) { members.put(UUID.fromString(rows.getString("player_uuid")), Role.valueOf(rows.getString("role"))); }
            }
            try (PreparedStatement c = prepare(connection, "SELECT chunk_x,chunk_z FROM settlement_chunks WHERE settlement_id=?", id);
                    ResultSet rows = c.executeQuery()) {
                while (rows.next()) { chunks.add(new ChunkPosition(rows.getInt(1), rows.getInt(2))); }
            }
            return viewFrom(row, members, chunks);
        }
    }

    private static List<SettlementView> loadAll(Connection connection) throws SQLException {
        var members = new HashMap<UUID, Map<UUID, Role>>();
        var chunks = new HashMap<UUID, Set<ChunkPosition>>();
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("SELECT * FROM settlement_members")) {
            while (rows.next()) {
                members.computeIfAbsent(UUID.fromString(rows.getString("settlement_id")), ignored -> new HashMap<>())
                        .put(UUID.fromString(rows.getString("player_uuid")), Role.valueOf(rows.getString("role")));
            }
        }
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("SELECT * FROM settlement_chunks")) {
            while (rows.next()) {
                chunks.computeIfAbsent(UUID.fromString(rows.getString("settlement_id")), ignored -> new HashSet<>())
                        .add(new ChunkPosition(rows.getInt("chunk_x"), rows.getInt("chunk_z")));
            }
        }
        var result = new ArrayList<SettlementView>();
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("SELECT * FROM settlements WHERE status='OWNED' ORDER BY name_key")) {
            while (rows.next()) {
                UUID id = UUID.fromString(rows.getString("settlement_id"));
                Set<ChunkPosition> claimed = chunks.getOrDefault(id, Set.of());
                if (claimed.isEmpty() || !connected(claimed)) { throw new SQLException("Invalid persisted Settlement topology: " + id); }
                Map<UUID, Role> team = members.getOrDefault(id, Map.of());
                if (team.containsKey(UUID.fromString(rows.getString("owner_uuid")))) { throw new SQLException("Owner is duplicated in membership: " + id); }
                result.add(viewFrom(rows, team, claimed));
            }
        }
        return List.copyOf(result);
    }

    private static SettlementView viewFrom(ResultSet row, Map<UUID, Role> members, Set<ChunkPosition> chunks) throws SQLException {
        return new SettlementView(UUID.fromString(row.getString("settlement_id")), UUID.fromString(row.getString("world_instance_id")),
                row.getString("name"), UUID.fromString(row.getString("owner_uuid")), Instant.ofEpochMilli(row.getLong("created_at")),
                row.getLong("ownership_revision"), row.getLong("chunk_revision"), members, chunks);
    }

    private static void migrate(Connection connection) throws SQLException {
        execute(connection, "CREATE TABLE IF NOT EXISTS civilization_schema (id INTEGER PRIMARY KEY CHECK(id=1), version INTEGER NOT NULL)");
        int version = 0;
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("SELECT version FROM civilization_schema WHERE id=1")) {
            if (rows.next()) { version = rows.getInt(1); }
        }
        if (version != 0 && version != 1) { throw new SQLException("Unsupported Civilization schema: " + version); }
        if (version == 1) { return; }
        execute(connection, """
                CREATE TABLE settlements (
                    settlement_id TEXT PRIMARY KEY, world_instance_id TEXT NOT NULL REFERENCES world_instances(world_instance_id),
                    name TEXT NOT NULL, name_key TEXT NOT NULL, owner_uuid TEXT, created_at INTEGER NOT NULL,
                    ownership_revision INTEGER NOT NULL, chunk_revision INTEGER NOT NULL,
                    status TEXT NOT NULL CHECK(status IN ('OWNED','RELEASED')),
                    UNIQUE(settlement_id,world_instance_id),
                    CHECK((status='OWNED' AND owner_uuid IS NOT NULL) OR (status='RELEASED' AND owner_uuid IS NULL))
                )
                """);
        execute(connection, "CREATE UNIQUE INDEX settlements_owned_names ON settlements(name_key) WHERE status='OWNED'");
        execute(connection, """
                CREATE TABLE settlement_members (
                    settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id), player_uuid TEXT NOT NULL,
                    role TEXT NOT NULL CHECK(role IN ('MANAGER','MEMBER')), PRIMARY KEY(settlement_id,player_uuid)
                )
                """);
        execute(connection, """
                CREATE TABLE settlement_chunks (
                    world_instance_id TEXT NOT NULL, chunk_x INTEGER NOT NULL, chunk_z INTEGER NOT NULL,
                    settlement_id TEXT NOT NULL, PRIMARY KEY(world_instance_id,chunk_x,chunk_z),
                    FOREIGN KEY(settlement_id,world_instance_id) REFERENCES settlements(settlement_id,world_instance_id)
                )
                """);
        execute(connection, "CREATE INDEX settlement_chunks_owner ON settlement_chunks(settlement_id)");
        execute(connection, """
                CREATE TABLE settlement_offers (
                    offer_id TEXT PRIMARY KEY, settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id),
                    type TEXT NOT NULL CHECK(type IN ('INVITE','PROMOTE','TRANSFER','ABANDON')),
                    issuer_uuid TEXT NOT NULL, recipient_uuid TEXT NOT NULL, expires_at INTEGER NOT NULL,
                    ownership_revision INTEGER NOT NULL, chunk_revision INTEGER NOT NULL,
                    state TEXT NOT NULL CHECK(state IN ('PENDING','ACCEPTED','CANCELLED','DECLINED','EXPIRED'))
                )
                """);
        execute(connection, "CREATE UNIQUE INDEX settlement_pending_offer ON settlement_offers(settlement_id,type,recipient_uuid) WHERE state='PENDING'");
        execute(connection, """
                CREATE TABLE settlement_events (
                    event_id TEXT PRIMARY KEY, settlement_id TEXT NOT NULL REFERENCES settlements(settlement_id),
                    actor_uuid TEXT NOT NULL, type TEXT NOT NULL, detail TEXT NOT NULL,
                    occurred_at INTEGER NOT NULL, delivered INTEGER NOT NULL DEFAULT 0 CHECK(delivered IN (0,1))
                )
                """);
        execute(connection, "CREATE INDEX settlement_events_history ON settlement_events(settlement_id,occurred_at)");
        execute(connection, "CREATE INDEX settlement_events_outbox ON settlement_events(delivered)");
        execute(connection, "INSERT INTO civilization_schema VALUES(1,1)");
    }
}
