package dev.dubhe.map.team;

import dev.anvilcraft.lib.v2.integration.Integration;
import dev.dubhe.map.AleeveAtlas;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.client.KnownClientPlayer;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// 由 AnvilLib 在安装 FTB Teams 时加载，直接调用正式 API。
@Integration(value = "ftbteams", version = "[2111.1.1,2112)")
public final class FtbTeamsIntegration {
    public void apply() {
        TeamProviders.register(new FtbTeamsProvider());
        TeamProviders.select(AleeveAtlas.CONFIG.team.provider);
    }

    static final class FtbTeamsProvider implements TeamProvider {
        /// 单机与 LAN 的客户端、服务端共用进程，不能仅按物理端选择 manager。
        private static boolean serverSide() {
            var server = ServerLifecycleHooks.getCurrentServer();
            return FMLEnvironment.getDist() == Dist.DEDICATED_SERVER
                    || server != null && server.isSameThread();
        }

        @Override public String id() { return "ftbteams"; }

        @Override public String teamIdOf(UUID playerId) {
            var api = FTBTeamsAPI.api();
            if (serverSide()) {
                return api.isManagerLoaded() ? api.getManager().getTeamForPlayerID(playerId)
                        .map(team -> team.getId().toString()).orElse(null) : null;
            }
            return api.isClientManagerLoaded() ? api.getClientManager().getKnownPlayer(playerId)
                    .map(player -> player.teamId().toString()).orElse(null) : null;
        }

        @Override public List<UUID> onlineMembersOf(String teamId) {
            UUID id = parseId(teamId);
            if (id == null) return List.of();
            var api = FTBTeamsAPI.api();
            if (serverSide()) {
                if (!api.isManagerLoaded()) return List.of();
                var manager = api.getManager();
                return manager.getServer().getPlayerList().getPlayers().stream()
                        .filter(player -> manager.getTeamForPlayer(player)
                                .map(team -> team.getId().equals(id)).orElse(false))
                        .map(player -> player.getUUID()).toList();
            }
            return api.isClientManagerLoaded() ? api.getClientManager().knownClientPlayers().stream()
                    .filter(player -> player.online() && player.teamId().equals(id))
                    .map(KnownClientPlayer::id).toList() : List.of();
        }

        @Override public boolean sameTeam(UUID first, UUID second) {
            String team = teamIdOf(first);
            return team != null && team.equals(teamIdOf(second));
        }

        @Override public Component teamDisplayName(String teamId) {
            return team(teamId).map(Team::getName).orElse(Component.empty());
        }

        @Override public int teamColor(String teamId) {
            return team(teamId).map(team -> 0xFF000000 | team.getProperty(TeamProperties.COLOR).rgb())
                    .orElse(0xFFFFFFFF);
        }

        private static Optional<Team> team(String teamId) {
            UUID id = parseId(teamId);
            if (id == null) return Optional.empty();
            var api = FTBTeamsAPI.api();
            if (serverSide()) return api.isManagerLoaded() ? api.getManager().getTeamByID(id) : Optional.empty();
            return api.isClientManagerLoaded() ? api.getClientManager().getTeamByID(id) : Optional.empty();
        }

        private static UUID parseId(String id) {
            if (id == null) return null;
            try { return UUID.fromString(id); }
            catch (IllegalArgumentException ignored) { return null; }
        }
    }
}
