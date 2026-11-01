package dev.dubhe.map.client;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.map.AtlasWorldMap;
import dev.dubhe.map.client.map.AtlasClientMap;
import dev.dubhe.map.client.map.MinimapZoom;
import dev.dubhe.map.client.waypoint.ClientWaypoints;
import dev.dubhe.map.client.waypoint.WaypointEditScreen;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.ping.ClientPings;
import dev.dubhe.map.ping.MapPingData;
import dev.dubhe.map.sync.ClientMapSync;
import dev.dubhe.map.waypoint.Waypoint;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/// 客户端 tick 入口：在 `ClientTickEvent.Post` 中 while 消费键位。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasClientTick {
    private static final Logger LOGGER =
            LoggerFactory.getLogger("aleeve_atlas/selftest");

    private AtlasClientTick() {
    }

    /// 自检只执行一次。
    private static boolean scrollSelfTestDone;
    /// 联机自检只执行一次。
    private static boolean connectSelfTestDone;
    /// 实体生成自检只执行一次。
    private static boolean entitySummonDone;
    /// 路径点与标点自检只执行一次。
    private static boolean markerDemoDone;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // 自动化验收：在单机世界生成各类实体，验证雷达分类与配色（正常游玩不触发）。
        if (!entitySummonDone && System.getenv("ALEEVE_ATLAS_SUMMON_ENTITIES") != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.hasSingleplayerServer()) {
                entitySummonDone = true;
                summonDemoEntities(minecraft);
            }
        }
        // 自动化验收：放置路径点与标点，验证图标与波纹渲染（正常游玩不触发）。
        if (!markerDemoDone && System.getenv("ALEEVE_ATLAS_DEMO_MARKERS") != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.level != null) {
                markerDemoDone = true;
                placeDemoMarkers(minecraft);
            }
        }
        // 自动化验收：打开世界地图并连续平移，验证像素搬移路径（正常游玩不触发）。
        if (!scrollSelfTestDone && System.getenv("ALEEVE_ATLAS_SCROLL_SELFTEST") != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.level != null
                    && minecraft.screen == null) {
                scrollSelfTestDone = true;
                AtlasWorldMap.open();
            }
        }
        // 自动化验收：程序化按住玩家列表键，验证混合模式的头像切换（正常游玩不触发）。
        String holdTab = System.getenv("ALEEVE_ATLAS_HOLD_TAB");
        if (holdTab != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                KeyMapping.set(minecraft.options.keyPlayerList.getDefaultKey(), true);
            }
        }
        // 自动化验收：直接连接指定服务器，验证双端握手与协同模式（正常游玩不触发）。
        String serverAddress = System.getenv("ALEEVE_ATLAS_CONNECT_SERVER");
        if (!connectSelfTestDone && serverAddress != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof TitleScreen) {
                connectSelfTestDone = true;
                ConnectScreen.startConnecting(
                        minecraft.screen,
                        minecraft,
                        ServerAddress.parseString(serverAddress),
                        new ServerData(
                                "atlas_selftest", serverAddress,
                                ServerData.Type.OTHER),
                        false,
                        null);
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // 标点由外部模组经 API 注入，本模组只负责生命周期推进与渲染。
        ClientPings.tick();
        // 地图增量分片按 tick 限速写入本地缓存。
        ClientMapSync.tick();
        if (minecraft.screen != null) {
            return;
        }
        while (AtlasKeyMappings.OPEN_WORLD_MAP.consumeClick()) {
            AtlasWorldMap.open();
        }
        while (AtlasKeyMappings.NEW_WAYPOINT.consumeClick()) {
            minecraft.setScreen(new WaypointEditScreen(null, minecraft.player.blockPosition()));
        }
        while (AtlasKeyMappings.MINIMAP_ZOOM_IN.consumeClick()) {
            adjustMinimapZoom(0.25);
        }
        while (AtlasKeyMappings.MINIMAP_ZOOM_OUT.consumeClick()) {
            adjustMinimapZoom(-0.25);
        }
    }

    private static void adjustMinimapZoom(double delta) {
        var manager = AtlasClientMap.manager();
        boolean cave = manager != null && manager.displaySlice().cave();
        var config = AtlasClientConfigs.CONFIG;
        MinimapZoom.adjust(config, cave, delta);
        // 配置界面读取 spec；同步当前模式的值，避免关页时把快捷键修改恢复成旧值。
        List<String> path = List.of(MinimapZoom.usesCaveZoom(config, cave) ? "caves" : "minimap", "zoom");
        for (ModConfig modConfig : ModConfigs.getModConfigs(AleeveAtlas.MOD_ID)) {
            if (modConfig.getType() == ModConfig.Type.CLIENT
                    && modConfig.getSpec() instanceof ModConfigSpec spec && spec.isLoaded()) {
                Object value = spec.getValues().get(path);
                if (value instanceof ModConfigSpec.DoubleValue zoomValue) {
                    zoomValue.set(MinimapZoom.target(config, cave));
                }
            }
        }
    }

    /// 在单机世界生成各类实体，覆盖雷达的全部类别。
    ///
    /// 通过内建服务器派发命令，无需玩家手动操作。
    private static void summonDemoEntities(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        String[] commands = {
            "execute at @p run summon minecraft:zombie ~3 ~ ~3",
            "execute at @p run summon minecraft:skeleton ~-3 ~ ~3",
            "execute at @p run summon minecraft:pig ~2 ~ ~-3",
            "execute at @p run summon minecraft:cow ~-2 ~ ~-3",
            "execute at @p run summon minecraft:chicken ~0 ~ ~4",
            "execute at @p run summon minecraft:oak_boat ~5 ~ ~0"
        };
        server.execute(() -> {
            var source = server.createCommandSourceStack();
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(source, command);
            }
            LOGGER.info("Summoned {} demo entities for radar verification", commands.length);
        });
    }

    /// 在玩家周围放置普通路径点、死亡点与各类型标点，验证渲染区分。
    ///
    /// 仅用于自动化验收，正常游玩不触发。
    private static void placeDemoMarkers(Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        var dimension = minecraft.level.dimension();
        var base = player.blockPosition();
        // 普通路径点分别置于四个方向，便于观察菱形与颜色。
        ClientWaypoints.add(Waypoint.create("north", base.offset(0, 0, -20), dimension,
                0xFF55FFFF));
        ClientWaypoints.add(Waypoint.create("east", base.offset(20, 0, 0), dimension,
                0xFFFFFF55));
        // 死亡点使用十字图标。
        ClientWaypoints.add(Waypoint.deathPoint(base.offset(-15, 0, -15), dimension));
        // 四种标点类型环绕玩家，验证形状区分与波纹扩散。
        long now = System.currentTimeMillis();
        MapPingData.PingIcon[] icons = MapPingData.PingIcon.values();
        for (int i = 0; i < icons.length; i++) {
            double angle = Math.PI * 2 * i / icons.length;
            Vec3 pos = new Vec3(player.getX() + Math.cos(angle) * 10,
                    player.getY(), player.getZ() + Math.sin(angle) * 10);
            ClientPings.upsert(new MapPingData(UUID.randomUUID(), player.getUUID(),
                    player.getName().getString(), 0xFF55FF55,
                    MapPingData.dimensionId(dimension), pos, null, icons[i], now, 600000L));
        }
        LOGGER.info("Placed demo waypoints and {} ping icons for render verification",
                icons.length);
    }
}
