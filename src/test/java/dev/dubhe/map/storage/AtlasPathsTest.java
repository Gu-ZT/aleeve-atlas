package dev.dubhe.map.storage;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AtlasPathsTest {
    @TempDir Path tempDir;

    @Test void preservesChineseWorldNamesAndDimensionLayout() {
        Path first = AtlasPaths.serverRoot(tempDir, "sp_新的世界_123");
        Path second = AtlasPaths.serverRoot(tempDir, "sp_测试存档_123");
        assertEquals("sp_新的世界_123", first.getFileName().toString());
        assertNotEquals(first, second);
        assertEquals(first.resolve("minecraft_overworld"), AtlasPaths.dimensionDir(
                tempDir, "sp_新的世界_123", ResourceLocation.parse("minecraft:overworld")));
    }

    @Test void escapesAddressPortsWithoutCollidingWithLiteralEscapeSequences() {
        Path address = AtlasPaths.serverRoot(tempDir, "server_127.0.0.1:25565");
        assertEquals("server_127.0.0.1%3A25565", address.getFileName().toString());
        assertNotEquals(address, AtlasPaths.serverRoot(tempDir, "server_127.0.0.1%3A25565"));
        assertNotEquals(address, AtlasPaths.serverRoot(tempDir, "server_127.0.0.1:25566"));
    }

    @Test void directoryKeysCannotEscapeMapdata() {
        Path parent = AtlasPaths.root(tempDir).resolve("mapdata");
        for (String key : new String[] {"..", "../outside", "server_[::1]:25565", "name. ", "a/b"}) {
            Path result = AtlasPaths.serverRoot(tempDir, key).normalize();
            assertEquals(parent, result.getParent());
        }
    }
}
