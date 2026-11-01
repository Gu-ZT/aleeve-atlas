package dev.dubhe.map.structure;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// 原版迷雾查找独立于自然发现，仅管理员开启后允许加载结构起点。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class ServerStructureSearch {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerStructureSearch.class);
    private static final StructureSearchGate GATE = new StructureSearchGate();
    private static final Map<UUID, Boolean> POLICIES = new HashMap<>();
    private static @Nullable Pending pending;

    private ServerStructureSearch() {
    }

    public static void request(StructurePayloads.Search request, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!AleeveAtlas.CONFIG.structures.allowUnexploredSearch) {
                send(player, request.requestId(), StructurePayloads.SearchStatus.DISABLED, null);
                return;
            }
            var rejection = GATE.begin(player.getUUID(), System.nanoTime() / 1_000_000);
            if (rejection != null) {
                send(player, request.requestId(), rejection, null);
                return;
            }
            pending = new Pending(player.getUUID(), player.level(), player.blockPosition(), request);
        });
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            boolean allowed = AleeveAtlas.CONFIG.structures.allowUnexploredSearch;
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                if (player.connection.hasChannel(StructurePayloads.Policy.TYPE)
                        && !Boolean.valueOf(allowed).equals(POLICIES.get(player.getUUID()))) {
                    PacketDistributor.sendToPlayer(player, new StructurePayloads.Policy(allowed));
                    POLICIES.put(player.getUUID(), allowed);
                }
            }
        }
        Pending task = pending;
        if (task == null) {
            return;
        }
        try {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(task.player);
            if (player == null || player.level() != task.level) {
                return;
            }
            if (!AleeveAtlas.CONFIG.structures.allowUnexploredSearch) {
                send(player, task.request.requestId(), StructurePayloads.SearchStatus.DISABLED, null);
                return;
            }
            StructureRecord record = locate(task.level, task.origin, task.request.kind());
            send(player, task.request.requestId(), record == null
                    ? StructurePayloads.SearchStatus.NOT_FOUND : StructurePayloads.SearchStatus.FOUND, record);
        } catch (RuntimeException e) {
            LOGGER.warn("结构查询失败，玩家 {} 维度 {} 起点 {} 类型 {}", task.player,
                    task.level.dimension().location(), task.origin, task.request.kind(), e);
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(task.player);
            if (player != null) {
                send(player, task.request.requestId(), StructurePayloads.SearchStatus.FAILED, null);
            }
        } finally {
            pending = null;
            GATE.finish();
        }
    }

    static @Nullable StructureRecord locate(ServerLevel level, BlockPos origin, StructureKind kind) {
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var holder = registry.get(kind.structureId()).orElse(null);
        if (holder == null) {
            return null;
        }
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), origin, 100, false);
        if (found == null || !level.getWorldBorder().isWithinBounds(found.getFirst())) {
            return null;
        }
        ChunkPos chunk = new ChunkPos(found.getFirst());
        var access = level.getChunk(chunk.x, chunk.z, ChunkStatus.STRUCTURE_STARTS);
        var start = access.getStartForStructure(found.getSecond().value());
        return start == null ? null : ServerStructures.describe(level, start, StructureRecord.Source.SEARCHED);
    }

    private static void send(ServerPlayer player, UUID id, StructurePayloads.SearchStatus status, @Nullable StructureRecord record) {
        PacketDistributor.sendToPlayer(player, new StructurePayloads.SearchResult(id, status, record));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.getEntity().getUUID();
        POLICIES.remove(player);
        GATE.clear(player);
        if (pending != null && pending.player.equals(player)) {
            pending = null;
            GATE.finish();
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        pending = null;
        POLICIES.clear();
        GATE.reset();
    }

    private record Pending(UUID player, ServerLevel level, BlockPos origin, StructurePayloads.Search request) {
    }
}
