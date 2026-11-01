package dev.dubhe.map.ping;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.team.TeamProviders;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/// 服务端标点处理：校验后广播给同队在线成员。
public final class ServerPingHandler {
    private ServerPingHandler() {
    }

    public static void handle(PingPayload payload, IPayloadContext context) {
        // 服务端关闭协同同步时丢弃同步类数据包。
        if (!AleeveAtlas.CONFIG.sync.enabled) {
            return;
        }
        if (!(context.player() instanceof ServerPlayer sender)) {
            return;
        }
        MapPingData ping = payload.ping();
        // 服务端以实际发送者信息覆盖，防止伪造。
        MapPingData verified = new MapPingData(ping.pingId(), sender.getUUID(),
                sender.getGameProfile().getName(), ping.teamColorArgb(), ping.dimensionId(),
                ping.position(), ping.targetEntityId(), ping.icon(), ping.createdAtMillis(),
                ping.lifetimeMillis());

        List<ServerPlayer> recipients = context.player().level().getServer().getPlayerList()
                .getPlayers().stream()
                .filter(player -> TeamProviders.active()
                        .sameTeam(sender.getUUID(), player.getUUID()))
                .toList();
        for (ServerPlayer recipient : recipients) {
            PacketDistributor.sendToPlayer(recipient, new PingBroadcastPayload(verified));
        }
    }
}
