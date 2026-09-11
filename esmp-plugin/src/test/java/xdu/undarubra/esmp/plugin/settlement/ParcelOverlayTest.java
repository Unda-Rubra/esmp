package xdu.undarubra.esmp.plugin.settlement;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import xdu.undarubra.esmp.civilization.settlement.*;

import java.time.Instant;
import java.util.*;

class ParcelOverlayTest {
    private record Point(double x, double y, double z) {}

    @Test
    void airTargetExitsCameraCellAlongFacingRay() {
        assertEquals(new ParcelCell(1, 0, 0), ParcelAim.adjacent(3, 4, 5, 1, 0, 0));
        assertEquals(new ParcelCell(0, -1, 0), ParcelAim.adjacent(3, 4, 5, 0, -1, 0));
        assertEquals(new ParcelCell(-2, -1, -1), ParcelAim.adjacent(-1, -2, -3, -1, 0, 0));
        assertEquals(new ParcelCell(-1, 0, 0), ParcelAim.adjacent(0, 4, 4, -1, 0, 0));
        assertEquals(new ParcelCell(1, 0, 1), ParcelAim.adjacent(4, 4, 4, 1, 0, 1));
        assertEquals(new ParcelCell(0, 1, 0), ParcelAim.adjacent(7.9, 7.99, 4, .01, 1, 0));
        assertEquals(
                new ParcelCell(0, -1, 0),
                ParcelAim.outsidePlayer(3, 8.5, 3, 0, -1, 0, new ParcelCell(0, 0, 0)));
        assertEquals(
                new ParcelCell(1, 1, 0),
                ParcelAim.outsidePlayer(3, 8.5, 3, 1, 0, 0, new ParcelCell(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> ParcelAim.adjacent(1, 1, 1, 0, 0, 0));
    }

    @Test
    void rayClipsBeforeUnloadedChunksIncludingCornerSides() {
        assertEquals(5, ParcelAim.loadedReach(1, 1, 1, 0, 5, (x, z) -> true));
        double clipped = ParcelAim.loadedReach(15, 1, 1, 0, 5, (x, z) -> x == 0);
        assertTrue(clipped < 1 && clipped > .99);
        assertEquals(0, ParcelAim.loadedReach(1, 1, 1, 0, 5, (x, z) -> false));
        assertEquals(5, ParcelAim.loadedReach(1, 1, 0, 0, 5, (x, z) -> true));
        double diagonal = ParcelAim.loadedReach(15, 15, 1, 1, 5, (x, z) -> x == z);
        assertTrue(diagonal < 1 && diagonal > .99);
    }

    @Test
    void targetSurfaceCoversFacesAndWireframesCoverAllEdgesWithinBudget() {
        var cell = new ParcelCell(1, -1, 0);
        var surface = new HashSet<Point>();
        int emitted =
                ParcelOverlayGeometry.surface(
                        cell, 512, (x, y, z) -> surface.add(new Point(x, y, z)));
        assertEquals(surface.size(), emitted);
        assertTrue(surface.contains(new Point(12, -8, 4)));
        assertTrue(surface.contains(new Point(12, 0, 4)));
        assertTrue(surface.contains(new Point(8, -4, 4)));
        assertTrue(
                surface.stream()
                        .allMatch(
                                p ->
                                        p.x() == 8 || p.x() == 16 || p.y() == -8 || p.y() == 0
                                                || p.z() == 0 || p.z() == 8));
        var border = new HashSet<Point>();
        int count =
                ParcelOverlayGeometry.border(
                        cell, cell, 128, (x, y, z) -> border.add(new Point(x, y, z)));
        assertTrue(count <= 128);
        assertTrue(border.contains(new Point(12, -8, 0)));
        assertFalse(border.contains(new Point(12, -8, 4)));
        var large = new ArrayList<Point>();
        int bounded =
                ParcelOverlayGeometry.border(
                        new ParcelCell(0, 0, 0),
                        new ParcelCell(100, 100, 100),
                        48,
                        (x, y, z) -> large.add(new Point(x, y, z)));
        assertTrue(bounded <= 48);
        assertTrue(large.contains(new Point(808, 808, 808)));
        assertEquals(0, ParcelOverlayGeometry.surface(cell, 0, (x, y, z) -> fail()));
    }

    @Test
    void existingBordersRespectConcaveCellGeometry() {
        var cells =
                Set.of(new ParcelCell(0, 0, 0), new ParcelCell(1, 0, 0), new ParcelCell(0, 0, 1));
        var edges = new HashSet<ParcelOverlayGeometry.Edge>();
        for (var cell : cells) ParcelOverlayGeometry.exposedEdges(cell, cells, edges);
        assertTrue(edges.contains(new ParcelOverlayGeometry.Edge(8, 0, 8, 1)));
        assertFalse(edges.contains(new ParcelOverlayGeometry.Edge(16, 0, 16, 1)));
        var points = new ArrayList<Point>();
        int count =
                ParcelOverlayGeometry.edges(
                        List.copyOf(edges), 0, 90, (x, y, z) -> points.add(new Point(x, y, z)));
        assertEquals(count, points.size());
        assertTrue(count <= 90);
    }

    @Test
    void invalidDraftKeepsItsGeometryAndOccupiedCellsAreClippedConsistently() {
        UUID id = UUID.randomUUID(), world = UUID.randomUUID(), owner = UUID.randomUUID();
        var s =
                new SettlementView(
                        id,
                        world,
                        "Home",
                        owner,
                        Instant.EPOCH,
                        1,
                        1,
                        Map.of(),
                        Set.of(new ChunkPosition(0, 0), new ChunkPosition(1, 0)));
        var base =
                new ParcelView(
                        UUID.randomUUID(),
                        id,
                        "Home",
                        1,
                        Set.of(new ParcelCell(0, 0, 0)),
                        Set.of(),
                        Map.of());
        var other =
                new ParcelView(
                        UUID.randomUUID(),
                        id,
                        "Other",
                        1,
                        Set.of(new ParcelCell(1, 0, 0)),
                        Set.of(),
                        Map.of());
        var land =
                new LandSnapshot.Land(
                        1,
                        null,
                        false,
                        0,
                        Set.of(),
                        Map.of(base.id(), base, other.id(), other),
                        Map.of(new ParcelCell(0, 0, 0), base, new ParcelCell(1, 0, 0), other));
        var split =
                ParcelDraft.add(
                        s,
                        land,
                        owner,
                        false,
                        "Home",
                        new ParcelCell(0, 0, 0),
                        new ParcelCell(2, 0, 0),
                        -64,
                        320);
        assertFalse(split.valid());
        assertEquals(3, split.cells().size());
        assertTrue(split.blocked().contains(new ParcelCell(1, 0, 0)));
        var wrap =
                ParcelDraft.add(
                        s,
                        land,
                        owner,
                        false,
                        "Home",
                        new ParcelCell(0, 0, 0),
                        new ParcelCell(1, 0, 1),
                        -64,
                        320);
        assertTrue(wrap.valid());
        assertEquals(2, wrap.added());
        assertEquals(Set.of(new ParcelCell(1, 0, 0)), wrap.blocked());
        var outside =
                ParcelDraft.add(
                        s,
                        land,
                        owner,
                        false,
                        "Home",
                        new ParcelCell(0, 0, 0),
                        new ParcelCell(0, 0, 2),
                        -64,
                        320);
        assertFalse(outside.valid());
        assertTrue(outside.blocked().contains(new ParcelCell(0, 0, 2)));
        var tall =
                ParcelDraft.add(
                        s,
                        land,
                        owner,
                        false,
                        "Home",
                        new ParcelCell(0, 40, 0),
                        new ParcelCell(0, 40, 0),
                        -64,
                        320);
        assertFalse(tall.valid());
        var denied =
                ParcelDraft.add(
                        s,
                        land,
                        UUID.randomUUID(),
                        false,
                        "New",
                        new ParcelCell(0, 0, 1),
                        new ParcelCell(0, 0, 1),
                        -64,
                        320);
        assertFalse(denied.valid());
    }
}
