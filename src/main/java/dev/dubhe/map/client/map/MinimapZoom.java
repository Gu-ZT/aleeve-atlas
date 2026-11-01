package dev.dubhe.map.client.map;

import dev.dubhe.map.config.AtlasClientConfig;
import dev.dubhe.map.config.ConfigBounds;

/// 小地图视野缩放：独立保存地表/洞穴目标，按实际经过时间平滑过渡。
public final class MinimapZoom {
    private static final double DURATION_NANOS = 180_000_000.0;
    private double start;
    private double target = Double.NaN;
    private long startedAt;
    private long lastFrame;

    public static boolean usesCaveZoom(AtlasClientConfig config, boolean cave) {
        return cave && config.caves.useSeparateZoom;
    }

    public static double target(AtlasClientConfig config, boolean cave) {
        return bounded(usesCaveZoom(config, cave) ? config.caves.zoom : config.minimap.zoom);
    }

    /// 快捷键只修改当前显示模式的目标，离开洞穴后保留原来的地表视野。
    public static void adjust(AtlasClientConfig config, boolean cave, double delta) {
        double zoom = bounded(target(config, cave) + delta);
        if (usesCaveZoom(config, cave)) {
            config.caves.zoom = zoom;
        } else {
            config.minimap.zoom = zoom;
        }
    }

    public double sample(double requested, boolean smooth, long now) {
        requested = bounded(requested);
        if (!Double.isFinite(this.target) || !smooth || now < this.lastFrame
                || now - this.lastFrame > 2_000_000_000L) {
            this.start = requested;
            this.target = requested;
            this.startedAt = now;
        } else if (requested != this.target) {
            this.start = this.value(now);
            this.target = requested;
            this.startedAt = now;
        }
        this.lastFrame = now;
        return this.value(now);
    }

    private double value(long now) {
        double progress = ConfigBounds.clamp((now - this.startedAt) / DURATION_NANOS, 0.0, 1.0);
        double remaining = 1.0 - progress;
        return this.target + (this.start - this.target) * remaining * remaining * remaining;
    }

    private static double bounded(double value) {
        return Double.isFinite(value)
                ? ConfigBounds.clamp(value, MinimapLayout.MIN_ZOOM, MinimapLayout.MAX_ZOOM)
                : MinimapLayout.DEFAULT_ZOOM;
    }
}
