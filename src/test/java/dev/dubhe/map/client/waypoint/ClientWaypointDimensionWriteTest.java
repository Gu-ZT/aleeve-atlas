package dev.dubhe.map.client.waypoint;

import dev.dubhe.map.waypoint.Waypoint;
import dev.dubhe.map.waypoint.WaypointStore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClientWaypointDimensionWriteTest {
    @TempDir
    Path directory;

    @Test
    void importIntoPreviouslyVisitedDimensionUpdatesCachedStoreAndSurvivesItsNextSave() {
        var nether = WaypointStore.open(this.directory, "example", Level.NETHER);
        var old = Waypoint.create("foo", new BlockPos(1, 64, 2), Level.NETHER, 0xFFFFFFFF);
        nether.upsert(old);
        var imported = Waypoint.create("bar", new BlockPos(16, 80, 32), Level.NETHER, 0xFFFF0000);
        ClientWaypoints.upsertStoredDimension(this.directory, "example",
                Map.of(Level.NETHER.location().toString(), nether), imported);
        assertEquals(imported, nether.get(imported.id()).orElseThrow());
        assertEquals(old, nether.get(old.id()).orElseThrow());
        nether.upsert(old.withEnabled(false));
        nether.flush();
        var reopened = WaypointStore.open(this.directory, "example", Level.NETHER);
        assertEquals(imported, reopened.get(imported.id()).orElseThrow());
        assertFalse(reopened.get(old.id()).orElseThrow().enabled());
    }

    @Test
    void unvisitedDimensionIsPersistedWithoutChangingAnotherDimensionCache() {
        var overworld = WaypointStore.open(this.directory, "example", Level.OVERWORLD);
        var imported = Waypoint.create("example", new BlockPos(-32, -20, 64), Level.NETHER, 0xFFFFFFFF);
        ClientWaypoints.upsertStoredDimension(this.directory, "example",
                Map.of(Level.OVERWORLD.location().toString(), overworld), imported);
        assertTrue(overworld.all().isEmpty());
        assertEquals(imported, WaypointStore.open(this.directory, "example", Level.NETHER).get(imported.id()).orElseThrow());
    }
}
