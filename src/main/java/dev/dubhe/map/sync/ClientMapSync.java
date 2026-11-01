package dev.dubhe.map.sync;

import dev.dubhe.map.AtlasSession;
import dev.dubhe.map.client.map.AtlasClientMap;
import dev.dubhe.map.client.map.ClientMapManager;
import dev.dubhe.map.client.map.WorldMapScreen;
import dev.dubhe.map.client.structure.ClientStructures;
import dev.dubhe.map.map.ChunkMapData;
import dev.dubhe.map.map.ChunkPixelCodec;
import dev.dubhe.map.map.MapDimensionCache;
import dev.dubhe.map.network.ChunkDeltaPayload;
import dev.dubhe.map.network.ChunkDigestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/// 地图同步：主线程捕获快照，后台读取旧区域与压缩差异，限速队列逐 tick 发送。
public final class ClientMapSync {
    public static final int CHUNKS_PER_TICK = 8;
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/mapsync");
    private static final SyncBudget BUDGET = new SyncBudget();
    private static volatile Session session = new Session();

    private ClientMapSync() { }

    /// 会话对象隔离异步任务；旧服务器的后台结果不会进入新服务器队列。
    private static final class Session {
        final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Atlas map sync");
            thread.setDaemon(true);
            return thread;
        });
        final ArrayBlockingQueue<Outgoing> outgoing = new ArrayBlockingQueue<>(32);
        final Queue<PendingWrite> writes = new ConcurrentLinkedQueue<>();
        final Map<Key, DigestAssembly> digests = new HashMap<>();
        final Set<Key> working = new HashSet<>();
    }

    private record Key(UUID player, String dimension) { }
    private record Outgoing(CustomPacketPayload packet, Key completed) { }
    private record PendingWrite(String dimension, ChunkMapData data) { }

    /// 摘要包含内存和磁盘中的全部地表缓存，空地图同样发送以触发追赶同步。
    public static void reportLocalDigest() {
        if (!AtlasSession.synchronizedMode()) {
            return;
        }
        ClientStructures.shareNext();
        Minecraft minecraft = Minecraft.getInstance();
        ClientMapManager manager = AtlasClientMap.manager();
        if (manager == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        String dimension = minecraft.level.dimension().location().toString();
        Key key = new Key(minecraft.player.getUUID(), dimension);
        Session current = session;
        if (!current.working.add(key)) {
            return;
        }
        MapDimensionCache.SyncSnapshot snapshot = manager.currentCache().snapshotForSync();
        current.worker.execute(() -> {
            try {
                List<ChunkDigestPayload.Entry> entries = snapshot.readAll().stream()
                        .map(data -> new ChunkDigestPayload.Entry(data.chunkX(), data.chunkZ(), data.version()))
                        .toList();
                for (ChunkDigestPayload packet : ChunkDigestPayload.split(dimension, entries, key.player())) {
                    current.outgoing.put(new Outgoing(packet, null));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.warn("Failed to read map digest for {}", dimension, e);
            } finally {
                finish(current, key);
            }
        });
    }

    /// 摘要分片必须完整聚合；同一队友的传输未完成时合并重复请求，避免重复压缩。
    public static void handleDigest(ChunkDigestPayload payload, IPayloadContext context) {
        if (!AtlasSession.synchronizedMode() || payload.senderId() == null) {
            return;
        }
        Session current = session;
        long now = System.currentTimeMillis();
        current.digests.values().removeIf(value -> value.expired(now));
        Key key = new Key(payload.senderId(), payload.dimensionId());
        List<ChunkDigestPayload.Entry> entries = current.digests
                .computeIfAbsent(key, ignored -> new DigestAssembly()).add(payload, now);
        if (entries == null) {
            return;
        }
        current.digests.remove(key);
        ClientMapManager manager = AtlasClientMap.manager();
        MapDimensionCache cache = manager == null ? null : manager.syncCache(payload.dimensionId());
        if (cache == null || !current.working.add(key)) {
            return;
        }
        MapDimensionCache.SyncSnapshot snapshot = cache.snapshotForSync();
        current.worker.execute(() -> {
            try {
                Map<Long, Long> versions = versions(entries);
                List<ChunkMapData> batch = new ArrayList<>(CHUNKS_PER_TICK);
                for (ChunkMapData data : snapshot.readAll()) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    Long version = versions.get(chunkKey(data.chunkX(), data.chunkZ()));
                    if (version == null || version < data.version()) {
                        batch.add(data);
                        if (batch.size() == CHUNKS_PER_TICK) {
                            queueBatch(current, key, batch);
                            batch.clear();
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    queueBatch(current, key, batch);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.warn("Failed to prepare map transfer to {}", key, e);
            } finally {
                finish(current, key);
            }
        });
    }

    /// 有界队列施加背压；发送预算耗尽时保留分片，后续 tick 继续发送。
    private static void queueBatch(Session current, Key key, List<ChunkMapData> batch)
            throws InterruptedException {
        for (ChunkDeltaPayload packet : ChunkDeltaPayload.splitForUpload(key.dimension(),
                computeDeltas(List.of(), batch), key.player())) {
            current.outgoing.put(new Outgoing(packet, null));
        }
    }

    private static void finish(Session current, Key key) {
        try {
            current.outgoing.put(new Outgoing(null, key));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static List<ChunkDeltaPayload.ChunkData> computeDeltas(
            List<ChunkDigestPayload.Entry> theirEntries, List<ChunkMapData> localChunks) {
        Map<Long, Long> theirVersions = versions(theirEntries);
        List<ChunkDeltaPayload.ChunkData> deltas = new ArrayList<>();
        for (ChunkMapData data : localChunks) {
            Long version = theirVersions.get(chunkKey(data.chunkX(), data.chunkZ()));
            if (version == null || version < data.version()) {
                deltas.add(new ChunkDeltaPayload.ChunkData(data.chunkX(), data.chunkZ(),
                        data.version(), ChunkPixelCodec.compressChunk(data)));
            }
        }
        return deltas;
    }

    private static Map<Long, Long> versions(List<ChunkDigestPayload.Entry> entries) {
        Map<Long, Long> versions = new HashMap<>();
        for (ChunkDigestPayload.Entry entry : entries) {
            versions.merge(chunkKey(entry.chunkX(), entry.chunkZ()), entry.version(), Math::max);
        }
        return versions;
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /// 网络线程解压；主线程按版本合并，迟到的旧分片不能覆盖刚探索的新数据。
    public static void handleDelta(ChunkDeltaPayload payload, IPayloadContext context) {
        if (!AtlasSession.synchronizedMode()) {
            return;
        }
        Session current = session;
        for (ChunkDeltaPayload.ChunkData chunk : payload.chunks()) {
            ChunkMapData data = ChunkPixelCodec.decompressChunk(chunk.chunkX(), chunk.chunkZ(),
                    chunk.pixels(), chunk.version());
            current.writes.add(new PendingWrite(payload.dimensionId(), data));
        }
    }

    public static void tick() {
        if (!AtlasSession.synchronizedMode()) {
            return;
        }
        Session current = session;
        BUDGET.beginTick();
        Outgoing next;
        while ((next = current.outgoing.peek()) != null) {
            if (next.completed() != null) {
                current.working.remove(next.completed());
                current.outgoing.poll();
                continue;
            }
            int bytes = estimate(next.packet());
            if (!BUDGET.canSend(bytes)) {
                break;
            }
            ClientPacketDistributor.sendToServer(next.packet());
            current.outgoing.poll();
            BUDGET.record(bytes);
        }
        ClientMapManager manager = AtlasClientMap.manager();
        if (manager == null) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            PendingWrite pending = current.writes.poll();
            if (pending == null) {
                break;
            }
            MapDimensionCache cache = manager.syncCache(pending.dimension());
            if (cache != null && merge(cache, pending.data())) {
                changed = true;
            }
        }
        if (changed) {
            WorldMapScreen.onMapDataChanged();
        }
    }

    /// 只接受缺失或更新的区块，避免重传反复触发纹理重绘和区域写盘。
    static boolean merge(MapDimensionCache cache, ChunkMapData incoming) {
        var existing = cache.get(incoming.chunkX(), incoming.chunkZ());
        if (existing.isPresent() && existing.get().version() >= incoming.version()) {
            return false;
        }
        cache.put(incoming);
        return true;
    }

    public static void clear() {
        Session previous = session;
        session = new Session();
        previous.worker.shutdownNow();
        previous.outgoing.clear();
        previous.writes.clear();
        BUDGET.reset();
    }

    private static int estimate(CustomPacketPayload packet) {
        if (packet instanceof ChunkDigestPayload digest) {
            return 320 + digest.entries().size() * 18;
        }
        ChunkDeltaPayload delta = (ChunkDeltaPayload) packet;
        int total = 320;
        for (ChunkDeltaPayload.ChunkData chunk : delta.chunks()) {
            total += chunk.pixels().length + 24;
        }
        return total;
    }
}
