package dev.dubhe.map.waypoint;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.team.TeamProviders;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/// 服务端路径点转发。
///
/// 服务端不持久化任何玩家个人路径点，仅校验并转发给同队在线成员。
public final class WaypointSyncHandler {
    private WaypointSyncHandler() {
    }

    public static void handle(WaypointSyncPayload payload, IPayloadContext context) {
        // 服务端关闭协同同步时丢弃同步类数据包。
        if (!AleeveAtlas.CONFIG.sync.enabled) {
            return;
        }
        if (!(context.player() instanceof ServerPlayer sender)) {
            return;
        }
        // 以实际发送者覆盖来源字段，防止伪造。
        Waypoint waypoint = payload.waypoint().withSource(sender.getGameProfile().getName());
        WaypointSyncPayload verified =
                new WaypointSyncPayload(payload.action(), waypoint);

        List<ServerPlayer> recipients = sender.level().getServer().getPlayerList().getPlayers()
                .stream()
                .filter(player -> player != sender && TeamProviders.active()
                        .sameTeam(sender.getUUID(), player.getUUID()))
                .toList();
        for (ServerPlayer recipient : recipients) {
            PacketDistributor.sendToPlayer(recipient, verified);
        }
    }
}
