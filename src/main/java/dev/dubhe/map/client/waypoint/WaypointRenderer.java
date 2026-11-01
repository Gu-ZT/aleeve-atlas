package dev.dubhe.map.client.waypoint;

import dev.dubhe.map.client.map.MapMarkerRenderer;
import dev.dubhe.map.client.map.MinimapLayout;
import dev.dubhe.map.config.AtlasClientConfig;
import dev.dubhe.map.config.MinimapShape;
import dev.dubhe.map.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

/// 路径点标记渲染。
///
/// 小地图与世界地图共用同一套绘制：以菱形标记 + 深色描边表示路径点位置。
public final class WaypointRenderer {
    private WaypointRenderer() {
    }

    /// 在小地图上绘制可见路径点。
    ///
    /// 落在小地图范围内的路径点绘制在对应坐标；超出范围的钳制到边缘，
    /// 以缩小标记提示方向，避免玩家看不到视野外路径点的方位。
    public static void renderOnMinimap(GuiGraphicsExtractor graphics, MinimapLayout layout,
            boolean circular, double rotationDegrees) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        List<Waypoint> waypoints = ClientWaypoints.visibleWaypoints();
        for (Waypoint waypoint : waypoints) {
            double[] rotated = layout.rotatedScreenPoint(waypoint.pos().getX() + 0.5,
                    waypoint.pos().getZ() + 0.5, rotationDegrees);
            double screenX = rotated[0];
            double screenZ = rotated[1];
            if (layout.contains(screenX, screenZ, circular)) {
                double[] fitted = layout.fitInside(screenX, screenZ, circular, 4.0, 0.0);
                drawMarker(graphics, fitted[0], fitted[1], waypoint.colorArgb(),
                        waypoint.icon());
                continue;
            }
            drawEdgeMarker(graphics, layout, screenX, screenZ, circular,
                    waypoint.colorArgb());
        }
    }

    /// 小地图边缘标记：把超出范围的路径点钳制到最外圈，绘制小尺寸方向点。
    private static void drawEdgeMarker(GuiGraphicsExtractor graphics, MinimapLayout layout,
            double screenX, double screenZ, boolean circular, int color) {
        double centerX = layout.x() + layout.size() / 2.0;
        double centerZ = layout.y() + layout.size() / 2.0;
        double dx = screenX - centerX;
        double dz = screenZ - centerZ;
        if (dx == 0.0 && dz == 0.0) {
            return;
        }
        // 边缘标记使用指向目标的光滑三角箭头，中心内缩以避免超出地图边界。
        double[] fitted = layout.fitInside(screenX, screenZ, circular, 6.0, 0.0);
        double angle = MapMarkerRenderer.directionAngle(dx, dz);
        MapMarkerRenderer.drawArrow(graphics, fitted[0], fitted[1], 8, angle, color);
    }

    /// 在世界地图上绘制路径点（屏幕坐标由调用方换算）。
    public static void renderOnWorldMap(GuiGraphicsExtractor graphics, double screenX, double screenZ,
            int color) {
        renderOnWorldMap(graphics, screenX, screenZ, color, Waypoint.DEFAULT_ICON);
    }

    /// 在世界地图上按图标类型绘制路径点。
    public static void renderOnWorldMap(GuiGraphicsExtractor graphics, double screenX, double screenZ,
            int color, String icon) {
        drawMarker(graphics, screenX, screenZ, color, icon);
    }

    /// 按图标类型绘制路径点标记。
    ///
    /// 死亡点绘制叉形，其余绘制菱形，形状差异使两类路径点在缩小后仍可区分。
    static void drawMarker(GuiGraphicsExtractor graphics, double screenX, double screenZ, int color,
            String icon) {
        if (Waypoint.DEATH_ICON.equals(icon)) {
            drawCross(graphics, screenX, screenZ, color);
        } else {
            drawDiamond(graphics, screenX, screenZ, color);
        }
    }

    /// 死亡点标记：以光滑叉形与普通路径点的菱形区分。
    private static void drawCross(GuiGraphicsExtractor graphics, double screenX, double screenZ,
            int color) {
        MapMarkerRenderer.drawCross(graphics, screenX, screenZ, 6, color);
    }

    private static void drawDiamond(GuiGraphicsExtractor graphics, double screenX, double screenZ,
            int color) {
        MapMarkerRenderer.drawDiamond(graphics, screenX, screenZ, 6, color);
    }

    /// 圆形小地图形态判定工具。
    public static boolean circular(AtlasClientConfig config) {
        return config.minimap.shape == MinimapShape.CIRCLE;
    }
}
