package dev.dubhe.map.client.hud;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.dubhe.map.client.AtlasClientState;
import dev.dubhe.map.client.render.state.ArrowMarkerRenderState;
import dev.dubhe.map.client.render.state.MarkerRenderState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.joml.Vector2f;

import javax.annotation.Nullable;

public class MinimapSelfMarkerGuiLayer implements GuiLayer {
    private static final int PLAYER_COLOR = 0xFFFFCC00;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        MinimapHudSupport.MinimapContext context = MinimapHudSupport.captureContext(guiGraphics);
        if (context == null) {
            return;
        }

        renderPlayerMarker(context.minecraft(), guiGraphics, context.mapX(), context.mapY(), context.mapSize(), context.circleMode());
    }

    private static void renderPlayerMarker(
        Minecraft minecraft,
        GuiGraphicsExtractor graphics,
        int mapX,
        int mapY,
        int mapSize,
        boolean circleClip
    ) {
        int cx = mapX + mapSize / 2;
        int cy = mapY + mapSize / 2;

        boolean northLocked = !AtlasClientState.isRotateWithPlayer();
        float arrowAngle = 0.0f;
        if (northLocked && minecraft.player != null) {
            float yawRad = (float) Math.toRadians(minecraft.player.getYRot());
            float dirMapX = (float) -Math.sin(yawRad);
            float dirMapY = (float) Math.cos(yawRad);
            arrowAngle = (float) Math.atan2(dirMapX, dirMapY);
        }

        float playerRadiusGui = 4.0f;
        float pad = playerRadiusGui + 1.5f;

        double guiScale = minecraft.getWindow().getGuiScale();
        int windowHeight = minecraft.getWindow().getHeight();
        float fbClipCx = (float) (cx * guiScale);
        float fbClipCy = (float) (windowHeight - cy * guiScale);
        float fbClipHalf = (float) (mapSize / 2.0 * guiScale);
        float fbClipMode = circleClip ? 1.0f : 0.0f;

        float fbMarkerCx = (float) Math.floor(cx * guiScale) + 0.5f;
        float fbMarkerCy = (float) Math.floor(windowHeight - cy * guiScale) + 0.5f;
        float fbRadius = playerRadiusGui * (float) guiScale;

        float x0 = cx - pad;
        float y0 = cy - pad;
        float x1 = cx + pad;
        float y1 = cy + pad;

        if (northLocked) {
            @Nullable GpuBufferSlice uniform = ArrowMarkerRenderState.createArrowMarkerUniform(
                new Vector2f(fbClipCx, fbClipCy),
                new Vector2f(fbClipHalf, fbClipHalf),
                fbClipHalf,
                fbClipMode,
                new Vector2f(fbMarkerCx, fbMarkerCy),
                fbRadius,
                arrowAngle
            );

            if (uniform == null) {
                graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, PLAYER_COLOR);
                return;
            }

            graphics.submitGuiElementRenderState(new ArrowMarkerRenderState(
                graphics.pose(),
                new Vector2f(x0, y0), new Vector2f(x1, y0),
                new Vector2f(x1, y1), new Vector2f(x0, y1),
                PLAYER_COLOR,
                uniform,
                graphics.peekScissorStack()
            ));
        } else {
            @Nullable GpuBufferSlice uniform = MarkerRenderState.createMarkerUniform(
                new Vector2f(fbClipCx, fbClipCy),
                new Vector2f(fbClipHalf, fbClipHalf),
                fbClipHalf,
                fbClipMode,
                new Vector2f(fbMarkerCx, fbMarkerCy),
                fbRadius
            );

            if (uniform == null) {
                graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, PLAYER_COLOR);
                return;
            }

            graphics.submitGuiElementRenderState(new MarkerRenderState(
                graphics.pose(),
                new Vector2f(x0, y0), new Vector2f(x1, y0),
                new Vector2f(x1, y1), new Vector2f(x0, y1),
                PLAYER_COLOR,
                uniform,
                graphics.peekScissorStack()
            ));
        }
    }
}
