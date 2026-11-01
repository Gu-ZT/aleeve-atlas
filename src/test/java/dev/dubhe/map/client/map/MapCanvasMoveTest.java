package dev.dubhe.map.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 直接验证生产纹理复制，像素颜色表示其固定世界坐标。
class MapCanvasMoveTest {
    @Test
    void recenterPreservesWorldCoordinatesAcrossAllDirections() {
        for (int deltaX : new int[] {-4, 0, 3}) {
            for (int deltaZ : new int[] {-3, 0, 2}) {
                try (NativeImage image = new NativeImage(11, 9, false)) {
                    int originX = -21;
                    int originZ = 37;
                    for (int row = 0; row < image.getHeight(); row++) {
                        for (int col = 0; col < image.getWidth(); col++) {
                            image.setPixelRGBA(col, row, worldColor(originX + col, originZ + row));
                        }
                    }
                    MapCanvas.movePixels(image, deltaX, deltaZ);
                    for (int row = Math.max(0, -deltaZ);
                            row < Math.min(image.getHeight(), image.getHeight() - deltaZ); row++) {
                        for (int col = Math.max(0, -deltaX);
                                col < Math.min(image.getWidth(), image.getWidth() - deltaX); col++) {
                            Assertions.assertEquals(worldColor(originX + deltaX + col,
                                            originZ + deltaZ + row), image.getPixelRGBA(col, row),
                                    "World coordinate changed after recenter: " + deltaX + "," + deltaZ);
                        }
                    }
                }
            }
        }
    }

    private static int worldColor(int worldX, int worldZ) {
        return 0xFF000000 | ((worldX & 255) << 8) | (worldZ & 255);
    }
}
