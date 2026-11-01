package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.config.PlayerIndicatorType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3x2f;

/// 两张地图共用的玩家指示器，位置和旋转均以玩家坐标为锚点。
final class PlayerIndicatorRenderer {
    private static final ResourceLocation WHITE_TEXTURE = AleeveAtlas.id("textures/gui/white.png");
    private static final ResourceLocation PLAYER_SPRITE = ResourceLocation.withDefaultNamespace("player");

    private PlayerIndicatorRenderer() {
    }

    static void draw(GuiGraphics graphics, PlayerIndicatorType type, double x, double y,
            float playerYaw, float mapRotation, float scale) {
        graphics.pose().pushPose();
        graphics.pose().mulPose(new org.joml.Matrix4f().set(transform(x, y, playerYaw, mapRotation, scale).get4x4(new float[16])));
        switch (type) {
            case ANTIALIASED_DROPLET -> graphics.blit(AtlasRenderPipelines.PLAYER_DROPLET,
                    WHITE_TEXTURE, -16, -16, 0f, 0f, 32, 32, 2, 2, 2, 2, -1);
            case VANILLA -> drawVanilla(graphics);
        }
        graphics.pose().popPose();
    }

    static float minimapScale(MinimapLayout layout) {
        return layout.size() / (float) MinimapLayout.BASE_SIZE;
    }

    static Matrix3x2f transform(double x, double y, float playerYaw, float mapRotation, float scale) {
        // 两种图案的纹理尖端均朝上；Minecraft 的零度朝向为南。
        float angle = (float) Math.toRadians((playerYaw + 180d + mapRotation) % 360);
        return new Matrix3x2f().translation((float) x, (float) y).rotate(angle).scale(scale);
    }

    private static void drawVanilla(GuiGraphics graphics) {
        var atlas = (net.minecraft.client.renderer.texture.TextureAtlas) Minecraft.getInstance().getTextureManager()
                .getTexture(ResourceLocation.withDefaultNamespace("textures/atlas/map_decorations.png"));
        var sprite = atlas.getSprite(PLAYER_SPRITE);
        // 原版图案可见包围框中心位于 8×8 纹理的 (4.5, 3.5)，修正其半像素偏移。
        graphics.pose().translate(-0.75f, 0.75f, 0.0F);
        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                if (offsetX != 0 || offsetY != 0) {
                    graphics.blitSprite(AtlasRenderPipelines.GUI_TEXTURED, sprite,
                            -6 + offsetX, -6 + offsetY, 12, 12, 0xFF000000);
                }
            }
        }
        graphics.blitSprite(AtlasRenderPipelines.GUI_TEXTURED, sprite, -6, -6, 12, 12);
    }
}
