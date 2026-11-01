package dev.dubhe.map.map;

import net.minecraft.world.level.material.MapColor;

/// 地图像素取色。
///
/// 颜色直接取原版 `MapColor`，明暗使用原版地图的相邻高差、水深和棋盘偏移规则。
public final class MapPalette {
    /// 透明像素（未探索/无数据）。
    public static final int TRANSPARENT = 0;

    private MapPalette() {
    }

    /// 按相邻列高度差选择陆地明暗，公式对应原版 `MapItem.update`。
    public static MapColor.Brightness brightnessForTerrain(double currentHeight,
            double previousHeight, int pixelX, int pixelZ) {
        double slope = (currentHeight - previousHeight) * 4.0 / 5.0
                + (((pixelX + pixelZ) & 1) - 0.5) * 0.4;
        if (slope > 0.6) {
            return MapColor.Brightness.HIGH;
        }
        if (slope < -0.6) {
            return MapColor.Brightness.LOW;
        }
        return MapColor.Brightness.NORMAL;
    }

    /// 按水深与棋盘偏移选择水体明暗，公式对应原版 `MapItem.update`。
    public static MapColor.Brightness brightnessForWater(int depth, int pixelX, int pixelZ) {
        double shade = depth * 0.1 + ((pixelX + pixelZ) & 1) * 0.2;
        if (shade < 0.5) {
            return MapColor.Brightness.HIGH;
        }
        if (shade > 0.9) {
            return MapColor.Brightness.LOW;
        }
        return MapColor.Brightness.NORMAL;
    }

    /// 按百分比缩放 RGB 通道，保留 alpha，供标记渐变使用。
    public static int scale(int argb, int percent) {
        final int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        r = r * percent / 100;
        g = g * percent / 100;
        b = b * percent / 100;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int fromPackedColor(int packedColor) {
        return net.minecraft.util.FastColor.ABGR32.fromArgb32(MapColor.getColorFromPackedId(packedColor));
    }

    /// 把方块的地图色按明暗档位转换为不透明 ARGB。
    public static int colorOf(MapColor mapColor, MapColor.Brightness brightness) {
        return mapColor == MapColor.NONE ? TRANSPARENT
                : net.minecraft.util.FastColor.ABGR32.fromArgb32(mapColor.calculateRGBColor(brightness)) | 0xFF000000;
    }
}
