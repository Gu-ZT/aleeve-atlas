package dev.dubhe.map.sync;

import com.mojang.authlib.GameProfile;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.AtlasSession;
import dev.dubhe.map.network.PlayerPositionPayload;
import dev.dubhe.map.network.TeamSightingsPayload;
import dev.dubhe.map.client.radar.VisibilityGate;
import dev.dubhe.map.team.TeamProviders;
import dev.dubhe.map.config.NonTeamVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 客户端远端玩家位置缓存与周期上报。
///
/// 远端对象只用于地图标记，实际加载的同名实体优先，避免重复绘制。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class ClientPlayerPositions {
    private static final int REPORT_INTERVAL_TICKS = 10;
    private static final Map<UUID, RemotePlayer> REMOTE_PLAYERS = new HashMap<>();
    private static final TeamSightingsCache SIGHTINGS = new TeamSightingsCache();
    private static String currentDimension;
    private static int ticks;

    private ClientPlayerPositions() {
    }

    /// 接收服务端转发的位置；维度不匹配或收到移除时删除缓存。
    public static void handle(PlayerPositionPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!syncDimension(minecraft) || payload.removal()
                || !payload.dimensionId().equals(currentDimension)) {
            REMOTE_PLAYERS.remove(payload.playerId());
            return;
        }
        RemotePlayer remote = REMOTE_PLAYERS.computeIfAbsent(payload.playerId(),
                id -> new RemotePlayer((ClientLevel) minecraft.level,
                        new GameProfile(id, payload.playerName())));
        remote.setPos(payload.x(), payload.y(), payload.z());
    }

    /// 当前维度的远端玩家，供雷达合并到实体列表。
    public static List<RemotePlayer> remotePlayers() {
        if (!syncDimension(Minecraft.getInstance())) {
            return List.of();
        }
        return List.copyOf(REMOTE_PLAYERS.values());
    }

    /// 共享视野只接受当前队友的快照；即使目标不在本机视距内，也保留地图标记。
    public static void handleSightings(TeamSightingsPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !syncDimension(minecraft)
                || !TeamProviders.active().sameTeam(minecraft.player.getUUID(), payload.observerId())) {
            return;
        }
        SIGHTINGS.update(payload, currentDimension, System.currentTimeMillis());
        for (PlayerPositionPayload sighting : payload.sightings()) {
            handle(sighting, context);
        }
    }

    public static boolean sharedVisible(UUID target) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && syncDimension(minecraft)
                && AtlasSession.nonTeamVisibility() != NonTeamVisibility.NEVER
                && SIGHTINGS.visible(target,
                        observer -> TeamProviders.active().sameTeam(minecraft.player.getUUID(), observer),
                        System.currentTimeMillis());
    }

    /// 维度切换后丢弃旧维度位置，防止远端标记出现在新世界。
    private static boolean syncDimension(Minecraft minecraft) {
        if (minecraft.level == null) {
            return false;
        }
        String dimension = minecraft.level.dimension().location().toString();
        if (!dimension.equals(currentDimension)) {
            REMOTE_PLAYERS.clear();
            SIGHTINGS.clear();
            currentDimension = dimension;
        }
        return true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!AtlasSession.synchronizedMode()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !syncDimension(minecraft)) {
            return;
        }
        if (++ticks < REPORT_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        var connection = minecraft.getConnection();
        if (connection == null || !connection.hasChannel(PlayerPositionPayload.TYPE)) {
            return;
        }
        PacketDistributor.sendToServer(new PlayerPositionPayload(
                minecraft.player.getUUID(), minecraft.player.getGameProfile().getName(),
                minecraft.level.dimension().location().toString(),
                minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(), false));
        if (connection.hasChannel(TeamSightingsPayload.TYPE)) {
            List<PlayerPositionPayload> sightings = new ArrayList<>();
            for (var target : minecraft.level.players()) {
                if (target == minecraft.player || TeamProviders.active()
                        .sameTeam(target.getUUID(), minecraft.player.getUUID())) {
                    continue;
                }
                // 只上报本机实际观察结果，不能把队友共享的视野再次转发形成回路。
                if (VisibilityGate.localOpacity(minecraft.level, minecraft.player, target) > 0.0F) {
                    sightings.add(new PlayerPositionPayload(target.getUUID(), target.getGameProfile().getName(),
                            currentDimension, target.getX(), target.getY(), target.getZ(), false));
                    if (sightings.size() == TeamSightingsPayload.MAX_SIGHTINGS) {
                        break;
                    }
                }
            }
            PacketDistributor.sendToServer(new TeamSightingsPayload(minecraft.player.getUUID(), sightings));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        REMOTE_PLAYERS.clear();
        SIGHTINGS.clear();
        currentDimension = null;
        ticks = 0;
    }
}
