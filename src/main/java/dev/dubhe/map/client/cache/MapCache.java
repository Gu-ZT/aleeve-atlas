package dev.dubhe.map.client.cache;

import dev.dubhe.map.AleeveAtlas;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
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

@Getter
public class MapCache {
    private static final String REGION_FILE_PREFIX = "r.";
    private static final String REGION_FILE_SUFFIX = ".nbt";

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
        RegionCache regionCache = regionCacheMap.computeIfAbsent(regionPos, pos -> loadRegion(pos).orElseGet(() -> new RegionCache(pos.x, pos.z)));
        regionCache.addChunk(chunk);
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
                            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                            RegionCache regionCache = RegionCache.deserialize(tag);
                            regionCacheMap.put(new RegionPos(regionCache.getX(), regionCache.getZ()), regionCache);
                        } catch (IOException exception) {
                            AleeveAtlas.LOGGER.warn("Failed to load map cache region file {}", path, exception);
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
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            return java.util.Optional.of(RegionCache.deserialize(tag));
        } catch (IOException exception) {
            AleeveAtlas.LOGGER.warn("Failed to load map cache region {} from {}", regionPos, path, exception);
            return java.util.Optional.empty();
        }
    }

    private void saveRegion(RegionPos regionPos, RegionCache regionCache) {
        Path path = regionFilePath(regionPos);
        try {
            Files.createDirectories(dimensionDir);
            NbtIo.writeCompressed(regionCache.serialize(), path);
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
