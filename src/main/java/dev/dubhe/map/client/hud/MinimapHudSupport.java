package dev.dubhe.map.client.hud;

import dev.dubhe.map.client.AleeveAtlasClientConfig;
import dev.dubhe.map.client.AtlasClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import javax.annotation.Nullable;

public final class MinimapHudSupport {
    private static final int MARGIN = 8;

    private MinimapHudSupport() {
    }

    public static @Nullable MinimapContext captureContext(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!AtlasClientState.isMinimapVisible()) {
            return null;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return null;
        }
        if (minecraft.screen != null) {
            return null;
        }

        int mapSize = AtlasClientState.getMinimapSizePx();
        int mapX = getMinimapLeft(graphics.guiWidth());
        int mapY = getMinimapTop(graphics.guiHeight());
        return new MinimapContext(minecraft, mapX, mapY, mapSize, getEffectiveRotationDegrees(minecraft));
    }

    public static float getEffectiveRotationDegrees(Minecraft minecraft) {
        float rotationDeg = AtlasClientState.getManualRotationDeg();
        if (AtlasClientState.isRotateWithPlayer() && minecraft.player != null) {
            rotationDeg += minecraft.player.getYRot() + 180.0F;
        }
        return rotationDeg;
    }

    public static int getMinimapLeft(int guiWidth) {
        int size = AtlasClientState.getMinimapSizePx();
        return switch (AtlasClientState.getMinimapAnchor()) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> guiWidth - size - MARGIN;
        };
    }

    public static int getMinimapTop(int guiHeight) {
        int size = AtlasClientState.getMinimapSizePx();
        return switch (AtlasClientState.getMinimapAnchor()) {
            case TOP_LEFT, TOP_RIGHT -> MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> guiHeight - size - MARGIN - 44;
        };
    }

    public static boolean isPointInsideMinimap(double guiX, double guiY, int guiWidth, int guiHeight) {
        int size = AtlasClientState.getMinimapSizePx();
        int left = getMinimapLeft(guiWidth);
        int top = getMinimapTop(guiHeight);
        int right = left + size;
        int bottom = top + size;
        if (guiX < left || guiX >= right || guiY < top || guiY >= bottom) {
            return false;
        }
        if (AtlasClientState.getMinimapShape() == AleeveAtlasClientConfig.MapShape.SQUARE) {
            return true;
        }
        double cx = left + size / 2.0D;
        double cy = top + size / 2.0D;
        double dx = guiX - cx;
        double dy = guiY - cy;
        double radius = size / 2.0D;
        return dx * dx + dy * dy <= radius * radius;
    }

    public record MinimapContext(
        Minecraft minecraft,
        int mapX,
        int mapY,
        int mapSize,
        float rotationDeg
    ) {
        public int mapRight() {
            return mapX + mapSize;
        }

        public int mapBottom() {
            return mapY + mapSize;
        }


        public boolean circleMode() {
            return AtlasClientState.getMinimapShape() == AleeveAtlasClientConfig.MapShape.CIRCLE;
        }
    }
}

