package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.api.territory.AtlasTerritory;
import dev.dubhe.map.client.territory.ClientTerritorySync;
import dev.dubhe.map.client.profiling.AtlasProfiling;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.config.ConfigBounds;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/// 客户端地图管理器的生命周期与调度。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasClientMap {
    /// 定期写入磁盘的间隔（tick），避免异常退出丢失已探索数据。
    private static final int FLUSH_INTERVAL_TICKS = 200;

    private static ClientMapManager manager;
    private static int ticksSinceFlush;

    private AtlasClientMap() {
    }

    public static ClientMapManager manager() {
        if (manager == null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null && minecraft.player != null) {
                initManager();
            }
        }
        return manager;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (manager == null) {
            initManager();
        }
        long started = System.nanoTime();
        try {
            manager.tick(minecraft.level);
        } finally {
            AtlasProfiling.recordMapTick(System.nanoTime() - started);
        }
        if (++ticksSinceFlush >= FLUSH_INTERVAL_TICKS) {
            ticksSinceFlush = 0;
            started = System.nanoTime();
            try {
                manager.flush();
                var imports = dev.dubhe.map.client.cartography.ClientCartography.currentStore();
                if (imports != null && imports.pruneExplored(manager.currentCache()) > 0) {
                    WorldMapScreen.onMapDataChanged();
                }
            } finally {
                AtlasProfiling.recordMapMaintenance(System.nanoTime() - started);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (manager != null) {
            manager.flush();
            manager.clear();
            // 释放动态纹理，避免重连时残留 GPU 纹理占用显存。
            manager.releaseTexture();
            manager = null;
        }
        AtlasWorldMap.release();
        // 领地分片累加器属会话级状态，切换服务器后残余条目会与新分片混合。
        ClientTerritorySync.clear();
        ticksSinceFlush = 0;
    }

    /// 配置重载时同步领地锚点权重。
    @SubscribeEvent
    public static void onConfigReload(
            ModConfigEvent.Reloading event) {
        applyAnchorWeight();
    }

    /// 资源重载完成后重新注册动态纹理。
    ///
    /// 资源重载会清空纹理管理器中的全部纹理，包括本模组注册的动态纹理；
    /// 若不重置注册标志，小地图会持续渲染空白直到重启游戏。
    @SubscribeEvent
    public static void registerReload(AddClientReloadListenersEvent event) {
        var id = AleeveAtlas.id("map_textures");
        event.addListener(id, (ResourceManagerReloadListener) resources -> onResourceReload());
        event.addDependency(VanillaClientListeners.TEXTURES, id);
    }

    private static void onResourceReload() {
        if (manager != null) {
            manager.onResourceReload();
        }
        AtlasWorldMap.onResourceReload();
    }

    /// 把 `territory.anchorWeight` 应用到领地注册表。
    private static void applyAnchorWeight() {
        float lambda = (float) ConfigBounds.clamp(AtlasClientConfigs.CONFIG.territory.anchorWeight,
                0.3, 0.5);
        AtlasTerritory.clientRegistry().setAnchorLambda(lambda);
    }

    /// 创建地图管理器并同步领地锚点权重配置。
    private static void initManager() {
        manager = new ClientMapManager();
        applyAnchorWeight();
        DemoTerritory.registerIfDebugging();
    }
}
