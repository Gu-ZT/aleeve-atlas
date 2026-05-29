package dev.dubhe.map.client.hud;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.dubhe.map.client.render.state.MinimapFrameRenderState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.joml.Vector2f;

import javax.annotation.Nullable;

public class MinimapBackgroudGuiLayer implements GuiLayer {
    private static final int BORDER_COLOR = 0xCC000000;
    private static final int BACKGROUND_COLOR = 0x80000000;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        MinimapHudSupport.MinimapContext context = MinimapHudSupport.captureContext(guiGraphics);
        if (context == null) return;

        if (context.circleMode()) {
            renderCircularFrame(guiGraphics, context.minecraft(), context.mapX(), context.mapY(), context.mapSize());
            return;
        }

        guiGraphics.outline(context.mapX(), context.mapY(), context.mapSize(), context.mapSize(), BORDER_COLOR);
        guiGraphics.fill(context.mapX(), context.mapY(), context.mapRight(), context.mapBottom(), BACKGROUND_COLOR);
    }

    private static void renderCircularFrame(GuiGraphicsExtractor graphics, Minecraft minecraft, int mapX, int mapY, int mapSize) {
        int centerX = mapX + mapSize / 2, centerY = mapY + mapSize / 2;
        float radiusGui = mapSize / 2.0F;
        float borderWidthGui = Math.max(1.0F, minecraft.getWindow().getGuiScale());
        double guiScale = minecraft.getWindow().getGuiScale();
        int windowHeight = minecraft.getWindow().getHeight();

        @Nullable GpuBufferSlice frameUniform = MinimapFrameRenderState.createFrameUniform(
            new Vector2f((float)(centerX * guiScale), (float)(windowHeight - centerY * guiScale)),
            radiusGui * (float)guiScale, borderWidthGui * (float)guiScale, BACKGROUND_COLOR, BORDER_COLOR);

        if (frameUniform == null) {
            drawCircleFilled(graphics, centerX, centerY, mapSize / 2, BACKGROUND_COLOR);
            drawCircleOutline(graphics, centerX, centerY, mapSize / 2, BORDER_COLOR);
            return;
        }

        graphics.submitGuiElementRenderState(new MinimapFrameRenderState(graphics.pose(),
            new Vector2f(mapX, mapY), new Vector2f(mapX + mapSize, mapY),
            new Vector2f(mapX + mapSize, mapY + mapSize), new Vector2f(mapX, mapY + mapSize),
            0xFFFFFFFF, frameUniform, graphics.peekScissorStack()));
    }

    @SuppressWarnings("SameParameterValue")
    private static void drawCircleFilled(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int y = -r; y <= r; y++) { int s = (int)Math.sqrt(r*r - y*y); graphics.fill(cx-s, cy+y, cx+s+1, cy+y+1, color); }
    }
    @SuppressWarnings("SameParameterValue")
    private static void drawCircleOutline(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        for (int y = -r; y <= r; y++) { int s = (int)Math.sqrt(r*r - y*y); graphics.fill(cx-s, cy+y, cx-s+1, cy+y+1, color); graphics.fill(cx+s, cy+y, cx+s+1, cy+y+1, color); }
    }
}
