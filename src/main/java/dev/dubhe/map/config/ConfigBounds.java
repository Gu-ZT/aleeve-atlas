package dev.dubhe.map.config;

/// 配置值区间钳制。
///
/// 配置界面与配置文件通过声明的范围校验，读取处仍做钳制，
/// 保护快捷键和其他客户端代码直接更新配置对象的路径。
public final class ConfigBounds {
    private ConfigBounds() {
    }

    public static double clamp(double value, double min, double max) {
        return Math.clamp(value, min, max);
    }

    public static float clamp(float value, float min, float max) {
        return Math.clamp(value, min, max);
    }

    public static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }
}
