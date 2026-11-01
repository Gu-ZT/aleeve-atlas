package dev.dubhe.map.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import dev.dubhe.map.api.territory.TerritorySyncPayload;
import dev.dubhe.map.client.territory.ClientTerritorySync;
import dev.dubhe.map.client.waypoint.ClientWaypointSync;
import dev.dubhe.map.ping.ClientPings;
import dev.dubhe.map.ping.PingBroadcastPayload;
import dev.dubhe.map.sync.ClientMapSync;
import dev.dubhe.map.sync.ClientPlayerPositions;
import dev.dubhe.map.waypoint.WaypointSyncPayload;

/// 客户端分发仅在收到客户端方向的负载后加载，专服不会解析客户端实体类型。
public final class AtlasClientPayloads {
    private AtlasClientPayloads() { }
    public static void handle(HandshakePayload payload, IPayloadContext context) {
        ClientPayloadHandler.handleHandshake(payload, context);
    }

    public static void handle(PingBroadcastPayload payload, IPayloadContext context) {
        ClientPings.handle(payload, context);
    }

    public static void handle(WaypointSyncPayload payload, IPayloadContext context) {
        ClientWaypointSync.handle(payload, context);
    }

    public static void handle(PlayerPositionPayload payload, IPayloadContext context) {
        ClientPlayerPositions.handle(payload, context);
    }

    public static void handle(TeamSightingsPayload payload, IPayloadContext context) {
        ClientPlayerPositions.handleSightings(payload, context);
    }

    public static void handle(ChunkDigestPayload payload, IPayloadContext context) {
        ClientMapSync.handleDigest(payload, context);
    }

    public static void handle(ChunkDeltaPayload payload, IPayloadContext context) {
        ClientMapSync.handleDelta(payload, context);
    }

    public static void handle(TerritorySyncPayload payload, IPayloadContext context) {
        ClientTerritorySync.handle(payload, context);
    }
}
