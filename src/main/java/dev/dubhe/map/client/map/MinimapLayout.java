package dev.dubhe.map.client.map;

import dev.dubhe.map.client.AtlasKeyMappings;
import dev.dubhe.map.config.AtlasClientConfig;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.config.ConfigBounds;
import dev.dubhe.map.config.MinimapPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/// 小地图在屏幕上的布局。
///
/// 同时供 HUD 渲染与实体雷达使用，保证地图像素与实体标记共用同一套坐标映射。
public record MinimapLayout(int x, int y, int size, double centerWorldX, double centerWorldZ,
        double zoom) {
    public MinimapLayout {
        zoom = ConfigBounds.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    }

    public MinimapLayout(int x, int y, int size, double centerWorldX, double centerWorldZ) {
        this(x, y, size, centerWorldX, centerWorldZ, 1.0);
    }

    public static final double MIN_ZOOM = 1.0;
    public static final double MAX_ZOOM = 4.0;
    public static final double DEFAULT_ZOOM = 1.5;
    /// 1.0 倍界面缩放时的小地图边长（GUI 像素）。
    public static final int BASE_SIZE = 128;
    /// 小地图与屏幕边缘的间距（GUI 像素）。
    static final int MARGIN = 6;
    /// 小地图可见基准范围；纹理额外覆盖旋转余量。
    public static final int COVERED_BLOCKS = 16 * 9;
    /// 地图内容与外轮廓之间的安全间距（GUI 像素）。
    public static final int CLIP_INSET = 3;

    private static double focusProgress;
    private static long lastFocusUpdate;
    private static final MinimapZoom VIEW_ZOOM = new MinimapZoom();

    /// 根据配置与屏幕尺寸计算布局；玩家为空时返回 {@code null}。
    public static MinimapLayout of(AtlasClientConfig config, Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            focusProgress = 0.0;
            lastFocusUpdate = 0L;
            return null;
        }
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int[] normal = computeBox(config.minimap.position, config.minimap.scale,
                config.minimap.offsetX, config.minimap.offsetY, screenWidth, screenHeight);
        boolean focused = focused(minecraft);
        long now = System.nanoTime();
        double elapsed = lastFocusUpdate == 0L ? 0.0 : (now - lastFocusUpdate) / 1.0E9;
        lastFocusUpdate = now;
        double delta = Math.min(elapsed, 0.1) * 8.0;
        focusProgress = focused ? Math.min(1.0, focusProgress + delta)
                : Math.max(0.0, focusProgress - delta);
        int[] box = normal;
        if (focusProgress > 0.0) {
            int[] expanded = focusedBox(screenWidth, screenHeight);
            box = new int[3];
            for (int index = 0; index < box.length; index++) {
                box[index] = (int) Math.round(normal[index]
                        + (expanded[index] - normal[index]) * focusProgress);
            }
        }
        var position = player.getPosition(minecraft.getTimer().getGameTimeDeltaPartialTick(false));
        ClientMapManager manager = AtlasClientMap.manager();
        boolean cave = manager != null && manager.displaySlice().cave();
        double zoom = VIEW_ZOOM.sample(MinimapZoom.target(config, cave), config.minimap.smoothZoom, now);
        return new MinimapLayout(box[0], box[1], box[2],
                position.x, position.z, zoom);
    }

    /// 计算小地图的尺寸与左上角坐标：`[x, y, size]`。
    ///
    /// 尺寸受限不超过屏幕短边可用空间，原点统一钳制到屏幕内，
    /// 避免极端缩放与自定义偏移下算出负坐标或溢出屏幕。
    static int[] computeBox(MinimapPosition position, double configuredScale, int offsetX,
            int offsetY, int screenWidth, int screenHeight) {
        float scale = (float) ConfigBounds.clamp(configuredScale, 0.5, 4.0);
        int margin = MARGIN;
        int maxSize = Math.max(BASE_SIZE / 2, Math.min(screenWidth, screenHeight) - margin * 2);
        int size = Math.min(Math.round(BASE_SIZE * scale), maxSize);
        int[] origin = switch (position) {
            case TOP_LEFT -> new int[] { margin, margin };
            case TOP_RIGHT -> new int[] { screenWidth - size - margin, margin };
            case BOTTOM_LEFT -> new int[] { margin, screenHeight - size - margin };
            case BOTTOM_RIGHT ->
                new int[] { screenWidth - size - margin, screenHeight - size - margin };
            case CUSTOM -> new int[] {
                    clamp(offsetX, 0, Math.max(0, screenWidth - size)),
                    clamp(offsetY, 0, Math.max(0, screenHeight - size)) };
        };
        origin[0] = clamp(origin[0], 0, Math.max(0, screenWidth - size));
        origin[1] = clamp(origin[1], 0, Math.max(0, screenHeight - size));
        return new int[] { origin[0], origin[1], size };
    }

    public static boolean focused(Minecraft minecraft) {
        return minecraft.screen == null && AtlasKeyMappings.FOCUS_MINIMAP.isDown();
    }

    static int[] focusedBox(int screenWidth, int screenHeight) {
        int size = Math.max(1, Math.min(screenWidth, screenHeight));
        return new int[] { (screenWidth - size) / 2, (screenHeight - size) / 2, size };
    }

    public double coveredBlocks() {
        return COVERED_BLOCKS / this.zoom;
    }

    /// 方形旋转前的绘制边长，保证旋转后的可视方形四角均有地形。
    public static int squareDrawSize(int visibleSize) {
        return (int) Math.ceil(visibleSize * Math.sqrt(2.0));
    }

    /// 世界坐标 → 小地图屏幕坐标（X 轴）。
    public double worldToScreenX(double worldX) {
        double centerScreenX = this.x + this.size / 2.0;
        return centerScreenX + (worldX - this.centerWorldX) / this.coveredBlocks() * this.size;
    }

    /// 世界坐标 → 小地图屏幕坐标（Z 轴）。
    public double worldToScreenZ(double worldZ) {
        double centerScreenZ = this.y + this.size / 2.0;
        return centerScreenZ + (worldZ - this.centerWorldZ) / this.coveredBlocks() * this.size;
    }

    /// 标记位置随地图旋转，调用方在旋转后的坐标上绘制不旋转的图标。
    public double[] rotatedScreenPoint(double worldX, double worldZ, double rotationDegrees) {
        return rotatePoint(this.worldToScreenX(worldX), this.worldToScreenZ(worldZ),
                this.x + this.size / 2.0, this.y + this.size / 2.0, rotationDegrees);
    }

    public static double[] rotatePoint(double screenX, double screenZ,
            double centerX, double centerZ, double rotationDegrees) {
        double dx = screenX - centerX;
        double dz = screenZ - centerZ;
        double radians = Math.toRadians(rotationDegrees);
        return new double[] {
            centerX + dx * Math.cos(radians) - dz * Math.sin(radians),
            centerZ + dx * Math.sin(radians) + dz * Math.cos(radians)
        };
    }

    /// 判断屏幕坐标是否落在小地图范围内（圆形形态下按圆形判定）。
    public boolean contains(double screenX, double screenZ, boolean circular) {
        double centerX = this.x + this.size / 2.0;
        double centerZ = this.y + this.size / 2.0;
        double dx = screenX - centerX;
        double dz = screenZ - centerZ;
        double half = Math.max(0.0, this.size / 2.0 - CLIP_INSET);
        if (circular) {
            return Math.sqrt(dx * dx + dz * dz) <= half;
        }
        return Math.abs(dx) <= half && Math.abs(dz) <= half;
    }

    /// 按当前地图旋转角判断坐标是否落在可视范围内，供标记分类使用。
    public boolean containsRotated(double screenX, double screenZ, boolean circular) {
        return this.containsRotated(screenX, screenZ, circular, currentRotationDegrees());
    }

    /// 按指定地图旋转角判断坐标是否落在可视范围内。
    public boolean containsRotated(double screenX, double screenZ, boolean circular,
            double rotationDegrees) {
        double centerX = this.x + this.size / 2.0;
        double centerZ = this.y + this.size / 2.0;
        double dx = screenX - centerX;
        double dz = screenZ - centerZ;
        double half = Math.max(0.0, this.size / 2.0 - CLIP_INSET);
        if (circular) {
            return Math.sqrt(dx * dx + dz * dz) <= half;
        }
        double radians = Math.toRadians(rotationDegrees);
        double rotatedX = dx * Math.cos(radians) - dz * Math.sin(radians);
        double rotatedZ = dx * Math.sin(radians) + dz * Math.cos(radians);
        return Math.abs(rotatedX) <= half && Math.abs(rotatedZ) <= half;
    }

    /// 把标记中心限制在地图边界内，并为标记半径预留边距。
    ///
    /// 圆形地图按半径缩放，方形地图按旋转后的最大坐标轴等比缩放；
    /// 标记方向保持不变，旋转后的中心仍落在可视方框内。
    public double[] fitInside(double screenX, double screenZ, boolean circular, double margin) {
        return this.fitInside(screenX, screenZ, circular, margin, currentRotationDegrees());
    }

    /// 按指定地图旋转角约束标记中心，供方向与旋转边界测试使用。
    public double[] fitInside(double screenX, double screenZ, boolean circular, double margin,
            double rotationDegrees) {
        double centerX = this.x + this.size / 2.0;
        double centerZ = this.y + this.size / 2.0;
        double dx = screenX - centerX;
        double dz = screenZ - centerZ;
        double limit = Math.max(0.0, this.size / 2.0 - CLIP_INSET - margin);
        if (circular) {
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length > limit && length > 0.0) {
                double scale = limit / length;
                dx *= scale;
                dz *= scale;
            }
        } else {
            double radians = Math.toRadians(rotationDegrees);
            double rotatedX = dx * Math.cos(radians) - dz * Math.sin(radians);
            double rotatedZ = dx * Math.sin(radians) + dz * Math.cos(radians);
            double maxAxis = Math.max(Math.abs(rotatedX), Math.abs(rotatedZ));
            if (maxAxis > limit && maxAxis > 0.0) {
                double scale = limit / maxAxis;
                dx *= scale;
                dz *= scale;
            }
        }
        return new double[] { centerX + dx, centerZ + dz };
    }

    private static double currentRotationDegrees() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return 0.0;
        }
        return MinimapRenderer.mapRotationDegrees(minecraft.player.getYRot(),
                AtlasClientConfigs.CONFIG.minimap.lockNorth);
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
