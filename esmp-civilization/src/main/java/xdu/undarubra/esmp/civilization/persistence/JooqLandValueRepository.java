package xdu.undarubra.esmp.civilization.persistence;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;

import org.jooq.DSLContext;
import org.jooq.Record;

import xdu.undarubra.esmp.civilization.land.LandValuePolicy;
import xdu.undarubra.esmp.civilization.land.LandValueProfile;
import xdu.undarubra.esmp.civilization.land.LandValueRepository;
import xdu.undarubra.esmp.civilization.persistence.legacy.ObservationLegacyMigrations;
import xdu.undarubra.esmp.core.storage.DatabaseAccess;
import xdu.undarubra.esmp.core.storage.UnitOfWork;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Civilization-owned SQL adapter for immutable natural land observations. */
public final class JooqLandValueRepository implements LandValueRepository {
    private final UnitOfWork database;
    private final LandValuePolicy policy;
    private volatile boolean ready;

    public JooqLandValueRepository(UnitOfWork database, LandValuePolicy policy) {
        this.database = Objects.requireNonNull(database);
        this.policy = Objects.requireNonNull(policy);
    }

    @Override
    public LandValuePolicy policy() {
        return policy;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return database.transact(
                        transaction -> {
                            DSLContext c = DatabaseAccess.context(transaction);
                            if (DatabaseAccess.legacy(transaction)) {
                                ObservationLegacyMigrations.land(c);
                            } else {
                                c.insertInto(LAND_VALUE_METADATA)
                                        .columns(
                                                LAND_VALUE_METADATA.ID,
                                                LAND_VALUE_METADATA.SCHEMA_VERSION)
                                        .values(1L, 1L)
                                        .onDuplicateKeyIgnore()
                                        .execute();
                            }
                            Record metadata =
                                    c.select(LAND_VALUE_METADATA.SCHEMA_VERSION)
                                            .from(LAND_VALUE_METADATA)
                                            .where(LAND_VALUE_METADATA.ID.eq(1L))
                                            .fetchOne();
                            if (metadata == null
                                    || metadata.get(LAND_VALUE_METADATA.SCHEMA_VERSION) != 1L) {
                                throw new SQLException("Unsupported land-value schema");
                            }
                            c.insertInto(LAND_VALUE_POLICIES)
                                    .columns(
                                            LAND_VALUE_POLICIES.VERSION,
                                            LAND_VALUE_POLICIES.BASE_VALUE,
                                            LAND_VALUE_POLICIES.RELIEF_PENALTY,
                                            LAND_VALUE_POLICIES.MAX_RELIEF_PENALTY,
                                            LAND_VALUE_POLICIES.SHORE_BONUS)
                                    .values(
                                            (long) policy.version(),
                                            policy.baseValue(),
                                            policy.reliefPenalty(),
                                            policy.maxReliefPenalty(),
                                            policy.shoreBonus())
                                    .onDuplicateKeyIgnore()
                                    .execute();
                            Record row =
                                    c.select(
                                                    LAND_VALUE_POLICIES.BASE_VALUE,
                                                    LAND_VALUE_POLICIES.RELIEF_PENALTY,
                                                    LAND_VALUE_POLICIES.MAX_RELIEF_PENALTY,
                                                    LAND_VALUE_POLICIES.SHORE_BONUS)
                                            .from(LAND_VALUE_POLICIES)
                                            .where(
                                                    LAND_VALUE_POLICIES.VERSION.eq(
                                                            (long) policy.version()))
                                            .fetchOne();
                            if (row == null
                                    || row.get(LAND_VALUE_POLICIES.BASE_VALUE) != policy.baseValue()
                                    || row.get(LAND_VALUE_POLICIES.RELIEF_PENALTY)
                                            != policy.reliefPenalty()
                                    || row.get(LAND_VALUE_POLICIES.MAX_RELIEF_PENALTY)
                                            != policy.maxReliefPenalty()
                                    || row.get(LAND_VALUE_POLICIES.SHORE_BONUS)
                                            != policy.shoreBonus()) {
                                throw new SQLException(
                                        "Land-value coefficients changed under an existing"
                                                + " version; choose a new valuation version for"
                                                + " future chunks");
                            }
                            return null;
                        })
                .thenRun(() -> ready = true);
    }

    @Override
    public CompletableFuture<LandValueProfile> find(UUID world, int x, int z) {
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    requireReady();
                    return find(c, world, x, z);
                });
    }

    @Override
    public CompletableFuture<Void> captureImmutableProfiles(List<LandValueProfile> profiles) {
        return database.transact(
                transaction -> {
                    DSLContext c = DatabaseAccess.context(transaction);
                    requireReady();
                    for (var p : profiles) {
                        if (!"MAIN"
                                .equals(
                                        DatabaseAccess.worldDefinition(
                                                transaction, p.worldInstance()))) {
                            throw new SQLException(
                                    "Land-value capture requires a registered Main World"
                                            + " instance");
                        }
                        c.insertInto(LAND_VALUE_PROFILES)
                                .columns(
                                        LAND_VALUE_PROFILES.WORLD_INSTANCE_ID,
                                        LAND_VALUE_PROFILES.CHUNK_X,
                                        LAND_VALUE_PROFILES.CHUNK_Z,
                                        LAND_VALUE_PROFILES.VALUATION_VERSION,
                                        LAND_VALUE_PROFILES.BASE_VALUE,
                                        LAND_VALUE_PROFILES.VALUATION_CLASS)
                                .values(
                                        p.worldInstance(),
                                        (long) p.chunkX(),
                                        (long) p.chunkZ(),
                                        (long) p.valuationVersion(),
                                        p.baseValue(),
                                        p.terrain().name())
                                .onDuplicateKeyIgnore()
                                .execute();
                        if (!p.equals(find(c, p.worldInstance(), p.chunkX(), p.chunkZ()))) {
                            throw new SQLException(
                                    "Conflicting immutable land profile at "
                                            + p.worldInstance()
                                            + " "
                                            + p.chunkX()
                                            + ","
                                            + p.chunkZ());
                        }
                    }
                    return null;
                });
    }

    private static LandValueProfile find(DSLContext c, UUID world, int x, int z)
            throws SQLException {
        Record row =
                c.select(
                                LAND_VALUE_PROFILES.VALUATION_VERSION,
                                LAND_VALUE_PROFILES.BASE_VALUE,
                                LAND_VALUE_PROFILES.VALUATION_CLASS)
                        .from(LAND_VALUE_PROFILES)
                        .where(LAND_VALUE_PROFILES.WORLD_INSTANCE_ID.eq(world))
                        .and(LAND_VALUE_PROFILES.CHUNK_X.eq((long) x))
                        .and(LAND_VALUE_PROFILES.CHUNK_Z.eq((long) z))
                        .fetchOne();
        return row == null
                ? null
                : new LandValueProfile(
                        world,
                        x,
                        z,
                        Math.toIntExact(row.get(LAND_VALUE_PROFILES.VALUATION_VERSION)),
                        row.get(LAND_VALUE_PROFILES.BASE_VALUE),
                        LandValueProfile.Terrain.valueOf(
                                row.get(LAND_VALUE_PROFILES.VALUATION_CLASS)));
    }

    private void requireReady() {
        if (!ready) throw new IllegalStateException("Land values are not initialized");
    }
}
