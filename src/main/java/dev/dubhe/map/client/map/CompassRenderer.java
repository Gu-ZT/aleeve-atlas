package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.config.CompassPosition;
import dev.dubhe.map.config.ConfigBounds;
import dev.dubhe.map.config.MinimapShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/// 小地图方向文字与世界地图四角星方向标。
public final class CompassRenderer {
    private static final ResourceLocation WHITE_TEXTURE = AleeveAtlas.id("textures/gui/white.png");
    private static final String[] CARDINALS = { "N", "E", "S", "W" };
    private static final double[][] DIRECTIONS = {
        { 0.0, -1.0 }, { 1.0, 0.0 }, { 0.0, 1.0 }, { -1.0, 0.0 }
    };
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_SHADOW = 0x66000000;
    private static final int WORLD_COMPASS_BASE_SIZE = 72;
    private static final float WORLD_COMPASS_LABEL_DISTANCE = 38.0F;
    private static final float WORLD_COMPASS_SCREEN_MARGIN = 2.0F;
    private static final float WORLD_COMPASS_TOP_INSET = 54.0F;
    private static final float WORLD_COMPASS_BOTTOM_INSET = 48.0F;

    private CompassRenderer() {
    }

    /// 方向位置随地图旋转，文字始终保持屏幕正向。
    public static void renderOnMinimap(GuiGraphics graphics, MinimapLayout layout,
            double rotationDegrees) {
        Minecraft minecraft = Minecraft.getInstance();
        double centerX = layout.x() + layout.size() / 2.0;
        double centerY = layout.y() + layout.size() / 2.0;
        boolean circular = AtlasClientConfigs.CONFIG.minimap.shape == MinimapShape.CIRCLE;
        double radius = Math.max(6.0,
                layout.size() / 2.0 - MinimapLayout.CLIP_INSET - 8.0);
        for (int index = 0; index < CARDINALS.length; index++) {
            String label = CARDINALS[index];
            double labelExtent = Math.max(minecraft.font.width(label) / 2.0,
                    minecraft.font.lineHeight / 2.0) + 2.0;
            double[] point = minimapLabelPoint(centerX, centerY, layout.size(), index,
                    rotationDegrees, circular, labelExtent, radius);
            graphics.drawString(minecraft.font, label,
                    (int) Math.round(point[0] - minecraft.font.width(label) / 2.0),
                    (int) Math.round(point[1] - minecraft.font.lineHeight / 2.0),
                    TEXT_COLOR, true);
        }
    }

    static double[] minimapLabelPoint(double centerX, double centerY, double size, int index,
            double rotationDegrees, boolean circular, double labelExtent, double circleRadius) {
        double directionX = DIRECTIONS[index][0];
        double directionY = DIRECTIONS[index][1];
        double radians = Math.toRadians(rotationDegrees);
        double rotatedX = directionX * Math.cos(radians) - directionY * Math.sin(radians);
        double rotatedY = directionX * Math.sin(radians) + directionY * Math.cos(radians);
        if (circular) {
            return new double[] { centerX + rotatedX * circleRadius,
                centerY + rotatedY * circleRadius };
        }
        double edge = Math.max(1.0, size / 2.0 - labelExtent);
        double scale = edge / Math.max(Math.abs(rotatedX), Math.abs(rotatedY));
        return new double[] { centerX + rotatedX * scale, centerY + rotatedY * scale };
    }

    /// 按配置在世界地图角落绘制方向标，图形和文字使用相同缩放倍率。
    public static void renderOnWorldMap(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int configuredSize = (int) Math.round(ConfigBounds.clamp(
                AtlasClientConfigs.CONFIG.worldMap.compassSize, 32.0, 192.0));
        CompassPosition position = AtlasClientConfigs.CONFIG.worldMap.compassPosition;
        float textOverhang = minecraft.font.lineHeight - minecraft.font.lineHeight / 2 + 1;
        for (String label : CARDINALS) {
            int textWidth = minecraft.font.width(label);
            textOverhang = Math.max(textOverhang, textWidth - textWidth / 2 + 1);
        }
        float topExtent = WORLD_COMPASS_LABEL_DISTANCE + minecraft.font.lineHeight / 2.0F;
        float edgeExtent = WORLD_COMPASS_LABEL_DISTANCE + textOverhang;
        double horizontalScale = (screenWidth / 2.0 - WORLD_COMPASS_SCREEN_MARGIN) / edgeExtent;
        float bottomInset = switch (position) {
            case TOP_LEFT, BOTTOM_LEFT -> WORLD_COMPASS_BOTTOM_INSET;
            case TOP_RIGHT, BOTTOM_RIGHT -> WORLD_COMPASS_SCREEN_MARGIN;
        };
        double verticalScale = (screenHeight - WORLD_COMPASS_TOP_INSET - bottomInset
                - WORLD_COMPASS_SCREEN_MARGIN) / (topExtent + edgeExtent);
        int availableSize = (int) Math.floor(Math.min(horizontalScale, verticalScale)
                * WORLD_COMPASS_BASE_SIZE);
        int size = Math.min(configuredSize, Math.max(1, availableSize));
        float scale = size / (float) WORLD_COMPASS_BASE_SIZE;
        int margin = Math.min(Math.min((int) Math.ceil(edgeExtent * scale
                + WORLD_COMPASS_SCREEN_MARGIN), screenWidth / 2), screenHeight / 2);
        int centerX = switch (position) {
            case TOP_LEFT, BOTTOM_LEFT -> margin;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - margin;
        };
        int centerY = switch (position) {
            case TOP_LEFT, TOP_RIGHT -> Math.min(screenHeight - margin,
                    (int) Math.ceil(WORLD_COMPASS_TOP_INSET + topExtent * scale));
            case BOTTOM_LEFT -> Math.max(margin, (int) Math.floor(screenHeight
                    - WORLD_COMPASS_BOTTOM_INSET - edgeExtent * scale));
            case BOTTOM_RIGHT -> screenHeight - margin;
        };
        graphics.blit(AtlasRenderPipelines.WORLD_COMPASS, WHITE_TEXTURE,
                centerX - size / 2, centerY - size / 2,
                0.0F, 0.0F, size, size, 2, 2, 2, 2, 0xFFFFFFFF);
        float labelOffset = WORLD_COMPASS_LABEL_DISTANCE * scale;
        for (int index = 0; index < CARDINALS.length; index++) {
            int x = (int) Math.round(centerX + DIRECTIONS[index][0] * labelOffset);
            int y = (int) Math.round(centerY + DIRECTIONS[index][1] * labelOffset);
            drawWorldLabel(graphics, minecraft, CARDINALS[index], x, y, scale);
        }
    }

    private static void drawWorldLabel(GuiGraphics graphics, Minecraft minecraft,
            String label, int centerX, int centerY, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        int x = -minecraft.font.width(label) / 2;
        int y = -minecraft.font.lineHeight / 2;
        graphics.drawString(minecraft.font, label, x + 1, y + 1, TEXT_SHADOW, false);
        graphics.drawString(minecraft.font, label, x, y, TEXT_COLOR, false);
        graphics.pose().popMatrix();
    }
}
