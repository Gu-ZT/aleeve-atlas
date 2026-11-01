package dev.dubhe.map.team;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.api.team.TeamMembershipChangedEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// 队伍成员变更监测。
///
/// 原版计分板不提供队伍变更回调，因此按固定间隔比较快照；
/// 外部队伍实现可通过 {@link #notifyChange} 主动推送变更以降低延迟。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class TeamMembershipWatcher {
    /// 快照比较间隔（tick）。
    private static final int POLL_INTERVAL_TICKS = 20;

    /// 玩家 UUID → 队伍 ID（无队伍为 null 时不入表）。
    private static final Map<UUID, String> SNAPSHOT = new HashMap<>();
    private static int ticks;

    private TeamMembershipWatcher() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++ticks < POLL_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            SNAPSHOT.clear();
            return;
        }
        TeamProvider provider = TeamProviders.active();
        Map<UUID, String> current = new HashMap<>();
        var connection = minecraft.getConnection();
        if (connection == null) {
            return;
        }
        for (var info : connection.getOnlinePlayers()) {
            UUID playerId = info.getProfile().getId();
            String teamId = provider.teamIdOf(playerId);
            if (teamId != null) {
                current.put(playerId, teamId);
            }
        }
        // 退队或换队：旧表中有、且队伍 ID 与当前不同。
        for (Map.Entry<UUID, String> entry : SNAPSHOT.entrySet()) {
            String nowTeam = current.get(entry.getKey());
            if (!entry.getValue().equals(nowTeam)) {
                NeoForge.EVENT_BUS.post(new TeamMembershipChangedEvent(entry.getKey(),
                        entry.getValue(), TeamMembershipChangedEvent.Change.LEFT));
            }
        }
        // 入队或换队：当前表中新增或队伍 ID 变化。
        for (Map.Entry<UUID, String> entry : current.entrySet()) {
            String wasTeam = SNAPSHOT.get(entry.getKey());
            if (!entry.getValue().equals(wasTeam)) {
                NeoForge.EVENT_BUS.post(new TeamMembershipChangedEvent(entry.getKey(),
                        entry.getValue(), TeamMembershipChangedEvent.Change.JOINED));
            }
        }
        SNAPSHOT.clear();
        SNAPSHOT.putAll(current);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SNAPSHOT.clear();
        ticks = 0;
    }

    /// 外部队伍实现主动推送变更，跳过轮询延迟。
    public static void notifyChange(UUID playerId, String teamId,
            TeamMembershipChangedEvent.Change change) {
        if (change == TeamMembershipChangedEvent.Change.LEFT) {
            SNAPSHOT.remove(playerId);
        } else if (teamId != null) {
            SNAPSHOT.put(playerId, teamId);
        }
        NeoForge.EVENT_BUS.post(new TeamMembershipChangedEvent(playerId, teamId, change));
    }
}
