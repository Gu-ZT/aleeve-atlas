package dev.dubhe.map.client.map;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.dubhe.map.AleeveAtlas;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/// 地图标记的解析几何绘制。
///
/// 圆点、菱形和方向箭头均由片元着色器生成抗锯齿轮廓，避免逐像素菱形与箭头。
public final class MapMarkerRenderer {
    private static final Identifier WHITE_TEXTURE = AleeveAtlas.id("textures/gui/white.png");

    private MapMarkerRenderer() {
    }

    /// 绘制圆点标记。
    public static void drawDot(GuiGraphics graphics, double centerX, double centerY,
            int size, int color) {
        blit(graphics, AtlasRenderPipelines.MAP_MARKER_DOT, centerX, centerY, size, color);
    }

    /// 绘制光滑菱形标记。
    public static void drawDiamond(GuiGraphics graphics, double centerX, double centerY,
            int size, int color) {
        blit(graphics, AtlasRenderPipelines.MAP_MARKER_DIAMOND, centerX, centerY, size, color);
    }

    /// 绘制带深色描边的光滑叉形标记。
    public static void drawCross(GuiGraphics graphics, double centerX, double centerY,
            int size, int color) {
        blit(graphics, AtlasRenderPipelines.MAP_MARKER_CROSS, centerX, centerY, size, color);
    }

    /// 绘制抗锯齿临时标点波纹。
    public static void drawRipple(GuiGraphics graphics, double centerX, double centerY,
            int size, int color) {
        blit(graphics, AtlasRenderPipelines.MAP_PING_RIPPLE, centerX, centerY, size, color);
    }

    /// 计算基础朝上箭头应旋转的角度，使其指向目标方向。
    public static double directionAngle(double dx, double dz) {
        return Math.atan2(dz, dx) + Math.PI / 2.0;
    }

    /// 绘制指向目标方向的三角箭头，基础形状尖端朝上。
    public static void drawArrow(GuiGraphics graphics, double centerX, double centerY,
            int size, double angleRadians, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) centerX, (float) centerY);
        graphics.pose().rotate((float) angleRadians);
        blit(graphics, AtlasRenderPipelines.MAP_MARKER_ARROW, 0.0, 0.0, size, color);
        graphics.pose().popMatrix();
    }

    private static void blit(GuiGraphics graphics, RenderPipeline pipeline,
            double centerX, double centerY, int size, int color) {
        int actualSize = Math.max(2, size);
        // 保留亚像素位置；奇数尺寸也必须精确居中，避免平移和缩放时逐像素跳动。
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) (centerX - actualSize / 2.0),
                (float) (centerY - actualSize / 2.0));
        graphics.blit(pipeline, WHITE_TEXTURE, 0, 0, 0.0F, 0.0F,
                actualSize, actualSize, 2, 2, 2, 2, color);
        graphics.pose().popMatrix();
    }
}
