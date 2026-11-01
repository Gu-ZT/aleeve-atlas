package dev.dubhe.map.config;

/// 配置值区间钳制。
///
/// AnvilLib `@BoundedDiscrete` 对 float/double 使用 `floor(min)` / `ceil(max)`，
/// 无法表达 `0.5 ~ 1.5` 这类非整数区间，因此浮点配置项的范围在读取处钳制。
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
