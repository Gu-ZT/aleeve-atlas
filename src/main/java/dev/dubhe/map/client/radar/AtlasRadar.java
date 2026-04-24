package dev.dubhe.map.client.radar;

import dev.dubhe.map.client.AtlasClientState;
import dev.dubhe.map.client.hud.MinimapHudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;

public final class AtlasRadar {
    private static final int HOSTILE_COLOR = 0xFFFF5555;
    private static final int FRIENDLY_COLOR = 0xFF55FF55;
    private static final int ITEM_COLOR = 0xFF55AAFF;
    private static final int PLAYER_COLOR = 0xFFFFFFFF;
    private static final int MAX_MARKERS = 48;
    private static final double HALF_CELL_COUNT = 23 / 2.0D;

    private AtlasRadar() {
    }

    public static void render(Minecraft minecraft, GuiGraphics graphics, int mapX, int mapY, int mapSizePx, float rotationDeg) {
        if (!AtlasClientState.isRadarEnabled() || minecraft.player == null || minecraft.level == null) {
            return;
        }

        double playerX = minecraft.player.getX();
        double playerZ = minecraft.player.getZ();
        double scanRange = AtlasClientState.getRadarRangeBlocks();
        double displayRange = Math.max(1.0D, AtlasClientState.getBlockStep() * HALF_CELL_COUNT);
        double maxDistanceSqr = scanRange * scanRange;
        double radiusPixels = mapSizePx / 2.0D - 4.0D;
        float rotationRad = (float) Math.toRadians(rotationDeg);
        double cos = Math.cos(rotationRad);
        double sin = Math.sin(rotationRad);
        int centerX = mapX + mapSizePx / 2;
        int centerY = mapY + mapSizePx / 2;
        int drawn = 0;

        for (Entity entity : minecraft.level.entitiesForRendering()) {
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
            if (!MinimapHudRenderer.isPointInsideMinimap(pixelX, pixelY, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight())) {
                // Out-of-range entities are hidden instead of being clamped on the minimap edge.
                continue;
            }
            graphics.fill((int) pixelX - 1, (int) pixelY - 1, (int) pixelX + 2, (int) pixelY + 2, color);
            drawn++;
            if (drawn >= MAX_MARKERS) {
                return;
            }
        }
    }

    private static int markerColor(Entity entity) {
        if (entity instanceof Player) {
            return AtlasClientState.showPlayersOnRadar() ? PLAYER_COLOR : 0;
        }
        if (entity instanceof ItemEntity) {
            return AtlasClientState.showItemsOnRadar() ? ITEM_COLOR : 0;
        }
        if (entity instanceof Enemy) {
            return AtlasClientState.showHostileOnRadar() ? HOSTILE_COLOR : 0;
        }
        if (entity instanceof LivingEntity) {
            return AtlasClientState.showFriendlyOnRadar() ? FRIENDLY_COLOR : 0;
        }
        return 0;
    }

}


