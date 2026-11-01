package dev.dubhe.map.network;

import net.minecraft.core.HolderLookup;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.saveddata.SavedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class MapCacheIdentityTest {
    @TempDir Path temp;

    @Test void persistsBeforeLoginAndReusesIdentityAfterRestart() throws Exception {
        String id;
        try (var storage = open(temp)) {
            id = MapCacheIdentity.initialize(storage).id();
            assertEquals(id, UUID.fromString(id).toString());
            assertTrue(Files.isRegularFile(temp.resolve("aleeve_atlas_map_cache_identity.dat")));
            assertEquals(id, MapCacheIdentity.initialize(storage).id());
        }
        try (var restarted = open(temp)) {
            assertEquals(id, MapCacheIdentity.initialize(restarted).id());
        }
        Path other = Files.createDirectory(temp.resolve("other-world"));
        try (var anotherWorld = open(other)) {
            assertNotEquals(id, MapCacheIdentity.initialize(anotherWorld).id());
        }
    }

    private static DimensionDataStorage open(Path path) {
        return new DimensionDataStorage(new SavedData.Context(null, 0L), path, DataFixers.getDataFixer(), HolderLookup.Provider.create(Stream.empty()));
    }
}
