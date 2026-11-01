package dev.dubhe.map.client.map;

import dev.dubhe.map.config.AtlasClientConfig;
import dev.dubhe.map.map.ChunkMapData;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/// 区块边界和史莱姆区块覆盖层。
public final class ChunkOverlayRenderer {
    private static final float WORLD_MAP_MIN_OVERLAY_PIXELS_PER_CHUNK = 0.8F;
    private static final int GRID_COLOR = 0x50000000;
    private static final int SLIME_COLOR = 0x5044DD44;

    private ChunkOverlayRenderer() {
    }

    public static void renderOnWorldMap(GuiGraphicsExtractor graphics, AtlasClientConfig config,
            double centerX, double centerZ, float pixelsPerChunk, int width, int height) {
        if (pixelsPerChunk < WORLD_MAP_MIN_OVERLAY_PIXELS_PER_CHUNK
                || (!config.worldMap.showChunkGrid && !config.worldMap.showSlimeChunks)) {
            return;
        }
        Long seed = config.worldMap.showSlimeChunks ? SlimeChunkTracker.currentSeed() : null;
        int minX = chunkAt(centerX - width / 2.0 / pixelsPerChunk);
        int maxX = chunkAt(centerX + width / 2.0 / pixelsPerChunk);
        int minZ = chunkAt(centerZ - height / 2.0 / pixelsPerChunk);
        int maxZ = chunkAt(centerZ + height / 2.0 / pixelsPerChunk);
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                double left = width / 2.0 + (chunkX * 16.0 - centerX) * pixelsPerChunk;
                double top = height / 2.0 + (chunkZ * 16.0 - centerZ) * pixelsPerChunk;
                drawCell(graphics, config.worldMap.showChunkGrid,
                        seed != null && SlimeChunkTracker.isSlimeChunk(seed, chunkX, chunkZ),
                        left, top, 16.0 * pixelsPerChunk, 16.0 * pixelsPerChunk,
                        false, 0.0, 0.0, 0.0);
            }
        }
    }

    public static void renderOnMinimap(GuiGraphicsExtractor graphics, AtlasClientConfig config,
            MinimapLayout layout, boolean circular) {
        if (!config.minimap.showChunkGrid && !config.minimap.showSlimeChunks) {
            return;
        }
        Long seed = config.minimap.showSlimeChunks ? SlimeChunkTracker.currentSeed() : null;
        int mapPixels = circular ? layout.size() : MinimapLayout.squareDrawSize(layout.size());
        int mapBlocks = circular ? (int) layout.coveredBlocks()
                : MinimapLayout.squareDrawSize((int) layout.coveredBlocks());
        double centerX = layout.x() + layout.size() / 2.0;
        double centerZ = layout.y() + layout.size() / 2.0;
        double blocksToPixels = mapPixels / (double) mapBlocks;
        int range = (int) Math.ceil(layout.coveredBlocks() / 32.0) + 1;
        int centerChunkX = chunkAt(layout.centerWorldX());
        int centerChunkZ = chunkAt(layout.centerWorldZ());
        for (int chunkX = centerChunkX - range; chunkX <= centerChunkX + range; chunkX++) {
            for (int chunkZ = centerChunkZ - range; chunkZ <= centerChunkZ + range; chunkZ++) {
                double left = centerX + (chunkX * 16.0 - layout.centerWorldX()) * blocksToPixels;
                double top = centerZ + (chunkZ * 16.0 - layout.centerWorldZ()) * blocksToPixels;
                drawCell(graphics, config.minimap.showChunkGrid,
                        seed != null && SlimeChunkTracker.isSlimeChunk(seed, chunkX, chunkZ),
                        left, top, 16.0 * blocksToPixels, 16.0 * blocksToPixels, circular,
                        layout.x() + layout.size() / 2.0, layout.y() + layout.size() / 2.0,
                        layout.size() / 2.0 - MinimapLayout.CLIP_INSET);
            }
        }
    }

    private static void drawCell(GuiGraphicsExtractor graphics, boolean grid, boolean slime,
            double x, double y, double width, double height, boolean circular,
            double centerX, double centerZ, double radius) {
        if (slime) {
            fill(graphics, x, y, width, height, SLIME_COLOR, circular, centerX, centerZ, radius);
        }
        if (grid) {
            fill(graphics, x, y, width, 1, GRID_COLOR, circular, centerX, centerZ, radius);
            fill(graphics, x, y, 1, height, GRID_COLOR, circular, centerX, centerZ, radius);
        }
    }

    private static void fill(GuiGraphicsExtractor graphics, double x, double y, double width, double height,
            int color, boolean circular, double centerX, double centerZ, double radius) {
        if (!circular) {
            fillRect(graphics, x, y, width, height, color);
            return;
        }
        double dx = Math.max(Math.abs(x - centerX), Math.abs(x + width - centerX));
        double dz = Math.max(Math.abs(y - centerZ), Math.abs(y + height - centerZ));
        if (dx * dx + dz * dz <= radius * radius) {
            fillRect(graphics, x, y, width, height, color);
            return;
        }
        // 裁剪只改变边缘；内部网格保留与地形一致的亚像素位置。
        for (double top = Math.max(y, centerZ - radius); top < Math.min(y + height, centerZ + radius);) {
            double bottom = Math.min(top + 1.0, Math.min(y + height, centerZ + radius));
            double dy = (top + bottom) * 0.5 - centerZ;
            double span = Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
            double left = Math.max(x, centerX - span);
            double right = Math.min(x + width, centerX + span);
            if (right > left) fillRect(graphics, left, top, right - left, bottom - top, color);
            top = bottom;
        }
    }

    private static void fillRect(GuiGraphicsExtractor graphics, double x, double y, double width, double height, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale((float) width, (float) height);
        graphics.fill(0, 0, 1, 1, color);
        graphics.pose().popMatrix();
    }

    private static int chunkAt(double worldCoordinate) {
        return (int) Math.floor(worldCoordinate / ChunkMapData.SIZE);
    }
}
