package dev.dubhe.map.client.cache;

import lombok.Getter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

@Getter
public class MapCache {
    private final Map<RegionPos, RegionCache> regionCacheMap = new HashMap<>();

    public MapCache() {
    }

    public void addChunk(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int regionX = chunkPos.x() / 64;
        int regionY = chunkPos.z() / 64;
        RegionPos regionPos = new RegionPos(regionX, regionY);
        RegionCache regionCache = regionCacheMap.computeIfAbsent(regionPos, pos -> new RegionCache(pos.x, pos.z));
        regionCache.addChunk(chunk);
    }

    public record RegionPos(int x, int z) {
    }
}
