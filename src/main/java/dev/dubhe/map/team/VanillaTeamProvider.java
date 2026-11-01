package dev.dubhe.map.team;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.UUID;

/// 原版计分板队伍适配：服务端读取自身计分板，客户端通过在线玩家名单解析 UUID。
public final class VanillaTeamProvider implements TeamProvider {
    @Override
    public String id() {
        return "vanilla";
    }

    /// 集成服务器与客户端共用 JVM，必须按调用线程选择逻辑端。
    private static MinecraftServer server() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && (server.isSameThread()
                || FMLEnvironment.dist == Dist.DEDICATED_SERVER) ? server : null;
    }

    @Override
    public String teamIdOf(UUID playerId) {
        MinecraftServer server = server();
        PlayerTeam team;
        if (server != null) {
            var player = server.getPlayerList().getPlayer(playerId);
            team = player == null ? null : player.getTeam();
        } else {
            team = FMLEnvironment.dist == Dist.CLIENT ? ClientAccess.teamOf(playerId) : null;
        }
        return team == null ? null : team.getName();
    }

    @Override
    public List<UUID> onlineMembersOf(String teamId) {
        if (teamId == null) {
            return List.of();
        }
        MinecraftServer server = server();
        if (server != null) {
            return server.getPlayerList().getPlayers().stream()
                    .filter(player -> teamId.equals(this.teamIdOf(player.getUUID())))
                    .map(player -> player.getUUID()).toList();
        }
        return FMLEnvironment.dist == Dist.CLIENT
                ? ClientAccess.onlinePlayers().stream()
                        .filter(id -> teamId.equals(this.teamIdOf(id))).toList() : List.of();
    }

    @Override
    public boolean sameTeam(UUID first, UUID second) {
        String firstTeam = this.teamIdOf(first);
        return firstTeam != null && firstTeam.equals(this.teamIdOf(second));
    }

    private static PlayerTeam namedTeam(String teamId) {
        MinecraftServer server = server();
        Scoreboard scoreboard = server != null ? server.getScoreboard()
                : FMLEnvironment.dist == Dist.CLIENT ? ClientAccess.scoreboard() : null;
        return teamId == null || scoreboard == null ? null : scoreboard.getPlayerTeam(teamId);
    }

    @Override
    public Component teamDisplayName(String teamId) {
        PlayerTeam team = namedTeam(teamId);
        return team == null ? Component.empty() : team.getDisplayName();
    }

    @Override
    public int teamColor(String teamId) {
        PlayerTeam team = namedTeam(teamId);
        Integer rgb = team == null ? null : team.getColor().getColor();
        return rgb == null ? 0xFFFFFFFF : 0xFF000000 | rgb;
    }

    /// 隔离客户端类型，专用服务器不加载 Minecraft 客户端类。
    private static final class ClientAccess {
        private static Scoreboard scoreboard() {
            var level = Minecraft.getInstance().level;
            return level == null ? null : level.getScoreboard();
        }

        private static PlayerTeam teamOf(UUID id) {
            var connection = Minecraft.getInstance().getConnection();
            var info = connection == null ? null : connection.getPlayerInfo(id);
            Scoreboard scoreboard = scoreboard();
            return info == null || scoreboard == null ? null
                    : scoreboard.getPlayersTeam(info.getProfile().getName());
        }

        private static List<UUID> onlinePlayers() {
            var connection = Minecraft.getInstance().getConnection();
            return connection == null ? List.of() : connection.getOnlinePlayers().stream()
                    .map(info -> info.getProfile().getId()).toList();
        }
    }
}
