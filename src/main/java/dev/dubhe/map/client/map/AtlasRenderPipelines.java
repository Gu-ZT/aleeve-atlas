package dev.dubhe.map.client.map;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.dubhe.map.AleeveAtlas;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/// 小地图叠加层渲染管线。
///
/// 圆形遮罩、圆形/方形边框与水滴形玩家指示器全部由片元着色器解析式绘制，
/// 避免逐行填充几何体，满足 HUD 帧耗要求。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasRenderPipelines {
    /// 地图纹理的圆形裁剪管线：圆外直接丢弃像素，使圆形小地图之外保持游戏画面原样。
    public static final RenderPipeline MINIMAP_CIRCLE = RenderPipeline
            .builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(AleeveAtlas.id("pipeline/minimap_circle"))
            .withVertexShader(AleeveAtlas.id("core/minimap_circle"))
            .withFragmentShader(AleeveAtlas.id("core/minimap_circle"))
            .build();

    /// 小地图边框独立绘制，避免玩家指示器样式影响边框。
    public static final RenderPipeline MINIMAP_OVERLAY = RenderPipeline
            .builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(AleeveAtlas.id("pipeline/minimap_overlay"))
            .withVertexShader(AleeveAtlas.id("core/minimap_overlay"))
            .withFragmentShader(AleeveAtlas.id("core/minimap_overlay"))
            .build();

    /// 世界地图光滑四角方向标，逆时针半臂使用空心轮廓。
    public static final RenderPipeline WORLD_COMPASS = markerPipeline(
            "world_compass", "core/world_compass");

    /// 玩家水滴指示器以距离场绘制抗锯齿轮廓和黑色描边。
    public static final RenderPipeline PLAYER_DROPLET = markerPipeline(
            "player_droplet", "core/player_droplet");

    /// 实体圆点标记管线。
    public static final RenderPipeline MAP_MARKER_DOT = markerPipeline(
            "map_marker_dot", "core/map_marker_dot");

    /// 光滑菱形标记管线。
    public static final RenderPipeline MAP_MARKER_DIAMOND = markerPipeline(
            "map_marker_diamond", "core/map_marker_diamond");

    /// 光滑叉形标记管线，供死亡点和危险标点复用。
    public static final RenderPipeline MAP_MARKER_CROSS = markerPipeline(
            "map_marker_cross", "core/map_marker_cross");

    /// 临时标点波纹管线，使用解析圆环实现抗锯齿。
    public static final RenderPipeline MAP_PING_RIPPLE = markerPipeline(
            "map_ping_ripple", "core/map_ping_ripple");

    /// 地图边缘方向箭头管线。
    public static final RenderPipeline MAP_MARKER_ARROW = markerPipeline(
            "map_marker_arrow", "core/map_marker_arrow");

    private static RenderPipeline markerPipeline(String name, String fragmentShader) {
        return RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(AleeveAtlas.id("pipeline/" + name))
                .withVertexShader(AleeveAtlas.id("core/minimap_overlay"))
                .withFragmentShader(AleeveAtlas.id(fragmentShader))
                .build();
    }

    private AtlasRenderPipelines() {
    }

    @SubscribeEvent
    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(MINIMAP_CIRCLE);
        event.registerPipeline(MINIMAP_OVERLAY);
        event.registerPipeline(WORLD_COMPASS);
        event.registerPipeline(PLAYER_DROPLET);
        event.registerPipeline(MAP_MARKER_DOT);
        event.registerPipeline(MAP_MARKER_DIAMOND);
        event.registerPipeline(MAP_MARKER_CROSS);
        event.registerPipeline(MAP_PING_RIPPLE);
        event.registerPipeline(MAP_MARKER_ARROW);
    }
}
