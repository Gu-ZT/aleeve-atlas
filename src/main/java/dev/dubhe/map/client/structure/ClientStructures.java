package dev.dubhe.map.client.structure;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.AtlasSession;
import dev.dubhe.map.client.map.AtlasClientMap;
import dev.dubhe.map.storage.AtlasPaths;
import dev.dubhe.map.storage.ServerKey;
import dev.dubhe.map.structure.StructurePayloads;
import dev.dubhe.map.structure.StructureRecord;
import dev.dubhe.map.structure.StructureStore;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// 服务端发送的候选结构只在地图采样完成后成为本地已知结构。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class ClientStructures {
    private static final Map<String, StructureStore> STORES = new HashMap<>();
    private static final Map<Long, Map<String, StructureRecord>> PENDING = new HashMap<>();
    private static String serverKey = "";
    private static String pendingDimension = "";
    private static int ticks;
    private static int shareCursor;
    private static int shareRemaining;
    private static int lastShareTick = -100;

    private ClientStructures() {
    }

    public static boolean available() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(StructurePayloads.Discovery.TYPE);
    }

    public static StructureStore store(String dimension) {
        var mc = Minecraft.getInstance();
        String key = ServerKey.resolveMapCache(mc);
        if (!serverKey.equals(key)) {
            flush();
            STORES.clear();
            serverKey = key;
        }
        return STORES.computeIfAbsent(dimension, d -> new StructureStore(
                AtlasPaths.dimensionDir(mc.gameDirectory.toPath(), key, ResourceLocation.parse(d)).resolve("structures.nbt"), d));
    }

    public static @Nullable StructureStore current() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : store(level.dimension().location().toString());
    }

    private static void updateDimension() {
        var level = Minecraft.getInstance().level;
        String dimension = level == null ? "" : level.dimension().location().toString();
        if (!pendingDimension.equals(dimension)) {
            PENDING.clear();
            pendingDimension = dimension;
            shareCursor = 0;
            shareRemaining = 0;
        }
    }

    public static void receiveDiscovery(StructurePayloads.Discovery payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            updateDimension();
            for (StructureRecord record : payload.records()) {
                if (record.dimension().equals(pendingDimension)) {
                    PENDING.computeIfAbsent(ChunkPos.asLong(payload.chunkX(), payload.chunkZ()), key -> new HashMap<>())
                            .put(record.key(), record.withSource(StructureRecord.Source.DISCOVERED));
                }
            }
            var manager = AtlasClientMap.manager();
            if (manager != null && manager.hasSampledChunk(payload.chunkX(), payload.chunkZ())) {
                sampled(payload.chunkX(), payload.chunkZ());
            }
        });
    }

    public static void sampled(int chunkX, int chunkZ) {
        updateDimension();
        var pending = PENDING.remove(ChunkPos.asLong(chunkX, chunkZ));
        if (pending != null) {
            StructureStore store = current();
            if (store != null) {
                pending.values().forEach(store::put);
            }
        }
    }

    public static void receiveShare(StructurePayloads.Share payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            for (StructureRecord record : payload.records()) {
                store(record.dimension()).put(record.withSource(StructureRecord.Source.SHARED));
            }
        });
    }

    public static void shareNext() {
        var connection = Minecraft.getInstance().getConnection();
        StructureStore store = current();
        if (!AtlasSession.synchronizedMode() || connection == null || store == null
                || !connection.hasChannel(StructurePayloads.Share.TYPE) || ticks - lastShareTick < 10) {
            return;
        }
        List<StructureRecord> records = store.all();
        if (records.isEmpty()) {
            return;
        }
        shareCursor %= records.size();
        int end = Math.min(records.size(), shareCursor + StructurePayloads.MAX_RECORDS);
        ClientPacketDistributor.sendToServer(new StructurePayloads.Share(records.subList(shareCursor, end)));
        shareRemaining = Math.max(0, shareRemaining - (end - shareCursor));
        shareCursor = end % records.size();
        lastShareTick = ticks;
    }

    public static void shareAll() {
        StructureStore store = current();
        if (store != null) {
            shareCursor = 0;
            shareRemaining = store.all().size();
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        updateDimension();
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        // 候选只保留到对应区块卸载，重新途经时服务端会再次发送。
        PENDING.keySet().removeIf(key -> !mc.level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)));
        if (++ticks % 200 == 0) {
            flush();
        }
        if (shareRemaining > 0 && ticks % 10 == 0) {
            shareNext();
        }
    }

    public static void flush() {
        STORES.values().forEach(StructureStore::flush);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        flush();
        STORES.clear();
        PENDING.clear();
        serverKey = "";
        pendingDimension = "";
        shareCursor = 0;
        shareRemaining = 0;
        ticks = 0;
        lastShareTick = -100;
    }
}
