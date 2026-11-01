package dev.dubhe.map.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ChunkOverlayRendererTest {
    @Test
    void chunkGridAndTerrainStayAlignedAtFractionalZoomAndPosition() {
        for (double zoom : new double[] {1.01, 1.50001, 3.95, 3.95001, 4.0}) {
            MinimapLayout layout = new MinimapLayout(23, 41, 512, -12.375, 24.875, zoom);
            double cellSize = 16.0 * ChunkOverlayRenderer.minimapBlocksToPixels(layout);
            // 相邻区块边界必须与地形/实体使用的世界坐标投影完全重合，包括负坐标。
            assertEquals(layout.worldToScreenX(0), layout.worldToScreenX(-16) + cellSize, 1.0E-10);
            assertEquals(layout.worldToScreenZ(32), layout.worldToScreenZ(16) + cellSize, 1.0E-10);
        }
        MinimapLayout before = new MinimapLayout(0, 0, 512, 0, 0, 3.95);
        MinimapLayout after = new MinimapLayout(0, 0, 512, 0, 0, 3.95001);
        assertNotEquals(ChunkOverlayRenderer.minimapBlocksToPixels(before),
                ChunkOverlayRenderer.minimapBlocksToPixels(after));
    }
}
