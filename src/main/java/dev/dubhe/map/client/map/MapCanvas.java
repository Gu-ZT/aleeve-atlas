package dev.dubhe.map.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.dubhe.map.client.profiling.AtlasProfiling;
import dev.dubhe.map.config.MapColorScheme;
import dev.dubhe.map.cartography.ImportedMapStore;
import dev.dubhe.map.cartography.MapSnapshot;
import dev.dubhe.map.client.cartography.ClientCartography;
import net.minecraft.world.level.material.MapColor;
import java.util.List;
import dev.dubhe.map.map.ChunkColorizer;
import dev.dubhe.map.map.ChunkMapData;
import dev.dubhe.map.map.MapDimensionCache;
import dev.dubhe.map.map.MapPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/// 全屏世界地图的画布纹理。
///
/// 近景逐方块绘制，远景按视口降低采样密度，始终保持固定大小的纹理。
public final class MapCanvas {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/mapcanvas");
    /// 画布覆盖的区块数（每边）。
    ///
    /// 近景覆盖 1040 个方块；缩小时每个纹理像素覆盖更多方块。
    public static final int CHUNKS_PER_SIDE = 65;
    /// 画布边长（像素）。
    public static final int PIXELS = CHUNKS_PER_SIDE * ChunkMapData.SIZE;
    /// 触发画布重建的最小位移（区块）。
    ///
    /// 渲染端按画布原点与视野中心的差值补偿采样位置，因此允许画布滞后于视野；
    /// 取 2 区块（32 像素）可把拖拽期间的重建次数降低约一半。
    private static final int RECENTER_THRESHOLD_CHUNKS = 2;
    /// 未探索区域底色（接近纯黑，保持低干扰）。
    private static final int UNEXPLORED_BASE_COLOR = 0xFF0A0A0A;
    /// 未探索区域网格线颜色。
    private static final int UNEXPLORED_GRID_COLOR = 0xFF1E1E1E;

    private final DynamicTexture texture;
    private final NativeImage image;
    private final ResourceLocation textureId;
    private int originChunkX;
    private int originChunkZ;
    private boolean initialized;
    /// 是否已按某个原点完成过一次定位；未定位时不能做像素搬移。
    private boolean positioned;
    private final Map<Long, Long> drawnVersions = new HashMap<>();
    private MapColorScheme colorScheme = MapColorScheme.VANILLA;
    private ImportedMapStore importedStore;
    private long importedRevision = -1;
    private List<MapSnapshot> importedMaps = List.of();
    private boolean showUnexploredGrid = true;
    private int blocksPerPixel = 1;
    private static final ExecutorService OVERVIEW_READER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "atlas-map-overview");
        thread.setDaemon(true);
        return thread;
    });
    private OverviewView requestedOverview;
    private OverviewView pendingOverview;
    private CompletableFuture<List<ChunkMapData>> overviewRead;
    private long pendingRevision;
    private long drawnOverviewRevision = -1;
    private OverviewView drawnOverview;
    private MapDimensionCache displayedCache;
    private boolean caveView;
    private boolean depthShading;

    public void setCaveView(boolean caveView, boolean depthShading) {
        if (this.caveView == caveView && this.depthShading == depthShading) return;
        this.caveView = caveView;
        this.depthShading = depthShading;
        this.positioned = false;
        this.drawnOverview = null;
        this.pendingOverview = null;
        this.overviewRead = null;
    }

    private record OverviewView(MapDimensionCache cache, int x, int z, int stride,
            MapColorScheme scheme, ImportedMapStore store, long importedRevision, boolean grid) { }

    public int blocksPerPixel() { return this.blocksPerPixel; }

    public int worldSize() { return PIXELS * this.blocksPerPixel; }

    static int resolutionFor(int width, int height, float zoom) {
        int stride = 1;
        double required = Math.max(width, height) / (double) zoom;
        while ((PIXELS - 64.0) * stride < required) stride *= 2;
        return stride;
    }

    public void setCenter(int centerChunkX, int centerChunkZ, MapDimensionCache cache,
            MapColorScheme scheme, int width, int height, float zoom) {
        boolean grid = zoom >= 0.8F;
        if (grid != this.showUnexploredGrid) {
            this.showUnexploredGrid = grid;
            this.positioned = false;
        }
        int stride = resolutionFor(width, height, zoom);
        if (stride == 1 || cache == null) {
            this.requestedOverview = null;
            if (this.blocksPerPixel != 1) {
                this.blocksPerPixel = 1;
                this.positioned = false;
            }
            this.drawnOverview = null;
            this.setCenter(centerChunkX, centerChunkZ, cache, scheme);
            return;
        }
        int x = Math.floorDiv(centerChunkX - CHUNKS_PER_SIDE * stride / 2, stride) * stride;
        int z = Math.floorDiv(centerChunkZ - CHUNKS_PER_SIDE * stride / 2, stride) * stride;
        ImportedMapStore store = this.caveView ? null : ClientCartography.currentStore();
        this.requestedOverview = new OverviewView(cache, x, z, stride, scheme, store,
                store == null ? -1 : store.revision(), grid);
        this.pollOverview();
    }

    /// 仅后台读取区域快照；纹理及其元数据始终一起在渲染线程替换。
    public void pollOverview() {
        if (this.overviewRead != null && this.overviewRead.isDone()) {
            try {
                List<ChunkMapData> chunks = this.overviewRead.join();
                if (this.pendingOverview.equals(this.requestedOverview)) {
                    this.drawOverview(this.pendingOverview, chunks);
                    this.drawnOverview = this.pendingOverview;
                    this.drawnOverviewRevision = this.pendingRevision;
                }
            } catch (CompletionException e) {
                LOGGER.warn("Failed to read world map overview", e.getCause());
            }
            this.overviewRead = null;
        }
        OverviewView view = this.requestedOverview;
        if (view == null || this.overviewRead != null
                || (view.equals(this.drawnOverview) && view.cache.revision() == this.drawnOverviewRevision)) return;
        this.pendingOverview = view;
        this.pendingRevision = view.cache.revision();
        var snapshot = view.cache.snapshotForSync();
        this.overviewRead = CompletableFuture.supplyAsync(() -> {
            try {
                return snapshot.readBounds(view.x, view.z,
                        view.x + CHUNKS_PER_SIDE * view.stride, view.z + CHUNKS_PER_SIDE * view.stride);
            } catch (java.io.IOException e) {
                throw new CompletionException(e);
            }
        }, OVERVIEW_READER);
    }

    private void drawOverview(OverviewView view, List<ChunkMapData> chunks) {
        this.blocksPerPixel = view.stride;
        this.originChunkX = view.x;
        this.originChunkZ = view.z;
        this.colorScheme = view.scheme;
        this.positioned = false;
        this.drawnVersions.clear();
        this.importedMaps = view.store == null ? List.of() : view.store.intersecting(view.x * 16, view.z * 16,
                view.x * 16 + this.worldSize(), view.z * 16 + this.worldSize());
        for (int z = 0; z < PIXELS; z++) {
            for (int x = 0; x < PIXELS; x++) {
                int imported = this.importedColor(x * view.stride, z * view.stride);
                boolean line = view.grid && (((x * view.stride) & 3) == 0 || ((z * view.stride) & 3) == 0);
                this.setPixel(x, z, imported != 0 ? imported : line ? UNEXPLORED_GRID_COLOR : UNEXPLORED_BASE_COLOR);
            }
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int step = Math.min(16, view.stride);
        for (ChunkMapData data : chunks) {
            int[] lighting = ChunkColorizer.lighting(data, view.scheme);
            for (int z = 0; z < 16; z += step) {
                for (int x = 0; x < 16; x += step) {
                    int sampleX = x + step / 2, sampleZ = z + step / 2;
                    int shadow = lighting == null ? 0 : lighting[sampleZ * 16 + sampleX];
                    int color = ChunkColorizer.colorAt(data, sampleX, sampleZ, view.scheme,
                            Minecraft.getInstance().level, cursor, shadow);
                    if (color != 0) this.setPixel(((data.chunkX() - view.x) * 16 + x) / view.stride,
                            ((data.chunkZ() - view.z) * 16 + z) / view.stride, color);
                }
            }
        }
        if (this.initialized) this.texture.upload();
    }

    public MapCanvas(ResourceLocation textureId) {
        this.textureId = textureId;
        this.image = new NativeImage(PIXELS, PIXELS, false);
        this.texture = new DynamicTexture(() -> "aleeve_atlas world map", this.image);
    }

    public void register() {
        Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
        this.initialized = true;
    }

    /// 资源重载后标记为未初始化，使下次打开世界地图时重新注册纹理。
    ///
    /// 资源重载会清空纹理管理器，若继续认为纹理有效，画布会渲染为空白。
    public void onResourceReload() {
        this.requestedOverview = null;
        this.drawnOverview = null;
        this.initialized = false;
        this.positioned = false;
    }

    public ResourceLocation textureId() {
        return this.textureId;
    }

    public boolean ready() {
        return this.initialized;
    }

    public int originChunkX() {
        return this.originChunkX;
    }

    public int originChunkZ() {
        return this.originChunkZ;
    }

    int pixelAt(int x, int z) {
        return this.image.getPixel(x, z);
    }

    int drawnVersionCount() {
        return this.drawnVersions.size();
    }

    /// 以给定区块为中心的视野重建画布；视野未变化时直接返回。
    ///
    /// 原点小幅平移时复用已有像素并只补绘新进入边界的条带，
    /// 避免拖拽过程中每次都遍历全部区块。
    public void setCenter(int centerChunkX, int centerChunkZ, MapDimensionCache cache,
            MapColorScheme scheme) {
        if (this.displayedCache != cache) {
            this.displayedCache = cache;
            this.positioned = false;
        }
        int targetX = centerChunkX - CHUNKS_PER_SIDE / 2;
        int targetZ = centerChunkZ - CHUNKS_PER_SIDE / 2;
        ImportedMapStore nextStore = this.caveView ? null : ClientCartography.currentStore();
        if (nextStore != this.importedStore || (nextStore != null && nextStore.revision() != this.importedRevision)) {
            this.importedStore = nextStore;
            this.importedRevision = nextStore == null ? -1 : nextStore.revision();
            this.positioned = false;
        }
        if (nextStore != null) this.importedMaps = nextStore.intersecting(targetX * 16 - 32, targetZ * 16 - 32,
                (targetX + CHUNKS_PER_SIDE) * 16 + 32, (targetZ + CHUNKS_PER_SIDE) * 16 + 32);
        else this.importedMaps = List.of();
        if (scheme != this.colorScheme) {
            this.colorScheme = scheme;
            this.positioned = false;
        }
        if (this.positioned && targetX == this.originChunkX && targetZ == this.originChunkZ) {
            this.refreshChangedChunks(cache);
            return;
        }
        if (this.positioned
                && Math.abs(targetX - this.originChunkX) < RECENTER_THRESHOLD_CHUNKS
                && Math.abs(targetZ - this.originChunkZ) < RECENTER_THRESHOLD_CHUNKS) {
            this.refreshChangedChunks(cache);
            return;
        }
        long startedAt = System.nanoTime();
        // 仅当已有画布内容且位移在覆盖范围内时才复用像素，首次定位与大幅跳转走全量重绘。
        boolean scrolled = this.positioned
                && Math.abs(targetX - this.originChunkX) < CHUNKS_PER_SIDE
                && Math.abs(targetZ - this.originChunkZ) < CHUNKS_PER_SIDE
                && (targetX != this.originChunkX || targetZ != this.originChunkZ);
        if (scrolled) {
            long scrollStartedAt = System.nanoTime();
            this.shiftOrigin(targetX, targetZ, cache);
            AtlasProfiling.recordCanvasScroll(System.nanoTime() - scrollStartedAt);
            this.refreshChangedChunks(cache);
        } else {
            this.originChunkX = targetX;
            this.originChunkZ = targetZ;
            this.redraw(cache);
            this.positioned = true;
        }
        AtlasProfiling.recordCanvas(System.nanoTime() - startedAt);
    }

    /// 仅重绘缓存版本发生变化的槽位，探索时无需重建整张画布。
    private void refreshChangedChunks(MapDimensionCache cache) {
        if (cache == null) {
            return;
        }
        boolean changed = false;
        for (int dx = 0; dx < CHUNKS_PER_SIDE; dx++) {
            for (int dz = 0; dz < CHUNKS_PER_SIDE; dz++) {
                int chunkX = this.originChunkX + dx;
                int chunkZ = this.originChunkZ + dz;
                long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
                Long drawn = this.drawnVersions.get(key);
                ChunkMapData data = cache.get(chunkX, chunkZ).orElse(null);
                if (data == null && drawn == null) {
                    continue;
                }
                if (data != null && drawn != null && drawn == data.version()) {
                    continue;
                }
                this.redrawChunk(dx, dz, cache);
                changed = true;
            }
        }
        if (changed && this.initialized) {
            this.texture.upload();
        }
    }

    /// 平移到新原点：先把重叠区域按区块粒度搬移，再补绘边缘条带。
    private void shiftOrigin(int targetX, int targetZ, MapDimensionCache cache) {
        int shiftChunksX = targetX - this.originChunkX;
        int shiftChunksZ = targetZ - this.originChunkZ;
        int originShiftPixelsX = shiftChunksX * ChunkMapData.SIZE;
        int originShiftPixelsZ = shiftChunksZ * ChunkMapData.SIZE;
        movePixels(this.image, originShiftPixelsX, originShiftPixelsZ);
        this.originChunkX = targetX;
        this.originChunkZ = targetZ;
        this.drawnVersions.keySet().removeIf(this::outsideCanvas);
        this.drawEdgeStrips(cache, shiftChunksX, shiftChunksZ);
        if (this.initialized) {
            this.texture.upload();
        }
    }

    private boolean outsideCanvas(long key) {
        int chunkX = (int) (key >> 32);
        int chunkZ = (int) key;
        return chunkX < this.originChunkX || chunkX >= this.originChunkX + CHUNKS_PER_SIDE
                || chunkZ < this.originChunkZ || chunkZ >= this.originChunkZ + CHUNKS_PER_SIDE;
    }

    /// 原点移动后，同一世界坐标的像素必须保留在新的局部坐标处。
    static void movePixels(NativeImage image, int originShiftPixelsX, int originShiftPixelsZ) {
        int contentShiftPixelsX = CanvasScroll.contentShiftForOriginShift(originShiftPixelsX);
        int contentShiftPixelsZ = CanvasScroll.contentShiftForOriginShift(originShiftPixelsZ);
        int[] rowOrder = CanvasScroll.safeOrder(image.getHeight(), contentShiftPixelsZ);
        int[] colOrder = CanvasScroll.safeOrder(image.getWidth(), contentShiftPixelsX);
        for (int row : rowOrder) {
            int sourceRow = CanvasScroll.sourceIndexForOriginShift(row, originShiftPixelsZ);
            for (int col : colOrder) {
                int sourceCol = CanvasScroll.sourceIndexForOriginShift(col, originShiftPixelsX);
                image.setPixel(col, row, image.getPixel(sourceCol, sourceRow));
            }
        }
    }

    /// 补绘因平移而新进入视野的边缘条带。
    private void drawEdgeStrips(MapDimensionCache cache, int shiftChunksX, int shiftChunksZ) {
        if (cache == null) {
            return;
        }
        // 横向条带：左或右边缘的若干列。
        if (shiftChunksX != 0) {
            int firstDx = shiftChunksX > 0 ? CHUNKS_PER_SIDE - shiftChunksX : 0;
            int count = Math.abs(shiftChunksX);
            for (int i = 0; i < count; i++) {
                int dx = firstDx + i;
                for (int dz = 0; dz < CHUNKS_PER_SIDE; dz++) {
                    this.redrawChunk(dx, dz, cache);
                }
            }
        }
        // 纵向条带：上或下边缘的若干行，跳过已由横向条带覆盖的角落。
        if (shiftChunksZ != 0) {
            int firstDz = shiftChunksZ > 0 ? CHUNKS_PER_SIDE - shiftChunksZ : 0;
            int count = Math.abs(shiftChunksZ);
            for (int i = 0; i < count; i++) {
                int dz = firstDz + i;
                for (int dx = 0; dx < CHUNKS_PER_SIDE; dx++) {
                    if (shiftChunksX != 0) {
                        int firstDx = shiftChunksX > 0 ? CHUNKS_PER_SIDE - shiftChunksX : 0;
                        if (dx >= firstDx && dx < firstDx + Math.abs(shiftChunksX)) {
                            continue;
                        }
                    }
                    this.redrawChunk(dx, dz, cache);
                }
            }
        }
    }

    /// 按画布局部坐标重绘单个区块槽位。
    private void redrawChunk(int dx, int dz, MapDimensionCache cache) {
        int chunkX = this.originChunkX + dx;
        int chunkZ = this.originChunkZ + dz;
        int baseX = dx * ChunkMapData.SIZE;
        int baseZ = dz * ChunkMapData.SIZE;
        long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        ChunkMapData data = cache.get(chunkX, chunkZ).orElse(null);
        if (data != null) {
            this.drawChunk(data, baseX, baseZ);
            this.drawnVersions.put(key, data.version());
        } else {
            // 未探索且未被队友同步的区块绘制网格底纹。
            this.drawUnexplored(baseX, baseZ);
            this.drawnVersions.remove(key);
        }
    }

    private void redraw(MapDimensionCache cache) {
        this.drawnVersions.clear();
        this.fill(MapPalette.TRANSPARENT);
        if (cache != null) {
            for (int dx = 0; dx < CHUNKS_PER_SIDE; dx++) {
                for (int dz = 0; dz < CHUNKS_PER_SIDE; dz++) {
                    this.redrawChunk(dx, dz, cache);
                }
            }
        }
        if (this.initialized) {
            this.texture.upload();
        }
    }

    /// 未探索区块底纹：深色底加每 4 像素的网格线，与已探索地形形成明确区分。
    private void drawUnexplored(int baseX, int baseZ) {
        for (int localZ = 0; localZ < ChunkMapData.SIZE; localZ++) {
            for (int localX = 0; localX < ChunkMapData.SIZE; localX++) {
                boolean line = this.showUnexploredGrid && ((localX & 3) == 0 || (localZ & 3) == 0);
                int imported = this.importedColor(baseX + localX, baseZ + localZ);
                this.setPixel(baseX + localX, baseZ + localZ,
                        imported != 0 ? imported : line ? UNEXPLORED_GRID_COLOR : UNEXPLORED_BASE_COLOR);
            }
        }
    }

    private void drawChunk(ChunkMapData data, int baseX, int baseZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] lighting = ChunkColorizer.lighting(data, this.colorScheme);
        for (int localZ = 0; localZ < ChunkMapData.SIZE; localZ++) {
            for (int localX = 0; localX < ChunkMapData.SIZE; localX++) {
                int shadow = lighting == null ? 0 : lighting[localZ * ChunkMapData.SIZE + localX];
                int color = ChunkColorizer.colorAt(data, localX, localZ, this.colorScheme,
                        Minecraft.getInstance().level, cursor, shadow);
                if (color == 0) color = this.importedColor(baseX + localX, baseZ + localZ);
                this.setPixel(baseX + localX, baseZ + localZ, color);
            }
        }
    }

    private void fill(int argb) {
        for (int y = 0; y < PIXELS; y++) {
            for (int x = 0; x < PIXELS; x++) {
                this.setPixel(x, y, argb);
            }
        }
    }

    private int importedColor(int x, int z) {
        return MapColor.getColorFromPackedId(ImportedMapStore.pixelAt(this.importedMaps,
                this.originChunkX * 16 + x, this.originChunkZ * 16 + z));
    }

    /// 写入原生纹理坐标，X 向右、Y 向下，与原版地图纹理一致。
    private void setPixel(int x, int y, int argb) {
        if (x < 0 || x >= PIXELS || y < 0 || y >= PIXELS) {
            return;
        }
        this.image.setPixel(x, y, argb);
    }

    /// 释放纹理：从纹理管理器注销并关闭，避免注册表残留已关闭对象。
    public void close() {
        this.requestedOverview = null;
        this.drawnOverview = null;
        if (!this.initialized) {
            return;
        }
        try {
            Minecraft.getInstance().getTextureManager().release(this.textureId);
        } catch (Exception e) {
            LOGGER.warn("Failed to release world map canvas", e);
        } finally {
            this.initialized = false;
            // 关闭后画布内容失效，重新打开时必须走全量重绘。
            this.positioned = false;
        }
    }
}
