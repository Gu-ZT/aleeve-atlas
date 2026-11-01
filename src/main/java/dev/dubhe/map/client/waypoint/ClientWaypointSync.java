package dev.dubhe.map.client.waypoint;

import dev.dubhe.map.api.waypoint.WaypointReceivedEvent;
import dev.dubhe.map.config.AtlasClientConfig;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.waypoint.Waypoint;
import dev.dubhe.map.waypoint.WaypointSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/// 客户端路径点接收策略。
///
/// `AUTO_ACCEPT` 静默入库并轻提示；`MANUAL_ACCEPT` 在聊天栏给出可点击的「接受/忽略」；
/// `AUTO_REJECT` 直接丢弃。
public final class ClientWaypointSync {
    private ClientWaypointSync() {
    }

    public static void handle(WaypointSyncPayload payload, IPayloadContext context) {
        AtlasClientConfig config = AtlasClientConfigs.CONFIG;
        Waypoint waypoint = payload.waypoint();
        switch (config.waypoint.receivePolicy) {
            case AUTO_REJECT -> {
                // 直接丢弃，不打扰玩家。
            }
            case AUTO_ACCEPT -> accept(waypoint, payload.action());
            case MANUAL_ACCEPT -> promptManual(waypoint, payload.action());
            default -> {
                // 新增策略默认不自动接受，避免未知枚举值产生非预期写入。
            }
        }
    }

    private static void accept(Waypoint waypoint, WaypointSyncPayload.Action action) {
        if (ClientWaypoints.store() == null) {
            return;
        }
        if (action == WaypointSyncPayload.Action.DELETE) {
            // 按路径点自身维度删除，跨维度同步删除同样生效。
            ClientWaypoints.removeInDimension(waypoint);
        } else {
            // 按路径点自身维度写入，跨维度分享不会落到当前维度。
            ClientWaypoints.upsertInDimension(waypoint);
        }
        NeoForge.EVENT_BUS.post(new WaypointReceivedEvent(waypoint, true));
        notify(Component.translatable("aleeve_atlas.message.waypoint_received",
                waypoint.displayName().getString(), waypoint.sourcePlayer()).withStyle(ChatFormatting.GREEN));
    }

    private static void promptManual(Waypoint waypoint, WaypointSyncPayload.Action action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Component accept = Component.translatable("aleeve_atlas.message.waypoint_accept")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/aleeve_atlas waypoint accept " + waypoint.id()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("aleeve_atlas.message.waypoint_accept"))));
        Component ignore = Component.translatable("aleeve_atlas.message.waypoint_ignore")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/aleeve_atlas waypoint ignore " + waypoint.id())));

        minecraft.player.displayClientMessage(
                Component.translatable("aleeve_atlas.message.waypoint_shared",
                        waypoint.displayName().getString(), waypoint.sourcePlayer(),
                        waypoint.pos().getX(), waypoint.pos().getY(), waypoint.pos().getZ()),
                false);
        minecraft.player.displayClientMessage(
                Component.empty().append(accept).append(Component.literal("  ")).append(ignore),
                false);
        // 暂存待确认项，供接受命令使用。
        ClientWaypoints.pending().put(waypoint.id(), forAction(waypoint, action));
    }

    private static PendingWaypoint forAction(Waypoint waypoint, WaypointSyncPayload.Action action) {
        return new PendingWaypoint(waypoint, action);
    }

    /// 接受一个待确认路径点（由聊天栏按钮触发）。
    public static void confirm(UUID id, boolean accepted) {
        PendingWaypoint pending = ClientWaypoints.pending().remove(id);
        if (pending == null) {
            return;
        }
        if (accepted) {
            accept(pending.waypoint(), pending.action());
        }
    }

    /// 待确认路径点。
    public record PendingWaypoint(Waypoint waypoint, WaypointSyncPayload.Action action) {
    }

    private static void notify(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }
}
