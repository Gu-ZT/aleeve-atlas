package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.config.MapColorScheme;
import dev.dubhe.map.map.CaveMode;
import dev.dubhe.map.map.MapSlice;
import dev.dubhe.map.map.ChunkMapData;
import dev.dubhe.map.map.ChunkMapSampler;
import dev.dubhe.map.map.DirtyChunks;
import dev.dubhe.map.map.MapDimensionCache;
import dev.dubhe.map.storage.AtlasPaths;
import dev.dubhe.map.storage.ServerKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// 客户端地图数据管理器。
///
/// 负责：按「服务器标识 → 维度」隔离地图缓存、按 tick 限速采样玩家周围区块、
/// 把采样结果写入小地图纹理。采样仅在区块加载时进行，稳态跑图不重复采样。
public final class ClientMapManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/clientmap");
    /// 每 tick 最多采样的区块数，避免集中采样造成掉帧。
    private static final int SAMPLES_PER_TICK = 6;
    /// 小地图采样半径，额外覆盖方形旋转所需的对角线范围。
    public static final int VIEW_CHUNK_RADIUS = 6;
    /// 待重新采样的区块：方块变更时由 {@code ClientLevelMixin} 标记。
    ///
    /// 以 {@link ChunkPos#asLong} 编码为键。标记与消费都在客户端主线程：
    /// 标记来自方块更新包处理，消费来自客户端 tick，无需并发容器。
    private static final DirtyChunks DIRTY_SURFACE_CHUNKS = new DirtyChunks();
    private static final DirtyChunks DIRTY_CAVE_CHUNKS = new DirtyChunks();

    private final MapTexture texture = new MapTexture(AleeveAtlas.id("minimap"));
    private final Map<String, MapDimensionCache> caches = new HashMap<>();
    /// 洞穴分层缓存：与地表缓存并行存在，切回地表时立即恢复。
    private MapDimensionCache caveCache;
    private String currentServerKey;
    private ResourceKey<Level> currentDimension;
    private MapColorScheme colorScheme = MapColorScheme.VANILLA;
    private boolean textureRegistered;
    private boolean loggedFirstSample;
    private MapSlice displaySlice = MapSlice.SURFACE;
    private final CaveMode.Tracker caveTracker = new CaveMode.Tracker();
    private long mappingTicks;
    private boolean depthShading = true;

    public MapSlice displaySlice() { return this.displaySlice; }

    /// 显示层与同步地表缓存分离，洞穴数据不能通过现有地表协议覆盖队友的地表图。
    public MapDimensionCache displayCache() {
        return this.displaySlice.cave() && this.caveCache != null ? this.caveCache : this.currentCache();
    }

    /// 注册纹理；资源重载后需重新注册。
    public void ensureRegistered() {
        if (!this.textureRegistered) {
            this.texture.register();
            this.textureRegistered = true;
        }
    }

    /// 资源重载后重置注册标志，使下次 tick 重新注册动态纹理。
    public void onResourceReload() {
        this.textureRegistered = false;
    }

    /// 标记某区块需要重新采样。
    ///
    /// 由客户端方块变更监听调用，使地图反映破坏与放置的结果。
    public static void markChunkChanged(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        DIRTY_SURFACE_CHUNKS.mark(key);
        DIRTY_CAVE_CHUNKS.mark(key);
    }

    /// 当前会话积累的地表/洞穴更新标记数，供周期性能日志诊断。
    public static String dirtyChunkSummary() {
        return "dirty chunks: surface=%d cave=%d".formatted(
                DIRTY_SURFACE_CHUNKS.size(), DIRTY_CAVE_CHUNKS.size());
    }

    /// 每个客户端 tick 调用：采样玩家周围缺失的区块。
    public void tick(ClientLevel level) {
        this.ensureRegistered();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String serverKey = serverKey(minecraft);
        MapColorScheme scheme = AtlasClientConfigs.CONFIG.minimap.colorScheme;
        boolean paletteChanged = scheme != this.colorScheme;
        boolean schemeChanged = paletteChanged || this.depthShading != AtlasClientConfigs.CONFIG.caves.depthShading;
        this.depthShading = AtlasClientConfigs.CONFIG.caves.depthShading;
        this.colorScheme = scheme;
        if (schemeChanged) {
            LOGGER.info("Map color scheme changed to {}", scheme);
            if (paletteChanged) this.texture.setColorScheme(scheme);
            else this.texture.recolor();
        }
        final MapDimensionCache cache = this.cacheFor(minecraft, serverKey, level.dimension());
        if (!serverKey.equals(this.currentServerKey) || !level.dimension().equals(this.currentDimension)) {
            // 切换服务器或维度前先写入磁盘，避免未到周期写盘时丢失洞穴层数据。
            if (this.caveCache != null) {
                this.caveCache.flush();
            }
            this.currentServerKey = serverKey;
            this.currentDimension = level.dimension();
            DIRTY_SURFACE_CHUNKS.clear();
            DIRTY_CAVE_CHUNKS.clear();
            this.caveCache = null;
            this.displaySlice = MapSlice.SURFACE;
            this.caveTracker.reset();
            this.texture.clearAll();
        }

        final ChunkPos center = minecraft.player.chunkPosition();
        MapSlice nextSlice = this.caveTracker.update(CaveMode.detect(level,
                minecraft.player.blockPosition(), AtlasClientConfigs.CONFIG.caves), ++this.mappingTicks);
        boolean sliceChanged = !nextSlice.equals(this.displaySlice);
        boolean layerChanged = !nextSlice.cacheLayer().equals(this.displaySlice.cacheLayer());
        if (layerChanged || nextSlice.cave() && this.caveCache == null) {
            if (this.caveCache != null) this.caveCache.flush();
            this.caveCache = nextSlice.cave()
                    ? this.caveCacheFor(minecraft, serverKey, level.dimension(), nextSlice) : null;
            this.texture.clearAll();
        }
        this.displaySlice = nextSlice;
        boolean caveMode = nextSlice.cave();
        if (layerChanged && caveMode) {
            // 重新进入旧层时刷新当前视距内的数据，避免复用在其它层活动期间已改变的地形。
            for (int dx = -VIEW_CHUNK_RADIUS; dx <= VIEW_CHUNK_RADIUS; dx++) {
                for (int dz = -VIEW_CHUNK_RADIUS; dz <= VIEW_CHUNK_RADIUS; dz++) {
                    DIRTY_CAVE_CHUNKS.mark(ChunkPos.asLong(center.x + dx, center.z + dz));
                }
            }
        }
        this.texture.setCaveMode(caveMode);
        this.texture.setImportedStore(dev.dubhe.map.client.cartography.ClientCartography.currentStore());
        this.texture.centerOn(minecraft.player.getX(), minecraft.player.getZ());
        MapDimensionCache activeCache = caveMode ? this.caveCache : cache;

        // 方块变更后保留受影响区块的旧图像，直到新的采样成功完成。
        // 主线程直接查询当前范围，避免每 tick 复制全部历史标记。
        DirtyChunks dirtyChunks = caveMode ? DIRTY_CAVE_CHUNKS : DIRTY_SURFACE_CHUNKS;

        int sampled = 0;
        int radius = VIEW_CHUNK_RADIUS;
        for (int ring = 0; ring <= radius && sampled < SAMPLES_PER_TICK; ring++) {
            for (int dx = -ring; dx <= ring && sampled < SAMPLES_PER_TICK; dx++) {
                for (int dz = -ring; dz <= ring && sampled < SAMPLES_PER_TICK; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int chunkX = center.x + dx;
                    int chunkZ = center.z + dz;
                    long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                    Optional<ChunkMapData> existing = activeCache.get(chunkX, chunkZ);
                    if (existing.isPresent() && existing.get().slice().equals(nextSlice) && !dirtyChunks.contains(chunkKey)) {
                        dev.dubhe.map.client.structure.ClientStructures.sampled(chunkX, chunkZ);
                        this.texture.putChunk(existing.get());
                        continue;
                    }
                    ChunkMapData sampledData = ChunkMapSampler.sample(level,
                            new ChunkPos(chunkX, chunkZ), nextSlice);
                    if (sampledData == null) {
                        continue;
                    }
                    sampledData.setVersion(System.currentTimeMillis());
                    // 分层数据写入独立缓存，不污染地表地图。
                    if (caveMode) {
                        this.caveCache.put(sampledData);
                    } else {
                        cache.put(sampledData);
                    }
                    dirtyChunks.sampled(chunkKey);
                    this.texture.putChunk(sampledData);
                    dev.dubhe.map.client.structure.ClientStructures.sampled(chunkX, chunkZ);
                    sampled++;
                }
            }
        }
        // 合并本 tick 的像素改动，只上传一次纹理。
        this.texture.flushDirty();
        if (sampled > 0 || schemeChanged || sliceChanged) {
            WorldMapScreen.onMapDataChanged();
        }
        if (sampled > 0 && !this.loggedFirstSample) {
            this.loggedFirstSample = true;
            LOGGER.info("First map sample written: {} chunk(s) around {},{}, cache dir {}",
                    sampled, center.x, center.z, cache.dimensionDir());
        }
    }

    /// 释放小地图动态纹理（断开连接时调用，避免 GPU 纹理泄漏）。
    public void releaseTexture() {
        this.texture.close();
    }

    public MapTexture texture() {
        return this.texture;
    }

    /// 维度缓存；调用前需保证在客户端主线程。
    public MapDimensionCache currentCache() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        return this.cacheFor(minecraft, serverKey(minecraft), minecraft.level.dimension());
    }

    /// 同步可访问其他维度的地表缓存，不能把远端维度写入当前地图。
    public MapDimensionCache syncCache(String dimensionId) {
        Minecraft minecraft = Minecraft.getInstance();
        Identifier id = Identifier.tryParse(dimensionId);
        if (minecraft.level == null || id == null) {
            return null;
        }
        return this.cacheFor(minecraft, serverKey(minecraft), ResourceKey.create(Registries.DIMENSION, id));
    }

    public boolean hasSampledChunk(int chunkX, int chunkZ) {
        MapDimensionCache surface = this.currentCache();
        Minecraft minecraft = Minecraft.getInstance();
        boolean sameWorld = minecraft.level != null && minecraft.level.dimension().equals(this.currentDimension)
                && serverKey(minecraft).equals(this.currentServerKey);
        return (surface != null && surface.get(chunkX, chunkZ).isPresent())
                || (sameWorld && this.caveCache != null && this.caveCache.get(chunkX, chunkZ).isPresent());
    }

    public void flush() {
        for (MapDimensionCache cache : this.caches.values()) {
            cache.flush();
        }
        // 洞穴分层缓存独立于地表缓存，必须一并写入磁盘。
        if (this.caveCache != null) {
            this.caveCache.flush();
        }
    }

    public void clear() {
        if (this.caveCache != null) {
            this.caveCache.flush();
            this.caveCache = null;
        }
        this.caches.clear();
        this.caveTracker.reset();
        this.displaySlice = MapSlice.SURFACE;
        this.currentServerKey = null;
        this.currentDimension = null;
        // 脏标记属会话级状态：切换服务器后旧坐标不应触发新会话的缓存失效。
        DIRTY_SURFACE_CHUNKS.clear();
        DIRTY_CAVE_CHUNKS.clear();
        this.texture.clearAll();
    }

    private MapDimensionCache cacheFor(Minecraft minecraft, String serverKey, ResourceKey<Level> dimension) {
        String key = serverKey + "/" + dimension.identifier();
        return this.caches.computeIfAbsent(key, ignored -> {
            Path base = AtlasPaths.dimensionDir(gameDir(minecraft), serverKey, dimension.identifier());
            return new MapDimensionCache(base);
        });
    }

    /// 地表、完整洞穴和 16 格分层独立持久化。
    private MapDimensionCache caveCacheFor(Minecraft minecraft, String serverKey,
            ResourceKey<Level> dimension, MapSlice slice) {
        Path base = AtlasPaths.dimensionDir(gameDir(minecraft), serverKey, dimension.identifier());
        Path caveBase = base.resolve("caves").resolve(slice.cacheLayer());
        return new MapDimensionCache(caveBase);
    }

    private static Path gameDir(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath();
    }

    /// 服务器/存档标识。
    private static String serverKey(Minecraft minecraft) {
        return ServerKey.resolveMapCache(minecraft);
    }
}
