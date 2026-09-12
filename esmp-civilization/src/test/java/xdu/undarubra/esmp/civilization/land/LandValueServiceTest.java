package xdu.undarubra.esmp.civilization.land;

import static org.junit.jupiter.api.Assertions.*;

import static xdu.undarubra.esmp.civilization.generated.Tables.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import xdu.undarubra.esmp.core.storage.WorldStore;
import xdu.undarubra.esmp.core.world.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletionException;

class LandValueServiceTest {
    @TempDir Path directory;
    WorldStore store;
    LandValueService values;
    UUID source = UUID.randomUUID(), resourceSource = UUID.randomUUID(), main, resource;

    @BeforeEach
    void open() {
        store = new WorldStore(directory.resolve("land.db"));
        var snapshot =
                store.initialize(
                                List.of(
                                        new WorldDefinition(
                                                "MAIN", "civilization", WorldEnvironment.NORMAL),
                                        new WorldDefinition(
                                                "RESOURCE_OVERWORLD",
                                                "resource",
                                                WorldEnvironment.NORMAL)),
                                List.of(
                                        new WorldBinding("MAIN", source, "world"),
                                        new WorldBinding(
                                                "RESOURCE_OVERWORLD", resourceSource, "resource")))
                        .join();
        main =
                snapshot.instances().stream()
                        .filter(w -> w.definitionId().equals("MAIN"))
                        .findFirst()
                        .orElseThrow()
                        .instanceId();
        resource =
                snapshot.instances().stream()
                        .filter(w -> w.definitionId().equals("RESOURCE_OVERWORLD"))
                        .findFirst()
                        .orElseThrow()
                        .instanceId();
        values =
                xdu.undarubra.esmp.civilization.api.ObservationServices.land(
                        store, LandValuePolicy.defaults());
        values.initialize().join();
    }

    @AfterEach
    void close() {
        store.close();
    }

    LandValueProfile profile(int x, int version, long value) {
        return new LandValueProfile(main, x, -2, version, value, LandValueProfile.Terrain.FLAT);
    }

    @Test
    void valuesSurviveRestartAndNewPoliciesDoNotRevalueOldLand() {
        var old = profile(-3, 1, 100);
        values.capture(List.of(old)).join();
        store.close();
        open();
        assertEquals(old, values.find(main, -3, -2).join());
        var newer =
                xdu.undarubra.esmp.civilization.api.ObservationServices.land(
                        store, new LandValuePolicy(2, 200, 2, 50, 25));
        newer.initialize().join();
        newer.capture(List.of(profile(4, 2, 200))).join();
        assertEquals(100, newer.find(main, -3, -2).join().baseValue());
        assertEquals(200, newer.find(main, 4, -2).join().baseValue());
        assertThrows(
                CompletionException.class,
                () ->
                        xdu.undarubra.esmp.civilization.api.ObservationServices.land(
                                        store, new LandValuePolicy(1, 200, 2, 50, 25))
                                .initialize()
                                .join());
    }

    @Test
    void replayIsIdempotentAndConflictingBatchCannotPartiallyCommit() {
        var original = profile(0, 1, 100);
        values.capture(List.of(original)).join();
        values.capture(List.of(original)).join();
        assertThrows(
                CompletionException.class,
                () -> values.capture(List.of(profile(1, 1, 100), profile(0, 1, 101))).join());
        assertEquals(original, values.find(main, 0, -2).join());
        assertNull(values.find(main, 1, -2).join());
    }

    @Test
    void unknownLandAndResourceWorldsCannotBecomeMainProfiles() {
        assertNull(values.find(main, 20, -2).join());
        values.capture(List.of(profile(0, 1, 100))).join();
        assertNull(values.find(resource, 0, -2).join());
        assertThrows(
                CompletionException.class,
                () ->
                        values.capture(
                                        List.of(
                                                new LandValueProfile(
                                                        resource,
                                                        0,
                                                        -2,
                                                        1,
                                                        100,
                                                        LandValueProfile.Terrain.FLAT)))
                                .join());
    }

    @Test
    void naturalFactorsStayPositiveAndArithmeticDoesNotOverflow() {
        var policy = new LandValuePolicy(1, 1000, 7, 100, 25);
        assertEquals(909, policy.value(13, 0));
        assertEquals(934, policy.value(13, 1));
        assertEquals(900, policy.value(Integer.MAX_VALUE, 16));
        assertEquals(LandValueProfile.Terrain.SHORE, policy.terrain(0, 15));
        assertEquals(LandValueProfile.Terrain.WATER, policy.terrain(0, 16));
        assertEquals(900, new LandValuePolicy(2, 1000, Long.MAX_VALUE, 100, 0).value(2, 0));
        assertThrows(IllegalArgumentException.class, () -> new LandValuePolicy(1, 100, 2, 100, 25));
        assertThrows(
                ArithmeticException.class, () -> new LandValuePolicy(1, Long.MAX_VALUE, 2, 50, 1));
    }

    @Test
    void schemaVersionIsNotResetAndFailedInitializationDoesNotBecomeReady() {
        var original = profile(0, 1, 100);
        values.capture(List.of(original)).join();
        store.transact(
                        c -> {
                            assertEquals(
                                    1,
                                    xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                            .update(LAND_VALUE_METADATA)
                                            .set(LAND_VALUE_METADATA.SCHEMA_VERSION, 2L)
                                            .where(LAND_VALUE_METADATA.ID.eq(1L))
                                            .execute());
                            return null;
                        })
                .join();
        var unsupported =
                xdu.undarubra.esmp.civilization.api.ObservationServices.land(
                        store, LandValuePolicy.defaults());
        var failure =
                assertThrows(CompletionException.class, () -> unsupported.initialize().join());
        assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals("Unsupported land-value schema", failure.getCause().getMessage());
        var notReady =
                assertThrows(CompletionException.class, () -> unsupported.find(main, 0, -2).join());
        assertInstanceOf(IllegalStateException.class, notReady.getCause());
        long version =
                store.transact(
                                c ->
                                        xdu.undarubra.esmp.core.storage.DatabaseAccess.context(c)
                                                .select(LAND_VALUE_METADATA.SCHEMA_VERSION)
                                                .from(LAND_VALUE_METADATA)
                                                .where(LAND_VALUE_METADATA.ID.eq(1L))
                                                .fetchSingle(LAND_VALUE_METADATA.SCHEMA_VERSION))
                        .join();
        assertEquals(2L, version);
        assertEquals(original, values.find(main, 0, -2).join());
    }

    @Test
    void terrainAndValuationVersionConflictsAlsoRollBackTheWholeBatch() {
        var original = profile(0, 1, 100);
        values.capture(List.of(original)).join();
        var shore = new LandValueProfile(main, 0, -2, 1, 100, LandValueProfile.Terrain.SHORE);
        assertThrows(
                CompletionException.class,
                () -> values.capture(List.of(profile(1, 1, 100), shore)).join());
        assertNull(values.find(main, 1, -2).join());
        assertEquals(original, values.find(main, 0, -2).join());
        var newer =
                xdu.undarubra.esmp.civilization.api.ObservationServices.land(
                        store, new LandValuePolicy(2, 200, 2, 50, 25));
        newer.initialize().join();
        assertThrows(
                CompletionException.class,
                () -> newer.capture(List.of(profile(2, 2, 200), profile(0, 2, 100))).join());
        assertNull(newer.find(main, 2, -2).join());
        assertEquals(original, newer.find(main, 0, -2).join());
    }
}
