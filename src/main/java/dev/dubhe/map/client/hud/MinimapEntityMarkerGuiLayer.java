package dev.dubhe.map.client.hud;

import dev.dubhe.map.client.radar.AtlasRadar;
import dev.dubhe.map.client.waypoint.WaypointRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

public class MinimapEntityMarkerGuiLayer implements GuiLayer {
    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        MinimapHudSupport.MinimapContext context = MinimapHudSupport.captureContext(guiGraphics);
        if (context == null) {
            return;
        }

        AtlasRadar.render(
            context.minecraft(),
            guiGraphics,
            context.mapX(),
            context.mapY(),
            context.mapSize(),
            context.rotationDeg()
        );
        WaypointRenderer.renderMinimap(
            context.minecraft(),
            guiGraphics,
            context.mapX(),
            context.mapY(),
            context.mapSize(),
            context.rotationDeg()
        );
    }
}
