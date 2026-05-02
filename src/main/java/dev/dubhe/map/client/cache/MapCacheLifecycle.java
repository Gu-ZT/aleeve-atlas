package dev.dubhe.map.client.cache;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class MapCacheLifecycle {
    private static final Path CACHE_ROOT = FMLPaths.CONFIGDIR.get()
        .resolve(AleeveAtlas.MOD_ID)
        .resolve("caches");
    private static final Map<String, MapCache> DIMENSION_CACHES = new HashMap<>();
    private static final Map<TrackedChunkKey, LevelChunk> TRACKED_CHUNKS = new HashMap<>();

    @Nullable
    private static Path sessionRoot;
    private static long tickCount;

    private MapCacheLifecycle() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft minecraft = Minecraft.getInstance();
        sessionRoot = resolveSessionRoot(minecraft);
        DIMENSION_CACHES.clear();
        TRACKED_CHUNKS.clear();
        tickCount = 0L;

        if (minecraft.level != null) {
            getOrCreateCache(minecraft.level);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        flushAll();
        DIMENSION_CACHES.clear();
        TRACKED_CHUNKS.clear();
        sessionRoot = null;
        tickCount = 0L;
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel clientLevel)) {
            return;
        }

        LevelChunk chunk = event.getChunk();
        MapCache cache = getOrCreateCache(clientLevel);
        cache.updateChunk(chunk);

        String dimensionId = dimensionId(clientLevel);
        TRACKED_CHUNKS.put(new TrackedChunkKey(dimensionId, packChunkPos(chunk.getPos())), chunk);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ClientLevel clientLevel)) {
            return;
        }

        LevelChunk chunk = event.getChunk();
        String dimensionId = dimensionId(clientLevel);
        TRACKED_CHUNKS.remove(new TrackedChunkKey(dimensionId, packChunkPos(chunk.getPos())));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || sessionRoot == null) {
            return;
        }

        tickCount++;
        if ((tickCount % 5L) != 0L) {
            return;
        }

        String currentDimension = dimensionId(level);
        MapCache cache = getOrCreateCache(level);
        boolean forceRefresh = (tickCount % 200L) == 0L;

        Iterator<Map.Entry<TrackedChunkKey, LevelChunk>> iterator = TRACKED_CHUNKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TrackedChunkKey, LevelChunk> entry = iterator.next();
            if (!entry.getKey().dimensionId.equals(currentDimension)) {
                continue;
            }

            LevelChunk chunk = entry.getValue();
            if (chunk.getLevel() != level) {
                iterator.remove();
                continue;
            }

            if (chunk.isUnsaved() || forceRefresh) {
                cache.updateChunk(chunk);
            }
        }

        if ((tickCount % 200L) == 0L) {
            flushAll();
        }
    }

    private static MapCache getOrCreateCache(ClientLevel level) {
        String dimensionId = dimensionId(level);
        return DIMENSION_CACHES.computeIfAbsent(dimensionId, ignored -> new MapCache(resolveDimensionPath(level)));
    }

    private static Path resolveSessionRoot(Minecraft minecraft) {
        if (minecraft.isLocalServer()) {
            IntegratedServer server = minecraft.getSingleplayerServer();
            String worldName = server != null ? server.getWorldData().getLevelName() : "local";
            long seed = server != null ? server.getWorldGenSettings().options().seed() : 0L;
            return CACHE_ROOT.resolve("local").resolve(sanitizePathSegment(worldName + "-" + seed));
        }

        ServerData serverData = minecraft.getCurrentServer();
        String serverHost = serverData != null ? serverData.ip : "unknown_server";
        return CACHE_ROOT.resolve("server").resolve(sanitizePathSegment(serverHost));
    }

    private static Path resolveDimensionPath(ClientLevel level) {
        String dimensionFolder = dimensionId(level).replace(':', '_');
        return Objects.requireNonNull(sessionRoot).resolve(dimensionFolder);
    }

    private static String dimensionId(ClientLevel level) {
        return level.dimension().identifier().toString();
    }

    private static void flushAll() {
        for (MapCache cache : DIMENSION_CACHES.values()) {
            cache.saveAll();
        }
    }

    public static @Nullable SurfaceSample getSurfaceSample(ClientLevel level, int blockX, int blockZ) {
        if (sessionRoot == null) {
            return null;
        }

        MapCache mapCache = DIMENSION_CACHES.get(dimensionId(level));
        if (mapCache == null) {
            return null;
        }

        BlockCache blockCache = mapCache.getBlock(blockX, blockZ);
        if (blockCache == null) {
            return null;
        }

        return new SurfaceSample(blockCache.color(), blockCache.y());
    }

    private static long packChunkPos(ChunkPos chunkPos) {
        return (((long) chunkPos.x()) << 32) ^ (chunkPos.z() & 0xFFFFFFFFL);
    }

    private static String sanitizePathSegment(String value) {
        String cleaned = value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }

    private record TrackedChunkKey(String dimensionId, long chunkPos) {
    }

    public record SurfaceSample(int color, int height) {
    }
}

