package dev.dubhe.map.client.cache;

import dev.dubhe.map.AleeveAtlas;
import lombok.Getter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

@Getter
public class MapCache {
    private static final String REGION_FILE_PREFIX = "r.";
    private static final String REGION_FILE_SUFFIX = ".atlas";

    private final Path dimensionDir;
    private final Map<RegionPos, RegionCache> regionCacheMap = new HashMap<>();
    private final Set<RegionPos> dirtyRegions = new HashSet<>();

    public MapCache(Path dimensionDir) {
        this.dimensionDir = dimensionDir;
        this.loadAllRegions();
    }

    public void updateChunk(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int regionX = Math.floorDiv(chunkPos.x(), 64);
        int regionZ = Math.floorDiv(chunkPos.z(), 64);
        RegionPos regionPos = new RegionPos(regionX, regionZ);
        RegionCache regionCache = regionCacheMap.computeIfAbsent(
            regionPos,
            pos -> loadRegion(pos).orElseGet(() -> new RegionCache(pos.x, pos.z))
        );

        ChunkCache newChunkCache = ChunkCache.create(regionCache, chunk);
        ChunkCache oldChunkCache = regionCache.getChunk(newChunkCache.getX(), newChunkCache.getZ());
        if (newChunkCache.sameData(oldChunkCache)) {
            return;
        }

        regionCache.addChunk(newChunkCache);
        dirtyRegions.add(regionPos);
    }

    public void saveAll() {
        if (dirtyRegions.isEmpty()) {
            return;
        }
        List<RegionPos> regionsToSave = new ArrayList<>(dirtyRegions);
        for (RegionPos regionPos : regionsToSave) {
            RegionCache regionCache = regionCacheMap.get(regionPos);
            if (regionCache == null) {
                continue;
            }
            saveRegion(regionPos, regionCache);
        }
    }

    public @Nullable BlockCache getBlock(int blockX, int blockZ) {
        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        int regionX = Math.floorDiv(chunkX, 64);
        int regionZ = Math.floorDiv(chunkZ, 64);

        RegionCache regionCache = regionCacheMap.get(new RegionPos(regionX, regionZ));
        if (regionCache == null) {
            return null;
        }

        short localChunkX = (short) Math.floorMod(chunkX, 64);
        short localChunkZ = (short) Math.floorMod(chunkZ, 64);
        ChunkCache chunkCache = regionCache.getChunk(localChunkX, localChunkZ);
        if (chunkCache == null) {
            return null;
        }

        int localBlockX = Math.floorMod(blockX, 16);
        int localBlockZ = Math.floorMod(blockZ, 16);
        return chunkCache.getBlock(localBlockX, localBlockZ);
    }

    private void loadAllRegions() {
        regionCacheMap.clear();
        dirtyRegions.clear();
        if (!Files.exists(dimensionDir)) {
            return;
        }

        try {
            Files.createDirectories(dimensionDir);
            try (var stream = Files.list(dimensionDir)) {
                stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(REGION_FILE_PREFIX) && name.endsWith(REGION_FILE_SUFFIX);
                    })
                    .forEach(path -> {
                        try {
                            byte[] data = Files.readAllBytes(path);
                            RegionCache regionCache = RegionCache.deserializeFromGzip(data);
                            regionCacheMap.put(new RegionPos(regionCache.getX(), regionCache.getZ()), regionCache);
                        } catch (IOException exception) {
                            AleeveAtlas.LOGGER.warn("Removing corrupted cache file: {}", path);
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException deleteEx) {
                                AleeveAtlas.LOGGER.warn("Failed to delete corrupted cache file {}", path);
                            }
                        }
                    });
            }
        } catch (IOException exception) {
            AleeveAtlas.LOGGER.warn("Failed to read map cache directory {}", dimensionDir, exception);
        }
    }

    private java.util.Optional<RegionCache> loadRegion(RegionPos regionPos) {
        Path path = regionFilePath(regionPos);
        if (!Files.exists(path)) {
            return java.util.Optional.empty();
        }
        try {
            byte[] data = Files.readAllBytes(path);
            return java.util.Optional.of(RegionCache.deserializeFromGzip(data));
        } catch (IOException exception) {
            AleeveAtlas.LOGGER.warn("Failed to load map cache region {} from {}", regionPos, path, exception);
            return java.util.Optional.empty();
        }
    }

    private void saveRegion(RegionPos regionPos, RegionCache regionCache) {
        Path path = regionFilePath(regionPos);
        try {
            Files.createDirectories(dimensionDir);
            byte[] data = regionCache.serializeToGzip();
            Files.write(path, data);
            dirtyRegions.remove(regionPos);
        } catch (IOException exception) {
            AleeveAtlas.LOGGER.warn("Failed to save map cache region {} to {}", regionPos, path, exception);
        }
    }

    private Path regionFilePath(RegionPos regionPos) {
        return dimensionDir.resolve(REGION_FILE_PREFIX + regionPos.x + "." + regionPos.z + REGION_FILE_SUFFIX);
    }

    public record RegionPos(int x, int z) {
    }
}
