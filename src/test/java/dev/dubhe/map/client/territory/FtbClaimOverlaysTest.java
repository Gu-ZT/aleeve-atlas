package dev.dubhe.map.client.territory;

import dev.dubhe.map.territory.SimpleTerritory;
import dev.dubhe.map.territory.TerritoryRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FtbClaimOverlaysTest {
    private final TerritoryRegistry registry = new TerritoryRegistry();
    private final FtbClaimOverlays claims = new FtbClaimOverlays(registry);
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final FtbClaimOverlays.Position a = new FtbClaimOverlays.Position(Level.OVERWORLD, new ChunkPos(-2, 3));
    private final FtbClaimOverlays.Position b = new FtbClaimOverlays.Position(Level.OVERWORLD, new ChunkPos(-1, 3));

    @Test void initialSyncBatchesChunksAndSeparatesDimensions() {
        claims.appearance(first, Component.literal("Team"), 0x123456);
        claims.update(a, first);
        claims.update(b, first);
        claims.update(new FtbClaimOverlays.Position(Level.NETHER, a.chunk()), first);
        assertTrue(registry.registeredTerritories().isEmpty());
        claims.flush();
        assertEquals(2, registry.registeredTerritories().size());
        assertEquals(Set.of(a.chunk(), b.chunk()), registry.registeredTerritories().stream()
                .filter(group -> group.dimension().equals(Level.OVERWORLD)).findFirst().orElseThrow().chunks());
    }

    @Test void ownershipTransferAndUnclaimRemoveOldOverlays() {
        claims.appearance(first, Component.literal("A"), 1);
        claims.update(a, first);
        claims.flush();
        claims.appearance(second, Component.literal("B"), 2);
        claims.update(a, second);
        claims.flush();
        assertEquals(1, registry.registeredTerritories().size());
        assertEquals("B", registry.registeredTerritories().iterator().next().displayName().getString());
        claims.update(a, null);
        claims.flush();
        assertTrue(registry.registeredTerritories().isEmpty());
        assertTrue(claims.teams().isEmpty());
    }

    @Test void renameAndColorUpdateWithoutChangingIdentityOrChunks() {
        claims.appearance(first, Component.literal("Old"), 1);
        claims.update(a, first);
        claims.flush();
        String id = registry.registeredTerritories().iterator().next().territoryId();
        claims.appearance(first, Component.literal("New"), 0);
        claims.flush();
        var group = registry.registeredTerritories().iterator().next();
        assertEquals(id, group.territoryId());
        assertEquals("New", group.displayName().getString());
        assertEquals(0, group.themeColorRgb());
        assertEquals(Set.of(a.chunk()), group.chunks());
    }

    @Test void duplicateUpdatesDoNotRebuildUnchangedOverlay() {
        claims.appearance(first, Component.literal("A"), 1);
        claims.update(a, first);
        claims.flush();
        var original = registry.registeredTerritories().iterator().next();
        claims.appearance(first, Component.literal("A"), 1);
        claims.update(a, first);
        claims.flush();
        assertSame(original, registry.registeredTerritories().iterator().next());
    }

    @Test void disconnectClearsOnlyFtbClaimsIncludingPendingChanges() {
        var other = SimpleTerritory.of("other", Level.OVERWORLD, Set.of(b.chunk()), Component.literal("Other"), 1);
        registry.upsertTerritory(other);
        claims.appearance(first, Component.literal("A"), 1);
        claims.update(a, first);
        claims.flush();
        claims.update(b, first);
        claims.clear();
        claims.flush();
        assertEquals(Set.of(other), registry.registeredTerritories());
        claims.update(a, first);
        claims.appearance(first, Component.literal("Reconnected"), 2);
        claims.flush();
        assertEquals(2, registry.registeredTerritories().size());
    }
}
