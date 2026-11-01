package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.map.MapDimensionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/// 全屏世界地图的入口与画布持有者。
///
/// 画布随世界地图界面生命周期复用，避免每次打开都新建 GPU 纹理。
public final class AtlasWorldMap {
    private static MapCanvas canvas;

    private AtlasWorldMap() {
    }

    /// 打开世界地图界面，视野中心对齐玩家当前位置。
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        ClientMapManager manager = AtlasClientMap.manager();
        if (manager == null) {
            return;
        }
        MapDimensionCache cache = manager.displayCache();
        MapCanvas mapCanvas = canvas();
        minecraft.setScreen(new WorldMapScreen(mapCanvas, cache, player.getX(), player.getZ()));
    }

    /// 资源重载后标记画布失效，下次打开时重新注册纹理。
    public static void onResourceReload() {
        dev.dubhe.map.client.cartography.ImportedMapRenderer.clear();
        if (canvas != null) {
            canvas.onResourceReload();
        }
    }

    /// 关闭并释放画布。
    public static void release() {
        dev.dubhe.map.client.cartography.ImportedMapRenderer.clear();
        WorldMapScreen.clearActive();
        if (canvas != null) {
            canvas.close();
            canvas = null;
        }
    }

    private static MapCanvas canvas() {
        if (canvas == null) {
            ResourceLocation textureId = AleeveAtlas.id("world_map_canvas");
            canvas = new MapCanvas(textureId);
        }
        return canvas;
    }
}
