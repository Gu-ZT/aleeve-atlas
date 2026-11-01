package dev.dubhe.map.waypoint;

import dev.dubhe.map.storage.AtlasPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/// 路径点存储。
///
/// 按「服务器标识 → 维度」隔离，与地图缓存同根目录、每维度独立文件，采用 `CompoundTag` 持久化。
public final class WaypointStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/waypoints");
    private static final String DIR_NAME = "waypoints";

    private final Path file;
    private final ResourceKey<Level> dimension;
    private final Map<UUID, Waypoint> waypoints = new LinkedHashMap<>();
    private boolean dirty;

    private WaypointStore(Path file, ResourceKey<Level> dimension) {
        this.file = file;
        this.dimension = dimension;
        this.load();
    }

    /// 接受已读取的存档内容，避免扫描目录时重复读盘。
    private WaypointStore(Path file, ResourceKey<Level> dimension, CompoundTag prefetched) {
        this.file = file;
        this.dimension = dimension;
        this.loadFrom(prefetched);
    }

    /// 打开某服务器标识下、指定维度的路径点存储。
    public static WaypointStore open(Path gameDir, String serverKey, ResourceKey<Level> dimension) {
        Path dir = AtlasPaths.serverRoot(gameDir, serverKey).resolve(DIR_NAME);
        String fileName = sanitize(dimension.location().toString()) + ".nbt";
        return new WaypointStore(dir.resolve(fileName), dimension);
    }

    /// 扫描磁盘上该服务器的全部维度存储。
    ///
    /// 导入导出需要覆盖所有维度，仅遍历内存中已加载的存储会遗漏玩家未曾访问的维度。
    public static List<WaypointStore> openAll(Path gameDir, String serverKey) {
        Path dir = AtlasPaths.serverRoot(gameDir, serverKey).resolve(DIR_NAME);
        List<WaypointStore> stores = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return stores;
        }
        try (var stream = Files.list(dir)) {
            for (Path path : stream.sorted().toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".nbt")) {
                    continue;
                }
                // 维度以文件内容记录的 `dimension` 字段为准：
                // 文件名经过字符替换（冒号变下划线），无法据此还原原始维度标识。
                stores.add(openExisting(path));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to list waypoint stores under {}", dir, e);
        }
        return stores;
    }

    /// 打开已存在的存储文件：只读取一次，维度取自文件头并按该维度过滤条目。
    private static WaypointStore openExisting(Path file) {
        CompoundTag tag = null;
        try {
            tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            LOGGER.warn("Failed to read waypoint file {}", file, e);
        }
        ResourceKey<Level> dimension = tag == null
                ? Level.OVERWORLD
                : resolveDimension(tag.getStringOr("dimension",
                        Level.OVERWORLD.location().toString()));
        WaypointStore store = new WaypointStore(file, dimension, tag);
        return store;
    }

    /// 该存储对应的维度。
    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    /// 存储文件路径（诊断与导入导出用）。
    public Path file() {
        return this.file;
    }

    public Collection<Waypoint> all() {
        return this.waypoints.values();
    }

    public Optional<Waypoint> get(UUID id) {
        return Optional.ofNullable(this.waypoints.get(id));
    }

    /// 该维度下启用且在可见距离内的路径点。
    public List<Waypoint> visibleIn(double x, double z) {
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : this.waypoints.values()) {
            if (!waypoint.enabled()) {
                continue;
            }
            double dx = waypoint.pos().getX() + 0.5 - x;
            double dz = waypoint.pos().getZ() + 0.5 - z;
            if (waypoint.visibleDistance() < 0
                    || dx * dx + dz * dz <= (double) waypoint.visibleDistance()
                    * waypoint.visibleDistance()) {
                result.add(waypoint);
            }
        }
        return result;
    }

    /// 新增或更新路径点。
    public void upsert(Waypoint waypoint) {
        this.waypoints.put(waypoint.id(), waypoint);
        this.dirty = true;
    }

    public boolean remove(UUID id) {
        boolean removed = this.waypoints.remove(id) != null;
        if (removed) {
            this.dirty = true;
        }
        return removed;
    }

    /// 死亡点保留策略：开关关闭时清除历史死亡点。
    public void pruneDeathPoints() {
        boolean changed = this.waypoints.values().removeIf(Waypoint::deathPoint);
        if (changed) {
            this.dirty = true;
        }
    }

    /// 写入磁盘。
    public void flush() {
        if (!this.dirty) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", this.dimension.location().toString());
        tag.put("waypoints", Waypoint.saveAll(this.waypoints.values()));
        try {
            Files.createDirectories(this.file.getParent());
            NbtIo.writeCompressed(tag, this.file);
            this.dirty = false;
        } catch (IOException e) {
            LOGGER.warn("Failed to write waypoints {}", this.file, e);
        }
    }

    public void clear() {
        this.waypoints.clear();
        this.dirty = false;
    }

    private void load() {
        if (!Files.isRegularFile(this.file)) {
            return;
        }
        try {
            this.loadFrom(NbtIo.readCompressed(this.file, NbtAccounter.unlimitedHeap()));
        } catch (IOException e) {
            LOGGER.warn("Failed to read waypoints {}", this.file, e);
        }
    }

    /// 从已读取的存档内容加载条目。
    private void loadFrom(CompoundTag tag) {
        if (tag == null) {
            // 调用方读取失败时传入 null，此处返回空存储，避免空指针中断整个目录扫描。
            LOGGER.warn("Waypoint file {} could not be read, starting with empty store", this.file);
            return;
        }
        ListTag list = tag.getListOrEmpty("waypoints");
        for (Waypoint waypoint : Waypoint.loadAll(list, WaypointStore::resolveDimension)) {
            // 仅接受属于本存储维度的条目，防止文件被移动后串维度。
            if (waypoint.dimension().equals(this.dimension)) {
                this.waypoints.put(waypoint.id(), waypoint);
            }
        }
        this.dirty = false;
    }

    /// 依次尝试解析维度标识，未知维度回退主世界。
    private static ResourceKey<Level> resolveDimension(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            // 单个条目维度非法时回退主世界，留日志便于定位损坏的存档数据。
            LOGGER.warn("Invalid dimension id '{}' in waypoint store, using overworld", raw);
            return Level.OVERWORLD;
        }
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    /// 维度标识中的非法文件名字符替换为下划线。
    private static String sanitize(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
