package dev.dubhe.map.map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// 单维度的区块地图缓存。
///
/// 按区域文件（32×32 区块）持久化，避免单文件过大；数据格式为 `CompoundTag`。
public final class MapDimensionCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/mapcache");
    private static final int REGION_SHIFT = 5;
    private static final int REGION_SIZE = 1 << REGION_SHIFT;

    /// 同时驻留的区域文件上限；超出时淘汰最久未用的已保存区域。
    private static final int MAX_LOADED_REGIONS = 32;

    private final Path dimensionDir;
    private final Map<Long, ChunkMapData> chunks = new HashMap<>();
    private long revision;
    /// 访问顺序即 LRU 顺序。
    private final Map<Long, RegionFile> loadedRegions = new LinkedHashMap<>(16, 0.75F,
            true);

    public MapDimensionCache(Path dimensionDir) {
        this.dimensionDir = dimensionDir;
    }

    public Optional<ChunkMapData> get(int chunkX, int chunkZ) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        ChunkMapData cached = this.chunks.get(key);
        if (cached != null) {
            // 内存命中也要刷新所属区域的 LRU 顺序，避免热点区域被误淘汰。
            // loadedRegions 为访问序 LinkedHashMap，get 的副作用即把该区域移到队尾。
            this.loadedRegions.get(ChunkPos.pack(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
            return Optional.of(cached);
        }
        RegionFile region = this.regionFor(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);
        ChunkMapData loaded = region.get(chunkX & (REGION_SIZE - 1), chunkZ & (REGION_SIZE - 1));
        if (loaded != null) {
            this.chunks.put(key, loaded);
            return Optional.of(loaded);
        }
        return Optional.empty();
    }

    /// 丢弃某区块的缓存数据，使其下次访问时重新采样。
    ///
    /// 从内存的区域条目中移除并标记脏，实际的磁盘写入交由 `flush` 批量完成。
    /// 单次方块变更即写盘会在爆炸等场景下反复重写整个区域文件。
    public void invalidate(int chunkX, int chunkZ) {
        this.revision++;
        this.chunks.remove(ChunkPos.pack(chunkX, chunkZ));
        this.regionFor(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT)
                .remove(chunkX & (REGION_SIZE - 1), chunkZ & (REGION_SIZE - 1));
    }

    public void put(ChunkMapData data) {
        this.revision++;
        long key = ChunkPos.pack(data.chunkX(), data.chunkZ());
        this.chunks.put(key, data);
        RegionFile region = this.regionFor(data.chunkX() >> REGION_SHIFT, data.chunkZ() >> REGION_SHIFT);
        region.put(data.chunkX() & (REGION_SIZE - 1), data.chunkZ() & (REGION_SIZE - 1), data);
        region.dirty = true;
    }

    /// 当前已访问的内存区块；同步全量地图应使用 snapshotForSync。
    public List<ChunkMapData> loadedChunks() {
        return List.copyOf(this.chunks.values());
    }

    public long revision() {
        return this.revision;
    }

    /// 主线程捕获已加载区域的快照；磁盘遍历与解压由后台线程完成，不改变渲染缓存的 LRU。
    public SyncSnapshot snapshotForSync() {
        Map<Path, List<ChunkMapData>> regions = new HashMap<>();
        for (RegionFile region : this.loadedRegions.values()) {
            regions.put(region.path, List.copyOf(region.entries.values()));
        }
        return new SyncSnapshot(this.dimensionDir, Map.copyOf(regions));
    }

    /// 区块写入缓存后不再修改；快照保留对象引用，后台只读。
    public record SyncSnapshot(Path directory, Map<Path, List<ChunkMapData>> loaded) {
        /// 远景只读取视口内已存在的区域，不遍历空区块，也不扰动渲染缓存的 LRU。
        public List<ChunkMapData> readBounds(int minX, int minZ, int maxX, int maxZ) throws IOException {
            List<ChunkMapData> result = new ArrayList<>();
            for (var entry : loaded.entrySet()) {
                if (intersects(entry.getKey(), minX, minZ, maxX, maxZ)) result.addAll(entry.getValue());
            }
            if (Files.isDirectory(directory)) {
                try (var paths = Files.newDirectoryStream(directory, "r.*.*.nbt")) {
                    for (Path path : paths) {
                        if (!loaded.containsKey(path) && intersects(path, minX, minZ, maxX, maxZ)) {
                            result.addAll(new RegionFile(path).entries.values());
                        }
                    }
                }
            }
            result.removeIf(data -> data.chunkX() < minX || data.chunkX() >= maxX
                    || data.chunkZ() < minZ || data.chunkZ() >= maxZ);
            return result;
        }

        private static boolean intersects(Path path, int minX, int minZ, int maxX, int maxZ) {
            String[] parts = path.getFileName().toString().split("\\.");
            if (parts.length != 4) return false;
            try {
                long x = Long.parseLong(parts[1]) * REGION_SIZE;
                long z = Long.parseLong(parts[2]) * REGION_SIZE;
                return x < maxX && z < maxZ && x + REGION_SIZE > minX && z + REGION_SIZE > minZ;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        public List<ChunkMapData> readAll() throws IOException {
            List<ChunkMapData> result = new ArrayList<>();
            loaded.values().forEach(result::addAll);
            if (Files.isDirectory(directory)) {
                try (var paths = Files.newDirectoryStream(directory, "r.*.*.nbt")) {
                    for (Path path : paths) {
                        if (!loaded.containsKey(path)) {
                            result.addAll(new RegionFile(path).entries.values());
                        }
                    }
                }
            }
            return result;
        }
    }

    /// 该缓存对应的维度目录（诊断用）。
    /// 当前驻留的区域文件数（诊断与 LRU 测试用）。
    public int loadedRegionCount() {
        return this.loadedRegions.size();
    }

    public Path dimensionDir() {
        return this.dimensionDir;
    }

    public void flush() {
        for (RegionFile region : this.loadedRegions.values()) {
            if (region.dirty) {
                region.save();
            }
        }
    }

    private RegionFile regionFor(int regionX, int regionZ) {
        long key = ChunkPos.pack(regionX, regionZ);
        RegionFile existing = this.loadedRegions.get(key);
        if (existing != null) {
            return existing;
        }
        this.evictRegionsIfNeeded();
        RegionFile created = new RegionFile(
                this.dimensionDir.resolve("r." + regionX + "." + regionZ + ".nbt"));
        this.loadedRegions.put(key, created);
        return created;
    }

    /// 超过驻留上限时淘汰最久未用的区域文件（淘汰前先写入磁盘）。
    ///
    /// 调用发生在插入新区域之前，故以 `>` 判定，使驻留数量可达 `MAX_LOADED_REGIONS`。
    private void evictRegionsIfNeeded() {
        while (this.loadedRegions.size() > MAX_LOADED_REGIONS - 1) {
            Iterator<Map.Entry<Long, RegionFile>> iterator =
                    this.loadedRegions.entrySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            Map.Entry<Long, RegionFile> eldest = iterator.next();
            RegionFile region = eldest.getValue();
            if (region.dirty) {
                region.save();
            }
            iterator.remove();
            // 同时释放该区域内的区块缓存，避免区块对象无限堆积。
            for (ChunkMapData data : region.entries.values()) {
                this.chunks.remove(ChunkPos.pack(data.chunkX(), data.chunkZ()));
            }
        }
    }

    /// 区域文件：32×32 区块的集合。
    private static final class RegionFile {
        private final Path path;
        private final Map<Long, ChunkMapData> entries = new HashMap<>();
        private boolean dirty;

        private RegionFile(Path path) {
            this.path = path;
            this.load();
        }

        private void load() {
            if (!Files.isRegularFile(this.path)) {
                return;
            }
            try {
                CompoundTag tag = NbtIo.readCompressed(this.path, NbtAccounter.unlimitedHeap());
                CompoundTag chunksTag = tag.getCompoundOrEmpty("chunks");
                for (String key : chunksTag.keySet()) {
                    CompoundTag chunkTag = chunksTag.getCompoundOrEmpty(key);
                    ChunkMapData data = ChunkMapData.load(chunkTag);
                    this.entries.put(ChunkPos.pack(data.chunkX() & (REGION_SIZE - 1),
                            data.chunkZ() & (REGION_SIZE - 1)), data);
                }
            } catch (IOException | RuntimeException e) {
                this.entries.clear();
                LOGGER.warn("Discarding unreadable map region {}", this.path, e);
                this.quarantine();
            }
        }

        /// 保留损坏文件供诊断，正式路径由后续采样重新生成。
        private void quarantine() {
            Path corrupted = this.path.resolveSibling(this.path.getFileName() + ".corrupt");
            try {
                Files.move(this.path, corrupted, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("Failed to quarantine unreadable map region {}", this.path, e);
            }
        }

        private ChunkMapData get(int localX, int localZ) {
            return this.entries.get(ChunkPos.pack(localX, localZ));
        }

        private void put(int localX, int localZ, ChunkMapData data) {
            this.entries.put(ChunkPos.pack(localX, localZ), data);
        }

        private boolean remove(int localX, int localZ) {
            if (this.entries.remove(ChunkPos.pack(localX, localZ)) == null) {
                return false;
            }
            this.dirty = true;
            return true;
        }

        private void save() {
            CompoundTag tag = new CompoundTag();
            CompoundTag chunksTag = new CompoundTag();
            for (Map.Entry<Long, ChunkMapData> entry : this.entries.entrySet()) {
                chunksTag.put(Long.toString(entry.getKey()), entry.getValue().save());
            }
            tag.put("chunks", chunksTag);
            Path temporary = this.path.resolveSibling(this.path.getFileName() + ".tmp");
            try {
                Files.createDirectories(this.path.getParent());
                NbtIo.writeCompressed(tag, temporary);
                try {
                    Files.move(temporary, this.path, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING);
                }
                this.dirty = false;
            } catch (IOException e) {
                LOGGER.warn("Failed to write map region {}", this.path, e);
            }
        }
    }
}
