package dev.dubhe.map.client.radar;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.dubhe.map.client.AleeveAtlasClientConfig;
import dev.dubhe.map.client.AtlasClientState;
import dev.dubhe.map.client.render.state.MarkerRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;

public final class AtlasRadar {
    private static final int COLOR_FRIENDLY = 0xFFFFFFFF, COLOR_NEUTRAL = 0xFF5599FF, COLOR_HOSTILE = 0xFFFF8800, COLOR_ITEM = 0xFFFF3333;
    private static final float ENTITY_MARKER_RADIUS = 1.8f;
    private static final int MAX_MARKERS = 256;
    private static final double HALF_CELL_COUNT = 23 / 2.0D;

    private AtlasRadar() {
    }

    public static void render(Minecraft minecraft, GuiGraphicsExtractor graphics, int mapX, int mapY, int mapSizePx, float rotationDeg) {
        if (!AtlasClientState.isRadarEnabled() || minecraft.player == null || minecraft.level == null) return;

        double playerX = minecraft.player.getX(), playerZ = minecraft.player.getZ();
        double scanRange = AtlasClientState.getRadarRangeBlocks();
        double displayRange = Math.max(1.0D, AtlasClientState.getBlockStep() * HALF_CELL_COUNT);
        double maxDistSqr = scanRange * scanRange;
        double radiusPx = mapSizePx / 2.0D - 4.0D;
        float rotRad = (float) Math.toRadians(-rotationDeg);
        double cos = Math.cos(rotRad), sin = Math.sin(rotRad);
        int cx = mapX + mapSizePx / 2, cy = mapY + mapSizePx / 2;

        boolean circle = AtlasClientState.getMinimapShape() == AleeveAtlasClientConfig.MapShape.CIRCLE;
        double gs = minecraft.getWindow().getGuiScale();
        int wh = minecraft.getWindow().getHeight();
        float fcCx = (float) (cx * gs), fcCy = (float) (wh - cy * gs), fcHalf = (float) (mapSizePx / 2.0 * gs), fcMode = circle ? 1f : 0f;
        Vector2f clipC = new Vector2f(fcCx, fcCy), clipHS = new Vector2f(fcHalf, fcHalf);

        List<RadarMarker> markers = new ArrayList<>();
        AABB qb = new AABB(
            playerX - scanRange, minecraft.player.getY() - scanRange, playerZ - scanRange,
            playerX + scanRange, minecraft.player.getY() + scanRange, playerZ + scanRange
        );

        for (Entity e : minecraft.level.getEntities(minecraft.player, qb, Entity::isAlive)) {
            if (e == minecraft.player || !e.isAlive()) continue;
            double dx = e.getX() - playerX, dz = e.getZ() - playerZ;
            if (dx * dx + dz * dz > maxDistSqr) continue;
            int color = markerColor(e);
            if (color == 0) continue;
            double mlx = dx * cos + dz * sin, mlz = -dx * sin + dz * cos;
            markers.add(new RadarMarker(
                cx + (mlx / displayRange) * radiusPx,
                cy + (mlz / displayRange) * radiusPx,
                color,
                dx * dx + dz * dz,
                e.getId()
            ));
        }

        markers.sort(Comparator.comparingDouble(RadarMarker::dist).thenComparingInt(RadarMarker::id));
        int limit = Math.min(MAX_MARKERS, markers.size());
        for (int i = 0; i < limit; i++) {
            RadarMarker m = markers.get(i);
            float fbX = (float) Math.floor(m.px * gs) + 0.5f, fbY = (float) Math.floor(wh - m.py * gs) + 0.5f, fbR = ENTITY_MARKER_RADIUS * (float) gs;
            @Nullable GpuBufferSlice u = MarkerRenderState.createMarkerUniform(clipC, clipHS, fcHalf, fcMode, new Vector2f(fbX, fbY), fbR);
            if (u == null) {
                int r = Math.round(ENTITY_MARKER_RADIUS);
                graphics.fill((int) m.px - r, (int) m.py - r, (int) m.px + r + 1, (int) m.py + r + 1, m.color);
                continue;
            }
            float pad = ENTITY_MARKER_RADIUS + 1f;
            graphics.submitGuiElementRenderState(new MarkerRenderState(
                graphics.pose(),
                new Vector2f((float) m.px - pad, (float) m.py - pad), new Vector2f((float) m.px + pad, (float) m.py - pad),
                new Vector2f((float) m.px + pad, (float) m.py + pad), new Vector2f((float) m.px - pad, (float) m.py + pad),
                m.color, u, graphics.peekScissorStack()
            ));
        }
    }

    private static int markerColor(@Nullable Entity e) {
        return switch (e) {
            case Player _ -> AtlasClientState.showPlayersOnRadar() ? COLOR_FRIENDLY : 0;
            case ItemEntity _ -> AtlasClientState.showItemsOnRadar() ? COLOR_ITEM : 0;
            case Enemy _ -> AtlasClientState.showHostileOnRadar() ? COLOR_HOSTILE : 0;
            case NeutralMob _ -> AtlasClientState.showFriendlyOnRadar() ? COLOR_NEUTRAL : 0;
            case LivingEntity _ -> AtlasClientState.showFriendlyOnRadar() ? COLOR_FRIENDLY : 0;
            case null, default -> 0;
        };
    }

    private record RadarMarker(double px, double py, int color, double dist, int id) {
    }
}
