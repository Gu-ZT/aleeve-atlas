package dev.dubhe.map.client.territory;

import dev.anvilcraft.lib.v2.integration.Integration;
import dev.anvilcraft.lib.v2.integration.IntegrationType;
import dev.dubhe.map.api.territory.AtlasTerritory;
import dev.ftb.mods.ftbchunks.api.client.event.ChunksUpdatedFromServerEvent;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/// 仅由 AnvilLib 在安装 FTB Chunks 的客户端加载；不访问服务端隐藏的领地。
@Integration(value = "ftbchunks", version = "[2111.1.1,2112)", type = IntegrationType.CLIENT)
public final class FtbChunksIntegration {
    private final FtbClaimOverlays claims = new FtbClaimOverlays(AtlasTerritory.client());
    private int ticks;

    public void applyClient() {
        ChunksUpdatedFromServerEvent.UPDATED.register(this::updated);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::logout);
    }

    private void updated(ChunksUpdatedFromServerEvent event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        // FTB 在客户端 tick 中统一派发此事件，包含登录同步和后续认领、取消认领。
        var team = event.getTeam().orElse(null);
        if (team != null) claims.appearance(team.getId(), team.getName(),
                team.getProperty(TeamProperties.COLOR).rgb());
        for (var position : event.getChunks()) {
            claims.update(new FtbClaimOverlays.Position(position.dimension(),
                    new ChunkPos(position.x(), position.z())), team == null ? null : team.getId());
        }
    }

    private void tick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) return;
        // 重新查询当前 manager，兼容全量队伍重同步、改名及改色，不持有失效的 Team 对象。
        if (++ticks >= 20) {
            ticks = 0;
            var api = FTBTeamsAPI.api();
            if (api.isClientManagerLoaded()) {
                for (var id : claims.teams()) api.getClientManager().getTeamByID(id).ifPresent(team ->
                        claims.appearance(id, team.getName(), team.getProperty(TeamProperties.COLOR).rgb()));
            }
        }
        claims.flush();
    }

    private void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        claims.clear();
        ticks = 0;
    }
}
