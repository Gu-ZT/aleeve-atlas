package dev.dubhe.map.network;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.api.territory.TerritorySyncPayload;
import dev.dubhe.map.ping.PingBroadcastPayload;
import dev.dubhe.map.ping.PingPayload;
import dev.dubhe.map.ping.ServerPingHandler;
import dev.dubhe.map.sync.PlayerPositionHandler;
import dev.dubhe.map.sync.ServerMapSync;
import dev.dubhe.map.waypoint.WaypointSyncHandler;
import dev.dubhe.map.waypoint.WaypointSyncPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/// 双端负载类型与服务端处理器注册，按负载方向延迟分发客户端处理器。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class AtlasPayloads {
    private AtlasPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(AleeveAtlas.PROTOCOL_VERSION).optional();
        registrar.playToClient(HandshakePayload.TYPE, HandshakePayload.STREAM_CODEC,
                (payload, context) -> AtlasClientPayloads.handle(payload, context));
        registrar.playToServer(PingPayload.TYPE, PingPayload.STREAM_CODEC, ServerPingHandler::handle);
        registrar.playToClient(PingBroadcastPayload.TYPE, PingBroadcastPayload.STREAM_CODEC,
                (payload, context) -> AtlasClientPayloads.handle(payload, context));
        registrar.playBidirectional(WaypointSyncPayload.TYPE, WaypointSyncPayload.STREAM_CODEC, (payload, context) -> {
            if (context.flow().isClientbound()) AtlasClientPayloads.handle(payload, context);
            else WaypointSyncHandler.handle(payload, context);
        });
        registrar.playBidirectional(PlayerPositionPayload.TYPE, PlayerPositionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.flow().isClientbound()) AtlasClientPayloads.handle(payload, context);
            else PlayerPositionHandler.handle(payload, context);
        });
        registrar.playBidirectional(TeamSightingsPayload.TYPE, TeamSightingsPayload.STREAM_CODEC, (payload, context) -> {
            if (context.flow().isClientbound()) AtlasClientPayloads.handle(payload, context);
            else PlayerPositionHandler.handleSightings(payload, context);
        });
        registrar.playBidirectional(ChunkDigestPayload.TYPE, ChunkDigestPayload.STREAM_CODEC, (payload, context) -> {
            if (context.flow().isClientbound()) AtlasClientPayloads.handle(payload, context);
            else ServerMapSync.handleDigest(payload, context);
        });

        PayloadRegistrar deltaRegistrar = event.registrar(AleeveAtlas.PROTOCOL_VERSION)
                .executesOn(HandlerThread.NETWORK).optional();
        deltaRegistrar.playBidirectional(ChunkDeltaPayload.TYPE, ChunkDeltaPayload.STREAM_CODEC, (payload, context) -> {
            if (context.flow().isClientbound()) AtlasClientPayloads.handle(payload, context);
            else ServerMapSync.handleDelta(payload, context);
        });
        registrar.playToClient(TerritorySyncPayload.TYPE, TerritorySyncPayload.STREAM_CODEC,
                (payload, context) -> AtlasClientPayloads.handle(payload, context));
    }
}
