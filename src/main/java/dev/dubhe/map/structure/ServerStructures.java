package dev.dubhe.map.structure;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.team.TeamProviders;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// 从已发送给玩家的区块读取结构，普通发现路径仅使用 getChunkNow。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class ServerStructures {
    private static final Map<UUID, LinkedHashMap<Long, PendingChunk>> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_SHARE = new HashMap<>();

    private ServerStructures() {
    }

    @SubscribeEvent
    public static void sent(ChunkWatchEvent.Sent event) {
        if (event.getPlayer().connection.hasChannel(StructurePayloads.Discovery.TYPE)) {
            PENDING.computeIfAbsent(event.getPlayer().getUUID(), key -> new LinkedHashMap<>())
                    .put(event.getPos().toLong(), new PendingChunk(event.getLevel(), event.getPos()));
        }
    }

    @SubscribeEvent
    public static void unwatch(ChunkWatchEvent.UnWatch event) {
        var pending = PENDING.get(event.getPlayer().getUUID());
        if (pending != null) {
            PendingChunk task = pending.get(event.getPos().toLong());
            if (task != null && task.level == event.getLevel()) {
                pending.remove(event.getPos().toLong());
            }
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            var pending = PENDING.get(player.getUUID());
            if (pending == null) {
                continue;
            }
            int budget = Math.min(8, pending.size());
            for (int i = 0; i < budget; i++) {
                var entry = pending.entrySet().iterator().next();
                long key = entry.getKey();
                PendingChunk task = entry.getValue();
                pending.remove(key);
                if (task.level == player.level() && !task.scan(player)) {
                    pending.put(key, task);
                }
            }
        }
    }

    public static @Nullable StructureRecord describe(ServerLevel level, StructureStart start, StructureRecord.Source source) {
        if (!start.isValid()) {
            return null;
        }
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var id = registry.getKey(start.getStructure());
        StructureKind kind = id == null ? null : StructureKind.fromId(id);
        if (kind == null) {
            return null;
        }
        var holder = registry.wrapAsHolder(start.getStructure());
        var state = level.getChunkSource().getGeneratorState();
        ChunkPos chunk = start.getChunkPos();
        var bounds = start.getBoundingBox();
        var position = chunk.getMiddleBlockPosition(bounds.minY());
        for (var placement : state.getPlacementsForStructure(holder)) {
            if (placement.isStructureChunk(state, chunk.x, chunk.z)) {
                position = placement.getLocatePos(chunk).atY(bounds.minY());
                break;
            }
        }
        return new StructureRecord(level.dimension().location().toString(), kind, chunk.x, chunk.z, position,
                bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ(), source);
    }

    public static void share(StructurePayloads.Share payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender) || !AleeveAtlas.CONFIG.sync.enabled) {
                return;
            }
            long now = sender.level().getServer().getTickCount();
            Long previous = LAST_SHARE.get(sender.getUUID());
            if (previous != null && now - previous < 5) {
                return;
            }
            LAST_SHARE.put(sender.getUUID(), now);
            String dimension = sender.level().dimension().location().toString();
            var records = payload.records().stream().filter(r -> r.dimension().equals(dimension))
                    .map(r -> r.withSource(StructureRecord.Source.SHARED)).toList();
            if (records.isEmpty()) {
                return;
            }
            for (ServerPlayer teammate : sender.level().getServer().getPlayerList().getPlayers()) {
                if (teammate != sender && TeamProviders.active().sameTeam(sender.getUUID(), teammate.getUUID())
                        && teammate.connection.hasChannel(StructurePayloads.Share.TYPE)) {
                    PacketDistributor.sendToPlayer(teammate, new StructurePayloads.Share(records));
                }
            }
        });
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        LAST_SHARE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        PENDING.clear();
        LAST_SHARE.clear();
    }

    private static final class PendingChunk {
        private final ServerLevel level;
        private final ChunkPos position;
        private final Set<String> sent = new HashSet<>();

        private PendingChunk(ServerLevel level, ChunkPos position) {
            this.level = level;
            this.position = position;
        }

        private boolean scan(ServerPlayer player) {
            Scan scan = scanLoaded(this.level, this.position);
            var records = new ArrayList<StructureRecord>();
            for (StructureRecord record : scan.records) {
                if (this.sent.add(record.key())) {
                    records.add(record);
                    if (records.size() == StructurePayloads.MAX_RECORDS) {
                        PacketDistributor.sendToPlayer(player,
                                new StructurePayloads.Discovery(this.position.x, this.position.z, records));
                        records.clear();
                    }
                }
            }
            if (!records.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new StructurePayloads.Discovery(this.position.x, this.position.z, records));
            }
            return scan.complete;
        }
    }

    static Scan scanLoaded(ServerLevel level, ChunkPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.x, position.z);
        if (chunk == null) {
            return new Scan(false, List.of());
        }
        var starts = new HashSet<>(chunk.getAllStarts().values());
        boolean complete = true;
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (var entry : chunk.getAllReferences().entrySet()) {
            var id = registry.getKey(entry.getKey());
            if (id == null || StructureKind.fromId(id) == null) {
                continue;
            }
            for (long reference : entry.getValue()) {
                ChunkPos startPos = new ChunkPos(reference);
                var origin = level.getChunkSource().getChunkNow(startPos.x, startPos.z);
                if (origin == null) {
                    complete = false;
                } else {
                    var start = origin.getStartForStructure(entry.getKey());
                    if (start != null) {
                        starts.add(start);
                    }
                }
            }
        }
        var records = new ArrayList<StructureRecord>();
        for (StructureStart start : starts) {
            StructureRecord record = describe(level, start, StructureRecord.Source.DISCOVERED);
            if (record != null && record.intersectsChunk(position.x, position.z)) {
                records.add(record);
            }
        }
        return new Scan(complete, records);
    }

    record Scan(boolean complete, List<StructureRecord> records) {
    }
}
