package dev.dubhe.map.client.ping;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.api.ping.MinimapPingAPI;
import dev.dubhe.map.client.map.MapMarkerRenderer;
import dev.dubhe.map.client.map.MinimapLayout;
import dev.dubhe.map.client.waypoint.WorldProjection;
import dev.dubhe.map.ping.ClientPings;
import dev.dubhe.map.ping.MapPingData;
import dev.dubhe.map.ping.PingRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// 游戏内 3D 视界临时标点覆层。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class PingWorldOverlay {
    private static final int DISTANCE_BACKGROUND = 0xFF101010;
    private static final int TEXT_COLOR = 0xBFFFFFFF;
    private static final float MARKER_OPACITY = 0.75F;

    private PingWorldOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (!MinimapPingAPI.isWorldPingsEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.gui.hud.isHidden()
                || MinimapLayout.focused(minecraft)) {
            return;
        }
        String dimension = MapPingData.dimensionId(minecraft.level.dimension());
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        for (MapPingData ping : ClientPings.active()) {
            if (!ping.dimensionId().equals(dimension)) {
                continue;
            }
            float opacity = PingRenderer.opacityByPolicy(minecraft, ping);
            if (opacity <= 0.0F) {
                continue;
            }
            WorldProjection.project(ping.position())
                    .ifPresent(projected -> drawMarker(graphics, minecraft, ping, projected,
                            opacity * MARKER_OPACITY));
        }
    }

    private static void drawMarker(GuiGraphicsExtractor graphics, Minecraft minecraft, MapPingData ping,
            WorldProjection.Projected projected, float opacity) {
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) projected.screenX(), (float) projected.screenY());
        int x = 0;
        int y = 0;
        MapMarkerRenderer.drawDot(graphics, x, y, 12,
                applyOpacity(ping.icon().colorArgb(), opacity));
        MapMarkerRenderer.drawDot(graphics, x, y, 6,
                applyOpacity(0xFF101010, opacity));

        Component distance = Component.literal("%dm".formatted(Math.round(projected.distance())))
                .withStyle(ChatFormatting.WHITE);
        int textWidth = minecraft.font.width(distance);
        int textX = x - textWidth / 2;
        int textY = y + 9;
        graphics.fill(textX - 2, textY - 1, textX + textWidth + 2,
                textY + minecraft.font.lineHeight + 1, DISTANCE_BACKGROUND);
        graphics.text(minecraft.font, distance, textX, textY, TEXT_COLOR, true);
        graphics.pose().popMatrix();
    }

    private static int applyOpacity(int argb, float opacity) {
        int alpha = Math.round((argb >>> 24) * Math.max(0.0F, opacity));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
