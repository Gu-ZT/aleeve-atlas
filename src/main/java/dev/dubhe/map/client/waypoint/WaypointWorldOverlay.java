package dev.dubhe.map.client.waypoint;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.map.MapMarkerRenderer;
import dev.dubhe.map.client.map.MinimapLayout;
import dev.dubhe.map.waypoint.Waypoint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

/// 游戏内 3D 视界路径点标识。
///
/// 在 HUD 阶段把路径点世界坐标投影到屏幕，绘制路径点图标与名称；
/// 玩家视线靠近路径点时额外显示距离。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class WaypointWorldOverlay {
    private static final int ICON_HALF_SIZE = 4;
    private static final float MARKER_OPACITY = 0.75F;
    private static final int TEXT_COLOR = 0xBFFFFFFF;
    private static final int DISTANCE_BACKGROUND = 0xFF101010;

    private WaypointWorldOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui
                || MinimapLayout.focused(minecraft)) {
            return;
        }
        List<Waypoint> waypoints = ClientWaypoints.visibleWaypoints();
        if (waypoints.isEmpty()) {
            return;
        }
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        for (Waypoint waypoint : waypoints) {
            Vec3 target = new Vec3(waypoint.pos().getX() + 0.5, waypoint.pos().getY() + 1.2,
                    waypoint.pos().getZ() + 0.5);
            WorldProjection.project(target)
                    .ifPresent(projected -> drawMarker(graphics, minecraft, waypoint, projected,
                            WorldProjection.isLookingAt(projected, guiWidth, guiHeight)));
        }
    }

    private static void drawMarker(GuiGraphicsExtractor graphics, Minecraft minecraft, Waypoint waypoint,
            WorldProjection.Projected projected, boolean showDistance) {
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) projected.screenX(), (float) projected.screenY());
        int x = 0;
        int y = 0;
        int markerColor = applyOpacity(waypoint.colorArgb(), MARKER_OPACITY);
        MapMarkerRenderer.drawDiamond(graphics, x, y, 10, markerColor);

        Component name = Component.empty().append(waypoint.displayName())
                .withStyle(ChatFormatting.WHITE);
        int nameWidth = minecraft.font.width(name);
        int nameY = y + ICON_HALF_SIZE + 3;
        graphics.text(minecraft.font, name, x - nameWidth / 2, nameY, TEXT_COLOR, true);
        if (showDistance) {
            drawDistance(graphics, minecraft, projected.distance(), x,
                    nameY + minecraft.font.lineHeight + 2);
        }
        graphics.pose().popMatrix();
    }

    private static void drawDistance(GuiGraphicsExtractor graphics, Minecraft minecraft, double distance,
            int centerX, int textY) {
        Component label = Component.literal("%dm".formatted(Math.round(distance)))
                .withStyle(ChatFormatting.WHITE);
        int textWidth = minecraft.font.width(label);
        int textX = centerX - textWidth / 2;
        graphics.fill(textX - 2, textY - 1, textX + textWidth + 2,
                textY + minecraft.font.lineHeight + 1, DISTANCE_BACKGROUND);
        graphics.text(minecraft.font, label, textX, textY, TEXT_COLOR, true);
    }

    static int applyOpacity(int argb, float opacity) {
        int alpha = Math.round((argb >>> 24) * opacity);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
