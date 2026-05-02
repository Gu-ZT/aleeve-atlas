package dev.dubhe.map.client.radar;

import dev.dubhe.map.client.AtlasClientState;
import dev.dubhe.map.client.render.state.MarkerRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector2f;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.dubhe.map.client.AleeveAtlasClientConfig;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AtlasRadar {
    // Entity marker colours (ARGB)
    private static final int COLOR_FRIENDLY = 0xFFFFFFFF;  // white  – passive / friendly mobs + other players
    private static final int COLOR_NEUTRAL  = 0xFF5599FF;  // blue   – neutral mobs (NeutralMob)
    private static final int COLOR_HOSTILE  = 0xFFFF8800;  // orange – hostile mobs (Enemy)
    private static final int COLOR_ITEM     = 0xFFFF3333;  // red    – dropped items

    /** GUI-pixel radius of entity dot markers. */
    private static final float ENTITY_MARKER_RADIUS = 1.8f;

    private static final int MAX_MARKERS = 256;
    private static final double HALF_CELL_COUNT = 23 / 2.0D;

    private AtlasRadar() {
    }

    public static void render(Minecraft minecraft, GuiGraphicsExtractor graphics, int mapX, int mapY, int mapSizePx, float rotationDeg) {
        if (!AtlasClientState.isRadarEnabled() || minecraft.player == null || minecraft.level == null) {
            return;
        }

        double playerX = minecraft.player.getX();
        double playerZ = minecraft.player.getZ();
        double scanRange = AtlasClientState.getRadarRangeBlocks();
        double displayRange = Math.max(1.0D, AtlasClientState.getBlockStep() * HALF_CELL_COUNT);
        double maxDistanceSqr = scanRange * scanRange;
        double radiusPixels = mapSizePx / 2.0D - 4.0D;
        float rotationRad = (float) Math.toRadians(-rotationDeg);
        double cos = Math.cos(rotationRad);
        double sin = Math.sin(rotationRad);
        int centerX = mapX + mapSizePx / 2;
        int centerY = mapY + mapSizePx / 2;

        // Precompute framebuffer clip data for all markers in this frame
        boolean circleClip = AtlasClientState.getMinimapShape() == AleeveAtlasClientConfig.MapShape.CIRCLE;
        double guiScale = minecraft.getWindow().getGuiScale();
        int windowHeight = minecraft.getWindow().getHeight();
        float fbClipCx = (float) (centerX * guiScale);
        float fbClipCy = (float) (windowHeight - centerY * guiScale);
        float fbClipHalf = (float) (mapSizePx / 2.0 * guiScale);
        float fbClipMode = circleClip ? 1.0f : 0.0f;
        Vector2f clipCenter = new Vector2f(fbClipCx, fbClipCy);
        Vector2f clipHalfSize = new Vector2f(fbClipHalf, fbClipHalf);

        List<RadarMarker> markers = new ArrayList<>();

        AABB queryBox = new AABB(
            playerX - scanRange, minecraft.player.getY() - scanRange, playerZ - scanRange,
            playerX + scanRange, minecraft.player.getY() + scanRange, playerZ + scanRange
        );

        for (Entity entity : minecraft.level.getEntities(minecraft.player, queryBox, Entity::isAlive)) {
            if (entity == minecraft.player || !entity.isAlive()) {
                continue;
            }
            double dx = entity.getX() - playerX;
            double dz = entity.getZ() - playerZ;
            double distanceSqr = dx * dx + dz * dz;
            if (distanceSqr > maxDistanceSqr) {
                continue;
            }

            int color = markerColor(entity);
            if (color == 0) {
                continue;
            }

            double mapLocalX = dx * cos + dz * sin;
            double mapLocalZ = -dx * sin + dz * cos;
            double normalizedX = mapLocalX / displayRange;
            double normalizedZ = mapLocalZ / displayRange;
            double pixelX = centerX + normalizedX * radiusPixels;
            double pixelY = centerY + normalizedZ * radiusPixels;

            markers.add(new RadarMarker(pixelX, pixelY, color, distanceSqr, entity.getId()));
        }

        // Keep marker selection stable frame-to-frame: nearest first, then entity id.
        markers.sort(Comparator
            .comparingDouble(RadarMarker::distanceSqr)
            .thenComparingInt(RadarMarker::entityId));

        int limit = Math.min(MAX_MARKERS, markers.size());
        for (int i = 0; i < limit; i++) {
            RadarMarker marker = markers.get(i);
            renderCircleMarker(
                graphics,
                marker.pixelX(),
                marker.pixelY(),
                ENTITY_MARKER_RADIUS,
                marker.color(),
                guiScale,
                windowHeight,
                clipCenter,
                clipHalfSize,
                fbClipHalf,
                fbClipMode
            );
        }
    }

    @SuppressWarnings("SameParameterValue")
    static void renderCircleMarker(
        GuiGraphicsExtractor graphics,
        double guiX, double guiY,
        float radiusGui,
        int color,
        double guiScale,
        int windowHeight,
        Vector2f clipCenter,
        Vector2f clipHalfSize,
        float clipRadius,
        float clipMode
    ) {
        float fbX = (float) Math.floor(guiX * guiScale) + 0.5f;
        float fbY = (float) Math.floor(windowHeight - guiY * guiScale) + 0.5f;
        float fbRadius = radiusGui * (float) guiScale;

        @Nullable GpuBufferSlice uniform = MarkerRenderState.createMarkerUniform(
            clipCenter, clipHalfSize, clipRadius, clipMode,
            new Vector2f(fbX, fbY), fbRadius
        );

        if (uniform == null) {
            // Fallback: draw a square dot
            int r = Math.round(radiusGui);
            graphics.fill((int) guiX - r, (int) guiY - r, (int) guiX + r + 1, (int) guiY + r + 1, color);
            return;
        }

        float pad = radiusGui + 1.0f;
        float x0 = (float) guiX - pad, y0 = (float) guiY - pad;
        float x1 = (float) guiX + pad, y1 = (float) guiY + pad;
        graphics.submitGuiElementRenderState(new MarkerRenderState(
            graphics.pose(),
            new Vector2f(x0, y0), new Vector2f(x1, y0),
            new Vector2f(x1, y1), new Vector2f(x0, y1),
            color, uniform,
            graphics.peekScissorStack()
        ));
    }

    private static int markerColor(@Nullable Entity entity) {
        return switch (entity) {
            case Player _ -> AtlasClientState.showPlayersOnRadar() ? COLOR_FRIENDLY : 0;
            case ItemEntity _ -> AtlasClientState.showItemsOnRadar() ? COLOR_ITEM : 0;
            case Enemy _ -> AtlasClientState.showHostileOnRadar() ? COLOR_HOSTILE : 0;
            case NeutralMob _ -> AtlasClientState.showFriendlyOnRadar() ? COLOR_NEUTRAL : 0;
            case LivingEntity _ -> AtlasClientState.showFriendlyOnRadar() ? COLOR_FRIENDLY : 0;
            case null, default -> 0;
        };
    }

    private record RadarMarker(double pixelX, double pixelY, int color, double distanceSqr, int entityId) {
    }
}
