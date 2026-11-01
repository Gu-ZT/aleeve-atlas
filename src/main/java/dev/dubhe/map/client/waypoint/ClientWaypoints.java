package dev.dubhe.map.client.waypoint;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.AtlasSession;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.config.WaypointSendPolicy;
import dev.dubhe.map.storage.ServerKey;
import dev.dubhe.map.waypoint.Waypoint;
import dev.dubhe.map.waypoint.WaypointStore;
import dev.dubhe.map.waypoint.WaypointSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// 客户端路径点管理器。
///
/// 负责按服务器标识打开/切换路径点存储、新建路径点、记录死亡点，并在退出时写入磁盘。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class ClientWaypoints {
    /// 定期写入磁盘的间隔（tick）。
    private static final int FLUSH_INTERVAL_TICKS = 200;

    private static WaypointStore store;
    private static int ticksSinceFlush;
    private static String currentServerKey;
    /// 按「服务器 + 维度」缓存的存储实例。
    private static final Map<String, WaypointStore> STORES =
            new HashMap<>();
    /// 待玩家确认的队友路径点（手动接受策略）。
    private static final Map<UUID, ClientWaypointSync.PendingWaypoint> PENDING =
            new ConcurrentHashMap<>();

    private ClientWaypoints() {
    }

    public static Map<UUID, ClientWaypointSync.PendingWaypoint> pending() {
        return PENDING;
    }

    /// 当前维度对应的路径点存储；未连接时返回 {@code null}。
    ///
    /// 每个维度独立文件，切换维度时自动切换存储实例。
    public static WaypointStore store() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        String key = serverKey(minecraft);
        if (!key.equals(currentServerKey)) {
            flushAll();
            currentServerKey = key;
            STORES.clear();
        }
        String dimensionId = minecraft.level.dimension().location().toString();
        WaypointStore cached = STORES.get(dimensionId);
        if (cached == null) {
            cached = WaypointStore.open(minecraft.gameDirectory.toPath(), key,
                    minecraft.level.dimension());
            STORES.put(dimensionId, cached);
            if (!AtlasClientConfigs.CONFIG.waypoint.keepDeathPoints) {
                cached.pruneDeathPoints();
            }
        }
        store = cached;
        return cached;
    }

    /// 取指定维度对应的存储：当前维度用活动存储，其他维度按需打开。
    private static WaypointStore deathStoreFor(ResourceKey<Level> dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && dimension.equals(minecraft.level.dimension())) {
            WaypointStore current = store();
            if (current != null) {
                return current;
            }
        }
        return WaypointStore.open(minecraft.gameDirectory.toPath(), serverKey(minecraft),
                dimension);
    }

    /// 把内存中已加载的维度存储写入磁盘。
    private static void flushAll() {
        for (WaypointStore each : STORES.values()) {
            each.flush();
        }
    }

    /// 把内存中已加载的维度存储写入磁盘（导出前调用）。
    ///
    /// 未访问过的维度其存储不在内存中，无需写入：`allAcrossDimensions` 会从磁盘补齐。
    public static void flushAllDimensions() {
        flushAll();
    }

    /// 全部维度的路径点（导入导出用）。
    ///
    /// 以磁盘文件为准：玩家未访问过的维度其存储不在内存中，
    /// 仅遍历已加载存储会让导出遗漏其他维度的路径点。
    public static List<Waypoint> allAcrossDimensions() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return List.of();
        }
        List<Waypoint> result = new ArrayList<>();
        for (WaypointStore each : WaypointStore.openAll(minecraft.gameDirectory.toPath(),
                serverKey(minecraft))) {
            result.addAll(each.all());
        }
        return result;
    }

    /// 按维度写入路径点：自动选择或创建对应维度的存储。
    public static void upsertInDimension(Waypoint waypoint) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        WaypointStore current = store();
        if (waypoint.dimension().equals(minecraft.level.dimension())) {
            if (current != null) {
                current.upsert(waypoint);
            }
            return;
        }
        upsertStoredDimension(minecraft.gameDirectory.toPath(), serverKey(minecraft), STORES, waypoint);
    }

    static void upsertStoredDimension(Path gameDir, String serverKey, Map<String, WaypointStore> stores, Waypoint waypoint) {
        // 已访问维度必须更新同一缓存实例，否则之后切回或保存时会丢失导入点。
        WaypointStore other = stores.get(waypoint.dimension().location().toString());
        if (other == null) {
            other = WaypointStore.open(gameDir, serverKey, waypoint.dimension());
        }
        other.upsert(waypoint);
        other.flush();
    }

    /// 按维度和 ID 删除路径点（跨维度同步删除用）。
    public static boolean removeInDimension(Waypoint waypoint) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        if (minecraft.level != null && waypoint.dimension().equals(minecraft.level.dimension())) {
            WaypointStore current = store();
            return current != null && current.remove(waypoint.id());
        }
        String key = serverKey(minecraft);
        WaypointStore other = WaypointStore.open(minecraft.gameDirectory.toPath(), key,
                waypoint.dimension());
        boolean removed = other.remove(waypoint.id());
        if (removed) {
            other.flush();
        }
        return removed;
    }

    /// 当前服务器标识（导入导出与离线维度写入用）。
    static String currentServerKeyForIo() {
        Minecraft minecraft = Minecraft.getInstance();
        return serverKey(minecraft);
    }

    /// 在玩家当前位置新建路径点（`N` 键）。
    public static Waypoint createHere(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || store() == null) {
            return null;
        }
        BlockPos pos = minecraft.player.blockPosition();
        Waypoint waypoint = Waypoint.create(name, pos, minecraft.level.dimension(), 0xFF55FFFF);
        // 经 add 统一处理写入与自动同步。
        add(waypoint);
        return waypoint;
    }

    /// 修改已有路径点（启用状态、私密标记等），自动同步策略下广播变更。
    public static void update(Waypoint waypoint) {
        WaypointStore waypointStore = store();
        if (waypointStore == null) {
            return;
        }
        waypointStore.upsert(waypoint);
        if (AtlasClientConfigs.CONFIG.waypoint.sendPolicy
                == WaypointSendPolicy.AUTO_SYNC) {
            shareIfAllowed(waypoint);
        }
    }

    /// 新增路径点（按自动同步策略下发往服务端）。
    public static void add(Waypoint waypoint) {
        WaypointStore waypointStore = store();
        if (waypointStore == null) {
            return;
        }
        waypointStore.upsert(waypoint);
        if (AtlasClientConfigs.CONFIG.waypoint.sendPolicy
                == WaypointSendPolicy.AUTO_SYNC) {
            shareIfAllowed(waypoint);
        }
    }

    /// 自动同步策略下的广播：私密路径点不参与，避免被自动分享。
    private static void shareIfAllowed(Waypoint waypoint) {
        if (!waypoint.shareable()) {
            return;
        }
        share(waypoint);
    }

    /// 某维度的全部路径点，包含未启用条目，供管理界面使用。
    public static List<Waypoint> allInDimension(ResourceKey<Level> dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return List.of();
        }
        WaypointStore current = store();
        if (current == null) {
            return List.of();
        }
        WaypointStore waypointStore = current;
        if (minecraft.level != null && !dimension.equals(minecraft.level.dimension())) {
            String dimensionId = dimension.location().toString();
            waypointStore = STORES.get(dimensionId);
            if (waypointStore == null) {
                waypointStore = WaypointStore.open(minecraft.gameDirectory.toPath(),
                        serverKey(minecraft), dimension);
                STORES.put(dimensionId, waypointStore);
            }
        }
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : waypointStore.all()) {
            if (waypoint.dimension().equals(dimension)) {
                result.add(waypoint);
            }
        }
        return result;
    }

    /// 某维度的全部启用路径点（世界地图展示用，不按玩家距离过滤）。
    public static List<Waypoint> waypointsFor(
            ResourceKey<Level> dimension) {
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : allInDimension(dimension)) {
            if (waypoint.enabled()) {
                result.add(waypoint);
            }
        }
        return result;
    }

    /// 把路径点分享给在线队友。
    public static void share(Waypoint waypoint) {
        if (!AtlasSession.synchronizedMode() || !waypoint.shareable()) {
            return;
        }
        ClientPacketDistributor.sendToServer(
                new WaypointSyncPayload(WaypointSyncPayload.Action.UPSERT, waypoint));
    }

    /// 通知队友删除某路径点。
    public static void shareDelete(Waypoint waypoint) {
        if (!AtlasSession.synchronizedMode()) {
            return;
        }
        ClientPacketDistributor.sendToServer(
                new WaypointSyncPayload(WaypointSyncPayload.Action.DELETE, waypoint));
    }

    /// 记录玩家死亡位置，供复活后生成死亡点。
    ///
    /// 使用客户端专属的 `ClientPlayerNetworkEvent.Clone`：服务端的 `PlayerEvent.Clone`
    /// 不会在客户端触发，此前订阅错误导致死亡点功能实际不可用。
    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        var oldPlayer = event.getOldPlayer();
        if (oldPlayer == null || oldPlayer.level() == null) {
            return;
        }
        // 仅当旧玩家确实死亡（生命值归零）时记录，避免维度切换误触发。
        if (oldPlayer.getHealth() > 0.0F) {
            return;
        }
        WaypointStore waypointStore = store();
        if (waypointStore == null) {
            return;
        }
        Waypoint death = Waypoint.deathPoint(oldPlayer.blockPosition(),
                oldPlayer.level().dimension());
        // 不保留历史死亡点时先清掉旧点，再写入本次死亡点；
        // 顺序反了会把刚写入的新点一并删除，导致死亡点功能失效。
        // 裁剪必须作用于死亡点自身的维度存储：在下界死亡、复活回主世界时活动存储是主世界。
        // 非当前维度的存储为临时实例，需立即写入磁盘，否则裁剪结果丢失。
        if (!AtlasClientConfigs.CONFIG.waypoint.keepDeathPoints) {
            WaypointStore deathStore = deathStoreFor(death.dimension());
            deathStore.pruneDeathPoints();
            if (deathStore != waypointStore) {
                deathStore.flush();
            }
        }
        // 死亡点按自身维度写入：在下界死亡、复活回主世界时不会写错文件。
        upsertInDimension(death);
        var newPlayer = event.getNewPlayer();
        if (newPlayer != null) {
            newPlayer.displayClientMessage(
                    Component.translatable("aleeve_atlas.message.death_point_added"), true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        flushAndClose();
    }

    /// 周期性写入磁盘：仅在异常退出或崩溃时仍能保留路径点。
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (store == null) {
            return;
        }
        if (++ticksSinceFlush >= FLUSH_INTERVAL_TICKS) {
            ticksSinceFlush = 0;
            flushAll();
        }
    }

    private static void flushAndClose() {
        flushAll();
        for (WaypointStore each : STORES.values()) {
            each.clear();
        }
        STORES.clear();
        // 未处理的手动接受请求随登出丢弃，避免跨服务器残留。
        PENDING.clear();
        store = null;
        currentServerKey = null;
        ticksSinceFlush = 0;
    }

    /// 供世界地图界面显示的路径点集合。
    public static List<Waypoint> visibleWaypoints() {
        Minecraft minecraft = Minecraft.getInstance();
        WaypointStore waypointStore = store();
        if (minecraft.player == null || minecraft.level == null || waypointStore == null) {
            return List.of();
        }
        return waypointStore.visibleIn(minecraft.player.getX(), minecraft.player.getZ());
    }

    private static String serverKey(Minecraft minecraft) {
        return ServerKey.resolve(minecraft);
    }

    /// 维度键工具（供其他模块复用）。
    public static ResourceKey<Level> dimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? Level.OVERWORLD : minecraft.level.dimension();
    }
}
