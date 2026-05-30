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

    private static void renderPlayerMarker(
        Minecraft minecraft, GuiGraphicsExtractor graphics,
        int mapX, int mapY, int mapSize, boolean circleClip
    ) {
        int cx = mapX + mapSize / 2, cy = mapY + mapSize / 2;
        boolean northLocked = !AtlasClientState.isRotateWithPlayer();
        float arrowAngle = 0f;
        if (northLocked && minecraft.player != null) {
            float yawRad = (float) Math.toRadians(minecraft.player.getYRot());
            arrowAngle = (float) Math.atan2(-Math.sin(yawRad), Math.cos(yawRad));
        }
        float pr = 4.0f, pad = pr + 1.5f;
        double gs = minecraft.getWindow().getGuiScale();
        int wh = minecraft.getWindow().getHeight();
        float fcCx = (float) (cx * gs), fcCy = (float) (wh - cy * gs);
        float fcHalf = (float) (mapSize / 2.0 * gs), fcMode = circleClip ? 1f : 0f;
        float fmCx = (float) Math.floor(cx * gs) + 0.5f, fmCy = (float) Math.floor(wh - cy * gs) + 0.5f, fmR = pr * (float) gs;
        float x0 = cx - pad, y0 = cy - pad, x1 = cx + pad, y1 = cy + pad;

        if (northLocked) {
            @Nullable GpuBufferSlice u = ArrowMarkerRenderState.createArrowMarkerUniform(
                new Vector2f(fcCx, fcCy), new Vector2f(fcHalf, fcHalf), fcHalf, fcMode,
                new Vector2f(fmCx, fmCy), fmR, arrowAngle
            );
            if (u == null) {
                graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, PLAYER_COLOR);
                return;
            }
            graphics.submitGuiElementRenderState(new ArrowMarkerRenderState(
                graphics.pose(),
                new Vector2f(x0, y0), new Vector2f(x1, y0), new Vector2f(x1, y1), new Vector2f(x0, y1),
                PLAYER_COLOR, u, graphics.peekScissorStack()
            ));
        } else {
            @Nullable GpuBufferSlice u = MarkerRenderState.createMarkerUniform(
                new Vector2f(fcCx, fcCy), new Vector2f(fcHalf, fcHalf), fcHalf, fcMode,
                new Vector2f(fmCx, fmCy), fmR
            );
            if (u == null) {
                graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, PLAYER_COLOR);
                return;
            }
            graphics.submitGuiElementRenderState(new MarkerRenderState(
                graphics.pose(),
                new Vector2f(x0, y0), new Vector2f(x1, y0), new Vector2f(x1, y1), new Vector2f(x0, y1),
                PLAYER_COLOR, u, graphics.peekScissorStack()
            ));
        }
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        MinimapHudSupport.MinimapContext context = MinimapHudSupport.captureContext(guiGraphics);
        if (context == null) return;
        renderPlayerMarker(context.minecraft(), guiGraphics, context.mapX(), context.mapY(), context.mapSize(), context.circleMode());
    }
}
