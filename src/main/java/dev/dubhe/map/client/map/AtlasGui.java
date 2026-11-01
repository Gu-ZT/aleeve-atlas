package dev.dubhe.map.client.map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/** Textured GUI quads using Minecraft 1.21.1's render buffers. */
public final class AtlasGui {
    private AtlasGui() {
    }

    public static void blit(GuiGraphics graphics, Function<ResourceLocation, RenderType> renderType,
            ResourceLocation texture, int x, int y, float u, float v, int width, int height,
            int textureWidth, int textureHeight) {
        blit(graphics, renderType, texture, x, y, u, v, width, height,
                width, height, textureWidth, textureHeight, -1);
    }

    public static void blit(GuiGraphics graphics, Function<ResourceLocation, RenderType> renderType,
            ResourceLocation texture, int x, int y, float u, float v, int width, int height,
            int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        blit(graphics, renderType, texture, x, y, u, v, width, height,
                regionWidth, regionHeight, textureWidth, textureHeight, -1);
    }

    public static void blit(GuiGraphics graphics, Function<ResourceLocation, RenderType> renderType,
            ResourceLocation texture, int x, int y, float u, float v, int width, int height,
            int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        quad(graphics, renderType.apply(texture), x, y, width, height,
                u / textureWidth, (u + regionWidth) / textureWidth,
                v / textureHeight, (v + regionHeight) / textureHeight, color);
    }

    public static void blitSprite(GuiGraphics graphics, Function<ResourceLocation, RenderType> renderType,
            TextureAtlasSprite sprite, int x, int y, int width, int height) {
        blitSprite(graphics, renderType, sprite, x, y, width, height, -1);
    }

    public static void blitSprite(GuiGraphics graphics, Function<ResourceLocation, RenderType> renderType,
            TextureAtlasSprite sprite, int x, int y, int width, int height, int color) {
        quad(graphics, renderType.apply(sprite.atlasLocation()), x, y, width, height,
                sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), color);
    }

    private static void quad(GuiGraphics graphics, RenderType type, int x, int y, int width, int height,
            float u0, float u1, float v0, float v1, int color) {
        var pose = graphics.pose().last().pose();
        var vertices = graphics.bufferSource().getBuffer(type);
        vertices.addVertex(pose, x, y, 0).setUv(u0, v0).setColor(color);
        vertices.addVertex(pose, x, y + height, 0).setUv(u0, v1).setColor(color);
        vertices.addVertex(pose, x + width, y + height, 0).setUv(u1, v1).setColor(color);
        vertices.addVertex(pose, x + width, y, 0).setUv(u1, v0).setColor(color);
        graphics.flush();
    }
}
