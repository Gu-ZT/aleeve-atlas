package dev.dubhe.map.client.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 小地图布局的坐标映射测试。
class MinimapLayoutTest {
    @Test
    void fractionalZoomKeepsTerrainAndMarkersContinuous() {
        MinimapLayout first = new MinimapLayout(0, 0, 128, 0, 0, 1.5);
        MinimapLayout next = new MinimapLayout(0, 0, 128, 0, 0, 1.50001);
        Assertions.assertNotEquals(first.coveredBlocks(), next.coveredBlocks());
        double movement = next.worldToScreenX(20) - first.worldToScreenX(20);
        Assertions.assertTrue(movement > 0.0 && movement < 0.001);
        Assertions.assertEquals(20 * 128 / next.coveredBlocks(), next.worldToScreenX(20) - 64, 1.0E-10);
    }

    @Test
    void fullScreenCoordinateMapping() {
        // 小地图为 128 像素、覆盖 144 方块，玩家位于世界原点。
        MinimapLayout layout = new MinimapLayout(100, 20, 128, 0.0, 0.0);
        Assertions.assertEquals(164.0, layout.worldToScreenX(0.0), 1e-6);
        Assertions.assertEquals(84.0, layout.worldToScreenZ(0.0), 1e-6);
        // 移动 144 方块应恰好移动一个完整边长。
        Assertions.assertEquals(164.0 + 128.0, layout.worldToScreenX(144.0), 1e-6);
        Assertions.assertEquals(84.0 + 128.0, layout.worldToScreenZ(144.0), 1e-6);
    }

    @Test
    void zoomScalesWorldCoordinatesAroundPlayer() {
        MinimapLayout normal = new MinimapLayout(0, 0, 128, 0.0, 0.0, 1.0);
        MinimapLayout zoomed = new MinimapLayout(0, 0, 128, 0.0, 0.0, 2.0);
        double normalOffset = normal.worldToScreenX(24.0) - normal.x() - normal.size() / 2.0;
        double zoomedOffset = zoomed.worldToScreenX(24.0) - zoomed.x() - zoomed.size() / 2.0;
        Assertions.assertEquals(normalOffset * 2.0, zoomedOffset, 1.0E-6);
        double normalZOffset = normal.worldToScreenZ(24.0) - normal.y() - normal.size() / 2.0;
        Assertions.assertEquals(normalZOffset * 2.0,
                zoomed.worldToScreenZ(24.0) - zoomed.y() - zoomed.size() / 2.0, 1.0E-6);
    }

    @Test
    void fractionalPlayerPositionMapsToExactMinimapCenter() {
        MinimapLayout layout = new MinimapLayout(31, 47, 128, -12.375, 24.875, 1.5);
        Assertions.assertEquals(layout.x() + layout.size() / 2.0,
                layout.worldToScreenX(layout.centerWorldX()), 1.0E-6);
        Assertions.assertEquals(layout.y() + layout.size() / 2.0,
                layout.worldToScreenZ(layout.centerWorldZ()), 1.0E-6);
    }

    @Test
    void focusedLayoutFillsScreenShortSide() {
        int[] box = MinimapLayout.focusedBox(640, 360);
        Assertions.assertEquals(360, box[2]);
        Assertions.assertEquals(140, box[0]);
        Assertions.assertEquals(0, box[1]);
    }

    @Test
    void squareDrawSizeCoversEveryRotation() {
        int visibleSize = 101;
        int drawSize = MinimapLayout.squareDrawSize(visibleSize);
        for (int degrees = 0; degrees < 360; degrees += 15) {
            double radians = Math.toRadians(degrees);
            double cornerExtent = (Math.abs(Math.cos(radians)) + Math.abs(Math.sin(radians)))
                    * visibleSize / 2.0;
            Assertions.assertTrue(cornerExtent <= drawSize / 2.0,
                    "rotation=" + degrees);
        }
    }

    @Test
    void containsRespectsShape() {
        MinimapLayout layout = new MinimapLayout(0, 0, 100, 0.0, 0.0);
        // 靠近角落但仍在内缩方形内的点；圆形不包含该点。
        Assertions.assertTrue(layout.contains(4.0, 4.0, false));
        Assertions.assertFalse(layout.contains(4.0, 4.0, true));
        // 圆心附近：两种形态都包含。
        Assertions.assertTrue(layout.contains(50.0, 50.0, false));
        Assertions.assertTrue(layout.contains(50.0, 50.0, true));
    }
}
